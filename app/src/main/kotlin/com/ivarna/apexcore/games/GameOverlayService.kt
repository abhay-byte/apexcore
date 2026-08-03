package com.ivarna.apexcore.games

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.util.TypedValue
import android.view.Choreographer
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.Toast
import androidx.compose.animation.animateColor
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.*
import androidx.savedstate.*
import com.ivarna.apexcore.R
import com.ivarna.apexcore.fps.FpsStack
import com.ivarna.apexcore.fps.model.FpsMethod
import com.ivarna.apexcore.freeze.FreezeFramework
import com.ivarna.apexcore.ui.components.zenGlassBackground
import com.ivarna.apexcore.ui.theme.*
import kotlinx.coroutines.*

/**
 * Game HUD overlay.
 *
 * FPS uses the factualstats multi-tier stack:
 * DMA daemon (Adreno cmdbatch-hybrid / Mali dma_fence) → SurfaceFlinger → gfxinfo,
 * with Choreographer only as last-resort for the own-app test overlay.
 */
class GameOverlayService : Service(), Choreographer.FrameCallback, LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {

    private lateinit var wm: WindowManager
    private lateinit var overlayView: FrameLayout
    private var params: WindowManager.LayoutParams? = null
    private var gamePkg: String? = null
    private var scope = CoroutineScope(Dispatchers.Main + Job())
    private var statsJob: Job? = null
    private var fpsJob: Job? = null
    private var exitWatcher: Job? = null
    private var autoCollapseJob: Job? = null

    // Choreographer fallback (own-app test only when real stack returns 0)
    private var frameCount = 0
    private var lastFpsTime = 0L
    private var choreographerFps = 0
    private var useChoreographerFallback = false

    private val fpsStack by lazy { FpsStack.get(applicationContext) }

    // Lifecycle/Compose Boilerplate for Service
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val store = ViewModelStore()
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore get() = store
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    // Dynamic UI states
    private val isExpandedState = mutableStateOf(false)
    private val fpsState = mutableStateOf(0)
    /** Normalized 0–1 FPS history for the sparkline (relative to refresh / 120). */
    private val fpsHistory = mutableStateListOf<Float>()
    /** Per-core load 0–1 for equalizer bars (factualstats /proc/stat). */
    private val cpuCoreLoads = mutableStateListOf<Float>()
    private val cpuLoadState = mutableStateOf(0f)
    private val isBoosting = mutableStateOf(false)
    private var fpsGraphCeiling = 120f

