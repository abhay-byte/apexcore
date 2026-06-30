package com.apexcore.app.freeze

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AccessibilityFreezeBackend : FreezeBackend {
    override val name = "Accessibility"
    override val priority = 2

    override suspend fun isReady(): Boolean = withContext(Dispatchers.IO) {
        granted
    }

    override suspend fun execute(op: FreezeOperation): FreezeOperation.Result {
        Log.d(TAG, "Accessibility execute ${op.name} ${op.pkg} not implemented in T4")
        return FreezeOperation.Result.Failure("a11y-per-app-not-implemented")
    }

    fun notifyGranted() {
        granted = true
    }

    companion object {
        private const val TAG = "ApexCore.Freeze"
        @Volatile var granted: Boolean = false
    }
}
