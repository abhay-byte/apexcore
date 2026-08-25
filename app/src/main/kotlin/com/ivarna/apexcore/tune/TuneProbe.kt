package com.ivarna.apexcore.tune

import android.app.NotificationManager
import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import com.ivarna.apexcore.fps.privilege.PrivilegeTier
import com.ivarna.apexcore.fps.util.GpuVendor
import com.ivarna.apexcore.fps.util.GpuVendorDetector
import com.ivarna.apexcore.freeze.FreezeFramework
import com.ivarna.apexcore.tune.cpu.CpuPolicyDiscovery
import com.ivarna.apexcore.tune.gpu.GpuDevfreqDiscovery
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * Capability-first asynchronous prober for Game Optimisation kernel nodes and settings.
 * Enforces strict budget (3500ms wall-clock, 120ms per-node timeout), caching,
 * write-verification, and GPU_FLOOR frequency-node gating.
 */
class TuneProbe(
    private val context: Context,
    private val shell: TuneShell,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
    private val backendResolver: TuneBackendResolver? = null
) {
    private val _capabilities = MutableStateFlow<Map<TuneId, TuneCapability>>(emptyMap())
    val capabilities: StateFlow<Map<TuneId, TuneCapability>> = _capabilities.asStateFlow()

    private val _isProbing = MutableStateFlow(false)
    val isProbing: StateFlow<Boolean> = _isProbing.asStateFlow()

    @Volatile
    private var lastProbeTime = 0L

    @Volatile
    private var lastBackendFingerprint: String? = null

    private var probeJob: Job? = null

    init {
        // Initialize default probing/uninitialized state
        _capabilities.value = TuneSpecs.all.associate { spec ->
            spec.id to TuneCapability(
                id = spec.id,
                available = false,
                needsRoot = false,
                writablePaths = emptyList(),
                subtitle = "Checking this kernel…"
            )
        }
    }

    /**
     * Refresh capabilities asynchronously. Returns immediately.
     */
    fun refreshCapabilities() {
        val currentBackend = backendResolver?.fingerprint() ?: FreezeFramework.activeBackend.value?.name
        val now = SystemClock.elapsedRealtime()
        if (now - lastProbeTime < CACHE_TTL_MS && currentBackend == lastBackendFingerprint && _capabilities.value.isNotEmpty()) {
            // Valid cache
            return
        }

        probeJob?.cancel()
        probeJob = scope.launch {
            probeInternal()
        }
    }

    suspend fun probeSync(force: Boolean = false): Map<TuneId, TuneCapability> = withContext(Dispatchers.IO) {
        try { probeJob?.join() } catch (_: Throwable) {}
        val now = SystemClock.elapsedRealtime()
        val currentBackend = backendResolver?.fingerprint() ?: FreezeFramework.activeBackend.value?.name
        val cached = _capabilities.value
        val hasProbed = cached.isNotEmpty() && cached.values.any { it.subtitle != "Checking this kernel…" }
        if (!force && now - lastProbeTime < CACHE_TTL_MS && currentBackend == lastBackendFingerprint && hasProbed) {
            return@withContext cached
        }
        probeInternal()
        _capabilities.value
    }

    private suspend fun probeInternal() = withContext(Dispatchers.IO) {
        val backend = backendResolver?.refresh() ?: when (FreezeFramework.activeBackend.value?.name) {
            "Root" -> TuneBackendIdentity.SU_ROOT
            "Shizuku" -> TuneBackendIdentity.SHIZUKU_SHELL
            else -> TuneBackendIdentity.STANDARD
        }
        val tier = backend.asPrivilegeTier()

        _isProbing.value = true
        val startTime = SystemClock.elapsedRealtime()
        val deadline = startTime + WALL_BUDGET_MS

        Log.i(TAG, "Starting capability probe with tier: $tier")

        val discoveredWritable = mutableMapOf<String, Boolean>() // path -> isWritable
        val discoveredAvailableOptions = mutableMapOf<TuneId, List<String>>()
        val gpuVendor = GpuVendorDetector.detect()
        val dynamicCpu = if (tier != PrivilegeTier.STANDARD) CpuPolicyDiscovery.discover(shell, tier) else emptyList()
        val dynamicGpu = if (tier != PrivilegeTier.STANDARD) GpuDevfreqDiscovery.discover(shell, tier).firstOrNull() else null

        if (tier == PrivilegeTier.STANDARD) {
            // Standard tier cannot write sysfs. Check Settings/Focus APIs only.
            val resultMap = buildStandardCapabilities()
            _capabilities.value = resultMap
            lastProbeTime = SystemClock.elapsedRealtime()
            lastBackendFingerprint = backendResolver?.fingerprint() ?: FreezeFramework.activeBackend.value?.name
            _isProbing.value = false
            return@withContext
        }

        // Phase 1: representative candidate per TuneId
        val phase1Nodes = TuneCatalog.phase1Candidates().filter { node ->
            isVendorCompatible(node.vendor, gpuVendor)
        }

        probeNodeBatch(phase1Nodes, tier, discoveredWritable, discoveredAvailableOptions, deadline)

        // Phase 2: fill remaining candidates up to MAX_TOTAL_PROBES
        if (SystemClock.elapsedRealtime() < deadline && discoveredWritable.size < MAX_TOTAL_PROBES) {
            val probedPaths = discoveredWritable.keys
            val remainingNodes = TuneCatalog.allNodes.filter { node ->
                !probedPaths.contains(node.path) && isVendorCompatible(node.vendor, gpuVendor)
            }.take(MAX_TOTAL_PROBES - discoveredWritable.size)

            if (remainingNodes.isNotEmpty()) {
                probeNodeBatch(remainingNodes, tier, discoveredWritable, discoveredAvailableOptions, deadline)
            }
        }

        // Build capability results for each TuneId
        val resultMap = mutableMapOf<TuneId, TuneCapability>()
        for (spec in TuneSpecs.all) {
            val id = spec.id
            val nodes = TuneCatalog.nodesByTuneId[id].orEmpty()
            val writableNodes = nodes.filter { discoveredWritable[it.path] == true }
            when (id) {
                TuneId.CPU_GOVERNOR -> resultMap[id] = probeCpuGovernor(dynamicCpu, tier, backend)
                TuneId.CPU_LOCK_MAX -> resultMap[id] = probeCpuLock(dynamicCpu, tier, backend)
                TuneId.GPU_GOVERNOR -> resultMap[id] = probeGpuGovernor(dynamicGpu, tier, backend)
                TuneId.GPU_LOCK_MAX -> resultMap[id] = probeGpuLock(dynamicGpu, tier, backend)
                TuneId.GAME_MODE_PERFORMANCE -> resultMap[id] = TuneCapability(
                    id = id,
                    available = false,
                    needsRoot = false,
                    writablePaths = emptyList(),
                    subtitle = "Available per game when Android exposes Performance mode",
                    reason = CapabilityReason.OPTION_NOT_SUPPORTED,
                    backend = backend
                )
                // Focus & Display special handling
                TuneId.FOCUS_DND -> {
                    val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                    val hasDndAccess = nm?.isNotificationPolicyAccessGranted == true
                    resultMap[id] = TuneCapability(
                        id = id,
                        available = hasDndAccess,
                        needsRoot = false,
                        writablePaths = emptyList(),
                        subtitle = if (hasDndAccess) "Silence notifications during game" else "Needs Do Not Disturb access"
                    )
                }
                TuneId.FOCUS_HEADSUP -> {
                    val isElevated = tier != PrivilegeTier.STANDARD
                    resultMap[id] = TuneCapability(
                        id = id,
                        available = isElevated,
                        needsRoot = false,
                        writablePaths = emptyList(),
                        subtitle = if (isElevated) "Suppress floating heads-up popups" else "Needs Shizuku or Root"
                    )
                }
                TuneId.FOCUS_IMMERSIVE -> {
                    val isElevated = tier != PrivilegeTier.STANDARD
                    resultMap[id] = TuneCapability(
                        id = id,
                        available = isElevated,
                        needsRoot = false,
                        writablePaths = emptyList(),
                        subtitle = if (isElevated) "Auto-hide status and navigation bars" else "Needs Shizuku or Root"
                    )
                }
                TuneId.DISPLAY_PEAK -> {
                    val isElevated = tier != PrivilegeTier.STANDARD
                    val peakStr = try {
                        Settings.System.getString(context.contentResolver, "peak_refresh_rate")
                            ?: Settings.Global.getString(context.contentResolver, "peak_refresh_rate")
                            ?: Settings.Secure.getString(context.contentResolver, "peak_refresh_rate")
                    } catch (_: Throwable) { null }
                    val hasValidPeak = peakStr != null && peakStr.isNotBlank() && (peakStr.toDoubleOrNull() ?: 0.0) > 0.0
                    val available = isElevated && hasValidPeak
                    resultMap[id] = TuneCapability(
                        id = id,
                        available = available,
                        needsRoot = false,
                        writablePaths = emptyList(),
                        subtitle = when {
                            !isElevated -> "Needs Shizuku or Root"
                            hasValidPeak -> "Lock refresh rate to peak (${peakStr}Hz)"
                            else -> "Peak rate setting not found on this ROM"
                        }
                    )
                }
                TuneId.DISPLAY_MIUI -> {
                    val isElevated = tier != PrivilegeTier.STANDARD
                    val miuiMode = try {
                        Settings.System.getString(context.contentResolver, "refresh_rate_mode")
                            ?: Settings.System.getString(context.contentResolver, "miui_refresh_rate")
                    } catch (_: Throwable) { null }
                    val hasMiui = miuiMode != null && miuiMode.isNotBlank()
                    val available = isElevated && hasMiui
                    resultMap[id] = TuneCapability(
                        id = id,
                        available = available,
                        needsRoot = false,
                        writablePaths = emptyList(),
                        subtitle = when {
                            !isElevated -> "Needs Shizuku or Root"
                            hasMiui -> "Xiaomi high refresh mode"
                            else -> "MIUI display mode unavailable"
                        }
                    )
                }
                TuneId.GPU_FLOOR -> {
                    // Critical KD-6: min_pwrlevel alone does NOT enable GPU_FLOOR.
                    // Must have a real frequency-floor node with groupId == "gpu_min".
                    val hasFreqFloor = writableNodes.any { it.groupId == "gpu_min" }
                    val needsRoot = nodes.any { it.privilege == TunePrivilege.ROOT_ONLY } && tier == PrivilegeTier.SHIZUKU_SHELL && !hasFreqFloor
                    resultMap[id] = TuneCapability(
                        id = id,
                        available = hasFreqFloor,
                        needsRoot = needsRoot,
                        writablePaths = writableNodes.map { it.path },
                        subtitle = when {
                            hasFreqFloor -> "Available (raises GPU clock floor)"
                            needsRoot -> "Needs Root backend"
                            else -> "Capability unavailable on this kernel"
                        }
                    )
                }
                else -> {
                    val isAvailable = writableNodes.isNotEmpty()
                    val needsRoot = nodes.any { it.privilege == TunePrivilege.ROOT_ONLY } && tier == PrivilegeTier.SHIZUKU_SHELL && !isAvailable
                    val options = discoveredAvailableOptions[id].orEmpty()
                    resultMap[id] = TuneCapability(
                        id = id,
                        available = isAvailable,
                        needsRoot = needsRoot,
                        writablePaths = writableNodes.map { it.path },
                        availableOptions = options,
                        subtitle = when {
                            isAvailable -> "Available on this kernel"
                            needsRoot -> "Needs Root backend"
                            else -> "Capability unavailable on this kernel"
                        }
                    )
                }
            }
        }

        _capabilities.value = resultMap
        lastProbeTime = SystemClock.elapsedRealtime()
        lastBackendFingerprint = backendResolver?.fingerprint() ?: FreezeFramework.activeBackend.value?.name
        _isProbing.value = false
        Log.i(TAG, "Capability probe finished in ${SystemClock.elapsedRealtime() - startTime}ms. Writable count: ${resultMap.count { it.value.available }}")
    }

    private suspend fun probeNodeBatch(
        nodes: List<TuneNode>,
        tier: PrivilegeTier,
        discoveredWritable: MutableMap<String, Boolean>,
        discoveredAvailableOptions: MutableMap<TuneId, List<String>>,
        deadline: Long
    ) {
        for (chunk in nodes.chunked(4)) {
            if (SystemClock.elapsedRealtime() > deadline) break
            coroutineScope {
                chunk.map { node ->
                    async(Dispatchers.IO) {
                        probeSingleNode(node, tier, discoveredWritable, discoveredAvailableOptions)
                    }
                }.awaitAll()
            }
        }
    }

    private fun probeSingleNode(
        node: TuneNode,
        tier: PrivilegeTier,
        discoveredWritable: MutableMap<String, Boolean>,
        discoveredAvailableOptions: MutableMap<TuneId, List<String>>
    ) {
        // Resolve the required identity, not just the historical privilege
        // label. A shell-backed Shizuku service must fail closed for root-only
        // nodes, while a verified root-backed Shizuku service is allowed.
        if (node.requiredIdentity == RequiredIdentity.ROOT &&
            tier != PrivilegeTier.SU_ROOT && tier != PrivilegeTier.SHIZUKU_ROOT
        ) {
            discoveredWritable[node.path] = false
            return
        }

        val exists = shell.exists(node.path, timeoutMs = PER_NODE_TIMEOUT_MS)
        if (!exists) {
            discoveredWritable[node.path] = false
            return
        }

        // Read current value
        val currentVal = shell.read(node.path, timeoutMs = PER_NODE_TIMEOUT_MS)?.trim()
        if (currentVal.isNullOrBlank()) {
            discoveredWritable[node.path] = false
            return
        }

        if (node.probeStrategy == ProbeStrategy.READ_METADATA_ONLY || node.probeStrategy == ProbeStrategy.COMMAND_QUERY) {
            discoveredWritable[node.path] = false
            return
        }

        // Read available options if availablePath exists
        node.availablePath?.let { availPath ->
            if (shell.exists(availPath, timeoutMs = PER_NODE_TIMEOUT_MS)) {
                val rawAvail = shell.read(availPath, timeoutMs = PER_NODE_TIMEOUT_MS)?.trim()
                if (!rawAvail.isNullOrBlank()) {
                    val tokens = rawAvail.split(Regex("\\s+")).filter { it.isNotBlank() }
                    if (tokens.isNotEmpty()) {
                        synchronized(discoveredAvailableOptions) {
                            discoveredAvailableOptions[node.id] = tokens
                        }
                    }
                }
            }
        }

        // For IO_SCHEDULER, parse available schedulers from currentVal if availablePath is null
        if (node.id == TuneId.IO_SCHEDULER && node.availablePath == null) {
            val tokens = currentVal.replace("[", "").replace("]", "").split(Regex("\\s+")).filter { it.isNotBlank() }
            if (tokens.isNotEmpty()) {
                synchronized(discoveredAvailableOptions) {
                    discoveredAvailableOptions[node.id] = tokens
                }
            }
        }

        // Write-verification of current value to ensure write permission actually succeeds
        val valueToWrite = if (node.id == TuneId.IO_SCHEDULER) {
            Regex("""\[(.*?)\]""").find(currentVal)?.groupValues?.get(1) ?: currentVal.split(Regex("\\s+")).firstOrNull() ?: currentVal
        } else {
            currentVal
        }
        val writeRes = shell.write(
            node.path,
            valueToWrite,
            tier,
            timeoutMs = PER_NODE_TIMEOUT_MS,
            verificationMode = node.verificationMode
        )
        discoveredWritable[node.path] = writeRes.verified
    }

    private fun isVendorCompatible(nodeVendor: TuneVendor, detectedGpu: GpuVendor): Boolean {
        return when (nodeVendor) {
            TuneVendor.GENERIC -> true
            TuneVendor.ADRENO -> detectedGpu == GpuVendor.ADRENO || detectedGpu == GpuVendor.UNKNOWN
            TuneVendor.MALI -> detectedGpu == GpuVendor.MALI || detectedGpu == GpuVendor.UNKNOWN
            TuneVendor.SAMSUNG -> true
        }
    }

    private fun probeCpuGovernor(
        policies: List<com.ivarna.apexcore.tune.cpu.CpuPolicyDescriptor>,
        tier: PrivilegeTier,
        backend: TuneBackendIdentity
    ): TuneCapability {
        val options = CpuPolicyDiscovery.governorIntersection(policies).toList().sorted()
        val writable = policies.filter { it.availableGovernors.isNotEmpty() &&
            shell.write(it.governorPath, it.currentGovernor, tier, PER_NODE_TIMEOUT_MS, VerificationMode.GOVERNOR_TOKEN).verified }
        val available = policies.isNotEmpty() && writable.size == policies.size && options.isNotEmpty()
        return TuneCapability(
            id = TuneId.CPU_GOVERNOR,
            available = available,
            needsRoot = tier == PrivilegeTier.SHIZUKU_SHELL && !available,
            writablePaths = writable.map { it.governorPath },
            subtitle = if (available) "Advertised by every discovered CPU policy" else if (tier == PrivilegeTier.SHIZUKU_SHELL) "Needs Root for this kernel" else "CPU governor capability unavailable",
            availableOptions = options,
            reason = when {
                available -> CapabilityReason.AVAILABLE
                tier == PrivilegeTier.SHIZUKU_SHELL -> CapabilityReason.SHIZUKU_SHELL_LIMITED
                policies.isEmpty() -> CapabilityReason.NODE_NOT_FOUND
                else -> CapabilityReason.WRITE_NOT_EFFECTIVE
            },
            backend = backend
        )
    }

    private fun probeCpuLock(
        policies: List<com.ivarna.apexcore.tune.cpu.CpuPolicyDescriptor>,
        tier: PrivilegeTier,
        backend: TuneBackendIdentity
    ): TuneCapability {
        val writable = policies.filter { policy ->
            val max = shell.write(policy.maxPath, policy.currentMaxKhz.toString(), tier, PER_NODE_TIMEOUT_MS, VerificationMode.EXACT_INT)
            val min = shell.write(policy.minPath, policy.currentMinKhz.toString(), tier, PER_NODE_TIMEOUT_MS, VerificationMode.EXACT_INT)
            max.verified && min.verified && policy.targetMaxKhz > 0
        }
        val available = policies.isNotEmpty() && writable.size == policies.size
        return TuneCapability(
            id = TuneId.CPU_LOCK_MAX,
            available = available,
            needsRoot = tier == PrivilegeTier.SHIZUKU_SHELL && !available,
            writablePaths = writable.flatMap { listOf(it.minPath, it.maxPath) },
            subtitle = if (available) "All discovered CPU policies expose verified min/max bounds" else if (tier == PrivilegeTier.SHIZUKU_SHELL) "Needs Root for this kernel" else "CPU max lock unavailable",
            reason = if (available) CapabilityReason.AVAILABLE else if (tier == PrivilegeTier.SHIZUKU_SHELL) CapabilityReason.SHIZUKU_SHELL_LIMITED else CapabilityReason.WRITE_NOT_EFFECTIVE,
            backend = backend
        )
    }

    private fun probeGpuGovernor(
        gpu: com.ivarna.apexcore.tune.gpu.GpuDevfreqDescriptor?,
        tier: PrivilegeTier,
        backend: TuneBackendIdentity
    ): TuneCapability {
        val path = gpu?.governorPath
        val verified = gpu != null && path != null && gpu.currentGovernor != null &&
            shell.write(path, gpu.currentGovernor, tier, PER_NODE_TIMEOUT_MS, VerificationMode.GOVERNOR_TOKEN).verified
        return TuneCapability(
            id = TuneId.GPU_GOVERNOR,
            available = verified && gpu!!.availableGovernors.isNotEmpty(),
            needsRoot = tier == PrivilegeTier.SHIZUKU_SHELL && !verified,
            writablePaths = path?.let { listOf(it) }.orEmpty(),
            subtitle = if (verified) "Advertised by the discovered GPU driver" else if (tier == PrivilegeTier.SHIZUKU_SHELL) "Needs Root for this kernel" else "GPU governor unavailable",
            availableOptions = gpu?.availableGovernors?.toList()?.sorted().orEmpty(),
            reason = if (verified) CapabilityReason.AVAILABLE else if (tier == PrivilegeTier.SHIZUKU_SHELL) CapabilityReason.SHIZUKU_SHELL_LIMITED else CapabilityReason.NODE_NOT_FOUND,
            backend = backend
        )
    }

    private fun probeGpuLock(
        gpu: com.ivarna.apexcore.tune.gpu.GpuDevfreqDescriptor?,
        tier: PrivilegeTier,
        backend: TuneBackendIdentity
    ): TuneCapability {
        val verified = gpu != null && gpu.hasReliableMax &&
            shell.write(gpu.maxPath, gpu.currentMax.toString(), tier, PER_NODE_TIMEOUT_MS, VerificationMode.EXACT_INT).verified &&
            shell.write(gpu.minPath, gpu.currentMin.toString(), tier, PER_NODE_TIMEOUT_MS, VerificationMode.EXACT_INT).verified
        return TuneCapability(
            id = TuneId.GPU_LOCK_MAX,
            available = verified,
            needsRoot = tier == PrivilegeTier.SHIZUKU_SHELL && !verified,
            writablePaths = if (gpu != null) listOf(gpu.minPath, gpu.maxPath) else emptyList(),
            subtitle = if (verified) "Verified GPU min/max pair and real maximum OPP" else if (tier == PrivilegeTier.SHIZUKU_SHELL) "Needs Root for this kernel" else "GPU max lock requires a real min/max pair",
            reason = if (verified) CapabilityReason.AVAILABLE else if (tier == PrivilegeTier.SHIZUKU_SHELL) CapabilityReason.SHIZUKU_SHELL_LIMITED else CapabilityReason.OPTION_NOT_SUPPORTED,
            backend = backend
        )
    }

    private fun buildStandardCapabilities(): Map<TuneId, TuneCapability> {
        return TuneSpecs.all.associate { spec ->
            val id = spec.id
            when (id) {
                TuneId.FOCUS_DND -> {
                    val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                    val hasDndAccess = nm?.isNotificationPolicyAccessGranted == true
                    id to TuneCapability(
                        id = id,
                        available = hasDndAccess,
                        needsRoot = false,
                        writablePaths = emptyList(),
                        subtitle = if (hasDndAccess) "Silence notifications during game" else "Needs Do Not Disturb access",
                        reason = if (hasDndAccess) CapabilityReason.AVAILABLE else CapabilityReason.OPTION_NOT_SUPPORTED,
                        backend = TuneBackendIdentity.STANDARD
                    )
                }
                else -> {
                    id to TuneCapability(
                        id = id,
                        available = false,
                        needsRoot = false,
                        writablePaths = emptyList(),
                        subtitle = "Needs Shizuku or Root",
                        reason = CapabilityReason.NEEDS_ROOT,
                        backend = TuneBackendIdentity.STANDARD
                    )
                }
            }
        }
    }

    companion object {
        private const val TAG = "ApexCore.TuneProbe"
        private const val WALL_BUDGET_MS = 3500L
        private const val PER_NODE_TIMEOUT_MS = 120L
        // T12 spec: "Total probes <= 16" (phase 1 per-TuneId candidates + phase 2 fill).
        private const val MAX_TOTAL_PROBES = 16
        private const val CACHE_TTL_MS = 60_000L
    }
}
