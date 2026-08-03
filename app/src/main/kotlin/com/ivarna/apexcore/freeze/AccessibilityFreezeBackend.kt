package com.ivarna.apexcore.freeze

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Accessibility stub — NOT ship-ready (T10c Decision 2).
 *
 * No accessibility service is declared in the manifest and this backend is
 * excluded from [FreezeBackendResolver] product candidates. [isReady] is hard
 * `false` so it can never surface as a working freeze path, and the stub is
 * never claimed in UI, listing, or privacy policy.
 */
class AccessibilityFreezeBackend : FreezeBackend {
    override val name = "Accessibility"
    override val priority = 2

    override suspend fun isReady(): Boolean = withContext(Dispatchers.IO) {
        false
    }

    override suspend fun execute(op: FreezeOperation): FreezeOperation.Result {
        Log.d(TAG, "Accessibility execute ${op.name} ${op.pkg} not implemented (not ship-ready, T10c)")
        return FreezeOperation.Result.SKIPPED_A11Y
    }

    companion object {
        private const val TAG = "ApexCore.Freeze"
    }
}
