package com.ivarna.apexcore.tune.cpu

import com.ivarna.apexcore.fps.privilege.PrivilegeTier
import com.ivarna.apexcore.tune.TuneShell

data class CpuPolicyDescriptor(
    val name: String,
    val relatedCpus: Set<Int>,
    val driver: String?,
    val minPath: String,
    val maxPath: String,
    val governorPath: String,
    val availableGovernors: Set<String>,
    val availableFrequenciesKhz: List<Long>,
    val cpuInfoMaxKhz: Long?,
    val currentMaxKhz: Long,
    val currentMinKhz: Long,
    val currentGovernor: String
) {
    val targetMaxKhz: Long
        get() = (availableFrequenciesKhz.maxOrNull() ?: cpuInfoMaxKhz ?: currentMaxKhz)
            .takeIf { it > 0 } ?: currentMaxKhz
}

/** Discovers actual cpufreq policy directories; it never guesses cluster IDs. */
object CpuPolicyDiscovery {
    fun discover(shell: TuneShell, tier: PrivilegeTier, timeoutMs: Long = 250L): List<CpuPolicyDescriptor> {
        val listing = shell.execute(
            "find /sys/devices/system/cpu/cpufreq -mindepth 1 -maxdepth 1 -type d -name 'policy*' -print 2>/dev/null | sort -V",
            tier,
            timeoutMs
        )
        val paths = listing.output.lineSequence()
            .map { it.trim() }
            .filter { it.matches(Regex("/sys/devices/system/cpu/cpufreq/policy[0-9]+")) }
            .distinct()
            .toList()

        return paths.mapNotNull { base -> describe(base, shell) }
    }

    fun describe(base: String, shell: TuneShell): CpuPolicyDescriptor? {
        val minPath = "$base/scaling_min_freq"
        val maxPath = "$base/scaling_max_freq"
        val governorPath = "$base/scaling_governor"
        if (!shell.exists(minPath) || !shell.exists(maxPath) || !shell.exists(governorPath)) return null

        val currentMin = shell.read(minPath)?.trim()?.toLongOrNull() ?: return null
        val currentMax = shell.read(maxPath)?.trim()?.toLongOrNull() ?: return null
        val currentGovernor = shell.read(governorPath)?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val availableGovernors = shell.read("$base/scaling_available_governors")
            ?.split(Regex("\\s+"))?.map { it.trim() }?.filter { it.isNotEmpty() }?.toSet().orEmpty()
        val availableFrequencies = shell.read("$base/scaling_available_frequencies")
            ?.split(Regex("\\s+"))?.mapNotNull { it.toLongOrNull() }?.sorted().orEmpty()
        val cpuInfoMax = shell.read("$base/cpuinfo_max_freq")?.trim()?.toLongOrNull()
        val related = shell.read("$base/related_cpus")
            ?.split(Regex("\\s+"))?.mapNotNull { it.toIntOrNull() }?.toSet().orEmpty()
        val driver = shell.read("$base/scaling_driver")?.trim()?.takeIf { it.isNotEmpty() }

        return CpuPolicyDescriptor(
            name = base.substringAfterLast('/'),
            relatedCpus = related,
            driver = driver,
            minPath = minPath,
            maxPath = maxPath,
            governorPath = governorPath,
            availableGovernors = availableGovernors,
            availableFrequenciesKhz = availableFrequencies,
            cpuInfoMaxKhz = cpuInfoMax,
            currentMaxKhz = currentMax,
            currentMinKhz = currentMin,
            currentGovernor = currentGovernor
        )
    }

    fun governorIntersection(policies: List<CpuPolicyDescriptor>): Set<String> {
        if (policies.isEmpty()) return emptySet()
        return policies.map { it.availableGovernors }.reduce { left, right -> left intersect right }
    }
}
