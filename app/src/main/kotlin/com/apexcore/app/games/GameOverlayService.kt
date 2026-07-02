package com.apexcore.app.games

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
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
import android.widget.LinearLayout
import android.widget.TextView
import com.apexcore.app.freeze.FreezeFramework
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class GameOverlayService : Service(), Choreographer.FrameCallback {

    private lateinit var wm: WindowManager
    private lateinit var overlayView: FrameLayout
    private var params: WindowManager.LayoutParams? = null
    private var isExpanded = false
    private var gamePkg: String? = null
    private var scope = CoroutineScope(Dispatchers.Main + Job())
    private var statsJob: Job? = null
    private var exitWatcher: Job? = null
    private var autoCollapseJob: Job? = null

    // FPS tracking
    private var frameCount = 0
    private var lastFpsTime = 0L
    private var currentFps = 0

    // UI views
    private lateinit var collapsedView: View
    private lateinit var expandedView: LinearLayout
    private lateinit var fpsText: TextView
    private lateinit var fpsLabel: TextView
    private lateinit var ramText: TextView
    private lateinit var cpuText: TextView
    private lateinit var boostBtn: TextView

    private val density: Float get() = resources.displayMetrics.density

    override fun onCreate() {
        super.onCreate()
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
        startFpsCounter()
        startStatsUpdates()
        startExitWatcher()
        return START_REDELIVER_INTENT
    }

    override fun onDestroy() {
        statsJob?.cancel()
        exitWatcher?.cancel()
        autoCollapseJob?.cancel()
        Choreographer.getInstance().removeFrameCallback(this)
        try {
            if (overlayView.parent != null) wm.removeView(overlayView)
        } catch (_: Throwable) {}
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ── FPS via Choreographer ──

    override fun doFrame(frameTimeNanos: Long) {
        val now = SystemClock.elapsedRealtime()
        frameCount++
        if (now - lastFpsTime >= 1000) {
            currentFps = frameCount
            frameCount = 0
            lastFpsTime = now
            if (::fpsText.isInitialized) fpsText.text = "$currentFps"
        }
        Choreographer.getInstance().postFrameCallback(this)
    }

    private fun startFpsCounter() {
        lastFpsTime = SystemClock.elapsedRealtime()
        frameCount = 0
        Choreographer.getInstance().postFrameCallback(this)
    }

    // ── Stats updater ──

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
        if (::ramText.isInitialized) {
            ramText.text = "${memInfo.first / 1024}/${memInfo.second / 1024} MB"
        }
        if (::cpuText.isInitialized) {
            cpuText.text = load
        }
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
    } catch (_: Throwable) { "—" }

    // ── Exit watcher ──

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
        // Fallback: stay alive if query fails or permission is not granted
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

    // ── Overlay view builder ──

    private fun createLayoutParams(): WindowManager.LayoutParams {
        val w = dpf(56f).toInt()
        val h = dpf(56f).toInt()
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
        }

        // Collapsed pill — small cyan dot with FPS
        collapsedView = TextView(this).apply {
            text = "—"
            setTextColor(Color.parseColor("#FFFFFF"))
            textSize = 12f
            typeface = Typeface.MONOSPACE
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#0F1623"))
                setStroke(3, Color.parseColor("#00E5FF"))
            }
            layoutParams = FrameLayout.LayoutParams(dpf(56f).toInt(), dpf(56f).toInt())
            setOnClickListener { toggleExpand() }
        }
        fpsText = collapsedView as TextView
        overlayView.addView(collapsedView)

        // Expanded panel
        expandedView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            visibility = View.GONE
            setPadding(dpf(16f).toInt(), dpf(16f).toInt(), dpf(16f).toInt(), dpf(16f).toInt())
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE; cornerRadius = dpf(16f)
                setColor(Color.parseColor("#0F1623")); setStroke(2, Color.parseColor("#1F2937"))
            }
            layoutParams = FrameLayout.LayoutParams(dpf(180f).toInt(), FrameLayout.LayoutParams.WRAP_CONTENT)
        }
        expandedView.apply {
            // FPS row
            val fpsRow = LinearLayout(this@GameOverlayService).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER }
            fpsLabel = TextView(this@GameOverlayService).apply { text = "$currentFps"; textSize = 28f; setTextColor(Color.parseColor("#00E5FF")); typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); gravity = Gravity.CENTER }
            fpsRow.addView(fpsLabel)
            fpsRow.addView(tiny("FPS"))
            addView(fpsRow)

            addView(spacer(12))

            // RAM row
            val ramRow = makeStatRow("RAM")
            ramText = ramRow.second
            addView(ramRow.first)
            addView(spacer(6))

            // CPU row
            val cpuRow = makeStatRow("CPU")
            cpuText = cpuRow.second
            addView(cpuRow.first)
            addView(spacer(16))

            // BOOST button
            boostBtn = TextView(this@GameOverlayService).apply {
                text = "BOOST"; setTextColor(Color.parseColor("#070A12")); textSize = 12f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                gravity = Gravity.CENTER
                setPadding(0, dpf(12f).toInt(), 0, dpf(12f).toInt())
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE; cornerRadius = dpf(10f)
                    colors = intArrayOf(Color.parseColor("#00E5FF"), Color.parseColor("#0EA5E9"))
                    orientation = GradientDrawable.Orientation.TL_BR
                }
                setOnClickListener {
                    scope.launch {
                        FreezeFramework.freezeAll(this@GameOverlayService)
                    }
                }
            }
            addView(boostBtn)
            addView(spacer(8))

            // Dismiss
            addView(tinyBtn("✕") { shutdown() })
        }
        overlayView.addView(expandedView)
    }

    private fun toggleExpand() {
        isExpanded = !isExpanded
        val lp = params ?: return
        if (isExpanded) {
            collapsedView.visibility = View.GONE
            expandedView.visibility = View.VISIBLE
            lp.width = WindowManager.LayoutParams.WRAP_CONTENT
            lp.height = WindowManager.LayoutParams.WRAP_CONTENT
            wm.updateViewLayout(overlayView, lp)
            startAutoCollapseTimer()
        } else {
            expandedView.visibility = View.GONE
            collapsedView.visibility = View.VISIBLE
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
            if (isExpanded) toggleExpand()
        }
    }

    // ── Touch (drag) handler ──

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
                        // Let the click listener handle it
                        v.performClick()
                    }
                    return true
                }
            }
            return false
        }
    }

    // ── UI helpers ──

    private fun makeStatRow(label: String): Pair<LinearLayout, TextView> {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        row.addView(TextView(this).apply {
            text = label; setTextColor(Color.parseColor("#6B7280")); textSize = 10f; typeface = Typeface.MONOSPACE
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        val tv = TextView(this).apply {
            text = "—"; setTextColor(Color.parseColor("#FFFFFF")); textSize = 11f; typeface = Typeface.MONOSPACE
        }
        row.addView(tv)
        return row to tv
    }

    private fun tiny(label: String) = TextView(this).apply {
        text = label; setTextColor(Color.parseColor("#6B7280")); textSize = 9f; typeface = Typeface.MONOSPACE; gravity = Gravity.CENTER
    }

    private fun spacer(dp: Int) = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dpf(dp.toFloat()).toInt())
    }

    private fun tinyBtn(label: String, onClick: () -> Unit) = TextView(this).apply {
        text = label; setTextColor(Color.parseColor("#6B7280")); textSize = 14f; gravity = Gravity.CENTER
        setPadding(dpf(24f).toInt(), dpf(6f).toInt(), dpf(24f).toInt(), dpf(6f).toInt())
        isClickable = true; setOnClickListener { onClick() }
    }

    private fun dpf(v: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics)

    // ── Notification ──

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
        private const val TAG = "ApexCore.Overlay"
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
