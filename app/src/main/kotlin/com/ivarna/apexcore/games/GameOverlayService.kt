package com.ivarna.apexcore.games

import android.animation.ValueAnimator
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.PixelFormat
import android.os.*
import android.view.*
import android.view.animation.DecelerateInterpolator
import androidx.core.animation.doOnEnd
import com.ivarna.apexcore.R
import com.ivarna.apexcore.fps.FpsStack
import com.ivarna.apexcore.fps.model.FpsMethod
import com.ivarna.apexcore.freeze.FreezeFramework
import com.ivarna.apexcore.getSystemMemStats
import com.ivarna.apexcore.thermal.ThermalMonitor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

class GameOverlayService : Service() {

    companion object {
        const val TAG = "GameOverlayService"
        private const val CHANNEL_ID = "apexcore_overlay"
        private const val NOTIF_ID = 1001
        const val EXTRA_PKG = "game_pkg"
        const val PREF_OVERLAY_RUNNING = "overlay_running"
        const val PREF_OVERLAY_PKG = "overlay_pkg"

        const val ACTION_START = "com.ivarna.apexcore.overlay.START"
        const val ACTION_STOP = "com.ivarna.apexcore.overlay.STOP"

        const val FPS_MEASUREMENT_PERIOD_MS = 850L
        const val HUD_REDRAW_PERIOD_MS = 350L
        const val MIN_DELAY_MS = 80L

        @Volatile
        var isRunning: Boolean = false

        fun start(context: Context, pkg: String): Boolean {
            if (!android.provider.Settings.canDrawOverlays(context)) return false
            return try {
                val intent = Intent(context, GameOverlayService::class.java).apply {
                    putExtra(EXTRA_PKG, pkg)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
                true
            } catch (_: Throwable) {
                false
            }
        }

        fun stop(context: Context) {
            context.getSharedPreferences("apexcore", Context.MODE_PRIVATE)
                .edit()
                .remove(PREF_OVERLAY_RUNNING)
                .remove(PREF_OVERLAY_PKG)
                .apply()
            context.stopService(Intent(context, GameOverlayService::class.java))
        }
    }

    private lateinit var wm: WindowManager
    private lateinit var rail: RailView
    private lateinit var params: WindowManager.LayoutParams
    private val handler = Handler(Looper.getMainLooper())
    private var gamePkg: String? = null

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private val prefs: SharedPreferences by lazy {
        getSharedPreferences("apexcore", MODE_PRIVATE)
    }

    override fun onBind(intent: Intent?) = null

    override fun onCreate() {
        super.onCreate()
        FreezeFramework.init(applicationContext)
        wm = getSystemService(WindowManager::class.java)
        createNotificationChannel()

        val onRight = prefs.getString("hud_edge", "LEFT") == "RIGHT"

        // Reuse telemetryScope for all background work — avoid ad-hoc Scope creation per click
        rail = RailView(this).apply {
            applyFit(prefs)
            onDefrost = {
                telemetryScope.launch {
                    FreezeFramework.unfreezeAll(applicationContext)
                }
            }
            onExpand = { expanded -> resizeWindow(expanded) }
            onDrag = { dy -> moveWindow(dy) }
            onDragEnd = { snapWindow() }
        }

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        params = WindowManager.LayoutParams(
            dp(12),
            dp(170),
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or (if (onRight) Gravity.END else Gravity.START)
            x = 0
            y = displayHeight() / 3
        }

        wm.addView(rail, params)
        isRunning = true
        startTelemetry()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            shutdown()
            return START_NOT_STICKY
        }
        val newPkg = intent?.getStringExtra(EXTRA_PKG)?.takeIf { it.isNotBlank() && it.contains('.') }
        val pkgChanged = newPkg != null && newPkg != gamePkg
        if (newPkg != null) gamePkg = newPkg
        // Mission: track launched game package, not transient focus — set before first sample
        try {
            FpsStack.get(applicationContext).repository.setTargetPackage(gamePkg)
            android.util.Log.i(TAG, "FPS target set to $gamePkg")
        } catch (t: Throwable) {
            android.util.Log.w(TAG, "Failed to set FPS target: ${t.message}")
        }
        if (pkgChanged) {
            // Restart telemetry so new package resolves fresh surface immediately
            latestFpsSnapshot = null
            startTelemetry()
        }
        startForeground(NOTIF_ID, buildNotification())
        prefs.edit()
            .putBoolean(PREF_OVERLAY_RUNNING, true)
            .putString(PREF_OVERLAY_PKG, gamePkg)
            .apply()
        return START_STICKY
    }

