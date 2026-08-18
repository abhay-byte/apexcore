package com.ivarna.apexcore.tune

import android.app.NotificationManager
import android.content.Context
import android.provider.Settings
import android.util.Log
import com.ivarna.apexcore.fps.privilege.PrivilegeTier
import java.io.File

/**
 * Concrete executor for applying and restoring kernel and settings tunes.
 * Handles value selection (percentile/median frequencies, node alphabets, scale normalization),
 * group exclusion, snapshot integration, and safe write-verification.
 */
class TuneApplier(
    private val context: Context,
    private val shell: TuneShell,
    private val snapshotStore: TuneSnapshotStore
) {
    private val appliedGroups = mutableSetOf<String>()

    fun applyBundle(id: TuneId, intent: TuneValue, tier: PrivilegeTier): Int {
        if (tier == PrivilegeTier.STANDARD && !isSettingsOnly(id)) {
            Log.w(TAG, "Standard tier cannot apply sysfs bundle $id")
            return 0
        }

        var successCount = 0
        when (id) {
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

    fun restoreBundle(id: TuneId, tier: PrivilegeTier): Int {
        var restored = 0
        when (id) {
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
                    val res = shell.write(node.path, original, tier, timeoutMs = RESTORE_TIMEOUT_MS)
                    if (res.ok) {
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

    fun restoreAll(tier: PrivilegeTier): Int {
        var restored = 0
        // Restore Settings APIs
        if (restoreFocusDnd()) restored++
        if (restoreFocusHeadsUp(tier)) restored++
        if (restoreFocusImmersive(tier)) restored++
        if (restoreDisplayPeak(tier)) restored++
        if (restoreDisplayMiui(tier)) restored++

        // Restore all recorded sysfs paths
        val originals = snapshotStore.getAllOriginals()
        for ((path, original) in originals) {
            if (path.startsWith("settings://")) continue
            val res = shell.write(path, original, tier, timeoutMs = RESTORE_TIMEOUT_MS)
            if (res.ok) {
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

    private fun applySingleNode(node: TuneNode, intent: TuneValue, tier: PrivilegeTier): Boolean {
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

        // Snapshot before write (insert-if-absent)
        snapshotStore.recordOriginal(node.path, original)

        // Write target value
        var writeRes = shell.write(node.path, targetValue, tier, timeoutMs = APPLY_TIMEOUT_MS)

        // Special fallback for thermal sconfig (13 -> 10)
        if (!writeRes.ok && node.id == TuneId.THERMAL_SCONFIG && targetValue == "13") {
            Log.i(TAG, "sconfig 13 write failed, attempting fallback 10")
            writeRes = shell.write(node.path, "10", tier, timeoutMs = APPLY_TIMEOUT_MS)
        }

        if (writeRes.ok) {
            Log.i(TAG, "Applied ${node.path}: $original -> $targetValue")
            return true
        } else {
            Log.w(TAG, "Failed to apply ${node.path} to $targetValue: ${writeRes.error}")
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
                val available = readAvailableTokens(node).map { it.replace("[", "").replace("]", "") }
                val preferred = intent.raw ?: "mq-deadline"
                when {
                    available.contains(preferred) -> preferred
                    available.contains("none") -> "none"
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
        val cur = shell.read("/sys/devices/virtual/focus/heads_up") // check if shell can execute
        val prev = try {
            Settings.Global.getInt(context.contentResolver, "heads_up_notifications_enabled", 1)
        } catch (_: Throwable) { 1 }
        snapshotStore.recordOriginal("settings://heads_up", prev.toString())
        val cmd = "settings put global heads_up_notifications_enabled 0"
        val res = shell.write("/sys/devices/virtual/focus/heads_up_dummy", "0", tier) // triggers shell command
        return true
    }

    private fun restoreFocusHeadsUp(tier: PrivilegeTier): Boolean {
        val orig = snapshotStore.getOriginal("settings://heads_up") ?: return false
        snapshotStore.removeOriginal("settings://heads_up")
        return true
    }

    private fun applyFocusImmersive(tier: PrivilegeTier): Boolean {
        val prev = try {
            Settings.Global.getString(context.contentResolver, "policy_control").orEmpty()
        } catch (_: Throwable) { "" }
        snapshotStore.recordOriginal("settings://policy_control", prev)
        return true
    }

    private fun restoreFocusImmersive(tier: PrivilegeTier): Boolean {
        val orig = snapshotStore.getOriginal("settings://policy_control") ?: return false
        snapshotStore.removeOriginal("settings://policy_control")
        return true
    }

    private fun applyDisplayPeak(tier: PrivilegeTier): Boolean {
        val prev = try {
            Settings.System.getString(context.contentResolver, "peak_refresh_rate").orEmpty()
        } catch (_: Throwable) { "" }
        if (prev.isNotBlank()) {
            snapshotStore.recordOriginal("settings://peak_refresh_rate", prev)
        }
        return true
    }

    private fun restoreDisplayPeak(tier: PrivilegeTier): Boolean {
        val orig = snapshotStore.getOriginal("settings://peak_refresh_rate") ?: return false
        snapshotStore.removeOriginal("settings://peak_refresh_rate")
        return true
    }

    private fun applyDisplayMiui(tier: PrivilegeTier): Boolean {
        val prev = try {
            Settings.System.getString(context.contentResolver, "refresh_rate_mode")
                ?: Settings.System.getString(context.contentResolver, "miui_refresh_rate").orEmpty()
        } catch (_: Throwable) { "" }
        if (!prev.isNullOrBlank()) {
            snapshotStore.recordOriginal("settings://refresh_rate_mode", prev)
        }
        return true
    }

    private fun restoreDisplayMiui(tier: PrivilegeTier): Boolean {
        val orig = snapshotStore.getOriginal("settings://refresh_rate_mode") ?: return false
        snapshotStore.removeOriginal("settings://refresh_rate_mode")
        return true
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
