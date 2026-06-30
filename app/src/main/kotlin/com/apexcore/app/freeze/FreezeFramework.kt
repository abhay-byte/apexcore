package com.apexcore.app.freeze

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

object FreezeFramework {

    private const val TAG = "ApexCore.Freeze"

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

    suspend fun detect(): FreezeBackend {
        val r = resolver ?: error("FreezeFramework.init() not called")
        val b = r.detect()
        _activeBackend.value = b
        return b
    }

    suspend fun isReady(): Boolean = try {
        detect().name.isNotEmpty()
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

        val beforeMem = readMemAvailKb()
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
                    if (res.reason.contains("not implemented", ignoreCase = true)) {
                        skipped++
                    } else {
                        failed++
                    }
                }
            }
        }

        if (backend is FallbackFreezeBackend) {
            Thread.sleep(800)
        }

        val afterMem = readMemAvailKb()
        val duration = System.currentTimeMillis() - start

        val result = FreezeResult(
            killed = killed,
            failed = failed,
            skipped = skipped,
            durationMs = duration,
            backend = backend.name,
            beforeAvailMb = beforeMem / 1024,
            afterAvailMb = afterMem / 1024
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

    private fun readMemAvailKb(): Long {
        return try {
            java.io.File("/proc/meminfo").useLines { lines ->
                for (line in lines) {
                    if (line.startsWith("MemAvailable:")) {
                        return line.split(Regex("\\s+"))[1].toLongOrNull() ?: 0L
                    }
                }
                0L
            }
        } catch (_: Throwable) { 0L }
    }
}
