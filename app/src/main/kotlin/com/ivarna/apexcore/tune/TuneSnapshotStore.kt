package com.ivarna.apexcore.tune

import android.content.Context
import android.provider.Settings
import android.util.Log

/**
 * Snapshot store for pre-session sysfs and settings values.
 * Enforces insert-if-absent semantics to prevent overwriting original values on re-apply.
 * Persists boot identity (BOOT_COUNT and /proc/sys/kernel/random/boot_id) to detect reboots.
 */
class TuneSnapshotStore(
    private val context: Context,
    private val prefs: KeyValue,
    private val shell: TuneShell
) {
    private val snapshotMap = mutableMapOf<String, String>()

    init {
        loadSnapshotFromPrefs()
    }

    @Synchronized
    fun recordOriginal(path: String, originalValue: String) {
        if (!snapshotMap.containsKey(path)) {
            snapshotMap[path] = originalValue
            saveSnapshotToPrefs()
        }
    }

    @Synchronized
    fun getOriginal(path: String): String? = snapshotMap[path]

    @Synchronized
    fun getAllOriginals(): Map<String, String> = snapshotMap.toMap()

    @Synchronized
    fun removeOriginal(path: String) {
        if (snapshotMap.remove(path) != null) {
            saveSnapshotToPrefs()
        }
    }

    @Synchronized
    fun clear() {
        snapshotMap.clear()
        prefs.putString(KEY_SNAPSHOT_JSON, "{}")
        prefs.putBoolean(KEY_APPLIED, false)
    }

    @Synchronized
    fun size(): Int = snapshotMap.size

    @Synchronized
    fun containsPath(path: String): Boolean = snapshotMap.containsKey(path)

    fun recordBootIdentity() {
        val (bootCount, bootId) = currentBoot(context, shell)
        prefs.putBoolean(KEY_APPLIED, true)
        prefs.putString(KEY_BOOT_COUNT, bootCount.toString())
        prefs.putString(KEY_BOOT_ID, bootId)
    }

    fun isBootMatching(): Boolean {
        val persistedCount = prefs.getString(KEY_BOOT_COUNT, "-1")?.toIntOrNull() ?: -1
        val persistedId = prefs.getString(KEY_BOOT_ID, "").orEmpty()
        val cur = currentBoot(context, shell)
        return bootMatch(persistedCount, persistedId, cur)
    }

    private fun loadSnapshotFromPrefs() {
        snapshotMap.clear()
        val jsonStr = prefs.getString(KEY_SNAPSHOT_JSON, "{}") ?: "{}"
        snapshotMap.putAll(SnapshotJson.decode(jsonStr))
    }

    private fun saveSnapshotToPrefs() {
        prefs.putString(KEY_SNAPSHOT_JSON, SnapshotJson.encode(snapshotMap))
    }

    object SnapshotJson {
        fun encode(map: Map<String, String>): String {
            val sb = StringBuilder("{")
            var first = true
            for ((k, v) in map) {
                if (!first) sb.append(",")
                first = false
                sb.append("\"").append(escape(k)).append("\":\"").append(escape(v)).append("\"")
            }
            sb.append("}")
            return sb.toString()
        }

        fun decode(json: String?): Map<String, String> {
            if (json.isNullOrBlank()) return emptyMap()
            val result = mutableMapOf<String, String>()
            val regex = Regex(""""((?:\\.|[^"\\])*)"\s*:\s*"((?:\\.|[^"\\])*)"""")
            for (match in regex.findAll(json)) {
                val k = unescape(match.groupValues[1])
                val v = unescape(match.groupValues[2])
                result[k] = v
            }
            return result
        }

        private fun escape(s: String): String = s.replace("\\", "\\\\").replace("\"", "\\\"")
        private fun unescape(s: String): String = s.replace("\\\"", "\"").replace("\\\\", "\\")
    }

    companion object {
        private const val TAG = "ApexCore.TuneSnapshot"
        const val KEY_SNAPSHOT_JSON = "tune_snapshot_json"
        const val KEY_APPLIED = "tune_applied"
        const val KEY_BOOT_COUNT = "tune_boot_count"
        const val KEY_BOOT_ID = "tune_boot_id"

        fun currentBoot(context: Context, shell: TuneShell): Pair<Int, String> {
            val count = try {
                Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1)
            } catch (_: Throwable) {
                -1
            }
            val id = shell.read("/proc/sys/kernel/random/boot_id")?.trim().orEmpty()
            return count to id
        }

        fun bootMatch(persistedCount: Int, persistedId: String, cur: Pair<Int, String>): Boolean {
            if (persistedId.isNotEmpty() && cur.second.isNotEmpty()) {
                return persistedId == cur.second
            }
            if (persistedCount >= 0 && cur.first >= 0) {
                return persistedCount == cur.first
            }
            return false // Unknown -> treat as reboot -> discard snapshot, do not write
        }
    }
}
