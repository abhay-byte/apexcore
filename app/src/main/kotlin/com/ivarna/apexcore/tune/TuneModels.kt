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

enum class TuneBackendIdentity {
    SU_ROOT,
    SHIZUKU_ROOT,
    SHIZUKU_SHELL,
    STANDARD;

    val isRootCapable: Boolean
        get() = this == SU_ROOT || this == SHIZUKU_ROOT

    val isElevated: Boolean
        get() = this != STANDARD

    fun asPrivilegeTier(): PrivilegeTier = when (this) {
        SU_ROOT -> PrivilegeTier.SU_ROOT
        SHIZUKU_ROOT -> PrivilegeTier.SHIZUKU_ROOT
        SHIZUKU_SHELL -> PrivilegeTier.SHIZUKU_SHELL
        STANDARD -> PrivilegeTier.STANDARD
    }
}

enum class RequiredIdentity {
    ANY,
    SHELL_OR_ROOT,
    ROOT
}

enum class VerificationMode {
    EXACT_STRING,
    EXACT_INT,
    GOVERNOR_TOKEN,
    IO_SCHEDULER_ACTIVE_TOKEN,
    BOOLEAN_NORMALIZED,
    SETTINGS_VALUE,
    CUSTOM
}

enum class ProbeStrategy {
    READ_METADATA_ONLY,
    WRITE_SAME_VALUE_SAFE,
    APPLY_VERIFY_ROLLBACK,
    COMMAND_QUERY
}

enum class CapabilityReason {
    AVAILABLE,
    NEEDS_ROOT,
    SHIZUKU_SHELL_LIMITED,
    NODE_NOT_FOUND,
    READ_DENIED,
    WRITE_DENIED,
    WRITE_NOT_EFFECTIVE,
    OPTION_NOT_SUPPORTED,
    THERMAL_SAFETY_BLOCKED,
    PROBE_TIMEOUT,
    UNKNOWN
}

enum class MutationFailure {
    INVALID_PATH,
    INVALID_VALUE,
    NO_PERMISSION,
    COMMAND_FAILED,
    WRITE_NOT_EFFECTIVE,
    READBACK_MISMATCH,
    TIMEOUT,
    ROLLED_BACK,
    UNSUPPORTED,
    THERMAL_SAFETY_BLOCKED,
    UNKNOWN
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
    val groupId: String,
    val requiredIdentity: RequiredIdentity = when (privilege) {
        TunePrivilege.ROOT_ONLY -> RequiredIdentity.ROOT
        TunePrivilege.SHELL_OK -> RequiredIdentity.SHELL_OR_ROOT
    },
    val verificationMode: VerificationMode = when (valueKind) {
        TuneValueKind.ENUM -> VerificationMode.GOVERNOR_TOKEN
        TuneValueKind.PWRLEVEL, TuneValueKind.FREQ_HZ, TuneValueKind.FREQ_KHZ,
        TuneValueKind.FREQ_MHZ -> VerificationMode.EXACT_INT
        TuneValueKind.RAW -> VerificationMode.EXACT_STRING
    },
    val probeStrategy: ProbeStrategy = ProbeStrategy.WRITE_SAME_VALUE_SAFE
)

data class TuneCapability(
    val id: TuneId,
    val available: Boolean,
    val needsRoot: Boolean,
    val writablePaths: List<String>,
    val subtitle: String,
    val availableOptions: List<String> = emptyList(),
    val sliderRange: IntRange? = null,
    val reason: CapabilityReason = if (available) CapabilityReason.AVAILABLE else CapabilityReason.UNKNOWN,
    val backend: TuneBackendIdentity = TuneBackendIdentity.STANDARD,
    val diagnostics: Map<String, String> = emptyMap()
)

data class TuneApplyReport(
    val applied: Int,
    val failed: Int,
    val skipped: Int,
    val sessionActive: Boolean,
    val details: Map<String, String> = emptyMap()
)

data class MutationResult(
    val commandOk: Boolean,
    val verified: Boolean,
    val requested: String,
    val readback: String? = null,
    val effectiveBackend: TuneBackendIdentity,
    val failure: MutationFailure? = null
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
