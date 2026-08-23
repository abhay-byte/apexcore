package com.ivarna.apexcore.ui.theme

import android.content.Context
import com.ivarna.apexcore.ui.iron.ThemeMode

object ThemePreferences {
    private const val PREFS = "apexcore"
    private const val KEY_THEME_MODE = "theme_mode"
    private const val KEY_LIGHT_TANK_BG = "light_tank_bg"
    private const val KEY_MECHANICAL_MOTION = "mechanical_motion"

    fun get(context: Context): ThemeMode {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_THEME_MODE, null)
        return fromStorage(raw)
    }

    fun set(context: Context, mode: ThemeMode) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_THEME_MODE, toStorage(mode))
            .apply()
    }

    fun fromStorage(value: String?): ThemeMode = when (value) {
        "light", "vellum" -> ThemeMode.VELLUM
        "dark", "graphite" -> ThemeMode.GRAPHITE
        else -> ThemeMode.SYSTEM
    }

    fun toStorage(mode: ThemeMode): String = when (mode) {
        ThemeMode.SYSTEM -> "system"
        ThemeMode.VELLUM -> "vellum"
        ThemeMode.GRAPHITE -> "graphite"
    }

    fun getLightTankBg(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!prefs.contains(KEY_LIGHT_TANK_BG)) return false
        return prefs.getBoolean(KEY_LIGHT_TANK_BG, false)
    }

    fun setLightTankBg(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_LIGHT_TANK_BG, enabled)
            .apply()
    }

    fun getMechanicalMotion(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_MECHANICAL_MOTION, "auto") ?: "auto"

    fun setMechanicalMotion(context: Context, value: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_MECHANICAL_MOTION, value)
            .apply()
    }

    fun reducedMotionOverride(context: Context): Boolean? = when (getMechanicalMotion(context)) {
        "full" -> false
        "reduced" -> true
        else -> null
    }
}
