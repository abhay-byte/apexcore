package com.apexcore.app.freeze

import android.content.Context
import android.util.Log

class FreezeBackendResolver(candidates: List<FreezeBackend>) {

    internal val candidates: List<FreezeBackend> = candidates

    constructor(appContext: Context) : this(
        listOf(
            ShizukuFreezeBackend(),
            RootFreezeBackend(),
            AccessibilityFreezeBackend(),
            FallbackFreezeBackend(appContext.applicationContext)
        )
    )

    @Volatile private var resolved: FreezeBackend? = null

    suspend fun detect(): FreezeBackend {
        resolved?.let { return it }
        for (backend in candidates) {
            try {
                if (backend.isReady()) {
                    Log.i(TAG, "Resolved backend: ${backend.name} (priority=${backend.priority})")
                    resolved = backend
                    return backend
                }
            } catch (t: Throwable) {
                Log.w(TAG, "Backend ${backend.name} threw: ${t.message}")
            }
        }
        val fb = candidates.last()
        resolved = fb
        return fb
    }

    fun invalidate() {
        resolved = null
    }

    companion object {
        private const val TAG = "ApexCore.Freeze"
    }
}
