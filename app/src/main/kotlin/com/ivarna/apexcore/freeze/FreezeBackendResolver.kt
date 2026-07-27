package com.ivarna.apexcore.freeze

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
    @Volatile private var preferredBackendName: String? = null

    fun setPreferredBackend(name: String?) {
        if (name != preferredBackendName) {
            preferredBackendName = name
            invalidate()
        }
    }

    suspend fun detect(): FreezeBackend {
        resolved?.let { return it }

        // If user has a preference, try that backend first
        if (preferredBackendName != null) {
            val pref = preferredBackendName
            val candidate = candidates.find { it.name.equals(pref, ignoreCase = true) }
            if (candidate != null) {
                try {
                    if (candidate.isReady()) {
                        Log.i(TAG, "Resolved preferred backend: ${candidate.name} (priority=${candidate.priority})")
                        resolved = candidate
                        return candidate
                    }
                } catch (t: Throwable) {
                    Log.w(TAG, "Preferred backend ${candidate.name} threw: ${t.message}")
                }
            }
            // Preferred not ready — fall through to auto-detect
            Log.i(TAG, "Preferred backend '$pref' not ready, falling back to auto-detect")
        }

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
