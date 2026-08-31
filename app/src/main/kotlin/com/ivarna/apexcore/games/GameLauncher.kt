package com.ivarna.apexcore.games

import android.content.Context
import android.content.Intent
import android.util.Log
import com.ivarna.apexcore.freeze.FreezeFramework
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object GameLauncher {

    private const val TAG = "ApexCore.Games"

    /** Fire the package launch intent only (Pack V PART phase). */
    fun fireIntent(context: Context, gamePkg: String): Boolean {
        val intent = context.packageManager.getLaunchIntentForPackage(gamePkg) ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        return try {
            context.startActivity(intent)
            true
        } catch (t: Throwable) {
            Log.w(TAG, "fireIntent failed for $gamePkg: ${t.message}")
            false
        }
    }

    /**
     * HUD rail + FPS target + session tune after PART (Pack V HANDOFF).
     * Skips silently when overlay permission is off.
     */
    fun attachRail(context: Context, gamePkg: String) {
        try {
            com.ivarna.apexcore.fps.FpsStack.get(context).repository.setTargetPackage(gamePkg)
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to set FPS target: ${t.message}")
        }

        val overlayStarted = try {
            GameOverlayService.start(context, gamePkg)
        } catch (_: Throwable) {
            false
        }

        val tuneManager = com.ivarna.apexcore.tune.TuneManager.get(context)
        if (overlayStarted) {
            tuneManager.setOwner(com.ivarna.apexcore.tune.TuneSessionOwner.OVERLAY)
        } else {
            tuneManager.setOwner(com.ivarna.apexcore.tune.TuneSessionOwner.WATCHDOG)
            com.ivarna.apexcore.tune.TuneSessionWatchdog.arm(context, gamePkg)
        }

        // Tune apply is best-effort; run detached so the ceremony isn't blocked.
        kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
            try {
                val report = tuneManager.applyForSession(gamePkg)
                if (overlayStarted && !GameOverlayService.isRunning) {
                    kotlinx.coroutines.delay(100)
                    if (!GameOverlayService.isRunning) {
                        Log.w(TAG, "Overlay start() returned true but service is not running; re-arming WATCHDOG")
                        tuneManager.setOwner(com.ivarna.apexcore.tune.TuneSessionOwner.WATCHDOG)
                        com.ivarna.apexcore.tune.TuneSessionWatchdog.arm(context, gamePkg)
                    }
                }
                if (report.applied == 0 && report.failed > 0 && GameOverlayService.isRunning) {
                    withContext(Dispatchers.Main) {
                        android.widget.Toast.makeText(
                            context,
                            "Game tune skipped — kernel nodes not writable",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            } catch (t: Throwable) {
                Log.w(TAG, "Tune apply failed for $gamePkg: ${t.message}")
            }
        }
    }

    /** Freeze all background apps (excluding the game + ApexCore), then launch the game. */
    suspend fun launch(context: Context, gamePkg: String): LaunchResult = withContext(Dispatchers.IO) {
        try {
            val result = FreezeFramework.freezeAll(
                context = context,
                protectPackages = setOf(gamePkg, context.packageName)
            )
            if (result.backend == "blocked") {
                Log.w(TAG, "Pre-launch freeze skipped: no Shizuku/Root elevation (backend=blocked)")
            }

            if (!fireIntent(context, gamePkg)) {
                Log.w(TAG, "No launch intent for $gamePkg")
                return@withContext LaunchResult(false, "no-launch-intent", null, result)
            }
            Log.i(TAG, "Launched $gamePkg after freezing ${result.killed} apps")
            attachRail(context, gamePkg)
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
