package com.ivarna.apexcore.tune

import android.app.NotificationManager
import android.content.Context
import android.provider.Settings
import android.util.Log
import com.ivarna.apexcore.fps.privilege.PrivilegeTier
import com.ivarna.apexcore.tune.cpu.CpuFrequencyLockController
import com.ivarna.apexcore.tune.cpu.CpuGovernorController
import com.ivarna.apexcore.tune.cpu.CpuPolicyDiscovery
import com.ivarna.apexcore.tune.gpu.GpuDevfreqDiscovery
import com.ivarna.apexcore.tune.gpu.GpuFrequencyLockController
import com.ivarna.apexcore.tune.gpu.GpuGovernorController

/**
 * Concrete executor for applying and restoring kernel and settings tunes.
 * Handles value selection (percentile/median frequencies, node alphabets, scale normalization),
 * group exclusion, snapshot integration, and safe write-verification.
 */
open class TuneApplier(
    private val context: Context,
    private val shell: TuneShell,
    private val snapshotStore: TuneSnapshotStore
) {
    private val appliedGroups = mutableSetOf<String>()
    private val cpuGovernorController = CpuGovernorController(shell, snapshotStore)
    private val cpuFrequencyLockController = CpuFrequencyLockController(shell, snapshotStore)
    private val gpuGovernorController = GpuGovernorController(shell, snapshotStore)
    private val gpuFrequencyLockController = GpuFrequencyLockController(shell, snapshotStore)
    private val gameModeController = GameModeController(shell, snapshotStore)
    @Volatile private var gameModePackage: String? = null

    open fun applyBundle(id: TuneId, intent: TuneValue, tier: PrivilegeTier): Int {
        if (tier == PrivilegeTier.STANDARD && !isSettingsOnly(id)) {
            Log.w(TAG, "Standard tier cannot apply sysfs bundle $id")
            return 0
        }

        var successCount = 0
        when (id) {
            TuneId.CPU_GOVERNOR -> {
                val policies = CpuPolicyDiscovery.discover(shell, tier)
                successCount += cpuGovernorController.apply(policies, intent, tier, backendFor(tier))
            }
            TuneId.CPU_LOCK_MAX -> {
                val policies = CpuPolicyDiscovery.discover(shell, tier)
                successCount += cpuFrequencyLockController.apply(policies, intent, tier, backendFor(tier))
            }
            TuneId.GPU_GOVERNOR -> {
                val gpu = GpuDevfreqDiscovery.discover(shell, tier).firstOrNull()
                if (gpu != null) successCount += gpuGovernorController.apply(gpu, intent, tier, backendFor(tier))
            }
            TuneId.GPU_LOCK_MAX -> {
                val gpu = GpuDevfreqDiscovery.discover(shell, tier).firstOrNull()
                successCount += gpuFrequencyLockController.apply(gpu, intent, tier, backendFor(tier))
            }
            TuneId.GAME_MODE_PERFORMANCE -> {
                // The target package is session state, so callers use
                // applyGameModeForSession rather than a package-less bundle.
            }
            TuneId.FOCUS_DND -> {
                if (applyFocusDnd()) successCount++
            }
            TuneId.FOCUS_HEADSUP -> {
                if (applyFocusHeadsUp(tier)) successCount++
            }
            TuneId.FOCUS_IMMERSIVE -> {
                if (applyFocusImmersive(tier)) successCount++
            }
            TuneId.DISPLAY_PEAK -> {
                if (applyDisplayPeak(tier)) successCount++
            }
            TuneId.DISPLAY_MIUI -> {
                if (applyDisplayMiui(tier)) successCount++
            }
            else -> {
                val nodes = TuneCatalog.nodesByTuneId[id].orEmpty()
                for (node in nodes) {
                    if (appliedGroups.contains(node.groupId)) {
                        // Only one write per group
                        continue
                    }
                    if (applySingleNode(node, intent, tier)) {
                        appliedGroups.add(node.groupId)
                        successCount++
                    }
                }
            }
        }
        return successCount
    }

    open fun restoreBundle(id: TuneId, tier: PrivilegeTier): Int {
        var restored = 0
        when (id) {
            TuneId.CPU_GOVERNOR -> {
                restored += restoreCpuGovernor(tier)
            }
            TuneId.CPU_LOCK_MAX -> {
                restored += cpuFrequencyLockController.restore(CpuPolicyDiscovery.discover(shell, tier), tier)
            }
            TuneId.GPU_GOVERNOR -> {
                restored += restoreGpuGovernor(tier)
            }
            TuneId.GPU_LOCK_MAX -> {
                restored += gpuFrequencyLockController.restore(GpuDevfreqDiscovery.discover(shell, tier).firstOrNull(), tier)
            }
            TuneId.GAME_MODE_PERFORMANCE -> {
                val pkg = gameModePackage
                if (pkg != null && gameModeController.restore(pkg, tier, backendFor(tier)).verified) restored++
            }
            TuneId.FOCUS_DND -> {
                if (restoreFocusDnd()) restored++
            }
            TuneId.FOCUS_HEADSUP -> {
                if (restoreFocusHeadsUp(tier)) restored++
            }
            TuneId.FOCUS_IMMERSIVE -> {
                if (restoreFocusImmersive(tier)) restored++
            }
            TuneId.DISPLAY_PEAK -> {
                if (restoreDisplayPeak(tier)) restored++
            }
            TuneId.DISPLAY_MIUI -> {
                if (restoreDisplayMiui(tier)) restored++
            }
            else -> {
                val nodes = TuneCatalog.nodesByTuneId[id].orEmpty()
                for (node in nodes) {
                    appliedGroups.remove(node.groupId)
                    val original = snapshotStore.getOriginal(node.path) ?: continue
                    val mode = snapshotStore.getEntry(node.path)?.verificationMode ?: verificationModeFor(original)
                    val res = shell.write(node.path, original, tier, timeoutMs = RESTORE_TIMEOUT_MS, verificationMode = mode)
                    if (res.verified) {
                        snapshotStore.removeOriginal(node.path)
                        restored++
                        Log.i(TAG, "Restored ${node.path} to original: $original")
                    } else {
                        Log.w(TAG, "Failed to restore ${node.path}: ${res.error}")
                    }
                }
            }
        }
        return restored
    }

    open fun restoreAll(tier: PrivilegeTier): Int {
        var restored = 0
        // Restore Settings APIs
        if (restoreFocusDnd()) restored++
        if (restoreFocusHeadsUp(tier)) restored++
        if (restoreFocusImmersive(tier)) restored++
        if (restoreDisplayPeak(tier)) restored++
        if (restoreDisplayMiui(tier)) restored++
        val modePackages = buildSet {
            gameModePackage?.let(::add)
            snapshotStore.getAllEntries().keys
                .filter { it.startsWith("game-mode://") }
                .mapTo(this) { it.removePrefix("game-mode://") }
        }
        for (modePackage in modePackages) {
            if (gameModeController.restore(modePackage, tier, backendFor(tier)).verified) restored++
        }
        gameModePackage = null

        // Restore bound pairs in their safe order before the generic recovery
        // loop. Any entry that remains is still retried below.
        restored += restoreCpuGovernor(tier)
        restored += restoreGpuGovernor(tier)
        restored += cpuFrequencyLockController.restore(CpuPolicyDiscovery.discover(shell, tier), tier)
        restored += gpuFrequencyLockController.restore(GpuDevfreqDiscovery.discover(shell, tier).firstOrNull(), tier)

        // Restore all recorded sysfs paths
        val originals = snapshotStore.getAllOriginals()
        for ((path, original) in originals) {
            if (path.startsWith("settings://")) continue
            if (path.startsWith("game-mode://")) continue
            val mode = snapshotStore.getEntry(path)?.verificationMode ?: verificationModeFor(original)
            val res = shell.write(path, original, tier, timeoutMs = RESTORE_TIMEOUT_MS, verificationMode = mode)
            if (res.verified) {
                snapshotStore.removeOriginal(path)
                restored++
                Log.i(TAG, "Restored $path to original: $original")
            } else {
                Log.w(TAG, "Failed to restore $path: ${res.error}")
            }
        }
        appliedGroups.clear()
        return restored
    }

    fun applyGameModeForSession(
        packageName: String,
        tier: PrivilegeTier,
        backend: TuneBackendIdentity = backendFor(tier)
    ): MutationResult {
        gameModePackage = packageName
        return gameModeController.applyPerformance(packageName, tier, backend)
    }

    fun gameModeCapability(packageName: String, tier: PrivilegeTier): GameModeCapability =
        gameModeController.query(packageName, tier)

    fun releaseMaxLocks(tier: PrivilegeTier): Int {
        var released = cpuFrequencyLockController.restore(CpuPolicyDiscovery.discover(shell, tier), tier)
        released += gpuFrequencyLockController.restore(GpuDevfreqDiscovery.discover(shell, tier).firstOrNull(), tier)
        return released
    }

    private fun restoreCpuGovernor(tier: PrivilegeTier): Int {
        var restored = 0
        for (policy in CpuPolicyDiscovery.discover(shell, tier)) {
            val original = snapshotStore.getOriginal(policy.governorPath) ?: continue
            val shouldRestore = snapshotStore.releaseOwner(policy.governorPath, TuneId.CPU_GOVERNOR)
            if (!shouldRestore) continue
            val result = shell.write(policy.governorPath, original, tier, verificationMode = VerificationMode.GOVERNOR_TOKEN)
            if (result.verified) {
                snapshotStore.removeOriginal(policy.governorPath)
                restored++
            }
        }
        return restored
    }

    private fun restoreGpuGovernor(tier: PrivilegeTier): Int {
        var restored = 0
        val path = GpuDevfreqDiscovery.discover(shell, tier).firstOrNull()?.governorPath ?: return 0
        val original = snapshotStore.getOriginal(path) ?: return 0
        if (!snapshotStore.releaseOwner(path, TuneId.GPU_GOVERNOR)) return 0
        val result = shell.write(path, original, tier, verificationMode = VerificationMode.GOVERNOR_TOKEN)
        if (result.verified) {
            snapshotStore.removeOriginal(path)
            restored++
        }
        return restored
    }

    private fun backendFor(tier: PrivilegeTier): TuneBackendIdentity = when (tier) {
        PrivilegeTier.SU_ROOT -> TuneBackendIdentity.SU_ROOT
        PrivilegeTier.SHIZUKU_ROOT -> TuneBackendIdentity.SHIZUKU_ROOT
        PrivilegeTier.SHIZUKU_SHELL -> TuneBackendIdentity.SHIZUKU_SHELL
        PrivilegeTier.STANDARD -> TuneBackendIdentity.STANDARD
    }

    private fun verificationModeFor(value: String): VerificationMode =
        if (value.trim().toLongOrNull() != null) VerificationMode.EXACT_INT else VerificationMode.EXACT_STRING

    private fun tierIsRootCapable(tier: PrivilegeTier): Boolean =
        tier == PrivilegeTier.SU_ROOT || tier == PrivilegeTier.SHIZUKU_ROOT

    private fun applySingleNode(node: TuneNode, intent: TuneValue, tier: PrivilegeTier): Boolean {
        if (node.requiredIdentity == RequiredIdentity.ROOT && !tierIsRootCapable(tier)) {
            Log.w(TAG, "Security rejection: ${node.path} requires a root-capable backend")
            return false
        }

        // Forbidden path audit
        if (isForbiddenPath(node.path)) {
            Log.e(TAG, "Security rejection: forbidden path ${node.path}")
            return false
        }

        if (!shell.exists(node.path, timeoutMs = APPLY_TIMEOUT_MS)) {
            return false
        }

        val original = shell.read(node.path, timeoutMs = APPLY_TIMEOUT_MS)?.trim()
        if (original.isNullOrBlank()) {
            return false
        }

        val targetValue = computeTargetValue(node, original, intent) ?: return false

        // Validate charset
        if (!PATH_REGEX.matches(node.path) || !VALUE_REGEX.matches(targetValue)) {
            Log.w(TAG, "Rejecting write due to invalid characters: ${node.path} = $targetValue")
            return false
        }

        // Snapshot before write (insert-if-absent). For IO_SCHEDULER, extract bare active token.
        val tokenToSnapshot = if (node.id == TuneId.IO_SCHEDULER) {
            Regex("""\[(.*?)\]""").find(original)?.groupValues?.get(1) ?: original.split(Regex("\\s+")).firstOrNull() ?: original
        } else {
            original
        }
        snapshotStore.recordOriginal(node.path, tokenToSnapshot)

        // Write target value
        val writeRes = shell.write(
            node.path,
            targetValue,
            tier,
            timeoutMs = APPLY_TIMEOUT_MS,
            verificationMode = node.verificationMode
        )

        if (writeRes.verified) {
            Log.i(TAG, "Applied ${node.path}: $original -> $targetValue")
            return true
        } else {
            val rollback = shell.write(
                node.path,
                tokenToSnapshot,
                tier,
                timeoutMs = RESTORE_TIMEOUT_MS,
                verificationMode = node.verificationMode
            )
            if (rollback.verified) snapshotStore.removeOriginal(node.path)
            Log.w(TAG, "Failed to apply ${node.path} to $targetValue: ${writeRes.error}; rollback=${rollback.verified}")
            return false
        }
    }

    private fun computeTargetValue(node: TuneNode, currentVal: String, intent: TuneValue): String? {
        return when (node.id) {
            TuneId.GPU_FLOOR, TuneId.GPU_SAMSUNG_MIN -> {
                val freqs = readAvailableFrequencies(node)
                if (freqs.isNotEmpty()) {
                    // Pick ~60th percentile
                    val idx = (freqs.size * 3 / 5).coerceIn(0, freqs.lastIndex)
                    freqs[idx].toString()
                } else {
                    currentVal
                }
            }
            TuneId.GPU_HOLD -> {
                when (node.groupId) {
                    "kgsl_idle_timer" -> {
                        val curInt = currentVal.toIntOrNull() ?: 0
                        if (curInt < 10000) "10000" else null
                    }
                    else -> "1"
                }
            }
            TuneId.GPU_ADRENO -> {
                intent.raw?.takeIf { it.isNotBlank() } ?: "2"
            }
            TuneId.GPU_GOVERNOR -> {
                if (currentVal.equals("powersave", ignoreCase = true)) {
                    val govs = readAvailableTokens(node)
                    if (govs.contains("msm-adreno-tz")) "msm-adreno-tz"
                    else if (govs.contains("simple_ondemand")) "simple_ondemand"
                    else null
                } else {
                    null
                }
            }
            TuneId.GPU_PWRLEVEL -> {
                val cur = currentVal.toIntOrNull() ?: return null
                val target = (cur - 2).coerceAtLeast(0)
                target.toString()
            }
            TuneId.GPU_GED_GAME -> "1"
            TuneId.GPU_SIMPLE -> "1"

            TuneId.CPU_FLOOR, TuneId.CPU_FLOOR_LITTLE, TuneId.CPU_FLOOR_BIG, TuneId.CPU_FLOOR_PRIME -> {
                val freqs = readAvailableFrequencies(node)
                if (freqs.isNotEmpty()) {
                    // Median available frequency
                    val median = freqs[freqs.size / 2]
                    median.toString()
                } else {
                    null
                }
            }
            TuneId.CPU_GOVERNOR -> {
                if (currentVal in listOf("powersave", "conservative")) {
                    val govs = readAvailableTokens(node)
                    if (govs.contains("schedutil")) "schedutil" else null
                } else {
                    null
                }
            }
            TuneId.CPU_UCLAMP -> {
                // KD-18: Top-app uclamp 10% of scale
                val curInt = currentVal.toIntOrNull() ?: 0
                val scale = if (curInt > 100) 1024 else 100
                val pct = intent.raw?.toIntOrNull() ?: 10
                val target = (scale * pct / 100).coerceIn(0, scale)
                target.toString()
            }
            TuneId.CPU_STUNE -> {
                intent.raw?.takeIf { it.isNotBlank() } ?: "10"
            }
            TuneId.CPU_STUNE_IDLE -> "1"

            TuneId.INPUT_BOOST_EN -> {
                if (currentVal.equals("N", ignoreCase = true) || currentVal.equals("Y", ignoreCase = true)) "Y" else "1"
            }
            TuneId.INPUT_BOOST_MS -> {
                val cur = currentVal.toIntOrNull() ?: 0
                val target = intent.raw?.toIntOrNull() ?: maxOf(cur, 64).coerceAtMost(128)
                target.toString()
            }
            TuneId.TOUCHBOOST -> "1"
            TuneId.CPUFREQ_BOOST -> "1"
            TuneId.DEVFREQ_BOOST -> {
                intent.raw?.takeIf { it.isNotBlank() } ?: "100"
            }
            TuneId.SCHED_BOOST_INPUT -> "1"
            TuneId.SULTAN_INPUT -> "1"

            TuneId.THERMAL_SCONFIG -> {
                intent.raw?.takeIf { it.isNotBlank() } ?: "13"
            }

            TuneId.VM_SWAPPINESS -> {
                val cur = currentVal.toIntOrNull() ?: 60
                val target = intent.raw?.toIntOrNull() ?: 30
                if (cur > target) target.toString() else null
            }
            TuneId.VM_VFS_CACHE -> {
                val cur = currentVal.toIntOrNull() ?: 100
                val target = intent.raw?.toIntOrNull() ?: 50
                if (cur > target) target.toString() else null
            }
            TuneId.VM_DIRTY_RATIO -> {
                val cur = currentVal.toIntOrNull() ?: 30
                val target = intent.raw?.toIntOrNull() ?: 20
                if (cur > target) target.toString() else null
            }

            TuneId.IO_SCHEDULER -> {
                val availList = if (node.availablePath != null) {
                    readAvailableTokens(node)
                } else {
                    currentVal.split(Regex("\\s+"))
                }
                val available = availList.map { it.replace("[", "").replace("]", "") }.filter { it.isNotBlank() }
                val preferred = intent.raw ?: "mq-deadline"
                when {
                    available.contains(preferred) -> preferred
                    available.contains("none") -> "none"
                    available.isNotEmpty() -> available.first()
                    else -> null
                }
            }
            TuneId.IO_READAHEAD -> {
                val cur = currentVal.toIntOrNull() ?: 128
                val target = intent.raw?.toIntOrNull() ?: maxOf(cur, 512).coerceAtMost(2048)
                target.toString()
            }

            TuneId.CHARGE_BYPASS -> "1"

            TuneId.NET_TCP -> {
                val available = readAvailableTokens(node)
                val preferred = intent.raw ?: "bbr"
                when {
                    available.contains(preferred) -> preferred
                    available.contains("westwood") -> "westwood"
                    else -> null
                }
            }

            else -> null
        }
    }

    private fun readAvailableFrequencies(node: TuneNode): List<Long> {
        val availPath = node.availablePath ?: return emptyList()
        if (!shell.exists(availPath, timeoutMs = APPLY_TIMEOUT_MS)) return emptyList()
        val raw = shell.read(availPath, timeoutMs = APPLY_TIMEOUT_MS)?.trim() ?: return emptyList()
        return raw.split(Regex("\\s+"))
            .mapNotNull { it.toLongOrNull() }
            .sorted()
    }

    private fun readAvailableTokens(node: TuneNode): List<String> {
        val availPath = node.availablePath ?: return emptyList()
        if (!shell.exists(availPath, timeoutMs = APPLY_TIMEOUT_MS)) return emptyList()
        val raw = shell.read(availPath, timeoutMs = APPLY_TIMEOUT_MS)?.trim() ?: return emptyList()
        return raw.split(Regex("\\s+")).filter { it.isNotBlank() }
    }

    // --- Settings / Focus APIs ---

    private fun applyFocusDnd(): Boolean {
        return try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return false
            if (nm.isNotificationPolicyAccessGranted) {
                val prevFilter = nm.currentInterruptionFilter
                snapshotStore.recordOriginal("settings://focus_dnd", prevFilter.toString())
                nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
                true
            } else {
                false
            }
        } catch (t: Throwable) {
            Log.w(TAG, "applyFocusDnd error: ${t.message}")
            false
        }
    }

    private fun restoreFocusDnd(): Boolean {
        val orig = snapshotStore.getOriginal("settings://focus_dnd")?.toIntOrNull() ?: return false
        return try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return false
            if (nm.isNotificationPolicyAccessGranted) {
                nm.setInterruptionFilter(orig)
                snapshotStore.removeOriginal("settings://focus_dnd")
                true
            } else {
                false
            }
        } catch (_: Throwable) {
            false
        }
    }

    private fun applyFocusHeadsUp(tier: PrivilegeTier): Boolean {
        if (tier == PrivilegeTier.STANDARD) return false
        val prev = try {
            Settings.Global.getInt(context.contentResolver, "heads_up_notifications_enabled", 1)
        } catch (_: Throwable) { 1 }
        snapshotStore.recordOriginal("settings://heads_up", prev.toString())
        val res = shell.execute("settings put global heads_up_notifications_enabled 0", tier, timeoutMs = APPLY_TIMEOUT_MS)
        return res.isSuccess
    }

    private fun restoreFocusHeadsUp(tier: PrivilegeTier): Boolean {
        val orig = snapshotStore.getOriginal("settings://heads_up") ?: return false
        if (tier == PrivilegeTier.STANDARD) return false
        val res = shell.execute("settings put global heads_up_notifications_enabled $orig", tier, timeoutMs = RESTORE_TIMEOUT_MS)
        if (res.isSuccess) {
            snapshotStore.removeOriginal("settings://heads_up")
            return true
        }
        return false
    }

    private fun applyFocusImmersive(tier: PrivilegeTier): Boolean {
        if (tier == PrivilegeTier.STANDARD) return false
        val prev = try {
            Settings.Global.getString(context.contentResolver, "policy_control").orEmpty()
        } catch (_: Throwable) { "" }
        snapshotStore.recordOriginal("settings://policy_control", prev)
        val res = shell.execute("settings put global policy_control 'immersive.full=*'", tier, timeoutMs = APPLY_TIMEOUT_MS)
        return res.isSuccess
    }

    private fun restoreFocusImmersive(tier: PrivilegeTier): Boolean {
        val orig = snapshotStore.getOriginal("settings://policy_control") ?: return false
        if (tier == PrivilegeTier.STANDARD) return false
        val res = if (orig.isBlank()) {
            shell.execute("settings delete global policy_control", tier, timeoutMs = RESTORE_TIMEOUT_MS)
        } else {
            shell.execute("settings put global policy_control '$orig'", tier, timeoutMs = RESTORE_TIMEOUT_MS)
        }
        if (res.isSuccess) {
            snapshotStore.removeOriginal("settings://policy_control")
            return true
        }
        return false
    }

    private fun applyDisplayPeak(tier: PrivilegeTier): Boolean {
        if (tier == PrivilegeTier.STANDARD) return false
        val curPeak = try {
            Settings.System.getString(context.contentResolver, "peak_refresh_rate")
                ?: Settings.Global.getString(context.contentResolver, "peak_refresh_rate")
                ?: Settings.Secure.getString(context.contentResolver, "peak_refresh_rate")
        } catch (_: Throwable) { null }
        if (curPeak.isNullOrBlank()) return false

        val curMin = try {
            Settings.System.getString(context.contentResolver, "min_refresh_rate")
                ?: Settings.Global.getString(context.contentResolver, "min_refresh_rate")
                ?: Settings.Secure.getString(context.contentResolver, "min_refresh_rate")
        } catch (_: Throwable) { "" }

        snapshotStore.recordOriginal("settings://peak_refresh_rate", curPeak)
        if (!curMin.isNullOrBlank()) {
            snapshotStore.recordOriginal("settings://min_refresh_rate", curMin)
        }

        val res = shell.execute("settings put system peak_refresh_rate $curPeak && settings put system min_refresh_rate $curPeak", tier, timeoutMs = APPLY_TIMEOUT_MS)
        return res.isSuccess
    }

    private fun restoreDisplayPeak(tier: PrivilegeTier): Boolean {
        val origPeak = snapshotStore.getOriginal("settings://peak_refresh_rate") ?: return false
        val origMin = snapshotStore.getOriginal("settings://min_refresh_rate")
        if (tier == PrivilegeTier.STANDARD) return false

        val restoreCmd = if (!origMin.isNullOrBlank()) {
            "settings put system peak_refresh_rate $origPeak && settings put system min_refresh_rate $origMin"
        } else {
            "settings put system peak_refresh_rate $origPeak && settings delete system min_refresh_rate"
        }
        val res = shell.execute(restoreCmd, tier, timeoutMs = RESTORE_TIMEOUT_MS)
        if (res.isSuccess) {
            snapshotStore.removeOriginal("settings://peak_refresh_rate")
            snapshotStore.removeOriginal("settings://min_refresh_rate")
            return true
        }
        return false
    }

    private fun applyDisplayMiui(tier: PrivilegeTier): Boolean {
        if (tier == PrivilegeTier.STANDARD) return false
        val mode = try {
            Settings.System.getString(context.contentResolver, "refresh_rate_mode")
        } catch (_: Throwable) { null }
        val miuiRate = try {
            Settings.System.getString(context.contentResolver, "miui_refresh_rate")
        } catch (_: Throwable) { null }

        if (mode.isNullOrBlank() && miuiRate.isNullOrBlank()) return false

        var success = false
        if (!mode.isNullOrBlank()) {
            snapshotStore.recordOriginal("settings://refresh_rate_mode", mode)
            val res = shell.execute("settings put system refresh_rate_mode 1", tier, timeoutMs = APPLY_TIMEOUT_MS)
            if (res.isSuccess) success = true
        }
        if (!miuiRate.isNullOrBlank()) {
            snapshotStore.recordOriginal("settings://miui_refresh_rate", miuiRate)
            val res = shell.execute("settings put system miui_refresh_rate 120", tier, timeoutMs = APPLY_TIMEOUT_MS)
            if (res.isSuccess) success = true
        }
        return success
    }

    private fun restoreDisplayMiui(tier: PrivilegeTier): Boolean {
        val origMode = snapshotStore.getOriginal("settings://refresh_rate_mode")
        val origRate = snapshotStore.getOriginal("settings://miui_refresh_rate")
        if (origMode == null && origRate == null) return false
        if (tier == PrivilegeTier.STANDARD) return false

        var success = true
        if (origMode != null) {
            val res = shell.execute("settings put system refresh_rate_mode $origMode", tier, timeoutMs = RESTORE_TIMEOUT_MS)
            if (res.isSuccess) {
                snapshotStore.removeOriginal("settings://refresh_rate_mode")
            } else {
                success = false
            }
        }
        if (origRate != null) {
            val res = shell.execute("settings put system miui_refresh_rate $origRate", tier, timeoutMs = RESTORE_TIMEOUT_MS)
            if (res.isSuccess) {
                snapshotStore.removeOriginal("settings://miui_refresh_rate")
            } else {
                success = false
            }
        }
        return success
    }

    private fun isSettingsOnly(id: TuneId): Boolean {
        return id in listOf(
            TuneId.FOCUS_DND,
            TuneId.FOCUS_HEADSUP,
            TuneId.FOCUS_IMMERSIVE,
            TuneId.DISPLAY_PEAK,
            TuneId.DISPLAY_MIUI
        )
    }

    private fun isForbiddenPath(path: String): Boolean {
        val forbiddenSubstrings = listOf(
            "/dev/mali0",
            "/proc/ged",
            "sched_util_clamp_min",
            "sched_util_clamp_max",
            "msm_thermal",
            "throttling",
            "gpu_dvfs_enable",
            "gx_force_cpu_boost",
            "boost_gpu_enable"
        )
        return forbiddenSubstrings.any { path.contains(it) }
    }

    companion object {
        private const val TAG = "ApexCore.TuneApplier"
        private const val APPLY_TIMEOUT_MS = 400L
        private const val RESTORE_TIMEOUT_MS = 400L
        private val PATH_REGEX = Regex("""^/(sys|dev|proc)/[A-Za-z0-9/_.:=-]+$""")
        private val VALUE_REGEX = Regex("""^[A-Za-z0-9_.:-]+$""")
    }
}
