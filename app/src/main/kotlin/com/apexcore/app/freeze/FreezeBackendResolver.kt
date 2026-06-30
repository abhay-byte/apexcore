package com.apexcore.app.freeze

import android.content.Context
import android.util.Log

class FreezeBackendResolver(appContext: Context) {

    private val shizuku = ShizukuFreezeBackend()
    private val root = RootFreezeBackend()
    private val accessibility = AccessibilityFreezeBackend()
    private val fallback = FallbackFreezeBackend(appContext.applicationContext)

    private val candidates: List<FreezeBackend> = listOf(shizuku, root, accessibility, fallback)

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
        resolved = fallback
        return fallback
    }

    fun backendByName(name: String): FreezeBackend? = when (name) {
        "Shizuku" -> shizuku
        "Root" -> root
        "Accessibility" -> accessibility
        "cached only" -> fallback
        else -> null
    }

    fun invalidate() {
        resolved = null
    }

    companion object {
        private const val TAG = "ApexCore.Freeze"
    }
}
