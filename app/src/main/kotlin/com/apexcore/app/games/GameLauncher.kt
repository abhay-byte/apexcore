package com.apexcore.app.games

import android.content.Context
import android.content.Intent
import android.util.Log
import com.apexcore.app.freeze.FreezeFramework
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object GameLauncher {

    private const val TAG = "ApexCore.Games"

    /** Freeze all background apps (excluding the game), then launch the game. */
    suspend fun launch(context: Context, gamePkg: String): LaunchResult = withContext(Dispatchers.IO) {
        try {
            // Freeze everything except the game being launched
            val result = FreezeFramework.freezeAll(context) { appInfo ->
                val keep = appInfo.packageName == gamePkg
                !keep && com.apexcore.app.freeze.FreezeFilter.default(context, appInfo)
            }

            // Build launch intent
            val intent = context.packageManager.getLaunchIntentForPackage(gamePkg)
            if (intent == null) {
                Log.w(TAG, "No launch intent for $gamePkg")
                return@withContext LaunchResult(false, "no-launch-intent", null, result)
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            context.startActivity(intent)
            Log.i(TAG, "Launched $gamePkg after freezing ${result.killed} apps")
            // Start game overlay (silently if overlay permission not granted)
            try { GameOverlayService.start(context, gamePkg) } catch (_: Throwable) {}
            return@withContext LaunchResult(true, null, gamePkg, result)
        } catch (t: Throwable) {
            Log.e(TAG, "Launch failed for $gamePkg: ${t.message}")
            return@withContext LaunchResult(false, t.message ?: "unknown", null, null)
        }
    }

    data class LaunchResult(
        val success: Boolean,
        val error: String?,
        val launchedPkg: String?,
        val freezeResult: com.apexcore.app.freeze.FreezeResult?
    )
}
