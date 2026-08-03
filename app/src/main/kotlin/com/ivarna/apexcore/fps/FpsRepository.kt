package com.ivarna.apexcore.fps

import com.ivarna.apexcore.fps.model.FpsMethod
import com.ivarna.apexcore.fps.model.FpsSnapshot
import com.ivarna.apexcore.fps.model.FrametimeBuffer
import com.ivarna.apexcore.fps.source.DmaFenceFpsDataSource
import com.ivarna.apexcore.fps.source.FpsDaemonManager
import com.ivarna.apexcore.fps.source.FpsDataSource
import com.ivarna.apexcore.fps.source.GfxinfoFpsDataSource
import com.ivarna.apexcore.fps.source.SurfaceFlingerFpsDataSource
import com.ivarna.apexcore.fps.util.ForegroundAppResolver

/**
 * Multi-tier FPS resolver (ported from factualstats).
 *
 * Cascade (matches factualstats FpsRepositoryImpl):
 * ```
 * Games:
 *   SurfaceFlinger --latency  (never DMA: counts vsync on Mali/non-Adreno)
 *   gfxinfo skipped
 * UI / non-game:
 *   DMA daemon (root Adreno/Mali) → SurfaceFlinger → gfxinfo
 * ```
 *
 * Skipping DMA for games is intentional — factualstats found dma_fence often
 * reports display refresh rate rather than real game frame rate, causing
 * huge FPS swings when methods fight (e.g. 120 vs 60–80).
 */
interface FpsRepository {
    suspend fun getFps(): FpsSnapshot
    fun ensureDaemonStarted(): Boolean
    fun stopDaemon()

    /** Hint: package being monitored by the overlay (force game routing). */
    fun setTargetPackage(packageName: String?)
}

