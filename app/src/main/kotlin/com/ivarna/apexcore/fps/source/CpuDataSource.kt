package com.ivarna.apexcore.fps.source

import com.ivarna.apexcore.fps.model.CpuCoreSnapshot
import com.ivarna.apexcore.fps.model.CpuSnapshot
import com.ivarna.apexcore.fps.privilege.PrivilegePolicy
import com.ivarna.apexcore.fps.privilege.ShellGateway
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * CPU load via `/proc/stat` deltas (ported from factualstats).
 *
 * First sample seeds counters (returns 0%); subsequent samples report
 * active/(active+idle) per core and overall, 0–100%.
 *
 * Privilege: STANDARD file read first, then Root/Shizuku `cat` via [ShellGateway].
 */
class CpuDataSource(
    private val shellGateway: ShellGateway
) {
    @Volatile
    private var lastCpuTimes: Map<String, CpuTime> = emptyMap()

    @Volatile
    private var cachedCoreCount: Int = -1

    internal var procStatPath: String = "/proc/stat"
    internal var cpuPresentPath: String = "/sys/devices/system/cpu/present"
    internal var cpuDirPrefix: String = "/sys/devices/system/cpu"

    private data class CpuTime(val active: Long, val total: Long)

    suspend fun readCpuStats(): CpuSnapshot = withContext(Dispatchers.IO) {
        val cores = mutableListOf<CpuCoreSnapshot>()
        var overallLoad = 0

        val currentCpuTimes = mutableMapOf<String, CpuTime>()
        val text = shellGateway.readPathText(procStatPath, PrivilegePolicy.DEFAULT_CHAIN)
            .orEmpty()
        val statLines = if (text.isNotEmpty()) text.split("\n") else emptyList()

        statLines.forEach { line ->
            if (!line.startsWith("cpu")) return@forEach
            val parts = line.trim().split(Regex("\\s+"))
            if (parts.size < 5) return@forEach
            val name = parts[0]
            val user = parts[1].toLongOrNull() ?: 0L
            val nice = parts[2].toLongOrNull() ?: 0L
            val system = parts[3].toLongOrNull() ?: 0L
            val idle = parts[4].toLongOrNull() ?: 0L
            val iowait = parts.getOrNull(5)?.toLongOrNull() ?: 0L
            val irq = parts.getOrNull(6)?.toLongOrNull() ?: 0L
            val softirq = parts.getOrNull(7)?.toLongOrNull() ?: 0L
            val steal = parts.getOrNull(8)?.toLongOrNull() ?: 0L

            val idleTotal = idle + iowait
            val active = user + nice + system + irq + softirq + steal
            val total = active + idleTotal
            currentCpuTimes[name] = CpuTime(active, total)
        }

        val numCores = getCoreCount()
        val frequencies = IntArray(numCores) { i -> readCoreFrequency(i) }

        synchronized(this@CpuDataSource) {
            val tempLastTimes = lastCpuTimes

            for (i in 0 until numCores) {
                val coreName = "cpu$i"
                val freqMhz = frequencies[i]
                val curTime = currentCpuTimes[coreName]
                val lastTime = tempLastTimes[coreName]
                var loadPercent = 0
                if (curTime != null && lastTime != null) {
                    val activeDiff = curTime.active - lastTime.active
                    val totalDiff = curTime.total - lastTime.total
                    if (totalDiff > 0) {
                        loadPercent = ((activeDiff * 100) / totalDiff).toInt().coerceIn(0, 100)
                    }
                }
                cores.add(CpuCoreSnapshot(id = i, frequencyMhz = freqMhz, loadPercent = loadPercent))
            }

            val curTotal = currentCpuTimes["cpu"]
            val lastTotal = tempLastTimes["cpu"]
            if (curTotal != null && lastTotal != null) {
                val activeDiff = curTotal.active - lastTotal.active
                val totalDiff = curTotal.total - lastTotal.total
                if (totalDiff > 0) {
                    overallLoad = ((activeDiff * 100) / totalDiff).toInt().coerceIn(0, 100)
                }
            }

            lastCpuTimes = currentCpuTimes
        }

        CpuSnapshot(overallLoadPercent = overallLoad, cores = cores)
    }

    private fun getCoreCount(): Int {
        val cached = cachedCoreCount
        if (cached > 0) return cached

        var count = 0
        val presentText = shellGateway.readPathText(cpuPresentPath)
        if (presentText != null) {
            val present = presentText.trim()
            val parts = present.split("-")
            if (parts.size >= 2) {
                val maxCore = parts[1].toIntOrNull()
                if (maxCore != null) count = maxCore + 1
            } else if (parts.size == 1) {
                // "0" or single core index
                val only = parts[0].toIntOrNull()
                if (only != null) count = only + 1
            }
        }

        if (count <= 0) {
            for (i in 0..31) {
                val check = shellGateway.readPathText("$cpuDirPrefix/cpu$i/online")
                    ?: shellGateway.readPathText("$cpuDirPrefix/cpu$i/cpufreq/scaling_cur_freq")
                if (check != null) count = i + 1
            }
        }

        if (count <= 0) {
            count = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
        }
        cachedCoreCount = count
        return count
    }

    private fun readCoreFrequency(coreId: Int): Int {
        val path = "$cpuDirPrefix/cpu$coreId/cpufreq/scaling_cur_freq"
        return try {
            val content = shellGateway.readPathText(path)
            val freqKhz = content?.trim()?.toLongOrNull() ?: 0L
            (freqKhz / 1000L).toInt()
        } catch (_: Exception) {
            0
        }
    }
}
