package com.apexcore.app.freeze

data class FreezeResult(
    val killed: Int,
    val failed: Int,
    val skipped: Int,
    val durationMs: Long,
    val backend: String,
    val beforeAvailMb: Long,
    val afterAvailMb: Long
) {
    val freedMb: Long get() = (afterAvailMb - beforeAvailMb).coerceAtLeast(0)
}
