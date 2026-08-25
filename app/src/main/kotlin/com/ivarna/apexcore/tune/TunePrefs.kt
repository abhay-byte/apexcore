package com.ivarna.apexcore.tune

import android.content.Context
import android.content.SharedPreferences

class TunePrefs(
    private val kv: KeyValue
) {
    constructor(context: Context) : this(
        SharedPrefsKeyValue(context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE))
    )

    fun getIntent(id: TuneId): TuneValue {
        val on = kv.getBoolean(prefixOn(id), false)
        val raw = kv.getString(prefixVal(id), null)
        return TuneValue(on = on, raw = raw)
    }

    fun setIntent(id: TuneId, value: TuneValue) {
        kv.putBoolean(prefixOn(id), value.on)
        if (value.raw != null) {
            kv.putString(prefixVal(id), value.raw)
        } else {
            kv.remove(prefixVal(id))
        }
    }

    fun getOwner(): TuneSessionOwner {
        val str = kv.getString(KEY_OWNER, TuneSessionOwner.NONE.name)
        return try {
            TuneSessionOwner.valueOf(str ?: TuneSessionOwner.NONE.name)
        } catch (_: Exception) {
            TuneSessionOwner.NONE
        }
    }

    fun setOwner(owner: TuneSessionOwner) {
        kv.putString(KEY_OWNER, owner.name)
    }

    fun getSessionPkg(): String = kv.getString(KEY_SESSION_PKG, "").orEmpty()

    fun setSessionPkg(pkg: String) {
        kv.putString(KEY_SESSION_PKG, pkg)
    }

    fun isApplied(): Boolean = kv.getBoolean(TuneSnapshotStore.KEY_APPLIED, false)

    fun setApplied(applied: Boolean) {
        kv.putBoolean(TuneSnapshotStore.KEY_APPLIED, applied)
    }

    fun isMigratedV1(): Boolean = kv.getBoolean(KEY_MIGRATED_V1, false)

    /**
     * PR 4 one-shot cleanup:
     * Deletes leftover dummy keys from early versions without migrating any true values.
     */
    fun deleteDummyKeysIfNeeded() {
        if (!isMigratedV1()) {
            kv.remove("dummy_opt_gpu_render")
            kv.remove("dummy_opt_cpu_thread")
            kv.remove("dummy_opt_opengl")
            kv.remove("dummy_opt_kernel")
            kv.putBoolean(KEY_MIGRATED_V1, true)
        }
    }

    fun isMaxLockAcked(): Boolean = kv.getBoolean(KEY_MAX_LOCK_ACKED, false)
    fun setMaxLockAcked(v: Boolean) = kv.putBoolean(KEY_MAX_LOCK_ACKED, v)

    companion object {
        const val PREFS_NAME = "apexcore"
        const val KEY_OWNER = "tune_owner"
        const val KEY_SESSION_PKG = "tune_session_pkg"
        const val KEY_MIGRATED_V1 = "tune_migrated_v1"
        const val KEY_MAX_LOCK_ACKED = "tune_ack_max_locks"

        private fun prefixOn(id: TuneId): String = "tune_on_${id.name}"
        private fun prefixVal(id: TuneId): String = "tune_val_${id.name}"
    }
}
