package com.ivarna.apexcore.tune.gpu

import com.ivarna.apexcore.fps.privilege.PrivilegeTier
import com.ivarna.apexcore.tune.TuneBackendIdentity
import com.ivarna.apexcore.tune.TuneId
import com.ivarna.apexcore.tune.TuneShell
import com.ivarna.apexcore.tune.TuneSnapshotStore
import com.ivarna.apexcore.tune.TuneValue
import com.ivarna.apexcore.tune.VerificationMode
import java.util.UUID

class GpuGovernorController(
    private val shell: TuneShell,
    private val snapshots: TuneSnapshotStore
) {
    fun apply(
        gpu: GpuDevfreqDescriptor,
        intent: TuneValue,
        tier: PrivilegeTier,
        backend: TuneBackendIdentity
    ): Int {
        val path = gpu.governorPath ?: return 0
        val requested = intent.raw?.trim().takeIf { !it.isNullOrEmpty() }
            ?: if (gpu.availableGovernors.contains("performance")) "performance" else return 0
        if (requested !in gpu.availableGovernors) return 0
        val transaction = "${TuneId.GPU_GOVERNOR.name}-${UUID.randomUUID()}"
        val original = gpu.currentGovernor ?: return 0
        snapshots.recordOriginal(path, original, TuneId.GPU_GOVERNOR, transaction, backend)
        val result = shell.write(path, requested, tier, verificationMode = VerificationMode.GOVERNOR_TOKEN)
        if (!result.verified) {
            if (snapshots.releaseOwner(path, TuneId.GPU_GOVERNOR) &&
                shell.write(path, original, tier, verificationMode = VerificationMode.GOVERNOR_TOKEN).verified
            ) {
                snapshots.removeOriginal(path)
            }
            return 0
        }
        snapshots.recordVerified(path, requested, VerificationMode.GOVERNOR_TOKEN)
        return 1
    }
}
