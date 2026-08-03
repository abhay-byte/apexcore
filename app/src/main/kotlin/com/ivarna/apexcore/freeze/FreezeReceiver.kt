package com.ivarna.apexcore.freeze

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class FreezeReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_FREEZE_ALL) return
        val pending = goAsync()
        val appCtx = context.applicationContext
        val keep = intent.getStringArrayListExtra(EXTRA_PROTECT_PACKAGES)
            ?.filter { it.isNotBlank() }
            ?.toSet()
            .orEmpty()
        FreezeFramework.init(appCtx)
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val result = FreezeFramework.freezeAll(
                    context = appCtx,
                    protectPackages = keep
                )
                Log.i(TAG, "FREEZE_ALL broadcast done protect=$keep result=$result")
            } catch (t: Throwable) {
                Log.w(TAG, "FREEZE_ALL failed: ${t.message}")
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private const val TAG = "ApexCore.Freeze"
        const val ACTION_FREEZE_ALL = "com.ivarna.apexcore.action.FREEZE_ALL"
        /** Optional string list of packages that must not be force-stopped. */
        const val EXTRA_PROTECT_PACKAGES = "protect_packages"
    }
}
