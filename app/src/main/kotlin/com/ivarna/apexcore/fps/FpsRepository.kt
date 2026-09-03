package com.ivarna.apexcore.fps

import com.ivarna.apexcore.fps.model.FpsMethod
import com.ivarna.apexcore.fps.model.FpsSnapshot
import com.ivarna.apexcore.fps.model.FrametimeBuffer
import com.ivarna.apexcore.fps.privilege.PrivilegeMode
import com.ivarna.apexcore.fps.privilege.PrivilegeModeStore
import com.ivarna.apexcore.fps.privilege.PrivilegeTier
import com.ivarna.apexcore.fps.privilege.ShellGateway
import com.ivarna.apexcore.fps.source.ChoreographerFpsDataSource
import com.ivarna.apexcore.fps.source.DmaFenceFpsDataSource
import com.ivarna.apexcore.fps.source.FpsDaemonManager
import com.ivarna.apexcore.fps.source.FpsDataSource
import com.ivarna.apexcore.fps.source.GfxinfoFpsDataSource
import com.ivarna.apexcore.fps.source.SurfaceFlingerFpsDataSource
import com.ivarna.apexcore.fps.util.ForegroundAppResolver
import com.ivarna.apexcore.fps.util.GpuVendor
import com.ivarna.apexcore.fps.util.GpuVendorDetector

/**
 * Production-grade FPS resolver — reworked from factualstats with improvements:
 * - Tracks launched game package via [setTargetPackage] instead of window focus
 * - Avoids expensive dumpsys while game is tracked (resolver + SF surface cache)
 * - Conservative game routing: SF presentation only (DMA/gfxinfo never for games)
 * - Privilege provenance + fail-closed cache on mode changes (monotonic)
 * - Diagnostics in every snapshot (provenance, age, fallback reason)
 * - Separation of responsive current-FPS vs long-window percentiles (source owns current)
 * - Never fabricates FPS when command succeeds but ring is empty (null check)
 */
interface FpsRepository {
    suspend fun getFps(): FpsSnapshot
    fun ensureDaemonStarted(): Boolean
    fun stopDaemon()

    /** Hint: package being monitored by the overlay (force game routing). */
    fun setTargetPackage(packageName: String?)

    /** Diagnostics for overlay: last routing decision */
    fun lastDiagnostics(): String?

    /** For fail-closed testing */
    fun clearCacheForTest()
}

