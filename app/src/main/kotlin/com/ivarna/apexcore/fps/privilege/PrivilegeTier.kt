package com.ivarna.apexcore.fps.privilege

/**
 * Resolved execution identity for a single operation.
 *
 * The old API exposed only ROOT/SHIZUKU/STANDARD.  Keep those source-compatible
 * aliases in the companion object, but make the actual values distinguish su
 * root from a root-backed or shell-backed Shizuku UserService.
 */
enum class PrivilegeTier {
    /** `su` command with an observed effective uid of 0. */
    SU_ROOT,
    /** Shizuku/Sui UserService with an observed effective uid of 0. */
    SHIZUKU_ROOT,
    /** Shizuku UserService with an observed effective uid of 2000. */
    SHIZUKU_SHELL,
    /** App process / ordinary shell-free execution. */
    STANDARD;

    /** Short badge label used in UI. */
    val badge: String get() = when (this) {
        SU_ROOT, SHIZUKU_ROOT -> "T1"
        SHIZUKU_SHELL -> "T2"
        STANDARD -> "T3"
    }

    companion object {
        /** Compatibility aliases for the pre-T13 call sites and tests. */
        @Deprecated("Use SU_ROOT or SHIZUKU_ROOT")
        val ROOT: PrivilegeTier = SU_ROOT

        @Deprecated("Use SHIZUKU_SHELL")
        val SHIZUKU: PrivilegeTier = SHIZUKU_SHELL
    }
}
