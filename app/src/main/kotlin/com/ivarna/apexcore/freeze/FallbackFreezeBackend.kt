package com.ivarna.apexcore.freeze

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Standard / no-elevation backend.
 *
 * Real force-stop requires Shizuku or Root (`am force-stop`). On modern Android,
 * [android.app.ActivityManager.killBackgroundProcesses] is best-effort only and
 * often a no-op for third-party apps — never report Success so UI killed counts stay honest.
 */
class FallbackFreezeBackend(private val appContext: Context) : FreezeBackend {
    override val name = "standard"
    override val priority = PRIORITY

    companion object {
        const val PRIORITY = 99
    }

    override suspend fun isReady(): Boolean = true

    override suspend fun execute(op: FreezeOperation): FreezeOperation.Result =
        withContext(Dispatchers.IO) {
            when (op) {
                is FreezeOperation.ForceStop -> {
                    try {
                        val am = appContext.getSystemService(Context.ACTIVITY_SERVICE)
                            as android.app.ActivityManager
                        am.killBackgroundProcesses(op.pkg)
                    } catch (_: Throwable) {
                        // Best-effort only; platform may no-op or throw.
                    }
                    // Never Success: would inflate "killed" on Standard path.
                    FreezeOperation.Result.SKIPPED_FALLBACK
                }
                else -> FreezeOperation.Result.Failure("unsupported-on-fallback")
            }
        }
}
