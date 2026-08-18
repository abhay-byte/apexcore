package com.ivarna.apexcore.tune

import android.content.SharedPreferences
import com.ivarna.apexcore.fps.privilege.PrivilegeTier

enum class TuneControlKind {
    SWITCH,
    SLIDER,
    ENUM
}

enum class TuneValueKind {
    FREQ_HZ,
    FREQ_KHZ,
    FREQ_MHZ,
    PWRLEVEL,
    ENUM,
    RAW
}

enum class TuneVendor {
    ADRENO,
    MALI,
    SAMSUNG,
    GENERIC
}

enum class TunePrivilege {
    ROOT_ONLY,
    SHELL_OK
}

enum class TuneSessionOwner {
    NONE,
    OVERLAY,
    WATCHDOG
}

/**
 * User-configured intent for a tune option.
 * [on]: whether the option is turned on.
 * [raw]: optional value for slider (e.g. "64") or enum token (e.g. "13", "mq-deadline", "bbr").
 */
data class TuneValue(
    val on: Boolean = false,
    val raw: String? = null
)

data class TuneNode(
    val path: String,
    val id: TuneId,
    val vendor: TuneVendor,
    val privilege: TunePrivilege,
    val valueKind: TuneValueKind,
    val availablePath: String? = null,
    val groupId: String
)

data class TuneCapability(
    val id: TuneId,
    val available: Boolean,
    val needsRoot: Boolean,
    val writablePaths: List<String>,
    val subtitle: String,
    val availableOptions: List<String> = emptyList(),
    val sliderRange: IntRange? = null
)

data class TuneApplyReport(
    val applied: Int,
    val failed: Int,
    val skipped: Int,
    val sessionActive: Boolean
)

interface KeyValue {
    fun getBoolean(key: String, default: Boolean): Boolean
    fun putBoolean(key: String, value: Boolean)
    fun getString(key: String, default: String?): String?
    fun putString(key: String, value: String?)
    fun remove(key: String)
}

class SharedPrefsKeyValue(private val prefs: SharedPreferences) : KeyValue {
    override fun getBoolean(key: String, default: Boolean): Boolean = prefs.getBoolean(key, default)
    override fun putBoolean(key: String, value: Boolean) = prefs.edit().putBoolean(key, value).apply()
    override fun getString(key: String, default: String?): String? = prefs.getString(key, default)
    override fun putString(key: String, value: String?) = prefs.edit().putString(key, value).apply()
    override fun remove(key: String) = prefs.edit().remove(key).apply()
}
