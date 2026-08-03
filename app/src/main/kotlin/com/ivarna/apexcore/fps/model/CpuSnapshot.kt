package com.ivarna.apexcore.fps.model

data class CpuCoreSnapshot(
    val id: Int,
    val frequencyMhz: Int,
    val loadPercent: Int
)

data class CpuSnapshot(
    val overallLoadPercent: Int,
    val cores: List<CpuCoreSnapshot>
) {
    companion object {
        val ZERO = CpuSnapshot(overallLoadPercent = 0, cores = emptyList())
    }
}
