package com.ivarna.apexcore.fps.util


data class ForegroundApp(
    val packageName: String,
    val pid: Int,
    val refreshRateHz: Float
)

class ForegroundAppResolver(
    private val shellExecutor: ShellExecutor
) {
    /**
     * When the game overlay is active it can steal `mCurrentFocus`.
     * Prefer this package for SF/gfxinfo surface selection so FPS tracks the game.
     */
    @Volatile
    var preferredPackage: String? = null

    fun resolve(): ForegroundApp? {
        val preferred = preferredPackage?.takeIf { it.isNotBlank() && it.contains('.') }
        val system = resolveSystemForeground()
        if (preferred != null) {
            val refresh = system?.refreshRateHz?.takeIf { it > 0f } ?: 60f
            val pid = when {
                system?.packageName == preferred -> system.pid
                else -> pidOf(preferred, useRoot = shellExecutor.isSuAvailable())
            }
            return ForegroundApp(preferred, pid, refresh)
        }
        return system
    }

    private fun resolveSystemForeground(): ForegroundApp? {
        readFromDaemonFile()?.let { return it }
        // Try dumpsys window — skip null focus lines, take first real one
        val fromWindow = readFromDumpsys(useRoot = false) ?: readFromDumpsys(useRoot = true)
        if (fromWindow != null) return fromWindow
        // Fallback: dumpsys activity activities ResumedActivity
        return readFromActivityDumpsys()
    }

    private fun pidOf(packageName: String, useRoot: Boolean): Int {
        val pidResult = shellExecutor.execute(
            "pidof $packageName 2>/dev/null | awk '{print \$1}'",
            useRoot = useRoot
        )
        return pidResult.output.trim().toIntOrNull() ?: 0
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
        if (KNOWN_GAME_PACKAGES.any { packageName.startsWith(it) || packageName.contains(it) }) {
            return true
        }
        if (hasGameLayer(packageName)) return true
        return hasGameFocusedActivity(packageName)
    }

    /** @deprecated Use [isGameLikeSurface]. */
    fun hasSurfaceViewLayer(packageName: String): Boolean = isGameLikeSurface(packageName)

    private fun hasGameLayer(packageName: String): Boolean {
        val listResult = shellExecutor.execute(
            "dumpsys SurfaceFlinger --list 2>/dev/null",
            useRoot = shellExecutor.isSuAvailable()
        )
        if (!listResult.isSuccess || listResult.output.isBlank()) return false

        val shortPkg = packageName.substringAfterLast('.')
        return listResult.output.lineSequence().any { line ->
            val trimmed = line.trim()
            val ownsLayer = trimmed.contains(packageName) ||
                (shortPkg.length >= 4 && trimmed.contains(shortPkg))
            ownsLayer && GAME_MARKERS.any { marker -> trimmed.contains(marker, ignoreCase = true) }
        }
    }

    private fun hasGameFocusedActivity(packageName: String): Boolean {
        val useRoot = shellExecutor.isSuAvailable()
        val windowResult = shellExecutor.execute(
            "dumpsys window 2>/dev/null | grep mCurrentFocus",
            useRoot = useRoot
        )
        if (!windowResult.isSuccess || windowResult.output.isBlank()) return false
        val line = windowResult.output
        if (!line.contains(packageName) && !line.contains(packageName.substringAfterLast('.'))) {
            return false
        }
        return GAME_MARKERS.any { marker -> line.contains(marker, ignoreCase = true) }
    }

    companion object {
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
        if (!shellExecutor.isSuAvailable()) return null
        val result = shellExecutor.execute("cat /data/local/tmp/fg_app 2>/dev/null", useRoot = true)
        if (!result.isSuccess || result.output.isBlank()) return null

        val parts = result.output.trim().split(Regex("\\s+"))
        if (parts.size < 3) return null

        val pid = parts[0].toIntOrNull() ?: 0
        val refreshRate = parts[1].toFloatOrNull()?.takeIf { it > 0f } ?: 60f
        val packageName = parts[2].takeIf { it != "unknown" && it.contains('.') } ?: return null

        return ForegroundApp(packageName, pid, refreshRate)
    }

    private fun readFromDumpsys(useRoot: Boolean): ForegroundApp? {
        val windowResult = shellExecutor.execute(
            "dumpsys window 2>/dev/null | grep -E 'mCurrentFocus|mFocusedApp'",
            useRoot = useRoot
        )
        if (!windowResult.isSuccess || windowResult.output.isBlank()) return null

        // Overlay steals primary display focus → first mCurrentFocus=null. Skip nulls.
        val packageName = windowResult.output.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.contains("=null") }
            .mapNotNull { extractPackage(it) }
            .firstOrNull()
            ?: return null

        val pidResult = shellExecutor.execute(
            "pidof $packageName 2>/dev/null | awk '{print \$1}'",
            useRoot = useRoot
        )
        val pid = pidResult.output.trim().toIntOrNull() ?: 0
        val refreshRate = readActiveRenderFrameRate(useRoot)
        return ForegroundApp(packageName, pid, refreshRate)
    }

    /** Fallback when window focus is null/stolen by overlay. */
    private fun readFromActivityDumpsys(): ForegroundApp? {
        val useRoot = shellExecutor.isSuAvailable()
        val result = shellExecutor.execute(
            "dumpsys activity activities 2>/dev/null | grep -E 'ResumedActivity|mResumedActivity' | head -5",
            useRoot = useRoot
        )
        if (!result.isSuccess || result.output.isBlank()) return null

        val packageName = result.output.lineSequence()
            .mapNotNull { extractPackage(it) }
            .firstOrNull()
            ?: return null

        val pidResult = shellExecutor.execute(
            "pidof $packageName 2>/dev/null | awk '{print \$1}'",
            useRoot = useRoot
        )
        val pid = pidResult.output.trim().toIntOrNull() ?: 0
        val refreshRate = readActiveRenderFrameRate(useRoot)
        return ForegroundApp(packageName, pid, refreshRate)
    }

    private fun readActiveRenderFrameRate(useRoot: Boolean): Float {
        val displayResult = shellExecutor.execute(
            "dumpsys display 2>/dev/null",
            useRoot = useRoot
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