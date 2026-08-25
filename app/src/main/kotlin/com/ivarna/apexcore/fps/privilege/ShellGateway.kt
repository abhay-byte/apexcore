package com.ivarna.apexcore.fps.privilege

import android.content.Context
import android.content.pm.PackageManager
import com.ivarna.apexcore.fps.util.ShellExecutor
import com.ivarna.apexcore.fps.util.ShellResult
import com.ivarna.apexcore.tune.MutationFailure
import com.ivarna.apexcore.tune.VerificationMode
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import rikka.shizuku.Shizuku

data class WriteResult(
    val ok: Boolean,
    val verified: Boolean,
    val readback: String?,
    val tier: PrivilegeTier?,
    val error: String? = null,
    val verificationMode: VerificationMode = VerificationMode.EXACT_STRING
)

/** Tier-aware command/mutation gateway. UserService is the primary Shizuku path. */
class ShellGateway(
    private val shellExecutor: ShellExecutor,
    private val store: PrivilegeModeStore,
    context: Context? = null
) {
    val mutex = Mutex()
    private val shizukuExecutor = context?.let { ShizukuExecutorClient(it) }

    fun canRoot(): Boolean = rootEffectiveUid() == 0

    fun rootEffectiveUid(): Int? {
        if (!shellExecutor.isSuAvailable()) return null
        return try {
            val result = shellExecutor.execute("id -u", useRoot = true, timeoutMs = ROOT_PROBE_TIMEOUT_MS)
            if (result.isSuccess) result.output.trim().lineSequence().firstOrNull()?.toIntOrNull() else null
        } catch (_: Throwable) { null }
    }

    fun canShizuku(): Boolean = try {
        Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (_: Throwable) { false }

    fun shizukuUid(): Int? {
        if (!canShizuku()) return null
        val uid = try { Shizuku.getUid() } catch (_: Throwable) { -1 }
        return if (uid >= 0) uid else shizukuExecutor?.uid()
    }

    fun shizukuTier(): PrivilegeTier? = when (shizukuUid()) {
        0 -> PrivilegeTier.SHIZUKU_ROOT
        2000 -> PrivilegeTier.SHIZUKU_SHELL
        null -> null
        else -> PrivilegeTier.SHIZUKU_SHELL
    }

    fun currentPolicy(): PrivilegePolicy = PrivilegePolicy(store.mode.value)

    fun execute(command: String, tier: PrivilegeTier, timeoutMs: Long = 8_000L): ShellResult = runBlocking {
        mutex.withLock {
            when (tier) {
                PrivilegeTier.SU_ROOT -> {
                    if (!canRoot()) return@withLock ShellResult("error: no effective uid 0", -1)
                    shellExecutor.execute(command, useRoot = true, timeoutMs = timeoutMs)
                }
                PrivilegeTier.SHIZUKU_ROOT, PrivilegeTier.SHIZUKU_SHELL -> {
                    if (!canShizuku()) return@withLock ShellResult("error: no Shizuku permission", -1)
                    executeViaShizuku(command, timeoutMs)
                }
                PrivilegeTier.STANDARD -> shellExecutor.execute(command, useRoot = false, timeoutMs = timeoutMs)
            }
        }
    }

    fun executeChain(command: String, chain: List<PrivilegeTier>): Pair<ShellResult, PrivilegeTier?> {
        var lastResult = ShellResult("", -1)
        var lastTier: PrivilegeTier? = null
        for (tier in chain) {
            lastTier = tier
            lastResult = execute(command, tier)
            if (lastResult.isSuccess) return lastResult to tier
        }
        return lastResult to lastTier
    }

    fun readPath(path: String, defaultChain: List<PrivilegeTier> = PrivilegePolicy.DEFAULT_CHAIN): Pair<String?, PrivilegeTier?> {
        val chain = currentPolicy().chain(defaultChain)
        if (chain.contains(PrivilegeTier.STANDARD)) {
            try {
                val value = File(path).takeIf { it.canRead() }?.readText()?.trim()?.takeIf { it.isNotEmpty() }
                if (value != null) return value to PrivilegeTier.STANDARD
            } catch (_: Throwable) { }
        }
        for (tier in chain) {
            val result = execute("cat \"$path\" 2>/dev/null", tier)
            if (result.isSuccess && result.output.isNotBlank()) return result.output.trim() to tier
        }
        return null to null
    }

    fun readPathText(path: String, defaultChain: List<PrivilegeTier> = PrivilegePolicy.DEFAULT_CHAIN): String? = readPath(path, defaultChain).first

    fun readPathDirect(path: String, tier: PrivilegeTier, timeoutMs: Long = 120L): String? {
        if (tier == PrivilegeTier.STANDARD) {
            return try { File(path).takeIf { it.canRead() }?.readText()?.trim()?.takeIf { it.isNotEmpty() } } catch (_: Throwable) { null }
        }
        val result = execute("cat \"$path\" 2>/dev/null", tier, timeoutMs)
        return result.output.trim().takeIf { result.isSuccess && it.isNotEmpty() }
    }

    fun exists(path: String, tier: PrivilegeTier, timeoutMs: Long = 120L): Boolean {
        if (tier == PrivilegeTier.STANDARD) return try { File(path).exists() } catch (_: Throwable) { false }
        val result = execute("[ -e \"$path\" ] && echo 1 || echo 0", tier, timeoutMs)
        return result.isSuccess && result.output.trim() == "1"
    }

    fun writePath(
        path: String,
        value: String,
        tier: PrivilegeTier,
        timeoutMs: Long = 400L,
        verificationMode: VerificationMode = inferVerificationMode(value)
    ): WriteResult {
        if (!PATH_REGEX.matches(path)) return failure(tier, verificationMode, "Path rejected", MutationFailure.INVALID_PATH)
        if (!VALUE_REGEX.matches(value)) return failure(tier, verificationMode, "Value rejected", MutationFailure.INVALID_VALUE)
        if (tier == PrivilegeTier.STANDARD) return failure(tier, verificationMode, "Standard tier cannot write", MutationFailure.NO_PERMISSION)
        val command = if (tier == PrivilegeTier.SU_ROOT || tier == PrivilegeTier.SHIZUKU_ROOT) {
            rootWriteCommand(path, value)
        } else {
            shellWriteCommand(path, value)
        }
        return parseWriteOutput(execute(command, tier, timeoutMs), value, tier, verificationMode)
    }

    fun closeShizukuExecutor() = shizukuExecutor?.close()

    private fun rootWriteCommand(path: String, value: String): String =
        "mode=\$(stat -c '%a' '$path' 2>/dev/null || echo ''); " +
            "printf '%s\\n' '$value' > '$path'; rc=\$?; " +
            "if [ \"\$rc\" -ne 0 ] && [ -n \"\$mode\" ]; then " +
            "chmod 644 '$path' 2>/dev/null; printf '%s\\n' '$value' > '$path'; rc=\$?; " +
            "chmod \"\$mode\" '$path' 2>/dev/null; fi; " +
            "readback=\$(cat '$path' 2>/dev/null | tr -d '\\n'); " +
            "printf 'RC=%s READBACK=%s\\n' \"\$rc\" \"\$readback\""

    private fun shellWriteCommand(path: String, value: String): String =
        "printf '%s\\n' '$value' > '$path'; rc=\$?; " +
            "readback=\$(cat '$path' 2>/dev/null | tr -d '\\n'); " +
            "printf 'RC=%s READBACK=%s\\n' \"\$rc\" \"\$readback\""

    private fun parseWriteOutput(result: ShellResult, expected: String, tier: PrivilegeTier, mode: VerificationMode): WriteResult {
        if (!result.isSuccess) return failure(tier, mode, result.output.ifBlank { "command exit ${result.exitCode}" }, MutationFailure.COMMAND_FAILED)
        val output = result.output.trim()
        val rc = Regex("""(?:^|\\s)RC=(\\d+)""").find(output)?.groupValues?.get(1)?.toIntOrNull() ?: -1
        val readback = Regex("""(?:^|\\s)READBACK=(.*)""").find(output)?.groupValues?.get(1)?.trim()
        val commandOk = rc == 0
        val verified = commandOk && valuesMatch(expected, readback, mode)
        return WriteResult(
            ok = commandOk,
            verified = verified,
            readback = readback,
            tier = tier,
            error = when {
                !commandOk -> "Exit code $rc"
                !verified -> "Readback did not match requested value"
                else -> null
            },
            verificationMode = mode
        )
    }

    private fun valuesMatch(expected: String, actual: String?, mode: VerificationMode): Boolean {
        if (actual == null) return false
        val e = expected.trim()
        val a = actual.trim()
        return when (mode) {
            VerificationMode.EXACT_INT -> e.toBigIntegerOrNull() != null && a.toBigIntegerOrNull() == e.toBigIntegerOrNull()
            VerificationMode.BOOLEAN_NORMALIZED -> normalizeBoolean(e) != null && normalizeBoolean(e) == normalizeBoolean(a)
            VerificationMode.IO_SCHEDULER_ACTIVE_TOKEN -> activeScheduler(a) == activeScheduler(e)
            VerificationMode.EXACT_STRING, VerificationMode.GOVERNOR_TOKEN,
            VerificationMode.SETTINGS_VALUE, VerificationMode.CUSTOM -> a == e
        }
    }

    private fun normalizeBoolean(value: String): String? = when (value.trim().lowercase()) {
        "1", "y", "yes", "true", "on", "enabled" -> "1"
        "0", "n", "no", "false", "off", "disabled" -> "0"
        else -> null
    }

    private fun activeScheduler(value: String): String = Regex("""\\[([^]]+)]""").find(value)?.groupValues?.get(1)?.trim() ?: value.trim()

    private fun inferVerificationMode(value: String): VerificationMode = if (value.toBigIntegerOrNull() != null) VerificationMode.EXACT_INT else VerificationMode.EXACT_STRING

    private fun failure(tier: PrivilegeTier, mode: VerificationMode, message: String, failure: MutationFailure) =
        WriteResult(false, false, null, tier, "$message (${failure.name})", mode)

    private fun executeViaShizuku(command: String, timeoutMs: Long): ShellResult {
        shizukuExecutor?.let { client ->
            val result = client.execute(command, timeoutMs)
            if (result.exitCode != -1 || !result.output.contains("user service unavailable")) return result
        }
        // Compatibility-only migration route; UserService is always attempted first.
        return LegacyShizukuProcessExecutor.execute(command, timeoutMs)
    }

    companion object {
        private const val ROOT_PROBE_TIMEOUT_MS = 1_500L
        private val PATH_REGEX = Regex("""^/(sys|dev|proc)/[A-Za-z0-9/_.:=-]+$""")
        private val VALUE_REGEX = Regex("""^[A-Za-z0-9_.:-]+$""")
    }
}
