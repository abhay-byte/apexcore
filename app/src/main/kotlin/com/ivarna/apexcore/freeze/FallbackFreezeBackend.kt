package com.ivarna.apexcore.freeze

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Internal safety backend — NOT a product freeze mode (Decision E).
 *
 * Excluded from [FreezeBackendResolver] product candidates, so auto-detect never
 * presents "standard" freeze as working. Retained only for tests / defensive
 * paths: ForceStop is best-effort [android.app.ActivityManager.killBackgroundProcesses]
 * and must NEVER report Success, so killed counts stay honest if this backend is ever hit.
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
