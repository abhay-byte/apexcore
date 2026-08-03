package com.ivarna.apexcore.fps.privilege

/**
 * Resolved privilege tier for a single metric sample.
 * Maps 1:1 to execution capability.
 */
enum class PrivilegeTier {
    /** uid0 / su — ftrace, debugfs, all sysfs */
    ROOT,
    /** Shizuku user service (~ shell 2000) — dumpsys + world-readable sysfs, **not** GED (0440), **not** ftrace enable */
    SHIZUKU,
    /** App process no DUMP — direct file read + public APIs + BatteryManager + TrafficStats */
    STANDARD;

    /** Short badge label used in UI. */
    val badge: String get() = when (this) {
        ROOT -> "T1"
        SHIZUKU -> "T2"
        STANDARD -> "T3"
    }
}
