package com.ivarna.apexcore.freeze

import android.content.Context
import android.content.SharedPreferences

object WhitelistStore {

    private const val PREFS = "apexcore_whitelist"
    private const val KEY = "pinned_packages"

    fun isPinned(context: Context, pkg: String): Boolean =
        allPinned(context).contains(pkg)

    fun setPinned(context: Context, pkg: String, pinned: Boolean) {
        val prefs = prefs(context)
        val current = allPinned(context).toMutableSet()
        if (pinned) current.add(pkg) else current.remove(pkg)
        prefs.edit().putStringSet(KEY, current).apply()
    }

    fun allPinned(context: Context): Set<String> =
        prefs(context).getStringSet(KEY, emptySet())?.toSet() ?: emptySet()

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