    private val density: Float get() = resources.displayMetrics.density

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)

        FreezeFramework.init(applicationContext)

        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
        buildOverlay()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        gamePkg = intent?.getStringExtra(EXTRA_PKG)
        startForeground(NOTIF_ID, buildNotification())

        if (overlayView.parent == null) {
            wm.addView(overlayView, createLayoutParams())
        }
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)

        fpsStack.repository.setTargetPackage(gamePkg)
        fpsStack.repository.ensureDaemonStarted()
        startChoreographerFallback()
        startFpsPolling()
        startStatsUpdates()
        startExitWatcher()
        return START_REDELIVER_INTENT
    }

    override fun onDestroy() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        statsJob?.cancel()
        fpsJob?.cancel()
        exitWatcher?.cancel()
        autoCollapseJob?.cancel()
        Choreographer.getInstance().removeFrameCallback(this)
        try {
            fpsStack.repository.stopDaemon()
        } catch (_: Throwable) {
        }
        try {
            if (overlayView.parent != null) wm.removeView(overlayView)
        } catch (_: Throwable) {}
        store.clear()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun doFrame(frameTimeNanos: Long) {
        val now = SystemClock.elapsedRealtime()
        frameCount++
        if (now - lastFpsTime >= 500) {
            choreographerFps = frameCount * 2
            frameCount = 0
            lastFpsTime = now
            if (useChoreographerFallback) {
                fpsState.value = choreographerFps
            }
        }
        Choreographer.getInstance().postFrameCallback(this)
    }

    private fun startChoreographerFallback() {
        lastFpsTime = SystemClock.elapsedRealtime()
        frameCount = 0
        Choreographer.getInstance().postFrameCallback(this)
    }

    /**
     * Poll factualstats cascade every 1s.
     * Games: SF → (no DMA, no gfxinfo). UI: DMA → SF → gfxinfo.
     */
    private fun startFpsPolling() {
        fpsJob?.cancel()
        fpsJob = scope.launch {
            while (isActive) {
                try {
                    val snapshot = withContext(Dispatchers.IO) {
                        fpsStack.repository.getFps()
                    }
                    if (snapshot.currentFps > 0f && snapshot.method != FpsMethod.NONE) {
                        useChoreographerFallback = false
                        val fps = snapshot.currentFps.coerceIn(1f, 240f)
                        fpsState.value = fps.toInt()
                        pushFpsHistory(fps)
                    } else {
                        // No real reading — allow Choreographer only for self-test HUD
                        useChoreographerFallback =
                            gamePkg == null || gamePkg == packageName
                        if (useChoreographerFallback && choreographerFps > 0) {
                            fpsState.value = choreographerFps
                            pushFpsHistory(choreographerFps.toFloat())
                        }
                    }
                } catch (t: Throwable) {
                    android.util.Log.w(TAG, "FPS poll failed: ${t.message}")
                    useChoreographerFallback =
                        gamePkg == null || gamePkg == packageName
                }
                delay(1000L)
            }
        }
    }

    private fun pushFpsHistory(fps: Float) {
        // Raise ceiling gradually so a 60fps game does not sit as a flat mid line on 120 scale
        if (fps > fpsGraphCeiling * 0.9f) {
            fpsGraphCeiling = (fps * 1.15f).coerceIn(60f, 240f)
        }
        val norm = (fps / fpsGraphCeiling).coerceIn(0.02f, 1f)
        if (fpsHistory.size >= HISTORY_POINTS) {
            fpsHistory.removeAt(0)
        }
        fpsHistory.add(norm)
    }

    private fun startStatsUpdates() {
        statsJob = scope.launch {
            // Seed flat baseline so first paint is not empty
            if (fpsHistory.isEmpty()) {
                repeat(HISTORY_POINTS) { fpsHistory.add(0.05f) }
            }
            // Warm CPU counters (first sample is always 0% with /proc/stat deltas)
            withContext(Dispatchers.IO) {
                runCatching { fpsStack.cpuDataSource.readCpuStats() }
            }
            delay(400L)

            while (isActive) {
                updateCpuStats()
                delay(1000L)
            }
        }
    }

    private suspend fun updateCpuStats() {
        val snap = withContext(Dispatchers.IO) {
            runCatching { fpsStack.cpuDataSource.readCpuStats() }.getOrNull()
        } ?: return

        cpuLoadState.value = (snap.overallLoadPercent / 100f).coerceIn(0f, 1f)

        val loads = if (snap.cores.isNotEmpty()) {
            snap.cores.map { (it.loadPercent / 100f).coerceIn(0.05f, 1f) }
        } else {
            // Fallback: synthesise 8 bars from overall load with mild shape
            val base = cpuLoadState.value.coerceIn(0.08f, 1f)
            List(8) { i ->
                val factor = when (i) {
                    0, 7 -> 0.45f
                    1, 6 -> 0.65f
                    2, 5 -> 0.85f
                    else -> 1f
                }
                (base * factor).coerceIn(0.08f, 1f)
            }
        }

        cpuCoreLoads.clear()
        // Show up to 8 bars; subsample many-core SoCs
        val display = if (loads.size <= 8) {
            loads
        } else {
            val step = loads.size / 8f
            (0 until 8).map { i -> loads[(i * step).toInt().coerceIn(0, loads.lastIndex)] }
        }
        cpuCoreLoads.addAll(display)
    }

    private fun startExitWatcher() {
        val pkg = gamePkg ?: return
        if (pkg == packageName) return // Don't self-terminate on test overlay
        exitWatcher = scope.launch {
            while (isActive) {
                delay(5000L)
                val running = isPackageOnTop(pkg)
                if (!running) break
            }
            shutdown()
        }
    }

    private fun isPackageOnTop(pkg: String): Boolean {
        try {
            val usm = getSystemService(Context.USAGE_STATS_SERVICE) as android.app.usage.UsageStatsManager
            val time = System.currentTimeMillis()
            val stats = usm.queryUsageStats(
                android.app.usage.UsageStatsManager.INTERVAL_DAILY,
                time - 1000 * 60,
                time
            )
            if (stats != null && stats.isNotEmpty()) {
                val sorted = stats.sortedByDescending { it.lastTimeUsed }
                return sorted.first().packageName == pkg
            }
        } catch (_: Throwable) {}
        return true
    }

    /**
     * Packages BOOST must never force-stop:
     * - ApexCore (always)
     * - Monitored game ([gamePkg])
     * - Whatever is currently resumed/foreground (UsageStats + dumpsys fallback)
     */
    private fun resolveBoostProtectPackages(): Set<String> {
        val protect = linkedSetOf<String>()
        protect.add(packageName)
        gamePkg?.takeIf { it.isNotBlank() }?.let { protect.add(it) }
        resolveForegroundPackage()?.let { protect.add(it) }
        return protect
    }

    private fun resolveForegroundPackage(): String? {
        // Prefer recent non-self usage (overlay steals window focus)
        try {
            val usm = getSystemService(Context.USAGE_STATS_SERVICE) as android.app.usage.UsageStatsManager
            val time = System.currentTimeMillis()
            val stats = usm.queryUsageStats(
                android.app.usage.UsageStatsManager.INTERVAL_DAILY,
                time - 1000 * 60 * 5,
                time
            )
            if (stats != null && stats.isNotEmpty()) {
                val top = stats
                    .filter { it.packageName != packageName && it.lastTimeUsed > 0 }
                    .maxByOrNull { it.lastTimeUsed }
                    ?.packageName
                if (!top.isNullOrBlank()) return top
            }
        } catch (_: Throwable) {
        }
        // dumpsys activity ResumedActivity
        try {
            val proc = Runtime.getRuntime().exec(
                arrayOf("sh", "-c", "dumpsys activity activities 2>/dev/null | grep -E 'ResumedActivity|mResumedActivity' | head -5")
            )
            val out = proc.inputStream.bufferedReader().readText()
            proc.waitFor()
            val match = Regex("""([a-zA-Z0-9_.]+)/[a-zA-Z0-9_.$]+""").findAll(out)
                .map { it.groupValues[1] }
                .firstOrNull { it != packageName && it.contains('.') }
            if (match != null) return match
        } catch (_: Throwable) {
        }
        return null
    }

    private fun shutdown() {
        Choreographer.getInstance().removeFrameCallback(this)
        statsJob?.cancel()
        fpsJob?.cancel()
        exitWatcher?.cancel()
        autoCollapseJob?.cancel()
        try {
            fpsStack.repository.stopDaemon()
        } catch (_: Throwable) {
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        try {
            if (overlayView.parent != null) wm.removeView(overlayView)
        } catch (_: Throwable) {}
        stopSelf()
    }

    private fun createLayoutParams(): WindowManager.LayoutParams {
        val w = if (isExpandedState.value) dpf(104f).toInt() else dpf(16f).toInt() // Zen glass column
        val h = if (isExpandedState.value) dpf(340f).toInt() else dpf(140f).toInt()
        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        val lp = WindowManager.LayoutParams(
            w, h,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        )
        lp.gravity = Gravity.TOP or Gravity.START
        lp.x = 0 // Left edge as specified
        lp.y = dpf(220f).toInt()
        params = lp
        return lp
    }

    private fun buildOverlay() {
        overlayView = FrameLayout(this).apply {
            setPadding(0, 0, 0, 0)
            setOnTouchListener(OverlayTouchListener())
            setViewTreeLifecycleOwner(this@GameOverlayService)
            setViewTreeViewModelStoreOwner(this@GameOverlayService)
            setViewTreeSavedStateRegistryOwner(this@GameOverlayService)
        }

        val composeView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@GameOverlayService)
            setViewTreeViewModelStoreOwner(this@GameOverlayService)
            setViewTreeSavedStateRegistryOwner(this@GameOverlayService)
            setContent {
                // Match Settings theme preference (System / Light / Dark)
                val systemDark = isSystemInDarkTheme()
                val darkTheme = ThemePreferences.get(this@GameOverlayService)
                    .resolveDark(systemDark)
                ApexCoreTheme(darkTheme = darkTheme) {
                    OverlayContent(
                        isExpanded = isExpandedState.value,
                        fps = fpsState.value,
                        fpsHistory = fpsHistory,
                        cpuCoreLoads = cpuCoreLoads,
                        cpuLoad = cpuLoadState.value,
                        isBoosting = isBoosting.value,
                        onBoostClick = {
                            if (isBoosting.value) return@OverlayContent
                            isBoosting.value = true
                            scope.launch {
                                try {
                                    val result = withContext(Dispatchers.IO) {
                                        // Never kill ApexCore, the monitored game, or current foreground.
                                        val protect = resolveBoostProtectPackages()
                                        android.util.Log.i(TAG, "BOOST protect=$protect")
                                        FreezeFramework.freezeAll(
                                            context = applicationContext,
                                            protectPackages = protect
                                        )
                                    }
                                    val msg = when {
                                        result.backend == "blocked" ->
                                            "BOOST needs Shizuku or Root — open setup"
                                        result.killed == 0 && result.freedKb == 0L ->
                                            "Already optimized"
                                        else ->
                                            "BOOST · ${result.killed} apps · +${result.freedKb / 1024} MB"
                                    }
                                    Toast.makeText(this@GameOverlayService, msg, Toast.LENGTH_SHORT).show()
                                } catch (t: Throwable) {
                                    Toast.makeText(this@GameOverlayService, "BOOST failed: ${t.message}", Toast.LENGTH_SHORT).show()
                                } finally {
                                    isBoosting.value = false
                                }
                            }
                        },
                        onToggleExpand = { toggleExpand() }
                    )
                }
            }
        }
        overlayView.addView(composeView)
    }

    private fun toggleExpand() {
        isExpandedState.value = !isExpandedState.value
        val lp = params ?: return
        if (isExpandedState.value) {
            lp.width = dpf(104f).toInt() // Zen glass column
            lp.height = dpf(340f).toInt()
            wm.updateViewLayout(overlayView, lp)
            startAutoCollapseTimer()
        } else {
            lp.width = dpf(16f).toInt() // slim rail touch target
            lp.height = dpf(140f).toInt()
            wm.updateViewLayout(overlayView, lp)
            autoCollapseJob?.cancel()
        }
    }

    private fun startAutoCollapseTimer() {
        autoCollapseJob?.cancel()
        autoCollapseJob = scope.launch {
            delay(5000L) // Auto-minimizes after 5 seconds of inactivity
            if (isExpandedState.value) toggleExpand()
        }
    }

    private inner class OverlayTouchListener : View.OnTouchListener {
        private var initialY = 0
        private var initialTouchY = 0f
        private var isDragging = false

        override fun onTouch(v: View, event: MotionEvent): Boolean {
            val lp = params ?: return false
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialY = lp.y
                    initialTouchY = event.rawY
                    isDragging = false
                    startAutoCollapseTimer() // Reset auto minimize timer on touch
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dy = (event.rawY - initialTouchY).toInt()
                    if (!isDragging && dy * dy > 64) isDragging = true
                    if (isDragging) {
                        lp.y = initialY + dy
                        wm.updateViewLayout(overlayView, lp)
                    }
                    return true
                }
                MotionEvent.ACTION_UP -> {
                    if (!isDragging) {
                        toggleExpand()
                    }
                    return true
                }
            }
            return false
        }
    }

    private fun dpf(v: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics)

    private fun createNotificationChannel() {
        val ch = NotificationChannel(CHANNEL_ID, "Game Overlay", NotificationManager.IMPORTANCE_LOW).apply {
            setShowBadge(false); lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        }
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(ch)
    }

    private fun buildNotification(): Notification =
        Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("ApexCore Overlay")
            .setContentText(gamePkg?.let { "Monitoring $it" } ?: "Game performance overlay active")
            .setSmallIcon(R.drawable.ic_stat_apex)
            .setOngoing(true)
            .setPriority(Notification.PRIORITY_LOW)
            .build()

    companion object {
        private const val TAG = "GameOverlayService"
        private const val CHANNEL_ID = "apexcore_overlay"
        private const val NOTIF_ID = 1001
        private const val HISTORY_POINTS = 48
        const val EXTRA_PKG = "game_pkg"

        fun start(context: Context, pkg: String) {
            if (!android.provider.Settings.canDrawOverlays(context)) return
            val intent = Intent(context, GameOverlayService::class.java).apply {
                putExtra(EXTRA_PKG, pkg)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, GameOverlayService::class.java))
        }
    }
}

