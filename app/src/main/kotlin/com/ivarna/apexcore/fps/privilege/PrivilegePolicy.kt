package com.ivarna.apexcore.fps.privilege

/**
 * Resolves the method chain for a metric based on the current [PrivilegeMode].
 *
 * Fail-closed design: when a mode is **forced**, metrics never silently demote to a lower tier.
 * They may still fall back to the next method **within** the same tier.
 */
class PrivilegePolicy(private val mode: PrivilegeMode) {

    /**
     * Given a metric's default tier chain, return the chain that should actually be tried.
     *
     * | Mode | Behaviour |
     * |------|-----------|
     * | AUTO | returns [default] unchanged (full cross-tier fallback allowed) |
     * | ROOT | returns [ROOT] only (root methods only; fail-closed) |
     * | SHIZUKU | returns [SHIZUKU] only (shizuku methods only; fail-closed) |
     * | STANDARD | returns [STANDARD] only (standard methods only; fail-closed) |
     */
    fun chain(default: List<PrivilegeTier>): List<PrivilegeTier> = when (mode) {
        PrivilegeMode.AUTO -> default
        PrivilegeMode.ROOT -> listOf(PrivilegeTier.SU_ROOT)
        PrivilegeMode.SHIZUKU -> listOf(PrivilegeTier.SHIZUKU_SHELL)
        PrivilegeMode.STANDARD -> listOf(PrivilegeTier.STANDARD)
    }

    /** Whether the policy allows cross-tier fallback (only AUTO does). */
    fun allowsCrossTierFallback(): Boolean = mode == PrivilegeMode.AUTO

    /** Human-readable label for UI. */
    fun modeLabel(): String = mode.name

    companion object {
        /** Default chain used by most metric families (root first, then shizuku, then standard). */
        val DEFAULT_CHAIN = listOf(PrivilegeTier.SU_ROOT, PrivilegeTier.SHIZUKU_ROOT, PrivilegeTier.SHIZUKU_SHELL, PrivilegeTier.STANDARD)
    }
}
