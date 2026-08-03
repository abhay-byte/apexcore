package com.ivarna.apexcore.freeze

import android.content.Context
import android.content.pm.ApplicationInfo

object FreezeFilter {

    /**
     * Packages that must never be force-stopped (self, shell/UI, input method hosts).
     * Always unioned with caller-provided keep packages (game / foreground).
     */
    val ALWAYS_PROTECT: Set<String> = setOf(
        "android",
        "com.android.systemui",
        "com.android.shell",
        "com.android.permissioncontroller",
        "com.google.android.permissioncontroller",
        "com.android.inputmethod.latin",
        "com.google.android.inputmethod.latin",
        "com.android.launcher",
        "com.android.launcher3",
        "com.google.android.apps.nexuslauncher",
    )

    /**
     * @return true if this package is a freeze **target** (eligible to force-stop).
     */
    fun default(context: Context, pkg: ApplicationInfo): Boolean {
        return shouldFreeze(context, pkg, protectPackages = emptySet())
    }

    /**
     * Same as [default] plus an explicit protect set (game, foreground, self, etc.).
     * Self package and [ALWAYS_PROTECT] are always excluded.
     */
    fun shouldFreeze(
        context: Context,
        pkg: ApplicationInfo,
        protectPackages: Set<String> = emptySet()
    ): Boolean {
        val name = pkg.packageName ?: return false
        if (name.isBlank()) return false
        if (name == context.packageName) return false
        if (name in ALWAYS_PROTECT) return false
        if (name in protectPackages) return false
        // Sub-process package names: "com.game:push" — protect if base is protected
        val base = name.substringBefore(':')
        if (base == context.packageName) return false
        if (base in protectPackages) return false
        if (WhitelistStore.isPinned(context, name) || WhitelistStore.isPinned(context, base)) {
            return false
        }
        if ((pkg.flags and ApplicationInfo.FLAG_STOPPED) != 0) return false
        val isPureSystem =
            (pkg.flags and ApplicationInfo.FLAG_SYSTEM) != 0 &&
                (pkg.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0
        if (isPureSystem) return false
        return true
    }
}