/**
 * In-game performance HUD — Zen Organic glass.
 *
 * Same material language as [GlassCard]: theme [surfaceContainerLowest] shell,
 * primary accents, onSurface labels. Follows Settings theme (System/Light/Dark).
 * Left-edge dock: flat start, rounded end (island pebble).
 */
@Composable
fun OverlayContent(
    isExpanded: Boolean,
    fps: Int,
    fpsHistory: List<Float>,
    cpuCoreLoads: List<Float>,
    cpuLoad: Float,
    isBoosting: Boolean,
    onBoostClick: () -> Unit,
    onToggleExpand: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val isThrottling = fps < 50
    val accent = if (isThrottling) scheme.tertiary else scheme.primary
    val labelMuted = scheme.onSurfaceVariant.copy(alpha = 0.78f)
    // Edge-docked island: flat against left, soft Zen radius on free edge
    val glassShape = RoundedCornerShape(
        topStart = 4.dp,
        bottomStart = 4.dp,
        topEnd = ZenDimens.roundedXl,
        bottomEnd = ZenDimens.roundedXl
    )
    // Same glass recipe as GlassCard — near-opaque for legibility over any game BG
    val glassFill = scheme.surfaceContainerLowest.copy(alpha = 0.94f)
    val glassBorder = scheme.outlineVariant.copy(alpha = 0.72f)

    if (!isExpanded) {
        // --- Minimized magnetic rail (theme primary / tertiary) ---
        val infiniteTransition = rememberInfiniteTransition(label = "pulse")
        val solid = accent
        val dim = solid.copy(alpha = 0.28f)

        val pulseColor by infiniteTransition.animateColor(
            initialValue = solid,
            targetValue = dim,
            animationSpec = infiniteRepeatable(
                animation = tween(800, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "pulse_color"
        )

        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(16.dp)
                .clickable { onToggleExpand() },
            contentAlignment = Alignment.CenterStart
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(6.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(solid.copy(alpha = 0.50f), Color.Transparent)
                        )
                    )
            )
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(3.dp)
                    .clip(RoundedCornerShape(topEnd = 2.dp, bottomEnd = 2.dp))
                    .background(pulseColor)
            )
        }
    } else {
        // --- Expanded Zen glass vertical column ---
        // No elevation shadow — TYPE_APPLICATION_OVERLAY clips outside window bounds
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .width(100.dp)
                .zenGlassBackground(
                    shape = glassShape,
                    fill = glassFill,
                    borderColor = glassBorder
                )
                .padding(vertical = 18.dp, horizontal = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // FPS number
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "FPS",
                    color = labelMuted,
                    fontSize = 9.sp,
                    fontFamily = PlusJakartaSans,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp
                )
                Text(
                    text = "$fps",
                    color = when {
                        fps >= 55 -> scheme.primary
                        fps >= 45 -> scheme.secondary
                        else -> scheme.tertiary
                    },
                    fontSize = 28.sp,
                    fontFamily = PlusJakartaSans,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }

            // FPS sparkline (was mislabeled RAM TREND with flat mem fraction)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "FPS GRAPH",
                    color = labelMuted,
                    fontSize = 8.sp,
                    fontFamily = PlusJakartaSans,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.6.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                val sparklineColor = scheme.primary
                val sparkFill = scheme.primary.copy(alpha = 0.12f)
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(36.dp)
                        .padding(horizontal = 2.dp)
                ) {
                    if (fpsHistory.size >= 2) {
                        val path = Path()
                        val fillPath = Path()
                        val pointsCount = fpsHistory.size
                        fpsHistory.forEachIndexed { index, valFrac ->
                            val x = index * (size.width / (pointsCount - 1).coerceAtLeast(1))
                            val y = size.height - (valFrac.coerceIn(0f, 1f) * size.height * 0.92f)
                            if (index == 0) {
                                path.moveTo(x, y)
                                fillPath.moveTo(x, size.height)
                                fillPath.lineTo(x, y)
                            } else {
                                path.lineTo(x, y)
                                fillPath.lineTo(x, y)
                            }
                        }
                        fillPath.lineTo(size.width, size.height)
                        fillPath.close()
                        drawPath(path = fillPath, color = sparkFill)
                        drawPath(
                            path = path,
                            color = sparklineColor,
                            style = Stroke(width = 2.dp.toPx())
                        )
                    }
                }
            }

            // CPU equalizer — real /proc/stat core loads (0–1)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = if (cpuLoad > 0f) {
                        "CPU ${ (cpuLoad * 100).toInt() }%"
                    } else {
                        "CPU LOAD"
                    },
                    color = labelMuted,
                    fontSize = 8.sp,
                    fontFamily = PlusJakartaSans,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.6.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                val barCount = (cpuCoreLoads.size.takeIf { it > 0 } ?: 8).coerceIn(4, 8)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    verticalAlignment = Alignment.Bottom,
                    modifier = Modifier.height(30.dp)
                ) {
                    repeat(barCount) { barIdx ->
                        key(barIdx) {
                            val target = cpuCoreLoads.getOrNull(barIdx)
                                ?: (cpuLoad * when (barIdx % 4) {
                                    0 -> 0.5f
                                    1 -> 0.75f
                                    2 -> 0.9f
                                    else -> 1f
                                }).coerceIn(0.08f, 1f)
                            val animHeight = remember(barIdx) { Animatable(0.08f) }
                            LaunchedEffect(target) {
                                animHeight.animateTo(
                                    targetValue = target.coerceIn(0.08f, 1f),
                                    animationSpec = tween(280, easing = FastOutSlowInEasing)
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .width(4.dp)
                                    .fillMaxHeight(animHeight.value)
                                    .background(scheme.primary, RoundedCornerShape(2.dp))
                            )
                        }
                    }
                }
            }

            // BOOST — primary pebble + water-drop (matches home CTAs)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "BOOST",
                    color = if (isBoosting) scheme.tertiary else labelMuted,
                    fontSize = 8.sp,
                    fontFamily = PlusJakartaSans,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(
                            if (isBoosting) {
                                scheme.tertiaryContainer
                            } else {
                                scheme.primary
                            }
                        )
                        .border(
                            width = 1.dp,
                            color = if (isBoosting) {
                                scheme.tertiary.copy(alpha = 0.45f)
                            } else {
                                scheme.primary.copy(alpha = 0.35f)
                            },
                            shape = CircleShape
                        )
                        .clickable(enabled = !isBoosting) { onBoostClick() }
                        .alpha(if (isBoosting) 0.72f else 1f),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_water_drop),
                        contentDescription = "Boost",
                        tint = if (isBoosting) scheme.onTertiaryContainer else scheme.onPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}
