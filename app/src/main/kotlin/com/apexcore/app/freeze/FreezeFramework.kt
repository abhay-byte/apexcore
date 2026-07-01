package com.apexcore.app.freeze

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

object FreezeFramework {

    private const val TAG = "ApexCore.Freeze"
    private const val FALLBACK_DELAY_MS = 800L

    private var resolver: FreezeBackendResolver? = null

    private val _activeBackend = MutableStateFlow<FreezeBackend?>(null)
    val activeBackend: StateFlow<FreezeBackend?> = _activeBackend.asStateFlow()

    private val _lastResult = MutableStateFlow<FreezeResult?>(null)
    val lastResult: StateFlow<FreezeResult?> = _lastResult.asStateFlow()

    fun init(context: Context) {
        if (resolver == null) {
            val appCtx = context.applicationContext
            resolver = FreezeBackendResolver(appCtx)
            ShizukuFreezeBackend.currentContext = appCtx
        }
    }

    fun resolver(): FreezeBackendResolver? = resolver

    suspend fun detect(): FreezeBackend {
        val r = resolver ?: error("FreezeFramework.init() not called")
        val b = r.detect()
        _activeBackend.value = b
        return b
    }

    suspend fun isReady(): Boolean = try {
        detect().priority < FallbackFreezeBackend.PRIORITY
    } catch (_: Throwable) { false }

    suspend fun freezeAll(
        context: Context,
        filter: (ApplicationInfo) -> Boolean = { FreezeFilter.default(context, it) }
    ): FreezeResult = withContext(Dispatchers.IO) {
        val backend = detect()
        val pm = context.packageManager
        val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        val targets = apps.filter(filter)
        Log.i(TAG, "freezeAll via ${backend.name} -> ${targets.size} apps")

        val (totalMemKb, beforeMemKb) = readMemInfo()
        val start = System.currentTimeMillis()

        var killed = 0
        var failed = 0
        var skipped = 0

        for (app in targets) {
            val res = try { backend.execute(FreezeOperation.ForceStop(app.packageName)) }
                catch (t: Throwable) {
                    FreezeOperation.Result.Failure(t.message ?: "threw")
                }
            when (res) {
                is FreezeOperation.Result.Success -> killed++
                is FreezeOperation.Result.Failure -> {
                    if (res.isSkipped) skipped++ else failed++
                }
            }
        }

        if (backend is FallbackFreezeBackend) {
            delay(FALLBACK_DELAY_MS)
        }

        val (_, afterMemKb) = readMemInfo()
        val duration = System.currentTimeMillis() - start

        val result = FreezeResult(
            killed = killed,
            failed = failed,
            skipped = skipped,
            durationMs = duration,
            backend = backend.name,
            totalMemMb = totalMemKb / 1024,
            beforeAvailMb = beforeMemKb / 1024,
            afterAvailMb = afterMemKb / 1024,
            swapTotalMb = readMemLine("SwapTotal:") / 1024,
            swapFreeMb = readMemLine("SwapFree:") / 1024
        )
        _lastResult.value = result
        Log.i(TAG, "freezeAll done: $result")
        result
    }

    suspend fun forceStopOne(context: Context, pkg: String): FreezeOperation.Result {
        val backend = detect()
        return try {
            backend.execute(FreezeOperation.ForceStop(pkg))
        } catch (t: Throwable) {
            FreezeOperation.Result.Failure(t.message ?: "threw")
        }
    }

    /** Returns (MemTotalKb, MemAvailableKb). */
    private fun readMemInfo(): Pair<Long, Long> {
        var total = 0L; var avail = 0L
        try {
            java.io.File("/proc/meminfo").useLines { lines ->
                for (line in lines) {
                    when {
                        line.startsWith("MemTotal:") -> total = parseKb(line)
                        line.startsWith("MemAvailable:") -> avail = parseKb(line)
                    }
                }
            }
        } catch (_: Throwable) {}
        return total to avail
    }

    private fun readMemLine(prefix: String): Long = try {
        java.io.File("/proc/meminfo").useLines { lines ->
            for (line in lines) if (line.startsWith(prefix)) return parseKb(line)
            0L
        }
    } catch (_: Throwable) { 0L }

    private fun parseKb(line: String): Long =
        line.split(Regex("\\s+"))[1].toLongOrNull() ?: 0L
}
