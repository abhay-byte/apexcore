package com.ivarna.apexcore.fps.source

import com.ivarna.apexcore.fps.model.FpsMethod
import com.ivarna.apexcore.fps.model.FpsSnapshot
import com.ivarna.apexcore.fps.util.ForegroundAppResolver
import com.ivarna.apexcore.fps.privilege.ShellGateway
import com.ivarna.apexcore.fps.privilege.PrivilegeTier
import com.ivarna.apexcore.fps.privilege.PrivilegePolicy

class SurfaceFlingerFpsDataSource(
    private val shellGateway: ShellGateway,
    private val foregroundAppResolver: ForegroundAppResolver
) : FpsDataSource {

    override val priority: Int = 2

    data class CachedSurface(
        val packageName: String,
        val layerName: String,
        val resolvedAtMs: Long
    )

    // Surface name cache: reuse while it continues working, invalidate on failures/TTL/package change
    private var cachedSurface: CachedSurface? = null
    private var surfaceCacheAtElapsedMs: Long = 0L
    private val surfaceCacheTtlMs = 30_000L // longer TTL, invalidate on failure not time alone

    // Also cache --list output for brief window to avoid double dumpsys when findSurface fails
    private var listCacheOutput: String? = null
    private var listCacheAtElapsedMs: Long = 0L
    private val listCacheTtlMs = 5000L

    private var consecutiveEmptyLatency = 0
    private var consecutiveLatencyFailure = 0
    private val maxEmptyBeforeInvalidate = 3
    private val maxFailureBeforeInvalidate = 2

    /** Fail-closed: clear SF caches when privilege mode changes */
    fun clearCache() {
        cachedSurface = null
        surfaceCacheAtElapsedMs = 0L
        listCacheOutput = null
        listCacheAtElapsedMs = 0L
        consecutiveEmptyLatency = 0
        consecutiveLatencyFailure = 0
    }

    override suspend fun readFps(): FpsSnapshot? {
        val foreground = foregroundAppResolver.resolve() ?: return null
        val pkg = foreground.packageName
        // Try cached surface first if still valid
        val cached = cachedSurface?.takeIf { it.packageName == pkg && android.os.SystemClock.elapsedRealtime() - surfaceCacheAtElapsedMs < surfaceCacheTtlMs }?.layerName
        val candidates = if (cached != null) {
            listOf(cached) + findCandidateLayers(pkg).filter { it != cached }
        } else {
            findCandidateLayers(pkg)
        }
        if (candidates.isEmpty()) {
            consecutiveLatencyFailure++
            if (consecutiveLatencyFailure >= maxFailureBeforeInvalidate) clearCache()
            return null
        }
        var lastTier: PrivilegeTier? = null
        for (surface in candidates) {
            val (latencyResult, tier) = shellGateway.executeChain(
                "dumpsys SurfaceFlinger --latency \"$surface\" 2>/dev/null",
                shellGateway.currentPolicy().chain(PrivilegePolicy.DEFAULT_CHAIN)
            )
            lastTier = tier
            if (!latencyResult.isSuccess) continue
            if (latencyResult.output.isBlank()) continue

            val snapshot = parseLatency(
                output = latencyResult.output,
                accessTier = tier,
                packageName = pkg,
                surfaceName = surface,
                refreshHintHz = foreground.refreshRateHz
            )
            if (snapshot != null) {
                // Cache the successful layer
                cachedSurface = CachedSurface(pkg, surface, android.os.SystemClock.elapsedRealtime())
                surfaceCacheAtElapsedMs = android.os.SystemClock.elapsedRealtime()
                consecutiveEmptyLatency = 0
                consecutiveLatencyFailure = 0
                return snapshot
            }
        }
        // All candidates failed to produce valid present timestamps
        consecutiveEmptyLatency++
        if (consecutiveEmptyLatency >= maxEmptyBeforeInvalidate) cachedSurface = null
        // Also consider latency failure if no success and tier was null? Keep cached stale.
        return null
    }

    private fun findCandidateLayers(packageName: String): List<String> {
        val now = android.os.SystemClock.elapsedRealtime()
        val listOutput: String
        if (listCacheOutput != null && now - listCacheAtElapsedMs < listCacheTtlMs) {
            listOutput = listCacheOutput!!
        } else {
            val (listResult, _) = shellGateway.executeChain(
                "dumpsys SurfaceFlinger --list 2>/dev/null",
                shellGateway.currentPolicy().chain(PrivilegePolicy.DEFAULT_CHAIN)
            )
            if (!listResult.isSuccess) return emptyList()
            listOutput = listResult.output
            listCacheOutput = listOutput
            listCacheAtElapsedMs = now
        }
        if (listOutput.isBlank()) return emptyList()

        val shortPkg = packageName.substringAfterLast('.')
        val owned = listOutput.lineSequence()
            .map { it.trim() }
            .mapNotNull { parseLayerName(it) }
            .filter { it.contains(packageName) || (shortPkg.length >= 4 && it.contains(shortPkg)) }
            .filter { !it.contains("ActivityRecord") && !it.contains("InputSink") }
            .toList()
        if (owned.isEmpty()) return emptyList()

        // Prefer game/render surfaces over Activity chrome layers.
        val preferred = owned.firstOrNull { line ->
            listOf("SurfaceView", "NativeActivity", "Vulkan", "BLAST", "GLSurfaceView")
                .any { marker -> line.contains(marker, ignoreCase = true) }
        }
        // Order: preferred first, then remaining with "#", then rest
        val withHash = owned.filter { it.contains("#") }
        val withoutHash = owned.filter { !it.contains("#") }
        val ordered = mutableListOf<String>()
        preferred?.let { ordered.add(it) }
        for (c in withHash) if (c != preferred && c !in ordered) ordered.add(c)
        for (c in withoutHash) if (c !in ordered) ordered.add(c)
        // Fallback to at least preferred or first
        if (ordered.isEmpty() && owned.isNotEmpty()) ordered.addAll(owned)
        return ordered
    }

    private fun findSurfaceForPackage(packageName: String): String? {
        val candidates = findCandidateLayers(packageName)
        val chosen = candidates.firstOrNull() ?: return null
        val now = android.os.SystemClock.elapsedRealtime()
        // Cache for backward compat (tests may call this)
        cachedSurface = CachedSurface(packageName, chosen, now)
        surfaceCacheAtElapsedMs = now
        return chosen
    }

    /**
     * Android 15+ --list emits `RequestedLayerState{name#id parentId=...}`.
     * --latency needs the bare layer name (optionally with #id).
     */
    internal fun parseLayerName(rawLine: String): String? {
        val trimmed = rawLine.trim()
        if (trimmed.isEmpty()) return null
        val brace = Regex("""RequestedLayerState\{([^}]+)\}""").find(trimmed)
        val body = brace?.groupValues?.get(1) ?: trimmed
        // Drop leading hex handle: "3fa18c4 com.pkg/...#1183 parentId=..."
        val withoutHandle = body.replace(Regex("""^[0-9a-fA-F]+\s+"""), "")
        // Keep up through #id, drop parentId/z suffixes
        val name = withoutHandle
            .replace(Regex("""\s+parentId=.*$"""), "")
            .replace(Regex("""\s+z=.*$"""), "")
            .replace(Regex("""\s+relativeParentId=.*$"""), "")
            .trim()
        return name.takeIf { it.isNotEmpty() }
    }

    internal fun parseLatency(
        output: String,
        accessTier: PrivilegeTier? = null,
        packageName: String? = null,
        surfaceName: String? = null,
        refreshHintHz: Float = 60f
    ): FpsSnapshot? {
        val lines = output.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toList()
        if (lines.size < 2) return null // fail-closed: header only is not a measurement

        val refreshPeriodNs = lines[0].toLongOrNull() ?: return null
        if (refreshPeriodNs <= 0L || refreshPeriodNs == Long.MAX_VALUE) return null
        // Sanity: refresh period should be between 1ms (1000000ns ~1000Hz) and 100ms (10Hz)
        if (refreshPeriodNs < 1_000_000L || refreshPeriodNs > 100_000_000L) return null

        val presentTimestampsNs = mutableListOf<Long>()

        for (line in lines.drop(1)) {
            val parts = line.split(Regex("\\s+")).filter { it.isNotBlank() }
            if (parts.size < 3) continue
            val desiredNs = parts[0].toLongOrNull() ?: continue
            val actualNs = parts[1].toLongOrNull() ?: continue
            val readyNs = parts[2].toLongOrNull() ?: continue

            // Filter invalid/pending entries (SF uses 0 or LONG_MAX for not-yet-queued fence)
            // Desired 0 means unused slot, actual 0 or MAX means fence pending, skip row entirely
            if (desiredNs == 0L || actualNs == 0L || readyNs == 0L) continue
            if (desiredNs == Long.MAX_VALUE || actualNs == Long.MAX_VALUE || readyNs == Long.MAX_VALUE) continue
            if (desiredNs < 0L || actualNs < 0L || readyNs < 0L) continue
            // Extreme values / sentinel beyond plausible boottime (e.g., > 10 years ~3e17 ns)
            // Long.MAX_VALUE is 9.22e18, but 1e18 already impossible for boottime
            if (actualNs > 9_000_000_000_000_000L) continue
            if (desiredNs > 9_000_000_000_000_000L) continue
            // Additional guard: actual must be >0 and not obviously bogus
            if (actualNs == Long.MIN_VALUE) continue

            // Valid actualPresentTime is the presentation timestamp we care about.
            // Use column 1 (middle) = actualPresentTime per Chromium collector and FrameTracker dumpStats
            presentTimestampsNs.add(actualNs)
        }

        if (presentTimestampsNs.size < 2) return null

        // Derive presentation cadence from consecutive actualPresent differences
        val intervalsNs = presentTimestampsNs.zipWithNext { prev, next -> next - prev }
            .filter { it > 0L }

        if (intervalsNs.isEmpty()) return null

        // Filter extreme intervals outside realistic display/game range
        // Jank will be calculated from all intervals, but FPS average should ignore idle gaps
        val deltasNsFiltered = intervalsNs.filter { deltaNs ->
            // Convert to ms for range check: 1ms (1000Hz) to 500ms (2 FPS) plausible
            // Use ns directly: 1_000_000 to 500_000_000
            deltaNs in 1_000_000L..500_000_000L
        }
        // If all intervals were idle gaps (>500ms), treat as no valid presentation
        val effectiveIntervals = if (deltasNsFiltered.isNotEmpty()) deltasNsFiltered else return null

        // Recent window for current-FPS responsiveness (separate from long-window stats)
        val recentIntervalsNs = if (effectiveIntervals.size > RECENT_FRAME_WINDOW) {
            effectiveIntervals.takeLast(RECENT_FRAME_WINDOW)
        } else effectiveIntervals

        if (recentIntervalsNs.size < 2) {
            // Need at least 2 intervals (3 presents) for stable FPS, but allow 1 for transition detection
            // If only 1 interval, still compute but caller may hold lastGood if too sparse
            if (recentIntervalsNs.isEmpty()) return null
        }

        // Require at least 2 present timestamps contributed to recent window for confidence
        // But don't fail entirely if only 1 interval - return null to signal unavailable
        if (recentIntervalsNs.size < 1) return null

        // Current FPS via recent window: two equivalent formulations
        // 1) 1e9 / avg(intervalNs), 2) 1e9 * count / (last - first)
        // Use average for stability; verify both give same for periodic timeline in tests
        val avgIntervalNs = recentIntervalsNs.average()
        if (avgIntervalNs <= 0.0 || avgIntervalNs.isNaN() || avgIntervalNs.isInfinite()) return null
        if (avgIntervalNs > 500_000_000.0) return null
        val avgMs = (avgIntervalNs / 1_000_000.0).toFloat()
        if (avgMs <= 0f || avgMs > 500f) return null
        var fps = (1_000_000_000.0 / avgIntervalNs).toFloat()

        // Alternative calc for verification (not used for final, but must match stable timeline)
        // For stable timeline, 1e9*count/(last-first) should equal 1000/avgMs

        // Jank: missed presentation periods relative to refresh period
        var jankCount = 0
        var totalMissed = 0
        for (deltaNs in intervalsNs) {
            // Ignore tiny jitter within half-period tolerance
            val periods = ((deltaNs + refreshPeriodNs / 2) / refreshPeriodNs).coerceAtLeast(1L)
            val missed = (periods - 1).coerceAtLeast(0L).toInt()
            totalMissed += missed
            // Also count intervals that exceed 1.5*refresh as janky? But spec says aggregate missed
        }
        jankCount = totalMissed

        // Clamp to display refresh ceiling with gentle grace (240 cap) but never exceed hint * 1.05
        val refreshCeiling = refreshHintHz.coerceIn(1f, 240f)
        fps = fps.coerceIn(1f, 240f).coerceAtMost(refreshCeiling * 1.05f)
        // Snap to refresh when very close (within 10%) and low variance to avoid 119.3 vs 120 jitter
        val expectedVsyncMs = 1000f / refreshCeiling
        if (avgMs in expectedVsyncMs * 0.90f..expectedVsyncMs * 1.10f) {
            val variance = recentIntervalsNs.map { (it / 1_000_000.0 - avgMs) * (it / 1_000_000.0 - avgMs) }.average()
            if (variance < 9.0) {
                fps = refreshCeiling
            }
        }

        if (fps.isNaN() || fps.isInfinite() || fps <= 0f || fps > 240f) return null

        // Preserve histogram for percentile buffer: use deltas in ms, filter to 1..200 ms
        val histogramForBuffer = effectiveIntervals.map { it / 1_000_000f }.filter { it in 1f..200f }

        val elapsedNow = android.os.SystemClock.elapsedRealtime()
        val diagnostics = buildString {
            append("SF pkg=$packageName surface=${surfaceName?.take(60)}")
            append(" presents=${presentTimestampsNs.size} intervals=${intervalsNs.size} recent=${recentIntervalsNs.size}")
            append(" avgMs=${"%.2f".format(avgMs)} fpsRaw=${"%.1f".format(1000f / avgMs)} fps=${"%.1f".format(fps)}")
            append(" refresh=${refreshCeiling.toInt()}Hz")
            if (jankCount > 0) append(" missed=$jankCount")
            append(" tier=${accessTier?.name ?: "null"}")
        }
        val sourceDetail = "SF:$surfaceName"

        return FpsSnapshot(
            currentFps = fps,
            frametimeAvgMs = avgMs,
            frametimeP1Ms = 0f,
            frametimeP01Ms = 0f,
            frametimeHistogram = histogramForBuffer,
            jankCount = jankCount,
            method = FpsMethod.SURFACEFLINGER,
            accessTier = accessTier,
            packageName = packageName,
            surfaceName = surfaceName,
            timestampMs = System.currentTimeMillis(),
            measuredAtElapsedMs = elapsedNow,
            diagnostics = diagnostics,
            sourceDetail = sourceDetail,
            isStale = false,
            frameCount = recentIntervalsNs.size
        )
    }

    // Legacy overload for tests that call parseLatency(output) without provenance args
    internal fun parseLatency(output: String): FpsSnapshot? = parseLatency(output, null, null, null, 60f)

    private companion object {
        /** ~0.5s at 60Hz / ~0.25s at 120Hz — responsive without full-ring noise. */
        const val RECENT_FRAME_WINDOW = 32
    }
}
