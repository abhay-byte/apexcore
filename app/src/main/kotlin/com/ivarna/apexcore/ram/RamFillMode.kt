package com.ivarna.apexcore.ram

import com.ivarna.apexcore.freeze.RootFreezeBackend
import com.ivarna.apexcore.freeze.ShizukuFreezeBackend

enum class RamFillMode(val displayName: String, val priority: Int) {
    STANDARD("Standard", 99),
    SHIZUKU("Shizuku", 0),
    ROOT("Root", 1);

    suspend fun isReady(): Boolean = when (this) {
        STANDARD -> true
        SHIZUKU -> ShizukuFreezeBackend().isReady()
        ROOT -> RootFreezeBackend().isReady()
    }

    companion object {
        fun fromPreference(pref: String?): RamFillMode = when (pref?.lowercase()) {
            "shizuku" -> SHIZUKU
            "root" -> ROOT
            else -> STANDARD
        }
    }
}
