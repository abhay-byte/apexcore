package com.ivarna.apexcore.tune.gpu

import com.ivarna.apexcore.fps.privilege.PrivilegeTier
import com.ivarna.apexcore.fps.util.GpuVendor
import com.ivarna.apexcore.tune.TuneShell

data class GpuDevfreqDescriptor(
    val basePath: String,
    val vendor: GpuVendor,
    val minPath: String,
    val maxPath: String,
    val governorPath: String?,
    val availableGovernorPath: String?,
    val availableFrequencyPath: String?,
    val availableGovernors: Set<String>,
    val availableFrequencies: List<Long>,
    val currentMin: Long,
    val currentMax: Long,
    val currentGovernor: String?
) {
    val targetMax: Long get() = availableFrequencies.maxOrNull() ?: currentMax
    val hasReliableMax: Boolean get() = targetMax > 0L && (availableFrequencies.isNotEmpty() || currentMax > 0L)
}

/** Identifies only GPU devfreq nodes, never arbitrary DDR/NPU/ISP entries. */
object GpuDevfreqDiscovery {
    fun discover(shell: TuneShell, tier: PrivilegeTier, timeoutMs: Long = 250L): List<GpuDevfreqDescriptor> {
        val candidates = linkedSetOf<String>()
        candidates += "/sys/class/kgsl/kgsl-3d0/devfreq"
        val listing = shell.execute(
            "find /sys/class/devfreq /sys/class/misc/mali0/device/devfreq -mindepth 1 -maxdepth 1 -type d -print 2>/dev/null",
            tier,
            timeoutMs
        )
        listing.output.lineSequence().map { it.trim() }
            .filter { it.startsWith("/sys/class/devfreq/") || it.contains("/mali0/device/devfreq/") }
            .forEach { candidates += it }

        return candidates.mapNotNull { describe(it, shell) }
            .filter { it.vendor == GpuVendor.ADRENO || it.vendor == GpuVendor.MALI || it.basePath.contains("gpu", true) }
            .distinctBy { it.basePath }
    }

    fun describe(base: String, shell: TuneShell): GpuDevfreqDescriptor? {
        val minPath = "$base/min_freq"
        val maxPath = "$base/max_freq"
        if (!shell.exists(minPath) || !shell.exists(maxPath)) return null
        val currentMin = shell.read(minPath)?.trim()?.toLongOrNull() ?: return null
        val currentMax = shell.read(maxPath)?.trim()?.toLongOrNull() ?: return null

        val governorPath = "$base/governor".takeIf { shell.exists(it) }
        val availableGovernorPath = "$base/available_governors".takeIf { shell.exists(it) }
        val availableFrequencyPath = sequenceOf(
            "$base/available_frequencies",
            "$base/gpu_available_frequencies",
            "$base/../gpu_available_frequencies"
        ).firstOrNull { shell.exists(it) }
        val governors = availableGovernorPath?.let { shell.read(it) }
            ?.split(Regex("\\s+"))?.map { it.trim() }?.filter { it.isNotEmpty() }?.toSet().orEmpty()
        val frequencies = availableFrequencyPath?.let { shell.read(it) }
            ?.split(Regex("\\s+"))?.mapNotNull { it.toLongOrNull() }?.sorted().orEmpty()
        val currentGovernor = governorPath?.let { shell.read(it)?.trim()?.takeIf(String::isNotEmpty) }
        val identityText = listOf(
            base,
            shell.read("$base/name"),
            shell.read("$base/device/uevent")
        ).filterNotNull().joinToString(" ").lowercase()
        val vendor = when {
            base.contains("kgsl", true) || identityText.contains("kgsl") || identityText.contains("adreno") -> GpuVendor.ADRENO
            base.contains("mali", true) || identityText.contains("mali") -> GpuVendor.MALI
            else -> GpuVendor.UNKNOWN
        }
        return GpuDevfreqDescriptor(
            basePath = base,
            vendor = vendor,
            minPath = minPath,
            maxPath = maxPath,
            governorPath = governorPath,
            availableGovernorPath = availableGovernorPath,
            availableFrequencyPath = availableFrequencyPath,
            availableGovernors = governors,
            availableFrequencies = frequencies,
            currentMin = currentMin,
            currentMax = currentMax,
            currentGovernor = currentGovernor
        )
    }
}
