package com.ivarna.apexcore.freeze

import android.content.Context
import android.util.Log

/**
 * Resolves the active freeze backend for product freeze features.
 *
 * Decision E: there is no product "standard" freeze mode. Only elevated backends
 * (Shizuku / Root) can force-stop apps. [detect] returns null when no elevated
 * backend is ready — callers must gate freeze (UI setup) instead of running a
 * useless skip-all path. [FallbackFreezeBackend] and [AccessibilityFreezeBackend]
 * are intentionally NOT candidates (Accessibility is not ship-ready, T10c).
 */
class FreezeBackendResolver(candidates: List<FreezeBackend>) {

    internal val candidates: List<FreezeBackend> = candidates

    constructor(appContext: Context) : this(
        listOf(
            ShizukuFreezeBackend(),
            RootFreezeBackend()
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

    /**
     * Returns the preferred elevated backend if ready, otherwise the first other
     * elevated backend that is ready, otherwise null (not-ready → setup gate).
     * Never returns a non-elevated fallback ("standard") or the Accessibility stub.
     */
    suspend fun detect(): FreezeBackend? {
        resolved?.let { return it }

        // If user has a preference, try that backend first
        if (preferredBackendName != null) {
            val pref = preferredBackendName
            val candidate = candidates.find { it.name.equals(pref, ignoreCase = true) && isElevated(it) }
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
            if (!isElevated(backend)) continue
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
        Log.i(TAG, "No elevated backend ready — freeze gated (Shizuku/Root setup required)")
        return null
    }

    /** Only Shizuku / Root can force-stop — Decision E excludes every other backend. */
    private fun isElevated(backend: FreezeBackend): Boolean =
        backend.name == "Shizuku" || backend.name == "Root"

    fun invalidate() {
        resolved = null
        candidates.forEach { it.invalidate() }
    }

    companion object {
        private const val TAG = "ApexCore.Freeze"
    }
}
