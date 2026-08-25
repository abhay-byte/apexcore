package com.ivarna.apexcore.fps.model

import com.ivarna.apexcore.fps.privilege.PrivilegeTier

data class FpsSnapshot(
    val currentFps: Float,
    val frametimeAvgMs: Float,
    val frametimeP1Ms: Float,
    val frametimeP01Ms: Float,
    val frametimeHistogram: List<Float>,
    val jankCount: Int,
    val method: FpsMethod,
    /** Privilege tier that produced this sample (provenance, not quality tier). */
    val accessTier: PrivilegeTier? = null,
    /** Package this sample was measured for (game pkg when overlay active). */
    val packageName: String? = null,
    /** SurfaceFlinger layer name when method==SF, otherwise null. */
    val surfaceName: String? = null,
    /** Epoch millis when sample was computed. */
    val timestampMs: Long = System.currentTimeMillis(),
    /** Monotonic elapsedRealtime when sample was computed (for age & cadence). */
    val measuredAtElapsedMs: Long = try { android.os.SystemClock.elapsedRealtime() } catch (_: Throwable) { 0L },
    /** Human-readable diagnostics: how value was obtained, confidence, fallback reason. */
    val diagnostics: String? = null,
    /** Compact source detail like SF:layerName, DMA:cmdbatch_inflight, GFX:framestats */
    val sourceDetail: String? = null,
    /** Age of sample when it was returned (elapsed now - measured). 0 = fresh. */
    val sampleAgeMs: Long = 0L,
    /** True when this is a held last-good sample because current poll produced no fresh data. */
    val isStale: Boolean = false,
    /** Number of valid frames that contributed to currentFps (SF ring recent window or DMA gap count). */
    val frameCount: Int = 0
) {
    companion object {
        val ZERO = FpsSnapshot(
            currentFps = 0f,
            frametimeAvgMs = 0f,
            frametimeP1Ms = 0f,
            frametimeP01Ms = 0f,
            frametimeHistogram = emptyList(),
            jankCount = 0,
            method = FpsMethod.NONE,
            accessTier = null,
            packageName = null,
            surfaceName = null,
            timestampMs = 0L,
            measuredAtElapsedMs = 0L,
            diagnostics = "no data",
            sourceDetail = null,
            isStale = false,
            frameCount = 0
        )
    }

    fun isFresh(maxAgeMs: Long = 4000L): Boolean =
        !isStale && currentFps > 0f && method != FpsMethod.NONE &&
            (try { android.os.SystemClock.elapsedRealtime() - measuredAtElapsedMs } catch (_: Throwable) { System.currentTimeMillis() - timestampMs }) < maxAgeMs
}

enum class FpsMethod {
    DMA_FENCE,
    SURFACEFLINGER,
    GFXINFO,
    CHOREOGRAPHER,
    NONE
}

fun FpsMethod.abbrev(): String = when (this) {
    FpsMethod.DMA_FENCE -> "DMA"
    FpsMethod.SURFACEFLINGER -> "SF"
    FpsMethod.GFXINFO -> "GFX"
    FpsMethod.CHOREOGRAPHER -> "CHR"
    FpsMethod.NONE -> "--"
}

/** Tier-aware provenance tag for overlay diagnostics. */
fun FpsSnapshot.provenanceLabel(): String = when {
    method == FpsMethod.NONE || currentFps <= 0f -> "--"
    accessTier == null -> method.abbrev()
    else -> "${method.abbrev()}@${accessTier.badge}"
}

data class PercentileResult(
    val avgFps: Float,
    val p1FrametimeMs: Float,
    val p01FrametimeMs: Float
)
