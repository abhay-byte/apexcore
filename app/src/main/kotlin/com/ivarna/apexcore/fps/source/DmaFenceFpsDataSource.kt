package com.ivarna.apexcore.fps.source

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import com.ivarna.apexcore.fps.model.FpsSnapshot
import com.ivarna.apexcore.fps.model.FpsMethod
import com.ivarna.apexcore.fps.model.FrametimeBuffer
import com.ivarna.apexcore.fps.privilege.PrivilegeTier

/**
 * Receives FPS from [fps_daemon.sh] (Adreno cmdbatch-hybrid = avg gap@2ms + inflight-drop;
 * same path as scripts/adb-app-fps-ondevice.sh).
 *
 * Diagnostics and fail-closed handling retained from factualstats:
 * - latestSnapshot carries provenance tier
 * - clearCache() is called when PrivilegeMode changes to avoid holding root samples after demotion
 */
class DmaFenceFpsDataSource(
    private val context: Context
) : FpsDataSource {

    @Volatile
    private var latestSnapshot: FpsSnapshot? = null

    @Volatile
    private var lastReceivedAtElapsedMs: Long = 0L

    @Volatile
    private var lastTimeline: String = "unknown"

    @Volatile
    private var lastPackage: String? = null

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
                val pkg = intent.getStringExtra("package") ?: intent.getStringExtra("pkg") ?: "unknown"
                android.util.Log.d(
                    "DmaFenceFpsDataSource",
                    "FPS: $currentFps method=$timeline pkg=$pkg frametimes=$frametimesStr"
                )

                val frametimes = frametimesStr.split(",")
                    .mapNotNull { it.toFloatOrNull() }
                    .filter { it in 1f..200f }

                lastTimeline = timeline
                lastPackage = pkg.takeIf { it != "unknown" && it.contains('.') }

                // Fail-closed: never fabricate FPS when daemon reports 0 or invalid
                if (currentFps <= 0f || currentFps.isNaN() || currentFps.isInfinite()) return
                if (currentFps > 240f) return // daemon already caps, treat >240 as suspect

                val capped = currentFps.coerceIn(1f, 240f)
                val ftMs = if (frametimes.isNotEmpty()) {
                    frametimes.average().toFloat()
                } else {
                    1000f / capped
                }
                // Guard frametime sanity
                if (ftMs <= 0f || ftMs > 500f) return

                val percentiles = if (frametimes.size >= 3) {
                    val buffer = FrametimeBuffer(maxSize = frametimes.size + 4)
                    frametimes.forEach { buffer.push(it) }
                    buffer.computePercentiles()
                } else {
                    null
                }
                val diagnostics = "DMA gpu=${if (timeline.contains("cmdbatch")) "adreno-cmdbatch" else timeline} pkg=$pkg frames=${frametimes.size} tier=T1"
                val sourceDetail = when {
                    timeline.contains("cmdbatch") && timeline.contains("inflight") -> "DMA:cmdbatch_inflight"
                    timeline.contains("cmdbatch") -> "DMA:cmdbatch"
                    timeline.contains("syncpoint") -> "DMA:syncpoint"
                    else -> "DMA:$timeline"
                }
                val elapsedNow = android.os.SystemClock.elapsedRealtime()
                latestSnapshot = FpsSnapshot(
                    currentFps = capped,
                    frametimeAvgMs = ftMs,
                    frametimeP1Ms = percentiles?.p1FrametimeMs ?: 0f,
                    frametimeP01Ms = percentiles?.p01FrametimeMs ?: 0f,
                    frametimeHistogram = frametimes.ifEmpty {
                        listOf(ftMs)
                    },
                    jankCount = 0,
                    method = FpsMethod.DMA_FENCE,
                    accessTier = PrivilegeTier.SU_ROOT, // daemon is root-only; always T1 when delivering
                    packageName = lastPackage,
                    surfaceName = null,
                    timestampMs = System.currentTimeMillis(),
                    measuredAtElapsedMs = elapsedNow,
                    diagnostics = diagnostics,
                    sourceDetail = sourceDetail,
                    sampleAgeMs = 0L,
                    isStale = false,
                    frameCount = frametimes.size.coerceAtLeast(1)
                )
                lastReceivedAtElapsedMs = elapsedNow
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

    /** Fail-closed: drop held root-daemon samples when privilege mode changes. */
    fun clearCache() {
        latestSnapshot = null
        lastReceivedAtElapsedMs = 0L
        lastTimeline = "unknown"
        lastPackage = null
    }

    override suspend fun readFps(): FpsSnapshot? {
        val snapshot = latestSnapshot ?: return null
        val elapsedNow = android.os.SystemClock.elapsedRealtime()
        val ageMs = elapsedNow - snapshot.measuredAtElapsedMs
        if (ageMs > STALE_MS) {
            // Stale is fail-closed: return null rather than holding fabricated FPS indefinitely
            return null
        }
        // Return with updated age for provenance, keep original measured time
        return snapshot.copy(
            sampleAgeMs = ageMs
        )
    }
}