    private fun resizeWindow(expanded: Boolean) {
        if (!::rail.isInitialized || !::wm.isInitialized) return
        if (rail.parent == null || !rail.isAttachedToWindow) return
        params.width = dp(if (expanded) rail.panelWidthDp else 12)
        try { wm.updateViewLayout(rail, params) } catch (_: Throwable) {}
    }

    private fun moveWindow(dy: Float) {
        if (!::rail.isInitialized || !::wm.isInitialized) return
        if (rail.parent == null || !rail.isAttachedToWindow) return
        params.y = (params.y + dy.toInt()).coerceIn(0, displayHeight() - dp(170))
        try { wm.updateViewLayout(rail, params) } catch (_: Throwable) {}
    }

    private fun snapWindow() {
        if (!::rail.isInitialized || !::wm.isInitialized) return
        if (rail.parent == null || !rail.isAttachedToWindow) return
        val h = displayHeight()
        val snapFractions = floatArrayOf(0.20f, 0.40f, 0.60f, 0.80f)
        val target = snapFractions.map { (it * h).toInt() }.minBy { abs(it - params.y) }
        ValueAnimator.ofInt(params.y, target).apply {
            duration = 120
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                if (rail.parent == null || !rail.isAttachedToWindow) return@addUpdateListener
                params.y = it.animatedValue as Int
                try { wm.updateViewLayout(rail, params) } catch (_: Throwable) {}
            }
            doOnEnd {
                try { if (rail.isAttachedToWindow) rail.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK) } catch (_: Throwable) {}
            }
            start()
        }
    }

    private fun displayHeight(): Int =
        if (Build.VERSION.SDK_INT >= 30)
            wm.currentWindowMetrics.bounds.height()
        else resources.displayMetrics.heightPixels

    private fun displayRefreshHz(): Float {
        return try {
            if (Build.VERSION.SDK_INT >= 30) {
                // Prefer current display; fallback to WindowManager's display
                display?.refreshRate ?: wm.defaultDisplay?.refreshRate ?: 60f
            } else {
                @Suppress("DEPRECATION")
                wm.defaultDisplay?.refreshRate ?: 60f
            }
        } catch (_: Throwable) { 60f }
    }

    // Telemetry sampling runs entirely off the main thread — the old handler loop
    // runBlocking'd dumpsys/proc reads on main every 500ms and janked the overlay
    // (and the host process) while games were running.
    // Separate measurement cadence (750-1000ms) from UI redraw cadence (250-500ms) using latest snapshot.
    private val telemetryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var measurementJob: kotlinx.coroutines.Job? = null
    private var uiJob: kotlinx.coroutines.Job? = null
    @Volatile
    private var latestFpsSnapshot: com.ivarna.apexcore.fps.model.FpsSnapshot? = null

    private fun startTelemetry() {
        measurementJob?.cancel()
        uiJob?.cancel()
        // Ensure target is set before first sample (already done in onStartCommand, but re-assert)
        gamePkg?.let { pkg ->
            try { FpsStack.get(applicationContext).repository.setTargetPackage(pkg) } catch (_: Throwable) {}
        }
        measurementJob = telemetryScope.launch {
            while (isActive) {
                val loopStart = android.os.SystemClock.elapsedRealtime()
                try {
                    val fpsStack = FpsStack.get(applicationContext)
                    val fpsSnapshot = fpsStack.repository.getFps()
                    latestFpsSnapshot = fpsSnapshot
                    // Rich diagnostics: target, surface, method, tier, intervals, raw/smoothed, age
                    fpsSnapshot.diagnostics?.let { diag ->
                        val age = try { android.os.SystemClock.elapsedRealtime() - fpsSnapshot.measuredAtElapsedMs } catch (_: Throwable) { 0L }
                        android.util.Log.d(TAG, "FPS target=$gamePkg method=${fpsSnapshot.method} tier=${fpsSnapshot.accessTier?.name} surface=${fpsSnapshot.sourceDetail} fps=${"%.1f".format(fpsSnapshot.currentFps)} age=${age}ms diag=$diag")
                    }
                } catch (t: Throwable) {
                    android.util.Log.w(TAG, "FPS measurement failed: ${t.message}")
                }
                val elapsed = android.os.SystemClock.elapsedRealtime() - loopStart
                val delayMs = (FPS_MEASUREMENT_PERIOD_MS - elapsed).coerceAtLeast(MIN_DELAY_MS)
                delay(delayMs)
            }
        }
        uiJob = telemetryScope.launch {
            while (isActive) {
                val loopStart = android.os.SystemClock.elapsedRealtime()
                try {
                    val fpsSnapshot = latestFpsSnapshot
                    // Trust repository's display FPS (already smoothed & capped); do not double-clamp with stale refresh
                    val fps = when {
                        fpsSnapshot == null -> 0
                        fpsSnapshot.method == FpsMethod.NONE || fpsSnapshot.currentFps <= 0f -> 0
                        else -> fpsSnapshot.currentFps.toInt()
                    }
                    val method = fpsSnapshot?.method ?: FpsMethod.NONE
                    val fpsStack = FpsStack.get(applicationContext)
                    val stats = getSystemMemStats(applicationContext)
                    val ramFraction = stats.ramUsedKb.toFloat() / stats.ramTotalKb.coerceAtLeast(1)
                    val cpuSnapshot = runCatching { fpsStack.cpuDataSource.readCpuStats() }.getOrNull()
                    val cpuFractions = if (cpuSnapshot != null && cpuSnapshot.cores.isNotEmpty()) {
                        FloatArray(cpuSnapshot.cores.size) { i -> (cpuSnapshot.cores[i].loadPercent / 100f).coerceIn(0f, 1f) }
                    } else {
                        FloatArray(8) { 0.2f }
                    }
                    val thermal = ThermalMonitor.getSnapshot(applicationContext).cpuTempCelsius > 45
                    withContext(Dispatchers.Main) {
                        rail.push(fps, ramFraction, cpuFractions, method)
                        rail.fpsMethod = method
                        rail.thermal = thermal
                    }
                } catch (t: Throwable) {
                    android.util.Log.w(TAG, "telemetry UI tick failed: ${t.message}")
                }
                val elapsed = android.os.SystemClock.elapsedRealtime() - loopStart
                val delayMs = (HUD_REDRAW_PERIOD_MS - elapsed).coerceAtLeast(MIN_DELAY_MS)
                delay(delayMs)
            }
        }
    }

    private fun resolveBoostProtectPackages(): Set<String> {
        val protect = linkedSetOf<String>()
        protect.add(packageName)
        gamePkg?.takeIf { it.isNotBlank() }?.let { protect.add(it) }
        return protect
    }

    private fun shutdown() {
        measurementJob?.cancel()
        uiJob?.cancel()
        latestFpsSnapshot = null
        // Fail-closed: clear FPS target so resolver no longer locks to dead package and
        // daemon file is not polled for stale game
        try {
            FpsStack.get(applicationContext).repository.setTargetPackage(null)
            android.util.Log.i(TAG, "FPS target cleared")
        } catch (_: Throwable) {}
        // Do not cancel the whole scope if we may be recreated quickly — but we must
        // ensure no further telemetry after shutdown. Cancel current job and keep scope.
        handler.removeCallbacksAndMessages(null)
        try {
            if (::rail.isInitialized) {
                rail.cancelPendingAnimations()
                if (rail.parent != null) wm.removeView(rail)
            }
        } catch (_: Throwable) {}
        isRunning = false
        gamePkg = null
        prefs.edit().remove(PREF_OVERLAY_RUNNING).remove(PREF_OVERLAY_PKG).apply()
        try { stopForeground(STOP_FOREGROUND_REMOVE) } catch (_: Throwable) {}
        try { stopSelf() } catch (_: Throwable) {}
    }

    override fun onDestroy() {
        shutdown()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(CHANNEL_ID, "Game Overlay", NotificationManager.IMPORTANCE_LOW).apply {
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            }
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(ch)
        }
    }

    private fun buildNotification(): Notification {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("ApexCore Overlay")
                .setContentText(gamePkg?.let { "Monitoring $it" } ?: "Game performance overlay active")
                .setSmallIcon(R.drawable.ic_stat_apex)
                .setOngoing(true)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setContentTitle("ApexCore Overlay")
                .setContentText(gamePkg?.let { "Monitoring $it" } ?: "Game performance overlay active")
                .setSmallIcon(R.drawable.ic_stat_apex)
                .setOngoing(true)
                .build()
        }
    }
}
