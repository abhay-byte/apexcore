package com.ivarna.apexcore.freeze

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class FallbackFreezeBackend(private val appContext: Context) : FreezeBackend {
    override val name = "cached only"
    override val priority = PRIORITY

    companion object {
        const val PRIORITY = 99
    }

    override suspend fun isReady(): Boolean = true

    override suspend fun execute(op: FreezeOperation): FreezeOperation.Result =
        withContext(Dispatchers.IO) {
            val am = appContext.getSystemService(Context.ACTIVITY_SERVICE)
                as android.app.ActivityManager
            when (op) {
                is FreezeOperation.ForceStop -> {
                    am.killBackgroundProcesses(op.pkg)
                    FreezeOperation.Result.Success
                }
                else -> FreezeOperation.Result.Failure("unsupported-on-fallback")
            }
        }
}
