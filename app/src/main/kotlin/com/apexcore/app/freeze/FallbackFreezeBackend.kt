package com.apexcore.app.freeze

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class FallbackFreezeBackend(private val appContext: Context) : FreezeBackend {
    override val name = "cached only"
    override val priority = 99

    override suspend fun isReady(): Boolean = true

    override suspend fun execute(op: FreezeOperation): FreezeOperation.Result =
        withContext(Dispatchers.IO) {
            val pkg = op.pkg
            try {
                val am = appContext.getSystemService(Context.ACTIVITY_SERVICE)
                    as android.app.ActivityManager
                am.killBackgroundProcesses(pkg)
                FreezeOperation.Result.Success
            } catch (t: Throwable) {
                FreezeOperation.Result.Failure(t.message ?: "kill-failed")
            }
        }
}
