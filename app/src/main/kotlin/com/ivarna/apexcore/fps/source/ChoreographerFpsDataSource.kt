package com.ivarna.apexcore.fps.source

import android.content.Context
import android.os.SystemClock
import android.view.Choreographer
import android.view.WindowManager
import com.ivarna.apexcore.fps.model.FpsMethod
import com.ivarna.apexcore.fps.model.FpsSnapshot
import com.ivarna.apexcore.fps.privilege.PrivilegeTier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Standard-tier FPS via Choreographer vsync.
 * Measures the app's own window vsync (display refresh) when privileged dumpsys is unavailable.
 * Used as last resort for UI in Standard mode; never for games.
 */
class ChoreographerFpsDataSource(
    private val context: Context?
) : FpsDataSource {

    override val priority: Int = 4

    private val frameTimesNs = mutableListOf<Long>()
    private var lastFrameNs: Long = 0L
    private var registered = false
    private val lock = Any()

    private val callback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNs: Long) {
            synchronized(lock) {
                if (lastFrameNs != 0L) {
                    val delta = frameTimeNs - lastFrameNs
                    if (delta in 1_000_000L..500_000_000L) {
                        frameTimesNs.add(delta)
                        if (frameTimesNs.size > 120) frameTimesNs.removeAt(0)
                    }
                }
                lastFrameNs = frameTimeNs
            }
            // Re-post
            try {
                Choreographer.getInstance().postFrameCallback(this)
            } catch (_: Throwable) {}
        }
    }

    fun start() {
        synchronized(lock) {
            if (registered) return
            registered = true
            frameTimesNs.clear()
            lastFrameNs = 0L
        }
        try {
            // Must be on main thread
            if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
                Choreographer.getInstance().postFrameCallback(callback)
            } else {
                val latch = CountDownLatch(1)
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    Choreographer.getInstance().postFrameCallback(callback)
                    latch.countDown()
                }
                latch.await(1, TimeUnit.SECONDS)
            }
        } catch (_: Throwable) {}
    }

    fun stop() {
        synchronized(lock) {
            registered = false
            frameTimesNs.clear()
            lastFrameNs = 0L
        }
        try {
            Choreographer.getInstance().removeFrameCallback(callback)
        } catch (_: Throwable) {}
    }

    override suspend fun readFps(): FpsSnapshot? {
        // Ensure started
        start()
        // Need at least 3 frames
        val intervals: List<Long>
        synchronized(lock) {
            if (frameTimesNs.size < 3) return fallbackToDisplayRefresh()
            intervals = frameTimesNs.toList()
        }
        // Use recent 32 intervals
        val recent = if (intervals.size > 32) intervals.takeLast(32) else intervals
        val avgNs = recent.average()
        if (avgNs <= 0 || avgNs.isNaN() || avgNs.isInfinite()) return fallbackToDisplayRefresh()
        var fps = (1_000_000_000.0 / avgNs).toFloat()
        // Use display refresh for snap only, not hard cap - allow 120 on 60 fallback detection
        val refresh = displayRefreshHz()
        fps = fps.coerceIn(1f, 240f)
        // Snap if very close to refresh to avoid 119.3 vs 120 jitter
        val avgMs = (avgNs / 1_000_000.0).toFloat()
        val expectedMs = 1000f / refresh
        if (avgMs in expectedMs * 0.9f..expectedMs * 1.1f) {
            val variance = recent.map { (it / 1_000_000.0 - avgMs) * (it / 1_000_000.0 - avgMs) }.average()
            if (variance < 9.0) fps = refresh
        } else {
            // If measured fps is near 120/144 but refresh is 60, do not cap - trust measurement
            // Only cap if measured >> refresh and refresh is reliable (e.g., >90)
            if (refresh >= 90f) {
                fps = fps.coerceAtMost(refresh * 1.05f)
            }
        }
        if (fps.isNaN() || fps.isInfinite() || fps <= 0f) return fallbackToDisplayRefresh()
        val elapsed = SystemClock.elapsedRealtime()
        return FpsSnapshot(
            currentFps = fps,
            frametimeAvgMs = avgMs,
            frametimeP1Ms = 0f,
            frametimeP01Ms = 0f,
            frametimeHistogram = recent.map { it / 1_000_000f }.filter { it in 1f..200f },
            jankCount = 0,
            method = FpsMethod.CHOREOGRAPHER,
            accessTier = PrivilegeTier.STANDARD,
            packageName = null,
            surfaceName = null,
            timestampMs = System.currentTimeMillis(),
            measuredAtElapsedMs = elapsed,
            diagnostics = "CHR fps=${"%.1f".format(fps)} avgMs=${"%.2f".format(avgMs)} frames=${recent.size} refresh=${refresh.toInt()}",
            sourceDetail = "CHR:vsync",
            isStale = false,
            frameCount = recent.size
        )
    }

    private fun fallbackToDisplayRefresh(): FpsSnapshot? {
        val refresh = displayRefreshHz()
        if (refresh <= 0f) return null
        val elapsed = SystemClock.elapsedRealtime()
        return FpsSnapshot(
            currentFps = refresh,
            frametimeAvgMs = 1000f / refresh,
            frametimeP1Ms = 0f,
            frametimeP01Ms = 0f,
            frametimeHistogram = listOf(1000f / refresh),
            jankCount = 0,
            method = FpsMethod.CHOREOGRAPHER,
            accessTier = PrivilegeTier.STANDARD,
            packageName = null,
            timestampMs = System.currentTimeMillis(),
            measuredAtElapsedMs = elapsed,
            diagnostics = "CHR fallback refresh=$refresh",
            sourceDetail = "CHR:refresh",
            isStale = false,
            frameCount = 1
        )
    }

    private fun displayRefreshHz(): Float {
        context?.let { ctx ->
            try {
                val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
                val display = if (android.os.Build.VERSION.SDK_INT >= 30) ctx.display else @Suppress("DEPRECATION") wm?.defaultDisplay
                display?.refreshRate?.takeIf { it > 0f }?.let { return it }
                wm?.defaultDisplay?.refreshRate?.takeIf { it > 0f }?.let { return it }
            } catch (_: Throwable) {}
        }
        return 60f
    }
}
