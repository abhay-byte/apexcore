package com.ivarna.apexcore.fps.privilege

import android.content.pm.PackageManager
import android.util.Log
import com.ivarna.apexcore.fps.util.ShellExecutor
import com.ivarna.apexcore.fps.util.ShellResult
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

data class WriteResult(
    val ok: Boolean,
    val verified: Boolean,
    val readback: String?,
    val tier: PrivilegeTier?,
    val error: String? = null
)

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
    val mutex = Mutex()

    fun canRoot(): Boolean = shellExecutor.isSuAvailable()

    fun canShizuku(): Boolean {
        return try {
            if (!rikka.shizuku.Shizuku.pingBinder()) return false
            rikka.shizuku.Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (_: Throwable) {
            false
        }
    }

    fun canStandard(): Boolean = true

    fun currentPolicy(): PrivilegePolicy = PrivilegePolicy(store.mode.value)

    fun execute(command: String, tier: PrivilegeTier, timeoutMs: Long = 8_000L): ShellResult = kotlinx.coroutines.runBlocking {
        mutex.withLock {
            when (tier) {
                PrivilegeTier.ROOT -> {
                    if (!canRoot()) {
                        return@withLock ShellResult("", exitCode = -1).also { markBlocked(it, "no_su") }
                    }
                    shellExecutor.execute(command, useRoot = true, timeoutMs = timeoutMs)
                }
                PrivilegeTier.SHIZUKU -> {
                    if (!canShizuku()) {
                        return@withLock ShellResult("", exitCode = -1).also { markBlocked(it, "no_shizuku") }
                    }
                    executeViaShizuku(command, timeoutMs = timeoutMs)
                }
                PrivilegeTier.STANDARD -> {
                    shellExecutor.execute(command, useRoot = false, timeoutMs = timeoutMs)
                }
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

    fun readPathDirect(path: String, tier: PrivilegeTier, timeoutMs: Long = 120L): String? {
        if (tier == PrivilegeTier.STANDARD) {
            return try {
                val f = File(path)
                if (f.canRead()) f.readText().trim().takeIf { it.isNotEmpty() } else null
            } catch (_: Throwable) {
                null
            }
        }
        val result = execute("cat \"$path\" 2>/dev/null", tier, timeoutMs)
        return if (result.isSuccess && result.output.isNotBlank()) {
            result.output.trim()
        } else {
            null
        }
    }

    fun exists(path: String, tier: PrivilegeTier, timeoutMs: Long = 120L): Boolean {
        if (tier == PrivilegeTier.STANDARD) {
            return try {
                File(path).exists()
            } catch (_: Throwable) {
                false
            }
        }
        val res = execute("[ -e \"$path\" ] && echo 1 || echo 0", tier, timeoutMs)
        return res.isSuccess && res.output.trim() == "1"
    }

    fun writePath(path: String, value: String, tier: PrivilegeTier, timeoutMs: Long = 400L): WriteResult {
        if (!PATH_REGEX.matches(path)) {
            return WriteResult(
                ok = false,
                verified = false,
                readback = null,
                tier = tier,
                error = "Path rejected by security policy"
            )
        }
        if (!VALUE_REGEX.matches(value)) {
            return WriteResult(
                ok = false,
                verified = false,
                readback = null,
                tier = tier,
                error = "Value rejected by security policy"
            )
        }

        return when (tier) {
            PrivilegeTier.STANDARD -> {
                WriteResult(
                    ok = false,
                    verified = false,
                    readback = null,
                    tier = tier,
                    error = "Standard tier cannot write kernel paths"
                )
            }
            PrivilegeTier.ROOT -> {
                val cmd = "mode=$(stat -c '%a' '$path' 2>/dev/null || echo \"\"); " +
                        "chmod 644 '$path' 2>/dev/null; " +
                        "printf '%s\\n' '$value' > '$path'; " +
                        "rc=\$?; " +
                        "readback=\$(cat '$path' 2>/dev/null | tr -d '\\n'); " +
                        "[ -n \"\$mode\" ] && chmod \"\$mode\" '$path' 2>/dev/null; " +
                        "printf 'RC=%s READBACK=%s\\n' \"\$rc\" \"\$readback\""
                val res = execute(cmd, tier, timeoutMs)
                parseWriteOutput(res, value, tier)
            }
            PrivilegeTier.SHIZUKU -> {
                val cmd = "printf '%s\\n' '$value' > '$path'; " +
                        "rc=\$?; " +
                        "readback=\$(cat '$path' 2>/dev/null | tr -d '\\n'); " +
                        "printf 'RC=%s READBACK=%s\\n' \"\$rc\" \"\$readback\""
                val res = execute(cmd, tier, timeoutMs)
                parseWriteOutput(res, value, tier)
            }
        }
    }

    private fun parseWriteOutput(res: ShellResult, expectedValue: String, tier: PrivilegeTier): WriteResult {
        if (!res.isSuccess) {
            return WriteResult(
                ok = false,
                verified = false,
                readback = null,
                tier = tier,
                error = res.output.ifBlank { "Command failed with code ${res.exitCode}" }
            )
        }
        val out = res.output.trim()
        val rcMatch = Regex("""RC=(\d+)""").find(out)
        val rc = rcMatch?.groupValues?.get(1)?.toIntOrNull() ?: -1
        val readbackMatch = Regex("""READBACK=(.*)""").find(out)
        val readback = readbackMatch?.groupValues?.get(1)?.trim()

        val ok = (rc == 0)
        val verified = ok && (readback == expectedValue || (readback != null && readback.contains(expectedValue)))
        return WriteResult(
            ok = ok,
            verified = verified,
            readback = readback,
            tier = tier,
            error = if (!ok) "Exit code $rc" else null
        )
    }

    fun clearCache() {}

    private fun executeViaShizuku(command: String, timeoutMs: Long = 8_000L): ShellResult {
        return try {
            val proc = newShizukuProcess(arrayOf("sh", "-c", command))
                ?: return ShellResult("error: newProcess=null", -1)
            val output = BufferedReader(InputStreamReader(proc.inputStream))
                .readText()
                .trim()
            val exit = waitExit(proc, timeoutMs) ?: -1
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
                Thread.sleep(20)
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
        private val PATH_REGEX = Regex("""^/(sys|dev|proc)/[A-Za-z0-9/_.:=-]+$""")
        private val VALUE_REGEX = Regex("""^[A-Za-z0-9_.:-]+$""")
        private val blockedReasons = mutableMapOf<ShellResult, String>()

        internal fun markBlocked(result: ShellResult, reason: String) {
            blockedReasons[result] = reason
        }

        fun blockReason(result: ShellResult): String? = blockedReasons[result]
    }
}

