package com.ivarna.apexcore.fps.util

/**
 * Detect GPU vendor for FPS path routing (Snapdragon/Adreno vs MediaTek/Mali).
 * Same heuristics as factualstats `scripts/fps/detect/gpu_detect.sh`.
 */
enum class GpuVendor {
    ADRENO,
    MALI,
    UNKNOWN;

    /** UI label: SoC family + GPU. */
    val displayName: String
        get() = when (this) {
            ADRENO -> "Snapdragon · Adreno"
            MALI -> "MediaTek · Mali"
            UNKNOWN -> "Unknown GPU"
        }

    val shortName: String
        get() = when (this) {
            ADRENO -> "Adreno"
            MALI -> "Mali"
            UNKNOWN -> "Unknown"
        }
}

object GpuVendorDetector {
    @Volatile
    private var cached: GpuVendor? = null

    fun detect(shellExecutor: ShellExecutor? = null): GpuVendor {
        cached?.let { return it }
        val result = detectOnce(shellExecutor)
        cached = result
        return result
    }

    fun invalidate() {
        cached = null
    }

    private fun detectOnce(shellExecutor: ShellExecutor?): GpuVendor {
        // Props (readable without elevation on most devices)
        val vulkan = getprop("ro.hardware.vulkan", shellExecutor).lowercase()
        val egl = getprop("ro.hardware.egl", shellExecutor).lowercase()
        val plat = getprop("ro.board.platform", shellExecutor).lowercase()
        val combo = vulkan + egl
        when {
            combo.contains("adreno") -> return GpuVendor.ADRENO
            combo.contains("mali") -> return GpuVendor.MALI
        }

        // Sysfs
        if (dirExists("/sys/class/kgsl") || dirExists("/sys/module/kgsl") ||
            dirExists("/sys/module/kgsl_core")
        ) {
            return GpuVendor.ADRENO
        }
        if (dirExists("/sys/class/misc/mali0") || listModulesMali()) {
            return GpuVendor.MALI
        }

        // Platform heuristic
        when {
            plat.startsWith("mt") || plat.contains("dimensity") -> return GpuVendor.MALI
            plat in ADRENO_PLATFORMS || plat.startsWith("sm") || plat.startsWith("sdm") ||
                plat.startsWith("msm") -> return GpuVendor.ADRENO
        }

        // Ftrace events (may need root; try via shell if available)
        if (dirExists("/sys/kernel/tracing/events/kgsl") ||
            dirExists("/sys/kernel/debug/tracing/events/kgsl")
        ) {
            return GpuVendor.ADRENO
        }

        return GpuVendor.UNKNOWN
    }

    private val ADRENO_PLATFORMS = setOf(
        "pineapple", "kalama", "taro", "lahaina", "kona",
        "cliffs", "anorak", "pitti", "canoe", "sun", "crow"
    )

    private fun getprop(key: String, shell: ShellExecutor?): String {
        // SystemProperties via reflection, then getprop shell
        try {
            val sp = Class.forName("android.os.SystemProperties")
            val get = sp.getMethod("get", String::class.java, String::class.java)
            val v = get.invoke(null, key, "") as? String
            if (!v.isNullOrBlank()) return v.trim()
        } catch (_: Throwable) {
        }
        if (shell != null) {
            val r = shell.execute("getprop $key 2>/dev/null", useRoot = false)
            if (r.isSuccess && r.output.isNotBlank()) return r.output.trim()
        }
        return ""
    }

    private fun dirExists(path: String): Boolean =
        try {
            java.io.File(path).isDirectory
        } catch (_: Throwable) {
            false
        }

    private fun listModulesMali(): Boolean =
        try {
            val mod = java.io.File("/sys/module")
            mod.list()?.any { it.startsWith("mali") } == true
        } catch (_: Throwable) {
            false
        }
}