class FpsRepositoryImpl(
    private val dmaFenceSource: DmaFenceFpsDataSource,
    private val surfaceFlingerSource: SurfaceFlingerFpsDataSource,
    private val gfxinfoSource: GfxinfoFpsDataSource,
    private val foregroundAppResolver: ForegroundAppResolver,
    private val fpsDaemonManager: FpsDaemonManager,
    private val privilegeModeStore: PrivilegeModeStore? = null,
    private val shellGateway: ShellGateway? = null,
    private val choreographerSource: ChoreographerFpsDataSource? = null
) : FpsRepository {

    private val sources: List<FpsDataSource> = listOfNotNull(
        dmaFenceSource,
        surfaceFlingerSource,
        gfxinfoSource,
        choreographerSource
    ).sortedBy { it.priority }

    private val frametimeBuffers = mutableMapOf<String, FrametimeBuffer>()
    private var lastSourceKey: String? = null
    private var lastBatchFingerprint: String? = null
    private var lastGoodSnapshot: FpsSnapshot? = null
    private var lastGoodAtElapsedMs: Long = 0L
    /** Short ring for HUD display stability (median of last samples). */
    private val recentDisplayFps = ArrayDeque<Float>(DISPLAY_SMOOTH_N)

    @Volatile
    private var targetPackage: String? = null

    @Volatile
    private var lastDiagnosticsStr: String? = null

    private var cachedGpuVendor: GpuVendor? = null

    override fun setTargetPackage(packageName: String?) {
        val newTarget = packageName?.takeIf { it.isNotBlank() && it.contains('.') }
        val changed = newTarget != targetPackage
        targetPackage = newTarget
        // Keep SF/gfxinfo locked on the monitored game even if overlay steals focus.
        foregroundAppResolver.preferredPackage = targetPackage
        foregroundAppResolver.setTargetPackage(targetPackage)
        if (changed) {
            // Clear held stale data so we don't show old game's FPS after switching
            lastGoodSnapshot = null
            lastGoodAtElapsedMs = 0L
            recentDisplayFps.clear()
            // Invalidate surface cache so new package resolves fresh layer
            surfaceFlingerSource.clearCache()
            gfxinfoSource.clearCache()
            // Don't clear long-window buffers immediately — keep per-method but source change will clear on next sample
            lastDiagnosticsStr = if (newTarget == null) "target cleared" else "target set to $newTarget"
        }
    }

    override fun ensureDaemonStarted(): Boolean = fpsDaemonManager.ensureStarted()

    override fun stopDaemon() = fpsDaemonManager.stop()

    fun onPrivilegeModeChanged() {
        // Fail-closed: drop held root/elevated samples when mode changes (matches factualstats)
        // Use monotonic timestamps for hold logic
        lastGoodSnapshot = null
        lastGoodAtElapsedMs = 0L
        lastSourceKey = null
        lastBatchFingerprint = null
        recentDisplayFps.clear()
        frametimeBuffers.values.forEach { it.clear() }
        dmaFenceSource.clearCache()
        surfaceFlingerSource.clearCache()
        gfxinfoSource.clearCache()
        cachedGpuVendor = null
        lastDiagnosticsStr = "privilege mode changed -> cache cleared"
    }

    override fun clearCacheForTest() = onPrivilegeModeChanged()

    override fun lastDiagnostics(): String? = lastDiagnosticsStr

    override suspend fun getFps(): FpsSnapshot {
        // Daemon is root-only; ensureStarted checks policy so cheap to call each tick
        try { fpsDaemonManager.ensureStarted() } catch (_: Throwable) {}

        val foreground = foregroundAppResolver.resolve()
        val pkg = targetPackage ?: foreground?.packageName
        val isGame = when {
            pkg == null -> false
            // Own-app test HUD is UI, not a game GPU path
            pkg.contains("apexcore") -> false
            targetPackage != null && !pkg.contains("apexcore") -> true
            else -> foregroundAppResolver.isGameLikeSurface(pkg)
        }

        val gpuVendor = resolveGpuVendor()

        var rawSnapshot: FpsSnapshot? = null
        val routingLog = StringBuilder()

        if (isGame) {
            // Game routing — CONSERVATIVE: SF presentation timestamps only
            // Never DMA for games (counts vsync on Mali, inflated on Adreno without hybrid proof)
            // Never gfxinfo for games (Vulkan/SurfaceView pipeline is fake)
            // Evidence-required before re-enabling DMA for games
            rawSnapshot = surfaceFlingerSource.readFps()?.takeIf { it.currentFps in 1f..240f && it.method != FpsMethod.NONE }
            if (rawSnapshot != null) {
                routingLog.append("game SF hit ${rawSnapshot.currentFps.toInt()}FPS tier=${rawSnapshot.accessTier?.name ?: "null"} layer=${rawSnapshot.sourceDetail}; ")
            } else {
                routingLog.append("game SF miss; ")
            }
            // No emergency gfxinfo fallback for games — fail-closed (return -- not 0)
        } else {
            // UI / non-game: DMA -> SF -> gfxinfo, with undercount heuristic
            rawSnapshot = dmaFenceSource.readFps()?.takeIf { it.currentFps in 1f..240f && it.method != FpsMethod.NONE }
            if (rawSnapshot != null) {
                routingLog.append("ui DMA hit ${rawSnapshot.currentFps.toInt()}FPS tier=${rawSnapshot.accessTier?.name}; ")
                // For UI idle undercount (<5 FPS), prefer gfxinfo if higher
                rawSnapshot = maybePreferGfxinfoForUi(rawSnapshot, isGame)
                if (rawSnapshot?.method == FpsMethod.GFXINFO) routingLog.append("ui DMA undercount -> GFX prefer; ")
            } else {
                routingLog.append("ui DMA miss; ")
                rawSnapshot = surfaceFlingerSource.readFps()?.takeIf { it.currentFps in 1f..240f && it.method != FpsMethod.NONE }
                if (rawSnapshot != null) routingLog.append("ui SF hit ${rawSnapshot.currentFps.toInt()}FPS; ") else routingLog.append("ui SF miss; ")
                if (rawSnapshot == null) {
                    // Last resort: gfxinfo for UI only
                    rawSnapshot = gfxinfoSource.readFps()?.takeIf { it.currentFps in 1f..240f && it.method != FpsMethod.NONE }
                    if (rawSnapshot != null) routingLog.append("ui GFX fallback hit ${rawSnapshot.currentFps.toInt()}FPS; ") else routingLog.append("ui GFX miss; ")
                }
                if (rawSnapshot == null && choreographerSource != null) {
                    rawSnapshot = choreographerSource.readFps()?.takeIf { it.currentFps in 1f..240f && it.method != FpsMethod.NONE }
                    if (rawSnapshot != null) routingLog.append("ui CHR hit ${rawSnapshot.currentFps.toInt()}FPS; ") else routingLog.append("ui CHR miss; ")
                }
            }
        }

        // Hold last good reading when GPU path briefly drops (avoid FPS=0 flash) — but fail-closed on privilege change
        // Use monotonic elapsed time for age
        val nowElapsed = android.os.SystemClock.elapsedRealtime()
        if (rawSnapshot == null && lastGoodSnapshot != null) {
            val age = nowElapsed - lastGoodAtElapsedMs
            val tierAllowed = isTierAllowed(lastGoodSnapshot?.accessTier)
            if (age < LAST_GOOD_HOLD_MS && tierAllowed) {
                rawSnapshot = lastGoodSnapshot!!.copy(
                    isStale = true,
                    sampleAgeMs = age,
                    diagnostics = (lastGoodSnapshot!!.diagnostics ?: "") + " [held ${age}ms stale]"
                )
                routingLog.append("held lastGood ${lastGoodSnapshot!!.currentFps.toInt()}FPS age=${age}ms; ")
            } else {
                if (!tierAllowed) routingLog.append("lastGood dropped (tier not allowed); ")
                else routingLog.append("lastGood expired age=${age}ms; ")
            }
        }

        // Only promote to lastGood if plausible and tier allowed
        if (rawSnapshot != null && !rawSnapshot.isStale && rawSnapshot.currentFps in 1f..240f && rawSnapshot.method != FpsMethod.NONE) {
            // Don't lock onto insane spikes as "last good" (e.g., DMA 240 spike)
            if (rawSnapshot.currentFps <= 144f || rawSnapshot.method != FpsMethod.DMA_FENCE) {
                if (isTierAllowed(rawSnapshot.accessTier)) {
                    lastGoodSnapshot = rawSnapshot
                    lastGoodAtElapsedMs = rawSnapshot.measuredAtElapsedMs.takeIf { it != 0L } ?: nowElapsed
                }
            }
        }

        if (rawSnapshot == null) {
            val diag = routingLog.toString().ifBlank { "no data pkg=$pkg isGame=$isGame vendor=$gpuVendor" }
            lastDiagnosticsStr = diag
            return FpsSnapshot.ZERO.copy(
                packageName = pkg,
                diagnostics = diag,
                sourceDetail = "none",
                timestampMs = System.currentTimeMillis(),
                measuredAtElapsedMs = nowElapsed,
                sampleAgeMs = 0L
            )
        }

        val sourceKey = rawSnapshot.method.name + "@" + (rawSnapshot.accessTier?.name ?: "none")
        if (sourceKey != lastSourceKey) {
            frametimeBuffers[sourceKey]?.clear()
            lastSourceKey = sourceKey
            lastBatchFingerprint = null
            recentDisplayFps.clear()
        }

        val buffer = frametimeBuffers.getOrPut(sourceKey) { FrametimeBuffer(maxSize = 7500) }
        ingestFrametimes(rawSnapshot, buffer, sourceKey)

        val percentiles = buffer.computePercentiles()
        val refreshHz = foreground?.refreshRateHz ?: 60f
        val resolvedFps = resolveDisplayFps(rawSnapshot, refreshHz)
        val displayFps = smoothDisplayFps(resolvedFps)
        val p1Ms = percentiles.p1FrametimeMs
            .takeIf { it in 1f..200f } ?: rawSnapshot.frametimeP1Ms.takeIf { it in 1f..200f } ?: 0f
        val p01Ms = percentiles.p01FrametimeMs
            .takeIf { it in 1f..200f } ?: rawSnapshot.frametimeP01Ms.takeIf { it in 1f..200f } ?: 0f

        val sampleAge = try { android.os.SystemClock.elapsedRealtime() - rawSnapshot.measuredAtElapsedMs } catch (_: Throwable) { 0L }
        val finalDiagnostics = buildString {
            append(routingLog.toString())
            append(" target=${targetPackage ?: "none"} pkg=$pkg isGame=$isGame vendor=$gpuVendor")
            append(" method=${rawSnapshot.method} tier=${rawSnapshot.accessTier?.name ?: "null"} src=${rawSnapshot.sourceDetail ?: "n/a"}")
            append(" layer=${rawSnapshot.surfaceName?.take(40) ?: "none"}")
            append(" raw=${"%.1f".format(rawSnapshot.currentFps)} beforeSmooth=${"%.1f".format(resolvedFps)} afterSmooth=${"%.1f".format(displayFps)}")
            append(" intervals=${rawSnapshot.frameCount} age=${sampleAge}ms")
            if (rawSnapshot.diagnostics != null) append(" srcDiag=[${rawSnapshot.diagnostics}]")
            append(" buf=${buffer.size}")
            if (rawSnapshot.isStale) append(" STALE")
        }
        lastDiagnosticsStr = finalDiagnostics

        return rawSnapshot.copy(
            currentFps = displayFps,
            frametimeAvgMs = if (displayFps > 0f) 1000f / displayFps else rawSnapshot.frametimeAvgMs,
            frametimeP1Ms = p1Ms,
            frametimeP01Ms = p01Ms,
            frametimeHistogram = if (buffer.size > 0) buffer.samples else rawSnapshot.frametimeHistogram,
            diagnostics = finalDiagnostics,
            sampleAgeMs = sampleAge,
            // Keep original measuredAtElapsedMs for isFresh, sourceDetail provenance
            isStale = rawSnapshot.isStale
        )
    }

    private fun isTierAllowed(tier: PrivilegeTier?): Boolean {
        if (tier == null) return true
        val store = privilegeModeStore ?: return true
        val chain = store.mode.value.let {
            // Map mode to allowed tiers without needing ShellGateway
            when (it) {
                PrivilegeMode.AUTO -> listOf(PrivilegeTier.SU_ROOT, PrivilegeTier.SHIZUKU_ROOT, PrivilegeTier.SHIZUKU_SHELL, PrivilegeTier.STANDARD)
                PrivilegeMode.ROOT -> listOf(PrivilegeTier.SU_ROOT)
                PrivilegeMode.SHIZUKU -> listOf(PrivilegeTier.SHIZUKU_SHELL, PrivilegeTier.SHIZUKU_ROOT)
                PrivilegeMode.STANDARD -> listOf(PrivilegeTier.STANDARD)
            }
        }
        return tier in chain
    }

    private fun resolveGpuVendor(): GpuVendor {
        cachedGpuVendor?.let { return it }
        val vendor = try {
            if (shellGateway != null) {
                GpuVendorDetector.detect(shellGateway.shellExecutor)
            } else {
                GpuVendorDetector.detect(null as com.ivarna.apexcore.fps.util.ShellExecutor?)
            }
        } catch (_: Throwable) {
            GpuVendorDetector.detect(null as com.ivarna.apexcore.fps.util.ShellExecutor?)
        }
        cachedGpuVendor = vendor
        return vendor
    }

    /** Median of last N samples — kills one-sample spikes without laggy EMA. */
    private fun smoothDisplayFps(fps: Float): Float {
        if (fps <= 0f) return fps
        while (recentDisplayFps.size >= DISPLAY_SMOOTH_N) recentDisplayFps.removeFirst()
        recentDisplayFps.addLast(fps)
        if (recentDisplayFps.size < 2) return fps
        val sorted = recentDisplayFps.sorted()
        return sorted[sorted.size / 2]
    }

    /**
     * UI apps may undercount on Adreno GPU paths when idle — prefer gfxinfo only then.
     */
    private suspend fun maybePreferGfxinfoForUi(
        dmaSnapshot: FpsSnapshot?,
        isGame: Boolean
    ): FpsSnapshot? {
        if (isGame) return dmaSnapshot
        if (dmaSnapshot == null || dmaSnapshot.method != FpsMethod.DMA_FENCE) return dmaSnapshot
        if (dmaSnapshot.currentFps >= UI_GPU_UNDERCNT_MAX_FPS) return dmaSnapshot

        val gfxSnapshot = gfxinfoSource.readFps()
        if (gfxSnapshot != null && gfxSnapshot.currentFps > dmaSnapshot.currentFps) {
            return gfxSnapshot
        }
        return dmaSnapshot
    }

    private companion object {
        const val UI_GPU_UNDERCNT_MAX_FPS = 5f
        const val LAST_GOOD_HOLD_MS = 4000L
        const val DISPLAY_SMOOTH_N = 3
    }

    private fun resolveDisplayFps(snapshot: FpsSnapshot, refreshHz: Float): Float {
        // Trust source's responsive FPS; only validate bounds.
        // Do not cap to stale 60Hz fallback when real display is 90/120/144.
        // Refresh is used for jank, not for hard FPS ceiling.
        return snapshot.currentFps.coerceIn(1f, 240f)
    }

    private fun ingestFrametimes(snapshot: FpsSnapshot, buffer: FrametimeBuffer, sourceKey: String) {
        val batch = snapshot.frametimeHistogram
        if (batch.isEmpty()) return

        val fingerprint = buildString {
            append(sourceKey)
            append(':')
            append(batch.size)
            append(':')
            append(batch.firstOrNull())
            append(':')
            append(batch.lastOrNull())
            append(':')
            append(batch.sum())
        }
        if (fingerprint == lastBatchFingerprint) return
        lastBatchFingerprint = fingerprint

        // Filter before pushing: keep separate long-window stats clean
        batch.forEach { ft ->
            if (ft in 1f..200f) buffer.push(ft)
        }
    }
}