class FpsRepositoryImpl(
    dmaFenceSource: DmaFenceFpsDataSource,
    surfaceFlingerSource: SurfaceFlingerFpsDataSource,
    private val gfxinfoSource: GfxinfoFpsDataSource,
    private val foregroundAppResolver: ForegroundAppResolver,
    private val fpsDaemonManager: FpsDaemonManager
) : FpsRepository {

    private val sources: List<FpsDataSource> = listOf(
        dmaFenceSource,
        surfaceFlingerSource,
        gfxinfoSource
    ).sortedBy { it.priority }

    private val frametimeBuffers = mutableMapOf<String, FrametimeBuffer>()
    private var lastSourceKey: String? = null
    private var lastBatchFingerprint: String? = null
    private var lastGoodSnapshot: FpsSnapshot? = null
    private var lastGoodAtMs: Long = 0L
    /** Short ring for HUD display stability (median of last samples). */
    private val recentDisplayFps = ArrayDeque<Float>(DISPLAY_SMOOTH_N)

    @Volatile
    private var targetPackage: String? = null

    override fun setTargetPackage(packageName: String?) {
        targetPackage = packageName
        // Keep SF/gfxinfo locked on the monitored game even if overlay steals focus.
        foregroundAppResolver.preferredPackage = packageName
    }

    override fun ensureDaemonStarted(): Boolean = fpsDaemonManager.ensureStarted()

    override fun stopDaemon() = fpsDaemonManager.stop()

    override suspend fun getFps(): FpsSnapshot {
        fpsDaemonManager.ensureStarted()

        val foreground = foregroundAppResolver.resolve()
        val pkg = targetPackage ?: foreground?.packageName
        val isGame = when {
            pkg == null -> false
            // Own-app test HUD is UI, not a game GPU path
            pkg.contains("apexcore") -> false
            targetPackage != null && !pkg.contains("apexcore") -> true
            else -> foregroundAppResolver.isGameLikeSurface(pkg)
        }

        var rawSnapshot: FpsSnapshot? = null
        for (source in sources) {
            // Never trust gfxinfo for Vulkan/NativeActivity/SurfaceView games.
            if (isGame && source is GfxinfoFpsDataSource) continue
            // For games, skip DMA_FENCE: on Mali/non-Adreno GPUs it counts
            // display refresh rate (vsync), not actual game frame rate.
            // Use SurfaceFlinger latency which measures real buffer presentation.
            if (isGame && source is DmaFenceFpsDataSource) continue
            val snapshot = source.readFps() ?: continue
            if (snapshot.currentFps <= 0f) continue
            rawSnapshot = snapshot
            break
        }

        // Emergency fallback: UI apps where DMA+SF both fail.
        if (rawSnapshot == null && !isGame) {
            val gfx = gfxinfoSource.readFps()
            if (gfx != null && gfx.currentFps > 0f) rawSnapshot = gfx
        }

        rawSnapshot = maybePreferGfxinfoForUi(rawSnapshot, isGame)

        // Hold last good reading when GPU path briefly drops (avoid FPS=0 flash).
        if (rawSnapshot == null && lastGoodSnapshot != null) {
            val age = System.currentTimeMillis() - lastGoodAtMs
            if (age < LAST_GOOD_HOLD_MS) {
                rawSnapshot = lastGoodSnapshot
            }
        }
        if (rawSnapshot != null && rawSnapshot.currentFps > 0f &&
            rawSnapshot.method != FpsMethod.NONE
        ) {
            if (rawSnapshot.currentFps <= 144f || rawSnapshot.method != FpsMethod.DMA_FENCE) {
                lastGoodSnapshot = rawSnapshot
                lastGoodAtMs = System.currentTimeMillis()
            }
        }

        if (rawSnapshot == null) return FpsSnapshot.ZERO

        val sourceKey = rawSnapshot.method.name
        if (sourceKey != lastSourceKey) {
            frametimeBuffers[sourceKey]?.clear()
            lastSourceKey = sourceKey
            lastBatchFingerprint = null
        }

        val buffer = frametimeBuffers.getOrPut(sourceKey) { FrametimeBuffer(maxSize = 7500) }
        ingestFrametimes(rawSnapshot, buffer, sourceKey)

        val percentiles = buffer.computePercentiles()
        val refreshHz = foreground?.refreshRateHz ?: 60f
        val resolvedFps = resolveDisplayFps(rawSnapshot, refreshHz)
        val displayFps = smoothDisplayFps(resolvedFps)
        val p1Ms = percentiles.p1FrametimeMs
            .takeIf { it in 1f..200f } ?: rawSnapshot.frametimeP1Ms.takeIf { it in 1f..200f } ?: 0f
        val p01Ms = percentiles.p01FrametimeMs
            .takeIf { it in 1f..200f } ?: rawSnapshot.frametimeP01Ms.takeIf { it in 1f..200f } ?: 0f
        return rawSnapshot.copy(
            currentFps = displayFps,
            frametimeAvgMs = if (displayFps > 0f) 1000f / displayFps else rawSnapshot.frametimeAvgMs,
            frametimeP1Ms = p1Ms,
            frametimeP01Ms = p01Ms,
            frametimeHistogram = if (buffer.size > 0) buffer.samples else rawSnapshot.frametimeHistogram
        )
    }

    /** Median of last N samples — kills one-sample spikes without laggy EMA. */
    private fun smoothDisplayFps(fps: Float): Float {
        if (fps <= 0f) return fps
        while (recentDisplayFps.size >= DISPLAY_SMOOTH_N) recentDisplayFps.removeFirst()
        recentDisplayFps.addLast(fps)
        val sorted = recentDisplayFps.sorted()
        return sorted[sorted.size / 2]
    }

    /**
     * UI apps may undercount on Adreno GPU paths when idle — prefer gfxinfo only then.
     */
    private suspend fun maybePreferGfxinfoForUi(
        dmaSnapshot: FpsSnapshot?,
        isGame: Boolean
    ): FpsSnapshot? {
        if (isGame) return dmaSnapshot
        if (dmaSnapshot == null || dmaSnapshot.method != FpsMethod.DMA_FENCE) return dmaSnapshot
        if (dmaSnapshot.currentFps >= UI_GPU_UNDERCNT_MAX_FPS) return dmaSnapshot

        val gfxSnapshot = gfxinfoSource.readFps()
        if (gfxSnapshot != null && gfxSnapshot.currentFps > dmaSnapshot.currentFps) {
            return gfxSnapshot
        }
        return dmaSnapshot
    }

    private companion object {
        const val UI_GPU_UNDERCNT_MAX_FPS = 5f
        const val LAST_GOOD_HOLD_MS = 4000L
        const val DISPLAY_SMOOTH_N = 3
    }

    private fun resolveDisplayFps(snapshot: FpsSnapshot, refreshHz: Float): Float {
        if (snapshot.method == FpsMethod.DMA_FENCE) {
            return snapshot.currentFps.coerceIn(1f, 240f)
        }

        val refreshCeiling = refreshHz.coerceIn(1f, 240f)
        if (snapshot.frametimeHistogram.size >= 2) {
            val avgMs = snapshot.frametimeHistogram.average().toFloat()
            if (avgMs > 0f) {
                val ftFps = (1000f / avgMs).coerceIn(1f, 240f)
                val expectedMs = 1000f / refreshCeiling
                if (avgMs in expectedMs * 0.82f..expectedMs * 1.18f) {
                    return refreshCeiling
                }
                return ftFps.coerceAtMost(refreshCeiling)
            }
        }
        return snapshot.currentFps.coerceAtMost(refreshCeiling)
    }

    private fun ingestFrametimes(snapshot: FpsSnapshot, buffer: FrametimeBuffer, sourceKey: String) {
        val batch = snapshot.frametimeHistogram
        if (batch.isEmpty()) return

        val fingerprint = buildString {
            append(sourceKey)
            append(':')
            append(batch.size)
            append(':')
            append(batch.firstOrNull())
            append(':')
            append(batch.lastOrNull())
            append(':')
            append(batch.sum())
        }
        if (fingerprint == lastBatchFingerprint) return
        lastBatchFingerprint = fingerprint

        batch.forEach { buffer.push(it) }
    }
}
