package com.apexcore.app.freeze

data class FreezeResult(
    val killed: Int,
    val failed: Int,
    val skipped: Int,
    val durationMs: Long,
    val backend: String,
    val totalMemMb: Long,
    val beforeAvailMb: Long,
    val afterAvailMb: Long,
    val swapTotalMb: Long,
    val swapFreeMb: Long
) {
    val freedMb: Long get() = (afterAvailMb - beforeAvailMb).coerceAtLeast(0)
}
