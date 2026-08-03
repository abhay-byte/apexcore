package com.ivarna.apexcore.fps.privilege

import android.content.pm.PackageManager
import android.util.Log
import com.ivarna.apexcore.fps.util.ShellExecutor
import com.ivarna.apexcore.fps.util.ShellResult
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

/**
 * Tier-aware shell gateway (ported from factualstats).
 *
 * Follows [PrivilegeModeStore] (synced with top-bar Root / Shizuku preference).
 *
 * | Tier | Implementation |
 * |------|----------------|
 * | ROOT | `su -c` via [ShellExecutor] |
 * | SHIZUKU | Shizuku `newProcess` (shell-uid elevated dumpsys) |
 * | STANDARD | plain `sh -c` |
 */
class ShellGateway(
    private val shellExecutor: ShellExecutor,
    private val store: PrivilegeModeStore
) {
    fun canRoot(): Boolean = shellExecutor.isSuAvailable()

    fun canShizuku(): Boolean {
        return try {
            if (!Shizuku.pingBinder()) return false
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (_: Throwable) {
            false
        }
    }

    fun canStandard(): Boolean = true

    fun currentPolicy(): PrivilegePolicy = PrivilegePolicy(store.mode.value)

    fun execute(command: String, tier: PrivilegeTier): ShellResult {
        return when (tier) {
            PrivilegeTier.ROOT -> {
                if (!canRoot()) {
                    return ShellResult("", exitCode = -1).also { markBlocked(it, "no_su") }
                }
                shellExecutor.execute(command, useRoot = true)
            }
            PrivilegeTier.SHIZUKU -> {
                if (!canShizuku()) {
                    return ShellResult("", exitCode = -1).also { markBlocked(it, "no_shizuku") }
                }
                executeViaShizuku(command)
            }
            PrivilegeTier.STANDARD -> {
                shellExecutor.execute(command, useRoot = false)
            }
        }
    }

    fun executeChain(
        command: String,
        chain: List<PrivilegeTier>
    ): Pair<ShellResult, PrivilegeTier?> {
        var lastResult = ShellResult("", exitCode = -1)
        var lastTier: PrivilegeTier? = null
        for (tier in chain) {
            lastTier = tier
            lastResult = execute(command, tier)
            if (lastResult.isSuccess) {
                return lastResult to tier
            }
        }
        return lastResult to lastTier
    }

    fun readPath(
        path: String,
        defaultChain: List<PrivilegeTier> = PrivilegePolicy.DEFAULT_CHAIN
    ): Pair<String?, PrivilegeTier?> {
        val chain = currentPolicy().chain(defaultChain)
        if (chain.contains(PrivilegeTier.STANDARD)) {
            try {
                val f = File(path)
                if (f.canRead()) {
                    val text = f.readText().trim().takeIf { it.isNotEmpty() }
                    if (text != null) return text to PrivilegeTier.STANDARD
                }
            } catch (_: Exception) {
            }
        }
        for (tier in chain) {
            val result = execute("cat \"$path\" 2>/dev/null", tier)
            if (result.isSuccess && result.output.isNotBlank()) {
                return result.output.trim() to tier
            }
        }
        return null to null
    }

    fun readPathText(
        path: String,
        defaultChain: List<PrivilegeTier> = PrivilegePolicy.DEFAULT_CHAIN
    ): String? = readPath(path, defaultChain).first

    fun clearCache() {}

    private fun executeViaShizuku(command: String): ShellResult {
        return try {
            val proc = newShizukuProcess(arrayOf("sh", "-c", command))
                ?: return ShellResult("error: newProcess=null", -1)
            val output = BufferedReader(InputStreamReader(proc.inputStream))
                .readText()
                .trim()
            val exit = waitExit(proc, 8000L) ?: -1
            ShellResult(output, exit)
        } catch (e: Exception) {
            Log.w(TAG, "Shizuku shell failed: ${e.message}")
            ShellResult("error: ${e.message}", -1)
        }
    }

    private fun newShizukuProcess(cmd: Array<String>): Process? {
        return try {
            val cls = Class.forName("rikka.shizuku.Shizuku")
            val method = findNewProcessMethod(cls)
            method.isAccessible = true
            method.invoke(null, cmd, null, null) as? Process
        } catch (t: Throwable) {
            Log.w(TAG, "Shizuku newProcess reflection failed: ${t.message}")
            null
        }
    }

    private fun findNewProcessMethod(cls: Class<*>): java.lang.reflect.Method {
        val targetTypes = arrayOf(
            Array<String>::class.java,
            Array<String>::class.java,
            String::class.java
        )
        try {
            return cls.getDeclaredMethod("newProcess", *targetTypes)
        } catch (_: NoSuchMethodException) {
        }
        try {
            return cls.getMethod("newProcess", *targetTypes)
        } catch (_: NoSuchMethodException) {
        }
        for (method in cls.declaredMethods + cls.methods) {
            if (method.name == "newProcess" && method.parameterTypes.size == 3) {
                return method
            }
        }
        throw NoSuchMethodException("Shizuku.newProcess not found")
    }

    private fun waitExit(proc: Process, timeoutMs: Long): Int? {
        try {
            return proc.exitValue()
        } catch (_: IllegalThreadStateException) {
        }
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            try {
                return proc.exitValue()
            } catch (_: IllegalThreadStateException) {
            }
            try {
                Thread.sleep(40)
            } catch (_: InterruptedException) {
                return null
            }
        }
        try {
            proc.destroy()
        } catch (_: Throwable) {
        }
        return null
    }

    companion object {
        private const val TAG = "ApexCore.ShellGateway"
        private val blockedReasons = mutableMapOf<ShellResult, String>()

        internal fun markBlocked(result: ShellResult, reason: String) {
            blockedReasons[result] = reason
        }

        fun blockReason(result: ShellResult): String? = blockedReasons[result]
    }
}
