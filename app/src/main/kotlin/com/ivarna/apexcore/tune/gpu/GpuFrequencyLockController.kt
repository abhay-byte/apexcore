package com.ivarna.apexcore.tune.gpu

import com.ivarna.apexcore.fps.privilege.PrivilegeTier
import com.ivarna.apexcore.tune.TuneBackendIdentity
import com.ivarna.apexcore.tune.TuneId
import com.ivarna.apexcore.tune.TuneShell
import com.ivarna.apexcore.tune.TuneSnapshotStore
import com.ivarna.apexcore.tune.TuneValue
import com.ivarna.apexcore.tune.VerificationMode
import java.util.UUID

/** Transactional GPU min=max lock. Min-only or pwrlevel-only nodes never qualify. */
class GpuFrequencyLockController(
    private val shell: TuneShell,
    private val snapshots: TuneSnapshotStore
) {
    fun apply(
        gpu: GpuDevfreqDescriptor?,
        intent: TuneValue,
        tier: PrivilegeTier,
        backend: TuneBackendIdentity
    ): Int {
        if (gpu == null || !gpu.hasReliableMax || gpu.targetMax <= 0L) return 0
        val transaction = "${TuneId.GPU_LOCK_MAX.name}-${UUID.randomUUID()}"
        snapshots.recordOriginal(gpu.minPath, gpu.currentMin.toString(), TuneId.GPU_LOCK_MAX, transaction, backend)
        snapshots.recordOriginal(gpu.maxPath, gpu.currentMax.toString(), TuneId.GPU_LOCK_MAX, transaction, backend)
        val target = gpu.targetMax.toString()
        val maxResult = shell.write(gpu.maxPath, target, tier, verificationMode = VerificationMode.EXACT_INT)
        if (!maxResult.verified) return rollback(gpu, tier)
        snapshots.recordVerified(gpu.maxPath, target, VerificationMode.EXACT_INT)
        val minResult = shell.write(gpu.minPath, target, tier, verificationMode = VerificationMode.EXACT_INT)
        if (!minResult.verified) return rollback(gpu, tier)
        snapshots.recordVerified(gpu.minPath, target, VerificationMode.EXACT_INT)
        return 2
    }

    fun restore(gpu: GpuDevfreqDescriptor?, tier: PrivilegeTier): Int {
        if (gpu == null) return 0
        val originalMin = snapshots.getOriginal(gpu.minPath) ?: return 0
        val originalMax = snapshots.getOriginal(gpu.maxPath)
        val restoreMin = snapshots.releaseOwner(gpu.minPath, TuneId.GPU_LOCK_MAX)
        val restoreMax = originalMax != null && snapshots.releaseOwner(gpu.maxPath, TuneId.GPU_LOCK_MAX)
        var count = 0
        var ok = true
        if (restoreMin && shell.write(gpu.minPath, originalMin, tier, verificationMode = VerificationMode.EXACT_INT).verified) count++ else if (restoreMin) ok = false
        if (restoreMax && originalMax != null) {
            if (shell.write(gpu.maxPath, originalMax, tier, verificationMode = VerificationMode.EXACT_INT).verified) count++ else ok = false
        }
        if (ok) {
            if (restoreMin) snapshots.removeOriginal(gpu.minPath)
            if (restoreMax) snapshots.removeOriginal(gpu.maxPath)
        }
        return count
    }

    private fun rollback(gpu: GpuDevfreqDescriptor, tier: PrivilegeTier): Int {
        val restoreMin = snapshots.releaseOwner(gpu.minPath, TuneId.GPU_LOCK_MAX)
        val restoreMax = snapshots.releaseOwner(gpu.maxPath, TuneId.GPU_LOCK_MAX)
        if (restoreMin) {
            snapshots.getOriginal(gpu.minPath)?.let {
                if (shell.write(gpu.minPath, it, tier, verificationMode = VerificationMode.EXACT_INT).verified) {
                    snapshots.removeOriginal(gpu.minPath)
                }
            }
        }
        if (restoreMax) {
            snapshots.getOriginal(gpu.maxPath)?.let {
                if (shell.write(gpu.maxPath, it, tier, verificationMode = VerificationMode.EXACT_INT).verified) {
                    snapshots.removeOriginal(gpu.maxPath)
                }
            }
        }
        snapshots.releaseOwner(TuneId.GPU_LOCK_MAX)
        return 0
    }
}
