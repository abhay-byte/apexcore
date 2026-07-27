package com.ivarna.apexcore.ram

enum class StopReason {
    RAM_CAP,
    OOM,
    TIMEOUT,
    CANCEL,
    BUDGET
}

sealed class RamFillProgress {
    data object PreFreeze : RamFillProgress()

    data class Filling(
        val allocatedMb: Long,
        val ramUsagePercent: Float,
        val swapUsagePercent: Float,
        val elapsedMs: Long,
        val chunkCount: Int = 0
    ) : RamFillProgress()

    data class Holding(val remainingMs: Long) : RamFillProgress()

    data object Releasing : RamFillProgress()

    data class Done(val result: RamFillResult) : RamFillProgress()
}

data class RamFillResult(
    val freedKb: Long,
    val swapFreedKb: Long,
    val peakAllocatedMb: Long,
    val peakRamPercent: Float,
    val peakSwapPercent: Float,
    val totalDurationMs: Long,
    val fillDurationMs: Long,
    val cancelled: Boolean,
    val oomTriggered: Boolean,
    val preFreezeRan: Boolean,
    val stopReason: StopReason = StopReason.RAM_CAP,
    val chunkCount: Int = 0,
    val heapMaxMb: Long = 0
)
