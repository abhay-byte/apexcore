package com.ivarna.apexcore.ui.theme

import android.content.Context

/**
 * User theme preference: follow system, or force light / dark.
 * Persisted in SharedPreferences ("apexcore" / "theme_mode").
 */
enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK;

    companion object {
        fun fromStorage(value: String?): ThemeMode = when (value) {
            "light" -> LIGHT
            "dark" -> DARK
            else -> SYSTEM
        }
    }

    fun toStorage(): String = when (this) {
        SYSTEM -> "system"
        LIGHT -> "light"
        DARK -> "dark"
    }

    fun resolveDark(systemDark: Boolean): Boolean = when (this) {
        SYSTEM -> systemDark
        LIGHT -> false
        DARK -> true
    }
}

object ThemePreferences {
    private const val PREFS = "apexcore"
    private const val KEY_THEME_MODE = "theme_mode"
    /** When true, RAM/SWAP leaf tanks keep light-mode glass even in dark theme. */
    private const val KEY_LIGHT_TANK_BG = "light_tank_bg"

    fun get(context: Context): ThemeMode {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_THEME_MODE, null)
        return ThemeMode.fromStorage(raw)
    }

    fun set(context: Context, mode: ThemeMode) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_THEME_MODE, mode.toStorage())
            .apply()
    }

    /** Default true — tanks always read as frosted light glass like light mode. */
    fun getLightTankBg(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_LIGHT_TANK_BG, true)

    fun setLightTankBg(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_LIGHT_TANK_BG, enabled)
            .apply()
    }
}
