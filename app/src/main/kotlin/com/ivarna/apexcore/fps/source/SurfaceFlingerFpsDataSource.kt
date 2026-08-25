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

    override suspend fun readFps(): FpsSnapshot? {
        val foreground = foregroundAppResolver.resolve() ?: return null
        val surface = findSurfaceForPackage(foreground.packageName) ?: return null
        val latencyOutput = shellGateway.executeChain(
            "dumpsys SurfaceFlinger --latency \"$surface\" 2>/dev/null",
            shellGateway.currentPolicy().chain(PrivilegePolicy.DEFAULT_CHAIN)
        ).first
        if (!latencyOutput.isSuccess) return null

        return parseLatency(latencyOutput.output)
    }

    private fun findSurfaceForPackage(packageName: String): String? {
        val listResult = shellGateway.executeChain(
            "dumpsys SurfaceFlinger --list 2>/dev/null",
            shellGateway.currentPolicy().chain(PrivilegePolicy.DEFAULT_CHAIN)
        ).first
        if (!listResult.isSuccess) return null

        val shortPkg = packageName.substringAfterLast('.')
        val owned = listResult.output.lineSequence()
            .map { it.trim() }
            .mapNotNull { parseLayerName(it) }
            .filter { it.contains(packageName) || (shortPkg.length >= 4 && it.contains(shortPkg)) }
            .filter { !it.contains("ActivityRecord") && !it.contains("InputSink") }
            .toList()

        // Prefer game/render surfaces over Activity chrome layers.
        val preferred = owned.firstOrNull { line ->
            listOf("SurfaceView", "NativeActivity", "Vulkan", "BLAST", "GLSurfaceView")
                .any { marker -> line.contains(marker, ignoreCase = true) }
        }
        if (preferred != null) return preferred

        return owned.firstOrNull { it.contains("#") }
            ?: owned.firstOrNull()
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

    internal fun parseLatency(output: String): FpsSnapshot? {
        val lines = output.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toList()
        if (lines.size < 2) return null

        val refreshPeriodNs = lines[0].toLongOrNull() ?: return null
        if (refreshPeriodNs <= 0) return null

        val frametimesMs = mutableListOf<Float>()
        var jankCount = 0
        var prevExpected = 0

        for (line in lines.drop(1)) {
            val parts = line.split(Regex("\\s+")).filter { it.isNotBlank() }
            if (parts.size < 3) continue
            val frameStartNs = parts[0].toLongOrNull() ?: continue
            val frameCompleteNs = parts[2].toLongOrNull() ?: continue
            if (frameCompleteNs <= frameStartNs) continue

            val frameTimeNs = frameCompleteNs - frameStartNs
            val frameTimeMs = frameTimeNs / 1_000_000f
            if (frameTimeMs <= 0f || frameTimeMs > 2000f) continue
            frametimesMs.add(frameTimeMs)

            val expected = kotlin.math.ceil(frameTimeNs.toDouble() / refreshPeriodNs).toInt()
            if (prevExpected > 0 && expected > prevExpected) jankCount++
            prevExpected = expected
        }

        if (frametimesMs.isEmpty()) return null

        // SF --latency is a ring of ~128 frames; old history makes FPS jump when
        // framerate changes. Instantaneous FPS uses the newest window only.
        val recent = if (frametimesMs.size > RECENT_FRAME_WINDOW) {
            frametimesMs.takeLast(RECENT_FRAME_WINDOW)
        } else {
            frametimesMs
        }
        val avgMs = recent.average().toFloat()
        if (avgMs <= 0f) return null
        val fps = 1000f / avgMs

        return FpsSnapshot(
            currentFps = fps.coerceIn(1f, 240f),
            frametimeAvgMs = avgMs,
            frametimeP1Ms = 0f,
            frametimeP01Ms = 0f,
            frametimeHistogram = frametimesMs,
            jankCount = jankCount,
            method = FpsMethod.SURFACEFLINGER
        )
    }

    private companion object {
        /** ~0.5s at 60Hz / ~0.25s at 120Hz — responsive without full-ring noise. */
        const val RECENT_FRAME_WINDOW = 32
    }
}
