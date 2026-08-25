package com.ivarna.apexcore.fps.util


data class ForegroundApp(
    val packageName: String,
    val pid: Int,
    val refreshRateHz: Float
)

class ForegroundAppResolver(
    private val shellGateway: com.ivarna.apexcore.fps.privilege.ShellGateway,
    private val appContext: android.content.Context? = null
) {
    /**
     * When the game overlay is active it can steal `mCurrentFocus`.
     * Prefer this package for SF/gfxinfo surface selection so FPS tracks the game.
     */
    @Volatile
    var preferredPackage: String? = null

    private var cachedForeground: ForegroundApp? = null
    private var cachedForegroundAtMs: Long = 0L
    private val cachedForegroundTtlMs = 1200L

    private var cachedPidPackage: String? = null
    private var cachedPidValue: Int = 0
    private var cachedPidAtMs: Long = 0L
    private val pidTtlMs = 2000L

    private var cachedRefreshHz: Float = 60f
    private var cachedRefreshAtMs: Long = 0L
    private val refreshTtlMs = 5000L

    fun setTargetPackage(pkg: String?) {
        preferredPackage = pkg?.takeIf { it.isNotBlank() && it.contains('.') }
        // Invalidate pid and foreground caches for new package — monotonic
        cachedPidPackage = null
        cachedPidAtMs = 0L
        cachedForeground = null
        cachedForegroundAtMs = 0L
        // Clear gameLike verdict for old package so new target is probed fresh
    }

    fun clearTargetPackage() {
        preferredPackage = null
        cachedPidPackage = null
        cachedPidAtMs = 0L
        cachedForeground = null
        cachedForegroundAtMs = 0L
    }

    fun resolve(): ForegroundApp? {
        val preferred = preferredPackage?.takeIf { it.isNotBlank() && it.contains('.') }
        if (preferred != null) {
            // Fast path: do not touch dumpsys window at all while game is tracked.
            // This is the mission requirement: track launched game package, not focus.
            val now = android.os.SystemClock.elapsedRealtime()
            // Use cached pid if recent
            val pid = pidOfCached(preferred)
            val refresh = cachedRefreshRate()
            // Return immediately — no expensive dumpsys window/display spam.
            // If pid == 0 the game may have died; caller (SF datasource) will handle null surface
            // and repository will fall back to system foreground after a grace period.
            // To avoid returning dead pid forever, if pid==0 for >3s, fall through to system resolve.
            if (pid != 0 || now - cachedPidAtMs < 3000L) {
                return ForegroundApp(preferred, pid, refresh)
            }
            // pid 0 for >3s -> game likely gone, fall through to system foreground detection
        }

        // System foreground path with short TTL to avoid spamming system_server 500ms loop
        val now = android.os.SystemClock.elapsedRealtime()
        if (cachedForeground != null && now - cachedForegroundAtMs < cachedForegroundTtlMs) {
            return cachedForeground
        }
        val system = resolveSystemForeground()
        cachedForeground = system
        cachedForegroundAtMs = now
        return system
    }

    private fun resolveSystemForeground(): ForegroundApp? {
        // Prefer fg_app daemon file when root ops allowed — cheapest, written 0.5s by daemon
        readFromDaemonFile()?.let { return it }
        // Try dumpsys window — skip null focus lines, take first real one (policy-aware)
        val fromWindow = readFromDumpsys()
        if (fromWindow != null) return fromWindow
        // Fallback: dumpsys activity activities ResumedActivity
        return readFromActivityDumpsys()
    }

    private fun pidOfCached(packageName: String): Int {
        val now = android.os.SystemClock.elapsedRealtime()
        if (cachedPidPackage == packageName && now - cachedPidAtMs < pidTtlMs) {
            return cachedPidValue
        }
        val pid = pidOf(packageName)
        cachedPidPackage = packageName
        cachedPidValue = pid
        cachedPidAtMs = now
        return pid
    }

    private fun pidOf(packageName: String): Int {
        // Try STANDARD first (no elevation), then policy chain if fails
        var result = shellGateway.execute("pidof $packageName 2>/dev/null | awk '{print \$1}'", com.ivarna.apexcore.fps.privilege.PrivilegeTier.STANDARD)
        if (result.isSuccess && result.output.trim().isNotEmpty()) {
            return result.output.trim().toIntOrNull() ?: 0
        }
        // Elevated fallback respecting current policy
        val chain = shellGateway.currentPolicy().chain(com.ivarna.apexcore.fps.privilege.PrivilegePolicy.DEFAULT_CHAIN)
        for (tier in chain) {
            if (tier == com.ivarna.apexcore.fps.privilege.PrivilegeTier.STANDARD) continue
            result = shellGateway.execute("pidof $packageName 2>/dev/null | awk '{print \$1}'", tier)
            if (result.isSuccess && result.output.trim().isNotEmpty()) {
                return result.output.trim().toIntOrNull() ?: 0
            }
        }
        return 0
    }

    /**
     * True when the foreground looks like a game/render surface.
     * Matches SurfaceView, NativeActivity, Vulkan, GLSurfaceView — not only
     * "SurfaceView" (3DMark uses AttanExtremeVulkanNativeActivity with no SurfaceView token).
     * gfxinfo framestats is unreliable for these.
     *
     * Note: do NOT match bare "BLAST" — most Android 12+ UI apps use BLASTBufferQueue.
     */
    fun isGameLikeSurface(packageName: String): Boolean {
        // The probes below (dumpsys SF --list / dumpsys window) are expensive —
        // cache the verdict per package so the HUD tick doesn't spam system_server.
        val now = android.os.SystemClock.elapsedRealtime()
        gameLikeCache[packageName]?.let { (verdict, expiresAt) ->
            if (now < expiresAt) return verdict
        }
        val verdict = run {
            if (KNOWN_GAME_PACKAGES.any { packageName.startsWith(it) || packageName.contains(it) }) {
                return@run true
            }
            if (hasGameLayer(packageName)) return@run true
            hasGameFocusedActivity(packageName)
        }
        gameLikeCache[packageName] = verdict to (now + GAME_LIKE_CACHE_MS)
        return verdict
    }

    private val gameLikeCache = java.util.concurrent.ConcurrentHashMap<String, Pair<Boolean, Long>>()

    /** @deprecated Use [isGameLikeSurface]. */
    fun hasSurfaceViewLayer(packageName: String): Boolean = isGameLikeSurface(packageName)

    private fun hasGameLayer(packageName: String): Boolean {
        val (result, _) = shellGateway.executeChain(
            "dumpsys SurfaceFlinger --list 2>/dev/null",
            shellGateway.currentPolicy().chain(com.ivarna.apexcore.fps.privilege.PrivilegePolicy.DEFAULT_CHAIN)
        )
        if (!result.isSuccess || result.output.isBlank()) return false

        val shortPkg = packageName.substringAfterLast('.')
        return result.output.lineSequence().any { line ->
            val trimmed = line.trim()
            val ownsLayer = trimmed.contains(packageName) ||
                (shortPkg.length >= 4 && trimmed.contains(shortPkg))
            ownsLayer && GAME_MARKERS.any { marker -> trimmed.contains(marker, ignoreCase = true) }
        }
    }

    private fun hasGameFocusedActivity(packageName: String): Boolean {
        val (windowResult, _) = shellGateway.executeChain(
            "dumpsys window 2>/dev/null | grep mCurrentFocus",
            shellGateway.currentPolicy().chain(com.ivarna.apexcore.fps.privilege.PrivilegePolicy.DEFAULT_CHAIN)
        )
        if (!windowResult.isSuccess || windowResult.output.isBlank()) return false
        val line = windowResult.output
        if (!line.contains(packageName) && !line.contains(packageName.substringAfterLast('.'))) {
            return false
        }
        return GAME_MARKERS.any { marker -> line.contains(marker, ignoreCase = true) }
    }

    companion object {
        private const val GAME_LIKE_CACHE_MS = 30_000L

        private val GAME_MARKERS = listOf(
            "SurfaceView",
            "NativeActivity",
            "Vulkan",
            "GLSurfaceView",
            "UnityPlayer",
            "Unreal",
            "Cocos2dx"
        )

        /** Packages where gfxinfo is known-wrong (Vulkan/native benchmarks & games). */
        private val KNOWN_GAME_PACKAGES = listOf(
            "com.futuremark.",
            "com.antutu.",
            "com.primatelabs.",
            "com.benchmark.",
            "com.miHoYo.",
            "com.tencent.",
            "com.epicgames.",
            "com.unity3d.",
            "com.garena.",
            "com.activision.",
            "com.roblox.",
            "com.mojang."
        )
    }

    private fun readFromDaemonFile(): ForegroundApp? {
        // Policy-aware: only attempt when root ops are allowed (daemon is root-only)
        val chain = shellGateway.currentPolicy().chain(com.ivarna.apexcore.fps.privilege.PrivilegePolicy.DEFAULT_CHAIN)
        if (com.ivarna.apexcore.fps.privilege.PrivilegeTier.SU_ROOT !in chain) return null
        val result = shellGateway.execute("cat /data/local/tmp/fg_app 2>/dev/null", com.ivarna.apexcore.fps.privilege.PrivilegeTier.SU_ROOT)
        if (!result.isSuccess || result.output.isBlank()) return null

        val parts = result.output.trim().split(Regex("\\s+"))
        if (parts.size < 3) return null

        val pid = parts[0].toIntOrNull() ?: 0
        val refreshRate = parts[1].toFloatOrNull()?.takeIf { it > 0f } ?: 60f
        val packageName = parts[2].takeIf { it != "unknown" && it.contains('.') } ?: return null

        return ForegroundApp(packageName, pid, refreshRate)
    }

    private fun readFromDumpsys(): ForegroundApp? {
        val (windowResult, _) = shellGateway.executeChain(
            "dumpsys window 2>/dev/null | grep -E 'mCurrentFocus|mFocusedApp'",
            shellGateway.currentPolicy().chain(com.ivarna.apexcore.fps.privilege.PrivilegePolicy.DEFAULT_CHAIN)
        )
        if (!windowResult.isSuccess || windowResult.output.isBlank()) return null

        // Overlay steals primary display focus → first mCurrentFocus=null. Skip nulls.
        val packageName = windowResult.output.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.contains("=null") }
            .mapNotNull { extractPackage(it) }
            .firstOrNull()
            ?: return null

        val pid = pidOfCached(packageName)
        val refreshRate = cachedRefreshRate()
        return ForegroundApp(packageName, pid, refreshRate)
    }

    /** Fallback when window focus is null/stolen by overlay. */
    private fun readFromActivityDumpsys(): ForegroundApp? {
        val (result, _) = shellGateway.executeChain(
            "dumpsys activity activities 2>/dev/null | grep -E 'ResumedActivity|mResumedActivity' | head -5",
            shellGateway.currentPolicy().chain(com.ivarna.apexcore.fps.privilege.PrivilegePolicy.DEFAULT_CHAIN)
        )
        if (!result.isSuccess || result.output.isBlank()) return null

        val packageName = result.output.lineSequence()
            .mapNotNull { extractPackage(it) }
            .firstOrNull()
            ?: return null

        val pid = pidOfCached(packageName)
        val refreshRate = cachedRefreshRate()
        return ForegroundApp(packageName, pid, refreshRate)
    }

    private fun cachedRefreshRate(): Float {
        val now = android.os.SystemClock.elapsedRealtime()
        if (now - cachedRefreshAtMs < refreshTtlMs && cachedRefreshHz > 0f) return cachedRefreshHz
        val rate = readActiveRenderFrameRate()
        cachedRefreshHz = rate
        cachedRefreshAtMs = now
        return rate
    }

    private fun readActiveRenderFrameRate(): Float {
        // Prefer WindowManager display refresh when context is available — no dumpsys cost
        appContext?.let { ctx ->
            try {
                val wm = ctx.getSystemService(android.content.Context.WINDOW_SERVICE) as? android.view.WindowManager
                val display = if (android.os.Build.VERSION.SDK_INT >= 30) {
                    ctx.display
                } else {
                    @Suppress("DEPRECATION") wm?.defaultDisplay
                }
                val rate = display?.refreshRate
                if (rate != null && rate > 0f) return rate
                // Also try WindowManager directly
                val wmRate = wm?.defaultDisplay?.refreshRate
                if (wmRate != null && wmRate > 0f) return wmRate
            } catch (_: Throwable) { }
        }
        // Fallback to dumpsys display (expensive) — cached via TTL
        val (displayResult, _) = shellGateway.executeChain(
            "dumpsys display 2>/dev/null",
            shellGateway.currentPolicy().chain(com.ivarna.apexcore.fps.privilege.PrivilegePolicy.DEFAULT_CHAIN)
        )
        if (displayResult.isSuccess && displayResult.output.isNotBlank()) {
            val activeRate = Regex("""mActiveRenderFrameRate=([0-9.]+)""")
                .find(displayResult.output)
                ?.groupValues
                ?.get(1)
                ?.toFloatOrNull()
            if (activeRate != null && activeRate > 0f) return activeRate

            val renderRate = Regex("""renderFrameRate ([0-9.]+)""")
                .find(displayResult.output)
                ?.groupValues
                ?.get(1)
                ?.toFloatOrNull()
            if (renderRate != null && renderRate > 0f) return renderRate
        }
        return 60f
    }

    private fun extractPackage(line: String): String? {
        val u0Match = Regex("""u0\s+([^/\s}]+)""").find(line)
        if (u0Match != null) return u0Match.groupValues[1]

        val braceMatch = Regex("""\{[^}]*\s+([^/\s}]+)/""").find(line)
        if (braceMatch != null) return braceMatch.groupValues[1]

        val slashIdx = line.indexOf('/')
        if (slashIdx > 0) {
            val beforeSlash = line.substring(0, slashIdx)
            val candidate = beforeSlash.substringAfterLast(' ').trim()
            if (candidate.contains('.')) return candidate
        }
        return null
    }
}
