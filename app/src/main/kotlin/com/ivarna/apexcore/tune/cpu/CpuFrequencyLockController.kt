package com.ivarna.apexcore.tune.cpu

import com.ivarna.apexcore.fps.privilege.PrivilegeTier
import com.ivarna.apexcore.tune.TuneBackendIdentity
import com.ivarna.apexcore.tune.TuneId
import com.ivarna.apexcore.tune.TuneShell
import com.ivarna.apexcore.tune.TuneSnapshotStore
import com.ivarna.apexcore.tune.TuneValue
import com.ivarna.apexcore.tune.VerificationMode
import java.util.UUID

/** Transactional CPUFreq min=max lock for all discovered policies. */
class CpuFrequencyLockController(
    private val shell: TuneShell,
    private val snapshots: TuneSnapshotStore
) {
    fun apply(
        policies: List<CpuPolicyDescriptor>,
        intent: TuneValue,
        tier: PrivilegeTier,
        backend: TuneBackendIdentity
    ): Int {
        if (policies.isEmpty()) return 0
        val transactionId = "${TuneId.CPU_LOCK_MAX.name}-${UUID.randomUUID()}"
        val changed = mutableListOf<CpuPolicyDescriptor>()
        for (policy in policies) {
            val target = policy.targetMaxKhz
            if (target <= 0L) return rollback(changed, tier, transactionId)
            snapshots.recordOriginal(policy.minPath, policy.currentMinKhz.toString(), TuneId.CPU_LOCK_MAX, transactionId, backend)
            snapshots.recordOriginal(policy.maxPath, policy.currentMaxKhz.toString(), TuneId.CPU_LOCK_MAX, transactionId, backend)

            // Max first, then min, so the new min never exceeds the new bound.
            val maxResult = shell.write(policy.maxPath, target.toString(), tier, verificationMode = VerificationMode.EXACT_INT)
            if (!maxResult.verified) return rollback(changed + policy, tier, transactionId)
            snapshots.recordVerified(policy.maxPath, target.toString(), VerificationMode.EXACT_INT)

            val minResult = shell.write(policy.minPath, target.toString(), tier, verificationMode = VerificationMode.EXACT_INT)
            if (!minResult.verified) return rollback(changed + policy, tier, transactionId)
            snapshots.recordVerified(policy.minPath, target.toString(), VerificationMode.EXACT_INT)
            changed += policy
        }
        return policies.size * 2
    }

    fun restore(policies: List<CpuPolicyDescriptor>, tier: PrivilegeTier): Int {
        var count = 0
        for (policy in policies) {
            val originalMin = snapshots.getOriginal(policy.minPath)
            val originalMax = snapshots.getOriginal(policy.maxPath)
            if (originalMin == null && originalMax == null) continue
            val restoreMin = originalMin != null && snapshots.releaseOwner(policy.minPath, TuneId.CPU_LOCK_MAX)
            val restoreMax = originalMax != null && snapshots.releaseOwner(policy.maxPath, TuneId.CPU_LOCK_MAX)
            var ok = true
            // Min first, then max, to avoid transient original-min > original-max.
            if (restoreMin && originalMin != null) {
                val result = shell.write(policy.minPath, originalMin, tier, verificationMode = VerificationMode.EXACT_INT)
                if (result.verified) count++ else ok = false
            }
            if (restoreMax && originalMax != null) {
                val result = shell.write(policy.maxPath, originalMax, tier, verificationMode = VerificationMode.EXACT_INT)
                if (result.verified) count++ else ok = false
            }
            if (ok) {
                if (restoreMin) snapshots.removeOriginal(policy.minPath)
                if (restoreMax) snapshots.removeOriginal(policy.maxPath)
            }
        }
        return count
    }

    private fun rollback(
        changed: List<CpuPolicyDescriptor>,
        tier: PrivilegeTier,
        transactionId: String
    ): Int {
        for (policy in changed.asReversed()) {
            // A path may also be owned by another active tune. Release this
            // transaction first and only restore when no owner remains.
            val restoreMin = snapshots.releaseOwner(policy.minPath, TuneId.CPU_LOCK_MAX)
            val restoreMax = snapshots.releaseOwner(policy.maxPath, TuneId.CPU_LOCK_MAX)
            if (restoreMin) {
                snapshots.getOriginal(policy.minPath)?.let {
                    if (shell.write(policy.minPath, it, tier, verificationMode = VerificationMode.EXACT_INT).verified) {
                        snapshots.removeOriginal(policy.minPath)
                    }
                }
            }
            if (restoreMax) {
                snapshots.getOriginal(policy.maxPath)?.let {
                    if (shell.write(policy.maxPath, it, tier, verificationMode = VerificationMode.EXACT_INT).verified) {
                        snapshots.removeOriginal(policy.maxPath)
                    }
                }
            }
        }
        snapshots.releaseOwner(TuneId.CPU_LOCK_MAX)
        return 0
    }
}
