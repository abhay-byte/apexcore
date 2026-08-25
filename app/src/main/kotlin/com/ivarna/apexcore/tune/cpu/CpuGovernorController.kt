package com.ivarna.apexcore.tune.cpu

import com.ivarna.apexcore.fps.privilege.PrivilegeTier
import com.ivarna.apexcore.tune.TuneBackendIdentity
import com.ivarna.apexcore.tune.TuneId
import com.ivarna.apexcore.tune.TuneShell
import com.ivarna.apexcore.tune.TuneSnapshotStore
import com.ivarna.apexcore.tune.TuneValue
import com.ivarna.apexcore.tune.VerificationMode
import java.util.UUID

class CpuGovernorController(
    private val shell: TuneShell,
    private val snapshots: TuneSnapshotStore
) {
    fun apply(
        policies: List<CpuPolicyDescriptor>,
        intent: TuneValue,
        tier: PrivilegeTier,
        backend: TuneBackendIdentity
    ): Int {
        val requested = intent.raw?.trim().takeIf { !it.isNullOrEmpty() }
            ?: if (CpuPolicyDiscovery.governorIntersection(policies).contains("performance")) "performance" else return 0
        if (requested !in CpuPolicyDiscovery.governorIntersection(policies)) return 0
        val transactionId = "${TuneId.CPU_GOVERNOR.name}-${UUID.randomUUID()}"
        val changed = mutableListOf<Pair<CpuPolicyDescriptor, String>>()
        for (policy in policies) {
            snapshots.recordOriginal(policy.governorPath, policy.currentGovernor, TuneId.CPU_GOVERNOR, transactionId, backend)
            val result = shell.write(policy.governorPath, requested, tier, verificationMode = VerificationMode.GOVERNOR_TOKEN)
            if (!result.verified) {
                changed.asReversed().forEach { (changedPolicy, original) ->
                    if (snapshots.releaseOwner(changedPolicy.governorPath, TuneId.CPU_GOVERNOR)) {
                        if (shell.write(changedPolicy.governorPath, original, tier, verificationMode = VerificationMode.GOVERNOR_TOKEN).verified) {
                            snapshots.removeOriginal(changedPolicy.governorPath)
                        }
                    }
                }
                snapshots.releaseOwner(TuneId.CPU_GOVERNOR)
                return 0
            }
            changed += policy to policy.currentGovernor
            snapshots.recordVerified(policy.governorPath, requested, VerificationMode.GOVERNOR_TOKEN)
        }
        return changed.size
    }
}
