package com.ivarna.apexcore.games

import android.content.Context
import android.content.Intent
import android.util.Log
import com.ivarna.apexcore.freeze.FreezeFramework
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object GameLauncher {

    private const val TAG = "ApexCore.Games"

    /** Freeze all background apps (excluding the game + ApexCore), then launch the game. */
    suspend fun launch(context: Context, gamePkg: String): LaunchResult = withContext(Dispatchers.IO) {
        try {
            // Freeze everything except the game being launched (and self — hard-gated in framework)
            val result = FreezeFramework.freezeAll(
                context = context,
                protectPackages = setOf(gamePkg, context.packageName)
            )
            if (result.backend == "blocked") {
                Log.w(TAG, "Pre-launch freeze skipped: no Shizuku/Root elevation (backend=blocked)")
            }

            // Build launch intent
            val intent = context.packageManager.getLaunchIntentForPackage(gamePkg)
            if (intent == null) {
                Log.w(TAG, "No launch intent for $gamePkg")
                return@withContext LaunchResult(false, "no-launch-intent", null, result)
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            // Do NOT re-broadcast FREEZE_ALL without protect list — that previously
            // force-stopped the game right after launch (and could kill ApexCore).
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
        val freezeResult: com.ivarna.apexcore.freeze.FreezeResult?
    )
}
