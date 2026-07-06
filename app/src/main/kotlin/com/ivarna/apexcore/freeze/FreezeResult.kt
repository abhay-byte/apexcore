package com.ivarna.apexcore.freeze

data class FreezeResult(
    val killed: Int,
    val failed: Int,
    val skipped: Int,
    val durationMs: Long,
    val backend: String,
    val totalMemMb: Long,
    val beforeAvailMb: Long,
    val afterAvailMb: Long,
    val freedKb: Long = 0,
    val swapTotalMb: Long,
    val swapFreeMb: Long
) {
    val freedMb: Long get() = freedKb / 1024
}
