package com.ivarna.apexcore.fps.model

class FrametimeBuffer(private val maxSize: Int = 7500) {
    private val buffer = mutableListOf<Float>()

    fun push(ftMs: Float) {
        if (ftMs <= 0f || ftMs > 2000f) return
        buffer.add(ftMs)
        if (buffer.size > maxSize) {
            buffer.removeAt(0)
        }
    }

    fun computePercentiles(): PercentileResult {
        if (buffer.size < 3) return PercentileResult(0f, 0f, 0f)

        val sorted = buffer.sorted()
        val totalMs = sorted.sum().toDouble()
        val avgFrametime = totalMs / sorted.size

        val p1Frametime = timeWeightedFrametime(sorted, totalMs, 0.01)
        val p01Frametime = timeWeightedFrametime(sorted, totalMs, 0.001)

        return PercentileResult(
            avgFps = (1000f / avgFrametime).toFloat(),
            p1FrametimeMs = p1Frametime,
            p01FrametimeMs = p01Frametime
        )
    }

    private fun timeWeightedFrametime(
        sortedAsc: List<Float>,
        totalMs: Double,
        fraction: Double
    ): Float {
        val target = totalMs * fraction
        var accum = 0.0
        for (ft in sortedAsc.asReversed()) {
            accum += ft
            if (accum >= target) return ft
        }
        return sortedAsc.last()
    }

    fun clear() {
        buffer.clear()
    }

    val samples: List<Float> get() = buffer.toList()
    val size: Int get() = buffer.size
}