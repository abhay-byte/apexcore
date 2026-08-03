package com.ivarna.apexcore.fps.source

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import com.ivarna.apexcore.fps.model.FpsSnapshot
import com.ivarna.apexcore.fps.model.FpsMethod
import com.ivarna.apexcore.fps.model.FrametimeBuffer

/**
 * Receives FPS from [fps_daemon.sh] (Adreno cmdbatch-hybrid = avg gap@2ms + inflight-drop;
 * same path as scripts/adb-app-fps-ondevice.sh).
 */
class DmaFenceFpsDataSource(
    private val context: Context
) : FpsDataSource {

    @Volatile
    private var latestSnapshot: FpsSnapshot? = null

    @Volatile
    private var lastReceivedAtMs: Long = 0L

    companion object {
        private const val STALE_MS = 6000L
    }

    init {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action != "com.ivarna.apexcore.FPS_DATA") return
                val currentFps = intent.getStringExtra("fps")?.toFloatOrNull() ?: 0f
                val timeline = intent.getStringExtra("timeline") ?: "unknown"
                val frametimesStr = intent.getStringExtra("frametimes") ?: ""
                android.util.Log.d(
                    "DmaFenceFpsDataSource",
                    "FPS: $currentFps method=$timeline frametimes=$frametimesStr"
                )

                val frametimes = frametimesStr.split(",")
                    .mapNotNull { it.toFloatOrNull() }
                    .filter { it in 1f..200f }

                if (currentFps > 0f) {
                    val capped = currentFps.coerceIn(1f, 240f)
                    val ftMs = if (frametimes.isNotEmpty()) {
                        frametimes.average().toFloat()
                    } else {
                        1000f / capped
                    }
                    val percentiles = if (frametimes.size >= 3) {
                        val buffer = FrametimeBuffer(maxSize = frametimes.size + 4)
                        frametimes.forEach { buffer.push(it) }
                        buffer.computePercentiles()
                    } else {
                        null
                    }
                    latestSnapshot = FpsSnapshot(
                        currentFps = capped,
                        frametimeAvgMs = ftMs,
                        frametimeP1Ms = percentiles?.p1FrametimeMs ?: 0f,
                        frametimeP01Ms = percentiles?.p01FrametimeMs ?: 0f,
                        frametimeHistogram = frametimes.ifEmpty {
                            listOf(ftMs)
                        },
                        jankCount = 0,
                        method = FpsMethod.DMA_FENCE
                    )
                    lastReceivedAtMs = System.currentTimeMillis()
                }
            }
        }
        val filter = IntentFilter("com.ivarna.apexcore.FPS_DATA")
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(receiver, filter)
        }
    }

    override val priority: Int = 1

    override suspend fun readFps(): FpsSnapshot? {
        val snapshot = latestSnapshot ?: return null
        if (System.currentTimeMillis() - lastReceivedAtMs > STALE_MS) return null
        return snapshot
    }
}
