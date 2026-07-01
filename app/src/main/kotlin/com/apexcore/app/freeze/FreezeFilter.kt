package com.apexcore.app.freeze

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager

object FreezeFilter {

    fun default(context: Context, pkg: ApplicationInfo): Boolean {
        if (pkg.packageName == context.packageName) return false
        val isPureSystem =
            (pkg.flags and ApplicationInfo.FLAG_SYSTEM) != 0 &&
                (pkg.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0
        if (isPureSystem) return false
        if ((pkg.flags and ApplicationInfo.FLAG_STOPPED) != 0) return false
        return true
    }
}
