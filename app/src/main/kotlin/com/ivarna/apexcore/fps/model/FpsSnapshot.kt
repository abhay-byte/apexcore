package com.ivarna.apexcore.fps.model

data class FpsSnapshot(
    val currentFps: Float,
    val frametimeAvgMs: Float,
    val frametimeP1Ms: Float,
    val frametimeP01Ms: Float,
    val frametimeHistogram: List<Float>,
    val jankCount: Int,
    val method: FpsMethod
) {
    companion object {
        val ZERO = FpsSnapshot(
            currentFps = 0f,
            frametimeAvgMs = 0f,
            frametimeP1Ms = 0f,
            frametimeP01Ms = 0f,
            frametimeHistogram = emptyList(),
            jankCount = 0,
            method = FpsMethod.NONE
        )
    }
}

enum class FpsMethod {
    DMA_FENCE,
    SURFACEFLINGER,
    GFXINFO,
    CHOREOGRAPHER,
    NONE
}

data class PercentileResult(
    val avgFps: Float,
    val p1FrametimeMs: Float,
    val p01FrametimeMs: Float
)
