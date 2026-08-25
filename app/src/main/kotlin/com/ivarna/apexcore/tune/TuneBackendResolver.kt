package com.ivarna.apexcore.tune

import com.ivarna.apexcore.fps.privilege.PrivilegeMode
import com.ivarna.apexcore.fps.privilege.PrivilegeModeStore
import com.ivarna.apexcore.fps.privilege.PrivilegeTier
import com.ivarna.apexcore.fps.privilege.ShellGateway
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Resolves tune identity independently from the app-freezing backend. */
class TuneBackendResolver(
    private val shellGateway: ShellGateway,
    private val modeStore: PrivilegeModeStore
) {
    private val _identity = MutableStateFlow(TuneBackendIdentity.STANDARD)
    val identity: StateFlow<TuneBackendIdentity> = _identity.asStateFlow()

    fun refresh(): TuneBackendIdentity {
        val next = when (modeStore.mode.value) {
            PrivilegeMode.STANDARD -> TuneBackendIdentity.STANDARD
            PrivilegeMode.ROOT -> if (shellGateway.canRoot()) TuneBackendIdentity.SU_ROOT else TuneBackendIdentity.STANDARD
            PrivilegeMode.SHIZUKU -> shellGateway.shizukuTier()?.toIdentity() ?: TuneBackendIdentity.STANDARD
            PrivilegeMode.AUTO -> when {
                shellGateway.canRoot() -> TuneBackendIdentity.SU_ROOT
                else -> shellGateway.shizukuTier()?.toIdentity() ?: TuneBackendIdentity.STANDARD
            }
        }
        _identity.value = next
        return next
    }

    fun current(): TuneBackendIdentity = _identity.value

    fun currentTier(): PrivilegeTier = _identity.value.asPrivilegeTier()

    fun fingerprint(): String {
        val identity = refresh()
        val uid = when (identity) {
            TuneBackendIdentity.SU_ROOT -> shellGateway.rootEffectiveUid()?.toString().orEmpty()
            TuneBackendIdentity.SHIZUKU_ROOT, TuneBackendIdentity.SHIZUKU_SHELL -> shellGateway.shizukuUid()?.toString().orEmpty()
            TuneBackendIdentity.STANDARD -> "standard"
        }
        val kernel = shellGateway.execute("uname -r 2>/dev/null", PrivilegeTier.STANDARD, 300L).output.trim()
        val boot = shellGateway.execute("cat /proc/sys/kernel/random/boot_id 2>/dev/null", PrivilegeTier.STANDARD, 300L).output.trim()
        return listOf(identity.name, uid, kernel, boot).joinToString("|")
    }

    private fun PrivilegeTier.toIdentity(): TuneBackendIdentity = when (this) {
        PrivilegeTier.SU_ROOT -> TuneBackendIdentity.SU_ROOT
        PrivilegeTier.SHIZUKU_ROOT -> TuneBackendIdentity.SHIZUKU_ROOT
        PrivilegeTier.SHIZUKU_SHELL -> TuneBackendIdentity.SHIZUKU_SHELL
        PrivilegeTier.STANDARD -> TuneBackendIdentity.STANDARD
    }
}
