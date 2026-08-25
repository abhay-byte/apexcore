package com.ivarna.apexcore.tune

import android.content.Context
import android.provider.Settings
import org.json.JSONArray
import org.json.JSONObject

data class TuneSnapshotEntry(
    val path: String,
    val originalValue: String,
    val owners: Set<TuneId> = emptySet(),
    val lastVerifiedValue: String? = null,
    val verificationMode: VerificationMode = VerificationMode.EXACT_STRING,
    val transactionId: String? = null,
    val backend: TuneBackendIdentity = TuneBackendIdentity.STANDARD
)

/** Persistent snapshot/ownership store with migration from T12 path->value JSON. */
class TuneSnapshotStore(
    private val context: Context,
    private val prefs: KeyValue,
    private val shell: TuneShell
) {
    private val entries = mutableMapOf<String, TuneSnapshotEntry>()

    init { loadSnapshotFromPrefs() }

    @Synchronized
    fun recordOriginal(path: String, originalValue: String) {
        if (path !in entries) {
            entries[path] = TuneSnapshotEntry(path, originalValue)
            saveSnapshotToPrefs()
        }
    }

    @Synchronized
    fun recordOriginal(
        path: String,
        originalValue: String,
        owner: TuneId,
        transactionId: String,
        backend: TuneBackendIdentity
    ) {
        val existing = entries[path]
        entries[path] = if (existing == null) {
            TuneSnapshotEntry(path, originalValue, setOf(owner), null, VerificationMode.EXACT_STRING, transactionId, backend)
        } else {
            existing.copy(
                owners = existing.owners + owner,
                transactionId = transactionId,
                backend = backend
            )
        }
        saveSnapshotToPrefs()
    }

    @Synchronized
    fun recordVerified(path: String, value: String, mode: VerificationMode) {
        entries[path]?.let {
            entries[path] = it.copy(lastVerifiedValue = value, verificationMode = mode)
            saveSnapshotToPrefs()
        }
    }

    @Synchronized
    fun getOriginal(path: String): String? = entries[path]?.originalValue

    @Synchronized
    fun getEntry(path: String): TuneSnapshotEntry? = entries[path]

    @Synchronized
    fun getAllEntries(): Map<String, TuneSnapshotEntry> = entries.toMap()

    @Synchronized
    fun getAllOriginals(): Map<String, String> = entries.mapValues { it.value.originalValue }

    /** Remove one owner; the entry remains until the caller restores it. */
    @Synchronized
    fun releaseOwner(owner: TuneId) {
        var changed = false
        entries.keys.toList().forEach { path ->
            val entry = entries[path] ?: return@forEach
            if (owner in entry.owners) {
                entries[path] = entry.copy(owners = entry.owners - owner)
                changed = true
            }
        }
        if (changed) saveSnapshotToPrefs()
    }

    @Synchronized
    fun releaseOwner(path: String, owner: TuneId): Boolean {
        val entry = entries[path] ?: return true
        if (owner !in entry.owners) return entry.owners.isEmpty()
        val updated = entry.copy(owners = entry.owners - owner)
        entries[path] = updated
        saveSnapshotToPrefs()
        return updated.owners.isEmpty()
    }

    @Synchronized
    fun owners(path: String): Set<TuneId> = entries[path]?.owners.orEmpty()

    @Synchronized
    fun removeOriginal(path: String) {
        if (entries.remove(path) != null) saveSnapshotToPrefs()
    }

    @Synchronized
    fun clear() {
        entries.clear()
        prefs.putString(KEY_SNAPSHOT_JSON, "{}")
        prefs.putBoolean(KEY_APPLIED, false)
    }

    @Synchronized fun size(): Int = entries.size
    @Synchronized fun containsPath(path: String): Boolean = path in entries

    fun recordBootIdentity() {
        val (bootCount, bootId) = currentBoot(context, shell)
        prefs.putBoolean(KEY_APPLIED, true)
        prefs.putString(KEY_BOOT_COUNT, bootCount.toString())
        prefs.putString(KEY_BOOT_ID, bootId)
    }

    fun isBootMatching(): Boolean {
        val persistedCount = prefs.getString(KEY_BOOT_COUNT, "-1")?.toIntOrNull() ?: -1
        val persistedId = prefs.getString(KEY_BOOT_ID, "").orEmpty()
        return bootMatch(persistedCount, persistedId, currentBoot(context, shell))
    }

    private fun loadSnapshotFromPrefs() {
        entries.clear()
        val raw = prefs.getString(KEY_SNAPSHOT_JSON, "{}") ?: "{}"
        try {
            val root = JSONObject(raw)
            val names = root.names()
            if (names != null) for (i in 0 until names.length()) {
                val path = names.optString(i)
                val value = root.opt(path)
                if (value is JSONObject) {
                    val owners = mutableSetOf<TuneId>()
                    val ownerArray = value.optJSONArray("owners")
                    if (ownerArray != null) for (j in 0 until ownerArray.length()) {
                        runCatching { owners += TuneId.valueOf(ownerArray.optString(j)) }
                    }
                    val mode = runCatching { VerificationMode.valueOf(value.optString("verificationMode")) }
                        .getOrDefault(VerificationMode.EXACT_STRING)
                    val backend = runCatching { TuneBackendIdentity.valueOf(value.optString("backend")) }
                        .getOrDefault(TuneBackendIdentity.STANDARD)
                    entries[path] = TuneSnapshotEntry(
                        path = path,
                        originalValue = value.optString("originalValue", ""),
                        owners = owners,
                        lastVerifiedValue = value.optString("lastVerifiedValue").takeIf { it.isNotEmpty() },
                        verificationMode = mode,
                        transactionId = value.optString("transactionId").takeIf { it.isNotEmpty() },
                        backend = backend
                    )
                } else if (value != null) {
                    // T12 migration: preserve the original path/value exactly.
                    entries[path] = TuneSnapshotEntry(path, value.toString())
                }
            }
        } catch (_: Throwable) {
            // Corrupt data must not crash startup; the old regex decoder is a
            // final migration fallback for simple path->value snapshots.
            entries.putAll(SnapshotJson.decode(raw).mapValues { TuneSnapshotEntry(it.key, it.value) })
        }
        // Android's local unit-test stubs do not implement JSONObject parsing;
        // also retain this fallback for pre-T13 path->value snapshots.
        if (entries.isEmpty() && raw != "{}") {
            entries.putAll(SnapshotJson.decode(raw).mapValues { TuneSnapshotEntry(it.key, it.value) })
        }
    }

    private fun saveSnapshotToPrefs() {
        val root = JSONObject()
        entries.forEach { (path, entry) ->
            val value = JSONObject()
            value.put("originalValue", entry.originalValue)
            value.put("owners", JSONArray(entry.owners.map { it.name }))
            value.put("lastVerifiedValue", entry.lastVerifiedValue ?: JSONObject.NULL)
            value.put("verificationMode", entry.verificationMode.name)
            value.put("transactionId", entry.transactionId ?: JSONObject.NULL)
            value.put("backend", entry.backend.name)
            root.put(path, value)
        }
        prefs.putString(KEY_SNAPSHOT_JSON, root.toString())
    }

    object SnapshotJson {
        fun encode(map: Map<String, String>): String {
            val root = JSONObject()
            map.forEach { (key, value) -> root.put(key, value) }
            return root.toString()
        }

        fun decode(json: String?): Map<String, String> {
            if (json.isNullOrBlank()) return emptyMap()
            return try {
                val root = JSONObject(json)
                val result = mutableMapOf<String, String>()
                val names = root.names()
                if (names != null) for (i in 0 until names.length()) {
                    val key = names.optString(i)
                    val value = root.opt(key)
                    if (value !is JSONObject && value != JSONObject.NULL) result[key] = value.toString()
                }
                if (result.isNotEmpty()) result else decodeWithRegex(json)
            } catch (_: Throwable) { decodeWithRegex(json) }
        }

        private fun decodeWithRegex(json: String): Map<String, String> {
            val result = mutableMapOf<String, String>()
            val regex = Regex("""\"((?:\\\\.|[^\"\\\\])*)\"\s*:\s*\"((?:\\\\.|[^\"\\\\])*)\"""")
            regex.findAll(json).forEach { match ->
                result[unescape(match.groupValues[1])] = unescape(match.groupValues[2])
            }
            return result
        }

        private fun unescape(value: String): String = value.replace("\\\"", "\"").replace("\\\\", "\\")
    }

    companion object {
        const val KEY_SNAPSHOT_JSON = "tune_snapshot_json"
        const val KEY_APPLIED = "tune_applied"
        const val KEY_BOOT_COUNT = "tune_boot_count"
        const val KEY_BOOT_ID = "tune_boot_id"

        fun currentBoot(context: Context, shell: TuneShell): Pair<Int, String> {
            val count = try { Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1) } catch (_: Throwable) { -1 }
            val id = shell.read("/proc/sys/kernel/random/boot_id")?.trim().orEmpty()
            return count to id
        }

        fun bootMatch(persistedCount: Int, persistedId: String, cur: Pair<Int, String>): Boolean {
            if (persistedId.isNotEmpty() && cur.second.isNotEmpty()) return persistedId == cur.second
            if (persistedCount >= 0 && cur.first >= 0) return persistedCount == cur.first
            return false
        }
    }
}
