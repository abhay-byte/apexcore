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

    /** Test seam: replace the resolver to force a backend state in unit tests. */
    fun setResolverForTest(r: FreezeBackendResolver?) {
        resolver = r
    }

    /** Test seam: explicitly set active backend flow value in unit tests. */
    fun setActiveBackendForTest(b: FreezeBackend?) {
        _activeBackend.value = b
    }

    fun setPreferredBackend(name: String?) {
        resolver?.setPreferredBackend(name)
    }

    suspend fun detect(): FreezeBackend? {
        val r = resolver ?: error("FreezeFramework.init() not called")
        val b = r.detect()
        _activeBackend.value = b
        return b
    }

    suspend fun unfreezeAll(context: Context? = null) {
        Log.i(TAG, "unfreezeAll called")
    }

    suspend fun isReady(): Boolean = try {
        detect() != null
    } catch (_: Throwable) { false }

    /**
     * Force-stop background apps to free RAM.
     *
     * @param protectPackages packages that must never be killed (game, foreground, etc.).
     *   ApexCore itself, pinned apps, pure system apps, and [FreezeFilter.ALWAYS_PROTECT]
     *   are always protected even if a custom [filter] would include them.
     * @param filter additional inclusion predicate; default is [FreezeFilter.default].
     */
    suspend fun freezeAll(
        context: Context,
        protectPackages: Set<String> = emptySet(),
        filter: (ApplicationInfo) -> Boolean = { FreezeFilter.default(context, it) }
    ): FreezeResult = withContext(Dispatchers.IO) {
        val backend = detect()
        if (backend == null) {
            // Decision E safety: never run a skip-all "freeze" without elevation.
            Log.w(TAG, "freezeAll blocked: no elevated backend (Shizuku/Root required)")
            val (totalMemKb, beforeMemKb) = readMemInfo()
            val result = FreezeResult(
                killed = 0,
                failed = 0,
                skipped = 0,
                durationMs = 0,
                backend = "blocked",
                totalMemMb = totalMemKb / 1024,
                beforeAvailMb = beforeMemKb / 1024,
                afterAvailMb = beforeMemKb / 1024,
                swapTotalMb = readMemLine("SwapTotal:") / 1024,
                swapFreeMb = readMemLine("SwapFree:") / 1024
            )
            _lastResult.value = result
            return@withContext result
        }

        val selfPkg = context.packageName
        val protected = buildProtectedSet(selfPkg, protectPackages)
        Log.i(TAG, "freezeAll protect=${protected.joinToString()}")

        val pm = context.packageManager
        val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        // Caller filter + hard protect gate (never force-stop self / game / always-protect)
        val targets = apps.filter { info ->
            val name = info.packageName ?: return@filter false
            if (isProtectedPackage(name, protected)) return@filter false
            filter(info) && FreezeFilter.shouldFreeze(context, info, protected)
        }
        Log.i(TAG, "freezeAll via ${backend.name} -> ${targets.size} apps (protected ${protected.size})")

        val targetPkgs = targets.map { it.packageName }.filter { !isProtectedPackage(it, protected) }
        val beforeRssKb = calculateTargetRssKb(backend, targetPkgs)
        Log.i(TAG, "Target apps before RSS sum = ${beforeRssKb}KB")

        val (totalMemKb, beforeMemKb) = readMemInfo()
        val beforeSwapFreeKb = readMemLine("SwapFree:")
        val start = System.currentTimeMillis()

        var killed = 0
        var failed = 0
        var skipped = 0

        val allOps = targetPkgs.map { FreezeOperation.ForceStop(it) }
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

        delay(1200)
        val afterAvailKb = readMemLine("MemAvailable:")
        val afterSwapFreeKb = readMemLine("SwapFree:")
        val afterRssKb = calculateTargetRssKb(backend, targetPkgs)

        val freedKb = (afterAvailKb - beforeMemKb).coerceAtLeast(0)
        val appsFreedKb = (beforeRssKb - afterRssKb).coerceAtLeast(0)
        val swapFreedKb = (afterSwapFreeKb - beforeSwapFreeKb).coerceAtLeast(0)
        Log.i(TAG, "beforeAvail=${beforeMemKb}KB afterAvail=${afterAvailKb}KB freed=${freedKb}KB beforeRss=${beforeRssKb}KB afterRss=${afterRssKb}KB appsFreed=${appsFreedKb}KB swapFreed=${swapFreedKb}KB")
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
            swapFreeMb = readMemLine("SwapFree:") / 1024,
            swapFreedKb = swapFreedKb
        )
        _lastResult.value = result
        Log.i(TAG, "freezeAll done: $result")
        result
    }

    suspend fun forceStopOne(context: Context, pkg: String): FreezeOperation.Result {
        val protected = buildProtectedSet(context.packageName, emptySet())
        if (isProtectedPackage(pkg, protected)) {
            Log.w(TAG, "forceStopOne refused protected package: $pkg")
            return FreezeOperation.Result.Failure("protected-package")
        }
        val backend = detect() ?: return FreezeOperation.Result.Failure("no-elevated-backend")
        return try {
            backend.execute(FreezeOperation.ForceStop(pkg))
        } catch (t: Throwable) {
            FreezeOperation.Result.Failure(t.message ?: "threw")
        }
    }

    private fun buildProtectedSet(selfPkg: String, extra: Set<String>): Set<String> {
        val out = LinkedHashSet<String>()
        out.add(selfPkg)
        out.addAll(FreezeFilter.ALWAYS_PROTECT)
        for (p in extra) {
            val trimmed = p.trim()
            if (trimmed.isNotEmpty()) {
                out.add(trimmed)
                out.add(trimmed.substringBefore(':'))
            }
        }
        return out
    }

    private fun isProtectedPackage(pkg: String, protected: Set<String>): Boolean {
        if (pkg in protected) return true
        val base = pkg.substringBefore(':')
        return base in protected
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
