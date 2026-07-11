package com.ivarna.apexcore.freeze

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
    private const val KILL_DELAY_MS = 1200L
    private const val ESTIMATED_KB_PER_APP = 40_000L

    private var resolver: FreezeBackendResolver? = null

    private val _activeBackend = MutableStateFlow<FreezeBackend?>(null)
    val activeBackend: StateFlow<FreezeBackend?> = _activeBackend.asStateFlow()

    private val _lastResult = MutableStateFlow<FreezeResult?>(null)
    val lastResult: StateFlow<FreezeResult?> = _lastResult.asStateFlow()

    fun init(context: Context) {
        if (resolver == null) {
            val appCtx = context.applicationContext
            resolver = FreezeBackendResolver(appCtx)
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

        val targetPkgs = targets.map { it.packageName }
        val beforeRssKb = calculateTargetRssKb(backend, targetPkgs)
        Log.i(TAG, "Target apps before RSS sum = ${beforeRssKb}KB")

        val (totalMemKb, beforeMemKb) = readMemInfo()
        val start = System.currentTimeMillis()

        var killed = 0
        var failed = 0
        var skipped = 0

        val allOps = targets.map { FreezeOperation.ForceStop(it.packageName) }
        val allResults = backend.executeMany(allOps)

        for (res in allResults) {
            when (res) {
                is FreezeOperation.Result.Success -> killed++
                is FreezeOperation.Result.Failure -> {
                    if (res.isSkipped) skipped++ else failed++
                }
            }
        }

        delay(KILL_DELAY_MS)

        try {
            if (backend is ShizukuFreezeBackend || backend is RootFreezeBackend) {
                backend.execute(FreezeOperation.ShellCommand("echo 3 > /proc/sys/vm/drop_caches 2>/dev/null"))
            }
        } catch (_: Throwable) {}

        delay(1200)
        val afterAvailKb = readMemLine("MemAvailable:")
        val afterRssKb = calculateTargetRssKb(backend, targetPkgs)

        val freedKbFromMem = (afterAvailKb - beforeMemKb).coerceAtLeast(0)
        val appsFreedKb = (beforeRssKb - afterRssKb).coerceAtLeast(0)
        val freedKb = maxOf(freedKbFromMem, appsFreedKb)
        Log.i(TAG, "beforeAvail=${beforeMemKb}KB afterAvail=${afterAvailKb}KB freedFromMem=${freedKbFromMem}KB beforeRss=${beforeRssKb}KB afterRss=${afterRssKb}KB appsFreed=${appsFreedKb}KB chosen=${freedKb}KB")
        val duration = System.currentTimeMillis() - start

        val result = FreezeResult(
            killed = killed,
            failed = failed,
            skipped = skipped,
            durationMs = duration,
            backend = backend.name,
            totalMemMb = totalMemKb / 1024,
            beforeAvailMb = beforeMemKb / 1024,
            afterAvailMb = afterAvailKb / 1024,
            freedKb = freedKb,
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

    private suspend fun calculateTargetRssKb(backend: FreezeBackend, targetPkgs: List<String>): Long {
        val output = try {
            backend.executeWithOutput("ps -A -o RSS,NAME")
        } catch (_: Throwable) {
            ""
        }
        if (output.isEmpty()) return 0L

        var totalRssKb = 0L
        val pkgSet = targetPkgs.toSet()

        output.lineSequence().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.isEmpty() || !trimmed[0].isDigit()) return@forEach
            val parts = trimmed.split(Regex("\\s+"), limit = 2)
            if (parts.size == 2) {
                val rss = parts[0].toLongOrNull() ?: 0L
                val name = parts[1]
                val basePkg = name.substringBefore(':')
                if (pkgSet.contains(basePkg)) {
                    totalRssKb += rss
                }
            }
        }
        return totalRssKb
    }
}
