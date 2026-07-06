package com.apexcore.app.games

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
import androidx.compose.animation.core.*
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.*
import androidx.savedstate.*
import com.apexcore.app.freeze.FreezeFramework
import com.apexcore.app.ui.theme.*
import kotlinx.coroutines.*
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
    private val fpsState = mutableStateOf(0)
    private val ramTotalState = mutableStateOf(1L)
    private val ramAvailState = mutableStateOf(1L)
    private val cpuLoadState = mutableStateOf("0.10 0.10")
    private val isBoostingState = mutableStateOf(false)

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
        if (now - lastFpsTime >= 1000) {
            fpsState.value = frameCount
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
            while (isActive) {
                updateStats()
                delay(3000L)
            }
        }
    }

    private fun updateStats() {
        val memInfo = readMemInfo()
        val load = readCpuLoad()
        ramTotalState.value = memInfo.first
        ramAvailState.value = memInfo.second
        cpuLoadState.value = load
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

    private fun readCpuLoad(): String = try {
        java.io.File("/proc/loadavg").readText().split(" ").take(2).joinToString(" ")
    } catch (_: Throwable) { "0.10 0.10" }

    private fun startExitWatcher() {
        val pkg = gamePkg ?: return
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
        val w = if (isExpandedState.value) WindowManager.LayoutParams.WRAP_CONTENT else dpf(56f).toInt()
        val h = if (isExpandedState.value) WindowManager.LayoutParams.WRAP_CONTENT else dpf(56f).toInt()
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
        lp.x = resources.displayMetrics.widthPixels - w - dpf(16f).toInt()
        lp.y = dpf(200f).toInt()
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
                        ramTotal = ramTotalState.value,
                        ramAvail = ramAvailState.value,
                        cpuLoad = cpuLoadState.value,
                        isBoosting = isBoostingState.value,
                        onBoostClick = {
                            isBoostingState.value = true
                            scope.launch {
                                FreezeFramework.freezeAll(this@GameOverlayService)
                                isBoostingState.value = false
                            }
                        },
                        onToggleExpand = { toggleExpand() },
                        onCloseClick = { shutdown() }
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
            lp.width = WindowManager.LayoutParams.WRAP_CONTENT
            lp.height = WindowManager.LayoutParams.WRAP_CONTENT
            wm.updateViewLayout(overlayView, lp)
            startAutoCollapseTimer()
        } else {
            lp.width = dpf(56f).toInt()
            lp.height = dpf(56f).toInt()
            wm.updateViewLayout(overlayView, lp)
            autoCollapseJob?.cancel()
        }
    }

    private fun startAutoCollapseTimer() {
        autoCollapseJob?.cancel()
        autoCollapseJob = scope.launch {
            delay(15000L)
            if (isExpandedState.value) toggleExpand()
        }
    }

    private inner class OverlayTouchListener : View.OnTouchListener {
        private var initialX = 0
        private var initialY = 0
        private var initialTouchX = 0f
        private var initialTouchY = 0f
        private var isDragging = false

        override fun onTouch(v: View, event: MotionEvent): Boolean {
            val lp = params ?: return false
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = lp.x
                    initialY = lp.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDragging = false
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()
                    if (!isDragging && (dx * dx + dy * dy > 100)) isDragging = true
                    if (isDragging) {
                        lp.x = initialX + dx
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
    ramTotal: Long,
    ramAvail: Long,
    cpuLoad: String,
    isBoosting: Boolean,
    onBoostClick: () -> Unit,
    onToggleExpand: () -> Unit,
    onCloseClick: () -> Unit
) {
    if (!isExpanded) {
        val infiniteTransition = rememberInfiniteTransition()
        val pulseScale by infiniteTransition.animateFloat(
            initialValue = 0.85f,
            targetValue = 1.15f,
            animationSpec = infiniteRepeatable(
                animation = tween(1000, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            )
        )

        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(SurfaceGlass)
                .border(1.5.dp, Brush.linearGradient(listOf(AccentPrimary, AccentSecondary)), CircleShape)
                .clickable { onToggleExpand() },
            contentAlignment = Alignment.Center
        ) {
            // Live pulsing core inside the pill
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .scale(pulseScale)
                    .clip(CircleShape)
                    .background(AccentSuccess)
                    .align(Alignment.TopEnd)
                    .offset(x = (-8).dp, y = 8.dp)
            )
            
            Text(
                text = if (fps > 0) "$fps" else "—",
                color = TextTitle,
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
        }
    } else {
        Column(
            modifier = Modifier
                .width(185.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(SurfaceCard.copy(alpha = 0.95f))
                .border(1.dp, BorderGlass, RoundedCornerShape(24.dp))
                .padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "MONITOR",
                    color = TextMuted,
                    fontSize = 8.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                // Small styled close button
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(BorderGlass)
                        .clickable { onCloseClick() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "X",
                        color = TextTitle,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "$fps",
                color = AccentWarning,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = (-0.02).em
            )
            Text(
                text = "CURRENT FPS",
                color = TextMuted,
                fontSize = 8.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(18.dp))
            
            // Visual Progress stats for RAM
            val ramUsed = (ramTotal - ramAvail).coerceAtLeast(0)
            val ramFraction = if (ramTotal > 0) ramUsed.toFloat() / ramTotal else 0f
            val ramText = "${ramUsed / 1024}G / ${ramTotal / 1024}G"
            OverlayVisualBar(label = "RAM USAGE", value = ramMute(ramText), progress = ramFraction)
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Visual Progress stats for CPU
            val cpuLoadVal = cpuLoad.split(" ").firstOrNull()?.toFloatOrNull() ?: 0.1f
            val cpuFraction = (cpuLoadVal / 8f).coerceIn(0f, 1f)
            OverlayVisualBar(label = "CPU LOAD", value = cpuLoad, progress = cpuFraction)
            
            Spacer(modifier = Modifier.height(20.dp))
            
            // Boosting button with premium gradients
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (isBoosting) androidx.compose.ui.graphics.SolidColor(AccentSuccess.copy(alpha = 0.5f))
                        else Brush.horizontalGradient(listOf(AccentPrimary, AccentSecondary))
                    )
                    .clickable(enabled = !isBoosting) { onBoostClick() }
                    .padding(vertical = 11.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (isBoosting) "BOOSTING…" else "BOOST SYSTEM",
                    color = TextTitle,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 0.5.sp
                )
            }
        }
    }
}

private fun ramMute(text: String): String {
    // Just a clean string formatter
    return text.replace(" ", "")
}

@Composable
fun OverlayVisualBar(label: String, value: String, progress: Float) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                color = TextMuted,
                fontSize = 8.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = value,
                color = TextTitle,
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        // Progress bar track
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(50))
                .background(BorderGlass)
        ) {
            // Progress fill
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(50))
                    .background(Brush.horizontalGradient(listOf(AccentPrimary, AccentSecondary)))
            )
        }
    }
}
