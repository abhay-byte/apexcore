package com.ivarna.apexcore.tune

import com.ivarna.apexcore.fps.privilege.PrivilegeTier

data class GameModeCapability(
    val packageName: String,
    val availableModes: Set<String>,
    val currentMode: String?,
    val supportsPerformance: Boolean,
    val reason: CapabilityReason
)

/** Android/OEM game-mode action for shell-backed Shizuku users. */
class GameModeController(
    private val shell: TuneShell,
    private val snapshots: TuneSnapshotStore
) {
    fun query(packageName: String, tier: PrivilegeTier): GameModeCapability {
        if (!PACKAGE_REGEX.matches(packageName)) {
            return GameModeCapability(packageName, emptySet(), null, false, CapabilityReason.UNKNOWN)
        }
        val list = shell.execute("cmd game list-modes $packageName 2>/dev/null", tier, 800L)
        val get = shell.execute("cmd game get-mode $packageName 2>/dev/null", tier, 800L)
        val modes = MODE_REGEX.findAll(list.output.lowercase()).map { it.value }.toSet()
        val current = parseCurrent(get.output, list.output)
        return GameModeCapability(
            packageName = packageName,
            availableModes = modes,
            currentMode = current,
            supportsPerformance = list.isSuccess && "performance" in modes,
            reason = when {
                !list.isSuccess -> CapabilityReason.WRITE_DENIED
                "performance" !in modes -> CapabilityReason.OPTION_NOT_SUPPORTED
                current == null -> CapabilityReason.READ_DENIED
                else -> CapabilityReason.AVAILABLE
            }
        )
    }

    fun applyPerformance(
        packageName: String,
        tier: PrivilegeTier,
        backend: TuneBackendIdentity
    ): MutationResult {
        val capability = query(packageName, tier)
        if (!capability.supportsPerformance || capability.currentMode == null) {
            return MutationResult(false, false, "performance", capability.currentMode, backend, MutationFailure.UNSUPPORTED)
        }
        val path = snapshotPath(packageName)
        val transaction = "${TuneId.GAME_MODE_PERFORMANCE.name}-$packageName"
        snapshots.recordOriginal(path, capability.currentMode, TuneId.GAME_MODE_PERFORMANCE, transaction, backend)
        val command = shell.execute("cmd game mode performance $packageName 2>/dev/null", tier, 1_000L)
        val after = query(packageName, tier).currentMode
        val verified = command.isSuccess && after == "performance"
        if (!verified) {
            restoreMode(packageName, capability.currentMode, tier)
            snapshots.releaseOwner(TuneId.GAME_MODE_PERFORMANCE)
        } else {
            snapshots.recordVerified(path, "performance", VerificationMode.EXACT_STRING)
        }
        return MutationResult(
            commandOk = command.isSuccess,
            verified = verified,
            requested = "performance",
            readback = after,
            effectiveBackend = backend,
            failure = if (verified) null else MutationFailure.WRITE_NOT_EFFECTIVE
        )
    }

    fun restore(packageName: String, tier: PrivilegeTier, backend: TuneBackendIdentity): MutationResult {
        val path = snapshotPath(packageName)
        val previous = snapshots.getOriginal(path)
            ?: return MutationResult(true, true, "", null, backend)
        if (!MODE_REGEX.matches(previous)) {
            return MutationResult(false, false, previous, null, backend, MutationFailure.UNSUPPORTED)
        }
        val command = restoreMode(packageName, previous, tier)
        val after = query(packageName, tier).currentMode
        val verified = command.isSuccess && after == previous
        if (verified) snapshots.removeOriginal(path)
        return MutationResult(command.isSuccess, verified, previous, after, backend, if (verified) null else MutationFailure.WRITE_NOT_EFFECTIVE)
    }

    private fun restoreMode(packageName: String, mode: String, tier: PrivilegeTier) =
        shell.execute("cmd game mode $mode $packageName 2>/dev/null", tier, 1_000L)

    private fun parseCurrent(getOutput: String, listOutput: String): String? {
        val fromGet = MODE_REGEX.find(getOutput.lowercase())?.value
        if (fromGet != null) return fromGet
        val marked = Regex("""(?:current|active|mode)\\s*[:=]\\s*(standard|performance|battery|custom)""")
            .find(listOutput.lowercase())?.groupValues?.get(1)
        return marked ?: Regex("""\\[(standard|performance|battery|custom)]""")
            .find(listOutput.lowercase())?.groupValues?.get(1)
    }

    private fun snapshotPath(packageName: String) = "game-mode://$packageName"

    companion object {
        private val PACKAGE_REGEX = Regex("^[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+$")
        private val MODE_REGEX = Regex("\\b(standard|performance|battery|custom)\\b")
    }
}
