package com.ivarna.apexcore.fps.privilege

/**
 * Global privilege mode set by the user on the Home screen.
 * Forces the tier used by all metrics.
 */
enum class PrivilegeMode {
    /** Each metric walks its own root → shizuku → standard fallback chain. */
    AUTO,
    /** Every metric uses **only** root paths; if su unavailable → metric null / error, no silent falldown. */
    ROOT,
    /** Every metric uses **only** Shizuku/elevated non-root shell; no `su`, no ftrace write, no debugfs mount. */
    SHIZUKU,
    /** App-only: direct file read where allowed + public APIs + dumpsys without elevated shell. */
    STANDARD;

    companion object {
        fun fromString(value: String): PrivilegeMode =
            entries.find { it.name.equals(value, ignoreCase = true) } ?: AUTO

        const val PREFS_KEY = "privilege_mode"
    }
}
