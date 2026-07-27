package com.ivarna.apexcore

import android.content.Context

data class MemStats(
    val ramUsedKb: Long,
    val ramTotalKb: Long,
    val swapUsedKb: Long,
    val swapTotalKb: Long
)

fun getSystemMemStats(context: Context): MemStats {
    var ramTotal = 0L; var ramAvail = 0L
    var swapTotal = 0L; var swapFree = 0L
    try {
        java.io.File("/proc/meminfo").useLines { lines ->
            for (line in lines) {
                when {
                    line.startsWith("MemTotal:") -> ramTotal = line.split(Regex("\\s+"))[1].toLongOrNull() ?: 0L
                    line.startsWith("MemAvailable:") -> ramAvail = line.split(Regex("\\s+"))[1].toLongOrNull() ?: 0L
                    line.startsWith("SwapTotal:") -> swapTotal = line.split(Regex("\\s+"))[1].toLongOrNull() ?: 0L
                    line.startsWith("SwapFree:") -> swapFree = line.split(Regex("\\s+"))[1].toLongOrNull() ?: 0L
                }
            }
        }
    } catch (_: Throwable) {}
    if (ramTotal == 0L) {
        try {
            val actManager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            val memInfo = android.app.ActivityManager.MemoryInfo()
            actManager.getMemoryInfo(memInfo)
            ramTotal = memInfo.totalMem / 1024
            ramAvail = memInfo.availMem / 1024
        } catch (_: Throwable) {}
    }
    val ramUsed = (ramTotal - ramAvail).coerceAtLeast(0)
    val swapUsed = (swapTotal - swapFree).coerceAtLeast(0)
    return MemStats(ramUsed, ramTotal, swapUsed, swapTotal)
}

fun getSystemRamKb(context: Context): Pair<Long, Long> {
    val stats = getSystemMemStats(context)
    return stats.ramUsedKb to stats.ramTotalKb
}
