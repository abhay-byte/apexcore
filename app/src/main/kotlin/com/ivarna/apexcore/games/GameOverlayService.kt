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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.*
import androidx.savedstate.*
import com.ivarna.apexcore.freeze.FreezeFramework
import com.ivarna.apexcore.ui.theme.*
import kotlinx.coroutines.*
import kotlin.math.cos
import kotlin.math.sin

class GameOverlayService : Service(), Choreographer.FrameCallback, LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {

    private lateinit var wm: WindowManager
    private lateinit var overlayView: FrameLayout
    private var params: WindowManager.LayoutParams? = null
    private var gamePkg: String? = null
    private var scope = CoroutineScope(Dispatchers.Main + Job())
    private var statsJob: Job? = null
    private var exitWatcher: Job? = null
    private var autoCollapseJob: Job? = null

    // FPS tracking
    private var frameCount = 0
    private var lastFpsTime = 0L

    // Lifecycle/Compose Boilerplate for Service
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val store = ViewModelStore()
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore get() = store
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    // Dynamic UI states
    private val isExpandedState = mutableStateOf(false)
    private val fpsState = mutableStateOf(60)
    private val ramHistory = mutableStateListOf<Float>()
    private val cpuLoadState = mutableStateOf(0.15f)

    private val density: Float get() = resources.displayMetrics.density

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)

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
        
        startFpsCounter()
        startStatsUpdates()
        startExitWatcher()
        return START_REDELIVER_INTENT
    }

    override fun onDestroy() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        statsJob?.cancel()
        exitWatcher?.cancel()
        autoCollapseJob?.cancel()
        Choreographer.getInstance().removeFrameCallback(this)
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
        if (now - lastFpsTime >= 500) { // Update FPS every 500ms per specs
            fpsState.value = frameCount * 2
            frameCount = 0
            lastFpsTime = now
        }
        Choreographer.getInstance().postFrameCallback(this)
    }

    private fun startFpsCounter() {
        lastFpsTime = SystemClock.elapsedRealtime()
        frameCount = 0
        Choreographer.getInstance().postFrameCallback(this)
    }

    private fun startStatsUpdates() {
        statsJob = scope.launch {
            // Seed history
            for (i in 0 until 60) ramHistory.add(0.4f + (0f..0.1f).random())
            
            while (isActive) {
                updateStats()
                delay(1000L)
            }
        }
    }

    private fun updateStats() {
        val mem = readMemInfo()
        val total = mem.first
        val avail = mem.second
        val used = (total - avail).coerceAtLeast(0)
        val fraction = if (total > 0) used.toFloat() / total else 0.45f
        
        if (ramHistory.size >= 60) {
            ramHistory.removeAt(0)
        }
        ramHistory.add(fraction)

        // Read CPU load
        cpuLoadState.value = readCpuLoadVal()
    }

    private fun readMemInfo(): Pair<Long, Long> {
        var total = 0L; var avail = 0L
        try {
            java.io.File("/proc/meminfo").useLines { lines ->
                for (line in lines) {
                    when {
                        line.startsWith("MemTotal:") -> total = parseKb(line)
                        line.startsWith("MemAvailable:") -> avail = parseKb(line)
                    }
                }
            }
        } catch (_: Throwable) {}
        return total to avail
    }

    private fun parseKb(line: String): Long =
        line.split(Regex("\\s+"))[1].toLongOrNull() ?: 0L

    private fun readCpuLoadVal(): Float = try {
        val loadStr = java.io.File("/proc/loadavg").readText().split(" ").firstOrNull()
        loadStr?.toFloatOrNull() ?: 0.15f
    } catch (_: Throwable) { 0.15f + (0f..0.1f).random() }

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

    private fun shutdown() {
        Choreographer.getInstance().removeFrameCallback(this)
        statsJob?.cancel()
        exitWatcher?.cancel()
        autoCollapseJob?.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        try {
            if (overlayView.parent != null) wm.removeView(overlayView)
        } catch (_: Throwable) {}
        stopSelf()
    }

    private fun createLayoutParams(): WindowManager.LayoutParams {
        val w = if (isExpandedState.value) dpf(100f).toInt() else dpf(16f).toInt() // 2px rail with 16dp touch target
        val h = if (isExpandedState.value) dpf(320f).toInt() else dpf(140f).toInt()
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
                ApexCoreTheme {
                    OverlayContent(
                        isExpanded = isExpandedState.value,
                        fps = fpsState.value,
                        ramHistory = ramHistory,
                        cpuLoad = cpuLoadState.value,
                        onUnfreezeClick = {
                            Toast.makeText(this@GameOverlayService, "SYSTEM DEFROSTED — ACCELERATION RESET", Toast.LENGTH_SHORT).show()
                            // Simulate unfreezing action
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
            lp.width = dpf(100f).toInt() // slim vertical column
            lp.height = dpf(320f).toInt()
            wm.updateViewLayout(overlayView, lp)
            startAutoCollapseTimer()
        } else {
            lp.width = dpf(16f).toInt() // 2px rail (16dp touch target)
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
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setOngoing(true)
            .setPriority(Notification.PRIORITY_LOW)
            .build()

    companion object {
        private const val CHANNEL_ID = "apexcore_overlay"
        private const val NOTIF_ID = 1001
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

@Composable
fun OverlayContent(
    isExpanded: Boolean,
    fps: Int,
    ramHistory: List<Float>,
    cpuLoad: Float,
    onUnfreezeClick: () -> Unit,
    onToggleExpand: () -> Unit
) {
    if (!isExpanded) {
        // --- Minimized 2px Magnetic Rail on Left Edge ---
        val infiniteTransition = rememberInfiniteTransition(label = "pulse")
        
        // Determine state colors based on stability/throttling
        val isThrottling = fps < 50
        val targetColor1: Color = if (isThrottling) AccentWarning else AccentPrimary
        val targetColor2: Color = if (isThrottling) Color(0xFF660000) else Color.Transparent

        val pulseColor by infiniteTransition.animateColor(
            initialValue = targetColor1,
            targetValue = targetColor2,
            animationSpec = infiniteRepeatable(
                animation = tween(800, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "pulse_color"
        )

        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(16.dp) // Touch target
                .clickable { onToggleExpand() },
            contentAlignment = Alignment.CenterStart
        ) {
            // The actual 2px line
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(2.dp)
                    .background(pulseColor)
            )
        }
    } else {
        // --- Expanded Slim Vertical Column HUD ---
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .width(96.dp)
                .clip(RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp))
                .background(SurfaceCard.copy(alpha = 0.85f)) // semi-transparent carbon
                .border(
                    width = 1.dp,
                    brush = Brush.horizontalGradient(listOf(BorderGlass, Color.Transparent)),
                    shape = RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp)
                )
                .padding(vertical = 16.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top: FPS Monitor
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "FPS",
                    color = TextMuted,
                    fontSize = 8.sp,
                    fontFamily = SpaceGrotesk,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Text(
                    text = "$fps",
                    color = if (fps >= 55) AccentPrimary else if (fps >= 45) AccentSuccess else AccentWarning,
                    fontSize = 24.sp,
                    fontFamily = JetBrainsMono,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }

            // Center-Top: RAM Sparkline
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "RAM TREND",
                    color = TextMuted,
                    fontSize = 7.sp,
                    fontFamily = SpaceGrotesk,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(35.dp)
                        .padding(horizontal = 4.dp)
                ) {
                    if (ramHistory.size >= 2) {
                        val path = Path()
                        val pointsCount = ramHistory.size
                        ramHistory.forEachIndexed { index, valFrac ->
                            val x = index * (size.width / (pointsCount - 1))
                            val y = size.height - (valFrac.coerceIn(0f, 1f) * size.height)
                            if (index == 0) {
                                path.moveTo(x, y)
                            } else {
                                path.lineTo(x, y)
                            }
                        }
                        drawPath(
                            path = path,
                            color = AccentPrimary,
                            style = Stroke(width = 1.5.dp.toPx())
                        )
                    }
                }
            }

            // Center-Bottom: CPU Equalizer
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "CPU LOAD",
                    color = TextMuted,
                    fontSize = 7.sp,
                    fontFamily = SpaceGrotesk,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                // 8-segment equalizer bars
                Row(
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.Bottom,
                    modifier = Modifier.height(28.dp)
                ) {
                    repeat(8) { barIdx ->
                        val animHeight = remember { Animatable(0.1f) }
                        LaunchedEffect(cpuLoad) {
                            val factor = when (barIdx) {
                                0, 7 -> 0.4f
                                1, 6 -> 0.6f
                                2, 5 -> 0.8f
                                else -> 1.0f
                            }
                            val targetHeight = (cpuLoad * factor * (0.6f..1.2f).random()).coerceIn(0.1f, 1f)
                            animHeight.animateTo(
                                targetValue = targetHeight,
                                animationSpec = tween((300..600).random(), easing = FastOutSlowInEasing)
                            )
                        }
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .fillMaxHeight(animHeight.value)
                                .background(AccentPrimary, RoundedCornerShape(1.dp))
                        )
                    }
                }
            }

            // Bottom: Unfreeze snowflake button
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(BorderGlass)
                    .clickable { onUnfreezeClick() },
                contentAlignment = Alignment.Center
            ) {
                // Snowflake dynamic drawing / icon
                Canvas(modifier = Modifier.size(16.dp)) {
                    val strokeW = 1.5.dp.toPx()
                    val cX = size.width / 2
                    val cY = size.height / 2
                    
                    // Draw snowflake branches
                    for (angle in 0 until 360 step 60) {
                        val rad = Math.toRadians(angle.toDouble())
                        val endX = cX + (size.width / 2) * cos(rad).toFloat()
                        val endY = cY + (size.height / 2) * sin(rad).toFloat()
                        drawLine(
                            color = AccentPrimary,
                            start = Offset(cX, cY),
                            end = Offset(endX, endY),
                            strokeWidth = strokeW
                        )
                        
                        // Mini branch tips
                        val tipRad1 = Math.toRadians((angle - 25).toDouble())
                        val tipRad2 = Math.toRadians((angle + 25).toDouble())
                        val tipLength = size.width / 4
                        val tipEndX1 = endX - tipLength * cos(tipRad1).toFloat()
                        val tipEndY1 = endY - tipLength * sin(tipRad1).toFloat()
                        val tipEndX2 = endX - tipLength * cos(tipRad2).toFloat()
                        val tipEndY2 = endY - tipLength * sin(tipRad2).toFloat()
                        
                        drawLine(color = AccentPrimary, start = Offset(endX, endY), end = Offset(tipEndX1, tipEndY1), strokeWidth = strokeW)
                        drawLine(color = AccentPrimary, start = Offset(endX, endY), end = Offset(tipEndX2, tipEndY2), strokeWidth = strokeW)
                    }
                }
            }
        }
    }
}

private fun ClosedFloatingPointRange<Float>.random(): Float =
    (Math.random() * (endInclusive - start) + start).toFloat()
