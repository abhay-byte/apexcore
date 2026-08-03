package com.ivarna.apexcore.fps.source

import com.ivarna.apexcore.fps.model.FpsMethod
import com.ivarna.apexcore.fps.model.FpsSnapshot
import com.ivarna.apexcore.fps.model.FrametimeBuffer
import com.ivarna.apexcore.fps.model.PercentileResult
import com.ivarna.apexcore.fps.util.ForegroundAppResolver
import com.ivarna.apexcore.fps.privilege.ShellGateway
import com.ivarna.apexcore.fps.privilege.PrivilegeTier

/**
 * UI-only FPS via gfxinfo framestats. Inaccurate for Vulkan/SurfaceView games.
 * Last resort before giving up on external-app measurement.
 */
class GfxinfoFpsDataSource(
    private val shellGateway: ShellGateway,
    private val foregroundAppResolver: ForegroundAppResolver
) : FpsDataSource {

    override val priority: Int = 3

    private var lastFrameCompletedNs: Long? = null
    private var lastPollTimeMs: Long = 0L
    private var lastPackage: String? = null
    private var profileBootstrapped = false

    private companion object {
        const val MAX_PLAUSIBLE_FRAMETIME_MS = 100f
        private const val BOOTSTRAP_MAX_FRAMES = 90
    }

    override suspend fun readFps(): FpsSnapshot? {
        val foreground = foregroundAppResolver.resolve() ?: return null
        if (foreground.packageName != lastPackage) {
            lastFrameCompletedNs = null
            lastPollTimeMs = 0L
            lastPackage = foreground.packageName
            profileBootstrapped = false
        }

        val result = shellGateway.executeChain(
            "dumpsys gfxinfo ${foreground.packageName} framestats 2>/dev/null",
            shellGateway.currentPolicy().chain(listOf(PrivilegeTier.ROOT, PrivilegeTier.SHIZUKU, PrivilegeTier.STANDARD))
        ).first
        if (!result.isSuccess || result.output.isBlank()) return null

        return parseGfxinfo(result.output, System.currentTimeMillis(), foreground.refreshRateHz)
    }

    internal fun parseGfxinfo(output: String, nowMs: Long, refreshRateHz: Float = 60f): FpsSnapshot? {
        var frametimesMs = parseFrameCompletedTimestamps(output)
        var usedBootstrap = false
        if (frametimesMs.isEmpty() && !profileBootstrapped) {
            frametimesMs = parseProfileBootstrap(output)
            profileBootstrapped = true
            usedBootstrap = frametimesMs.isNotEmpty()
        }

        if (frametimesMs.isNotEmpty()) {
            val avgMs = frametimesMs.average().toFloat()
            val pollDeltaSec = if (lastPollTimeMs > 0) (nowMs - lastPollTimeMs) / 1000f else 0f
            lastPollTimeMs = nowMs
            val fps = fpsFromFrametimes(frametimesMs, avgMs, pollDeltaSec, refreshRateHz)
            val percentiles = computeFrametimePercentiles(frametimesMs)
            val (p1Ms, p01Ms) = resolvePercentiles(output, percentiles)
            return FpsSnapshot(
                currentFps = fps.coerceIn(1f, 240f),
                frametimeAvgMs = avgMs,
                frametimeP1Ms = p1Ms,
                frametimeP01Ms = p01Ms,
                frametimeHistogram = frametimesMs,
                jankCount = 0,
                method = FpsMethod.GFXINFO
            )
        }

        val uiHistogram = parseUiHistogram(output)
        if (uiHistogram.isNotEmpty()) {
            val histogramFps = fpsFromHistogram(uiHistogram)
            if (histogramFps <= 0f) return null
            val avgMs = 1000f / histogramFps
            val percentiles = histogramPercentiles(uiHistogram)
            return FpsSnapshot(
                currentFps = histogramFps,
                frametimeAvgMs = avgMs,
                frametimeP1Ms = percentiles.p1FrametimeMs,
                frametimeP01Ms = percentiles.p01FrametimeMs,
                frametimeHistogram = emptyList(),
                jankCount = 0,
                method = FpsMethod.GFXINFO
            )
        }

        val histogramFps = parseGpuHistogram(output)
        if (histogramFps <= 0f) return null
        val ftMs = 1000f / histogramFps
        return FpsSnapshot(
            currentFps = histogramFps,
            frametimeAvgMs = ftMs,
            frametimeP1Ms = parseReportedPercentile(output, 99),
            frametimeP01Ms = parseReportedPercentile(output, 99),
            frametimeHistogram = emptyList(),
            jankCount = 0,
            method = FpsMethod.GFXINFO
        )
    }

    private fun parseFrameCompletedTimestamps(output: String): List<Float> {
        val frametimes = mutableListOf<Float>()
        var inStats = false
        var frameCompletedIdx = -1

        for (line in output.lineSequence()) {
            val trimmed = line.trim()
            if (trimmed.startsWith("---PROFILEDATA---")) {
                inStats = true
                continue
            }
            if (!inStats) continue
            if (trimmed.startsWith("---")) break
            if (trimmed.startsWith("Flags,")) {
                val headers = trimmed.split(',').map { it.trim() }
                frameCompletedIdx = headers.indexOf("FrameCompleted")
                continue
            }
            if (frameCompletedIdx < 0) continue

            val parts = trimmed.split(',').map { it.trim() }
            if (parts.size <= frameCompletedIdx) continue
            val frameCompleted = parts[frameCompletedIdx].toLongOrNull() ?: continue
            val prev = lastFrameCompletedNs
            if (prev != null && frameCompleted > prev) {
                val deltaMs = (frameCompleted - prev) / 1_000_000f
                if (isPlausibleFrametime(deltaMs)) {
                    frametimes.add(deltaMs)
                }
            }
            lastFrameCompletedNs = frameCompleted
        }
        return frametimes
    }

    private fun parseProfileBootstrap(output: String): List<Float> {
        val frametimes = mutableListOf<Float>()
        var inStats = false
        var frameCompletedIdx = -1
        var prevCompleted: Long? = null

        for (line in output.lineSequence()) {
            val trimmed = line.trim()
            if (trimmed.startsWith("---PROFILEDATA---")) {
                inStats = true
                frameCompletedIdx = -1
                continue
            }
            if (!inStats) continue
            if (trimmed.startsWith("---")) break
            if (trimmed.startsWith("Flags,")) {
                val headers = trimmed.split(',').map { it.trim() }
                frameCompletedIdx = headers.indexOf("FrameCompleted")
                continue
            }
            if (frameCompletedIdx < 0) continue

            val parts = trimmed.split(',').map { it.trim() }
            if (parts.size <= frameCompletedIdx) continue
            val frameCompleted = parts[frameCompletedIdx].toLongOrNull() ?: continue
            val prev = prevCompleted
            if (prev != null && frameCompleted > prev) {
                val deltaMs = (frameCompleted - prev) / 1_000_000f
                if (isPlausibleFrametime(deltaMs)) {
                    frametimes.add(deltaMs)
                }
            }
            prevCompleted = frameCompleted
        }
        return frametimes.takeLast(BOOTSTRAP_MAX_FRAMES)
    }

    private fun isPlausibleFrametime(deltaMs: Float): Boolean =
        deltaMs > 0f && deltaMs <= MAX_PLAUSIBLE_FRAMETIME_MS

    private fun fpsFromFrametimes(
        frametimesMs: List<Float>,
        avgMs: Float,
        pollDeltaSec: Float,
        refreshRateHz: Float
    ): Float {
        val refreshCeiling = refreshRateHz.coerceIn(1f, 240f)
        if (frametimesMs.size >= 2 && avgMs > 0f) {
            val ftFps = (1000f / avgMs).coerceIn(1f, 240f)
            val expectedMs = 1000f / refreshCeiling
            if (avgMs in expectedMs * 0.82f..expectedMs * 1.18f) {
                return refreshCeiling
            }
            return ftFps.coerceAtMost(refreshCeiling)
        }
        if (pollDeltaSec > 0f && frametimesMs.isNotEmpty()) {
            return (frametimesMs.size / pollDeltaSec).coerceIn(1f, refreshCeiling)
        }
        return (1000f / avgMs).coerceIn(1f, refreshCeiling)
    }

    private fun parseUiHistogram(output: String): Map<Int, Int> {
        val histogramLine = output.lineSequence()
            .filter { line ->
                val trimmed = line.trim()
                trimmed.startsWith("HISTOGRAM:") && !trimmed.startsWith("GPU HISTOGRAM:")
            }
            .lastOrNull()
            ?: return emptyMap()

        val buckets = linkedMapOf<Int, Int>()
        val entries = histogramLine.substringAfter("HISTOGRAM:").trim().split(Regex("\\s+"))
        for (entry in entries) {
            val match = Regex("""([\d.]+)ms=(\d+)""").find(entry) ?: continue
            val ms = match.groupValues[1].toDoubleOrNull()?.toInt() ?: continue
            val count = match.groupValues[2].toIntOrNull() ?: continue
            if (ms >= 4950 || count <= 0) continue
            buckets[ms] = count
        }
        return buckets
    }

    private fun fpsFromHistogram(buckets: Map<Int, Int>): Float {
        var totalFrames = 0
        var weightedMs = 0.0
        for ((ms, count) in buckets) {
            totalFrames += count
            weightedMs += ms * count
        }
        if (totalFrames == 0) return 0f
        val avgMs = weightedMs / totalFrames
        return if (avgMs > 0) (1000.0 / avgMs).toFloat() else 0f
    }

    private fun histogramPercentiles(buckets: Map<Int, Int>): PercentileResult {
        val sorted = buckets.entries.sortedBy { it.key }
        val totalFrames = sorted.sumOf { it.value }
        if (totalFrames == 0) return PercentileResult(0f, 0f, 0f)

        fun percentileFrameTime(fraction: Double): Float {
            val target = (totalFrames * fraction).coerceAtLeast(1.0)
            var seen = 0
            for ((ms, count) in sorted.asReversed()) {
                seen += count
                if (seen >= target) return ms.toFloat()
            }
            return sorted.last().key.toFloat()
        }

        val avgMs = sorted.sumOf { it.key * it.value }.toDouble() / totalFrames
        return PercentileResult(
            avgFps = (1000.0 / avgMs).toFloat(),
            p1FrametimeMs = percentileFrameTime(0.01),
            p01FrametimeMs = percentileFrameTime(0.001)
        )
    }

    private fun histogramSamples(buckets: Map<Int, Int>, maxSamples: Int = 240): List<Float> {
        val samples = ArrayList<Float>(maxSamples.coerceAtMost(buckets.values.sum()))
        for ((ms, count) in buckets.entries.sortedByDescending { it.value * it.key }) {
            if (samples.size >= maxSamples) break
            val remaining = maxSamples - samples.size
            val take = minOf(count, remaining)
            repeat(take) { samples.add(ms.toFloat()) }
        }
        return samples
    }

    private fun resolvePercentiles(output: String, fromFrametimes: PercentileResult): Pair<Float, Float> {
        var p1Ms = fromFrametimes.p1FrametimeMs
        var p01Ms = fromFrametimes.p01FrametimeMs
        val plausible = p1Ms in 1f..MAX_PLAUSIBLE_FRAMETIME_MS &&
            p01Ms in 1f..MAX_PLAUSIBLE_FRAMETIME_MS
        if (plausible) return p1Ms to p01Ms
        p1Ms = 0f
        p01Ms = 0f

        val uiHistogram = parseUiHistogram(output)
        if (uiHistogram.isNotEmpty()) {
            val histPct = histogramPercentiles(uiHistogram)
            if (p1Ms <= 0f) p1Ms = histPct.p1FrametimeMs
            if (p01Ms <= 0f) p01Ms = histPct.p01FrametimeMs
        }
        if (p1Ms <= 0f) p1Ms = parseReportedPercentile(output, 99)
        if (p01Ms <= 0f) p01Ms = parseReportedPercentile(output, 99)
        return p1Ms to p01Ms
    }

    private fun computeFrametimePercentiles(frametimesMs: List<Float>): PercentileResult {
        if (frametimesMs.size < 3) {
            return PercentileResult(0f, 0f, 0f)
        }
        val sorted = frametimesMs.sorted()
        val totalMs = sorted.sum().toDouble()
        val avgMs = totalMs / sorted.size
        fun timeWeighted(fraction: Double): Float {
            val target = totalMs * fraction
            var accum = 0.0
            for (ft in sorted.asReversed()) {
                accum += ft
                if (accum >= target) return ft
            }
            return sorted.last()
        }
        return PercentileResult(
            avgFps = (1000f / avgMs).toFloat(),
            p1FrametimeMs = timeWeighted(0.01),
            p01FrametimeMs = timeWeighted(0.001)
        )
    }

    private fun parseReportedPercentile(output: String, percentile: Int): Float {
        val pattern = Regex("""${percentile}th percentile:\s*([\d.]+)ms""")
        return output.lineSequence()
            .mapNotNull { line -> pattern.find(line)?.groupValues?.get(1)?.toFloatOrNull() }
            .lastOrNull() ?: 0f
    }

    private fun parseGpuHistogram(output: String): Float {
        val histogramLine = output.lineSequence()
            .firstOrNull { it.trim().startsWith("GPU HISTOGRAM:") }
            ?: return 0f

        var totalFrames = 0
        var weightedMs = 0.0
        val entries = histogramLine.substringAfter("GPU HISTOGRAM:").trim().split(Regex("\\s+"))
        for (entry in entries) {
            val match = Regex("""([\d.]+)ms=(\d+)""").find(entry) ?: continue
            val ms = match.groupValues[1].toDoubleOrNull() ?: continue
            val count = match.groupValues[2].toIntOrNull() ?: continue
            if (ms >= 4950) continue
            totalFrames += count
            weightedMs += ms * count
        }
        if (totalFrames == 0) return 0f
        val avgMs = weightedMs / totalFrames
        return if (avgMs > 0) (1000.0 / avgMs).toFloat() else 0f
    }
}