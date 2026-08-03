package com.ivarna.apexcore.fps.privilege

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Global privilege mode for FPS (and other metrics).
 *
 * Synced with the top-bar backend preference (`preferred_backend` in apexcore prefs):
 * - `root` → [PrivilegeMode.ROOT]
 * - `shizuku` → [PrivilegeMode.SHIZUKU]
 * - null / auto → [PrivilegeMode.AUTO]
 */
class PrivilegeModeStore(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _mode = MutableStateFlow(readInitial())
    val mode: StateFlow<PrivilegeMode> = _mode.asStateFlow()

    private val listeners = mutableListOf<() -> Unit>()

    fun addOnModeChangedListener(listener: () -> Unit) {
        listeners.add(listener)
    }

    fun removeOnModeChangedListener(listener: () -> Unit) {
        listeners.remove(listener)
    }

    fun setMode(mode: PrivilegeMode) {
        if (_mode.value == mode) return
        _mode.value = mode
        // Keep freeze backend pref in lockstep with FPS privilege mode
        when (mode) {
            PrivilegeMode.ROOT ->
                prefs.edit().putString(KEY_PREFERRED_BACKEND, "root").apply()
            PrivilegeMode.SHIZUKU ->
                prefs.edit().putString(KEY_PREFERRED_BACKEND, "shizuku").apply()
            PrivilegeMode.AUTO, PrivilegeMode.STANDARD ->
                prefs.edit().remove(KEY_PREFERRED_BACKEND).apply()
        }
        listeners.forEach { it.invoke() }
    }

    /**
     * Apply mode from top-bar / freeze preferred backend string.
     * @param pref `"root"`, `"shizuku"`, or null for auto
     */
    fun syncFromPreferredBackend(pref: String?) {
        val next = when (pref?.lowercase()) {
            "root" -> PrivilegeMode.ROOT
            "shizuku" -> PrivilegeMode.SHIZUKU
            else -> PrivilegeMode.AUTO
        }
        if (_mode.value != next) {
            _mode.value = next
            listeners.forEach { it.invoke() }
        }
    }

    fun label(): String = when (_mode.value) {
        PrivilegeMode.AUTO -> "Auto"
        PrivilegeMode.ROOT -> "Root"
        PrivilegeMode.SHIZUKU -> "Shizuku"
        PrivilegeMode.STANDARD -> "Standard"
    }

    private fun readInitial(): PrivilegeMode {
        val pref = prefs.getString(KEY_PREFERRED_BACKEND, null)
        return when (pref?.lowercase()) {
            "root" -> PrivilegeMode.ROOT
            "shizuku" -> PrivilegeMode.SHIZUKU
            "standard" -> PrivilegeMode.STANDARD
            else -> PrivilegeMode.AUTO
        }
    }

    companion object {
        private const val PREFS = "apexcore"
        private const val KEY_PREFERRED_BACKEND = "preferred_backend"
    }
}
