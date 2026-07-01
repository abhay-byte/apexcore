package com.apexcore.app.games

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class GameManager(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val pm = context.applicationContext.packageManager

    /** All user-added and auto-detected games (deduped by pkg). */
    fun load(): List<GameInfo> = decode(prefs.getString(KEY_LIST, "[]") ?: "[]")

    fun save(games: List<GameInfo>) {
        prefs.edit().putString(KEY_LIST, encode(games)).apply()
    }

    fun add(pkg: String, name: String, autoDetected: Boolean = false) {
        val current = load().toMutableList()
        if (current.any { it.pkg == pkg }) return
        current.add(GameInfo(pkg, name, autoDetected))
        save(current)
    }

    fun remove(pkg: String) {
        save(load().filter { it.pkg != pkg })
    }

    /** Scan PackageManager for apps with CATEGORY_GAME, return those not already in list. */
    suspend fun detect(context: Context): List<GameInfo> = withContext(Dispatchers.IO) {
        val existing = load().map { it.pkg }.toSet()
        val infoList = if (isAtLeastT) {
            pm.getInstalledApplications(
                PackageManager.ApplicationInfoFlags.of(PackageManager.GET_META_DATA.toLong())
            )
        } else {
            @Suppress("DEPRECATION") pm.getInstalledApplications(PackageManager.GET_META_DATA)
        }
        infoList.filter { app ->
            app.packageName !in existing &&
                app.packageName != context.packageName &&
                (app.category == ApplicationInfo.CATEGORY_GAME || hasGameMeta(app))
        }.map { app ->
            val label = pm.getApplicationLabel(app)?.toString() ?: app.packageName
            GameInfo(app.packageName, label, isAutoDetected = true)
        }
    }

    /** Accept all detected games, merging into saved list. */
    suspend fun acceptDetected(context: Context) {
        val current = load().toMutableList()
        val existingPkgs = current.map { it.pkg }.toSet()
        val detected = detect(context)
        for (game in detected) {
            if (game.pkg !in existingPkgs) current.add(game)
        }
        save(current)
    }

    private fun hasGameMeta(app: ApplicationInfo): Boolean = try {
        val ai = pm.getApplicationInfo(app.packageName, PackageManager.GET_META_DATA)
        ai.metaData?.containsKey("isGame") == true
    } catch (_: Throwable) { false }

    private fun encode(games: List<GameInfo>): String = JSONArray().apply {
        for (g in games) put(JSONObject().apply {
            put("pkg", g.pkg); put("name", g.name); put("auto", g.isAutoDetected)
        })
    }.toString()

    private fun decode(raw: String): List<GameInfo> = try {
        val arr = JSONArray(raw)
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            GameInfo(o.getString("pkg"), o.getString("name"), o.optBoolean("auto", false))
        }
    } catch (_: Exception) { emptyList() }

    companion object {
        private const val TAG = "ApexCore.Games"
        private const val PREFS_NAME = "apexcore_games"
        private const val KEY_LIST = "game_list"
        private val isAtLeastT = android.os.Build.VERSION.SDK_INT >= 33
    }
}
