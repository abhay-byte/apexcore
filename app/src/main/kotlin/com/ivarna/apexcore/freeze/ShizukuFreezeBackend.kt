package com.ivarna.apexcore.freeze

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import com.ivarna.apexcore.fps.privilege.LegacyShizukuProcessExecutor
import com.ivarna.apexcore.fps.privilege.ShizukuExecutorClient
import com.ivarna.apexcore.fps.util.ShellResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku

/** Freeze backend sharing the same UserService executor as tuning. */
class ShizukuFreezeBackend(context: Context? = null) : FreezeBackend {
    override val name = "Shizuku"
    override val priority = 0
    private val executor = context?.let { ShizukuExecutorClient(it) }

    override suspend fun isReady(): Boolean = withContext(Dispatchers.IO) {
        try {
            Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (t: Throwable) {
            Log.w(TAG, "Shizuku readiness failed: ${t.message}")
            false
        }
    }

    override suspend fun execute(op: FreezeOperation): FreezeOperation.Result = withContext(Dispatchers.IO) {
        val result = runCommand(commandFor(op), EXEC_TIMEOUT_MS)
        when {
            result.exitCode == 0 || result.exitCode == 255 -> FreezeOperation.Result.Success
            result.exitCode == -1 && result.output.contains("timeout") -> FreezeOperation.Result.Failure("timeout")
            else -> FreezeOperation.Result.Failure("exit=${result.exitCode}")
        }
    }

    override suspend fun executeMany(ops: List<FreezeOperation>): List<FreezeOperation.Result> = withContext(Dispatchers.IO) {
        if (ops.isEmpty()) return@withContext emptyList()
        val script = buildString {
            appendLine("#!/system/bin/sh")
            ops.forEachIndexed { index, op ->
                appendLine("${commandFor(op)} 2>&1 && echo OK:$index || echo FAIL:$index")
            }
        }
        val output = runCommand(script, BATCH_TIMEOUT_MS).output
        ops.mapIndexed { index, _ ->
            if (output.lineSequence().any { it.trim() == "OK:$index" }) {
                FreezeOperation.Result.Success
            } else {
                FreezeOperation.Result.Failure("no-output-line")
            }
        }
    }

    override suspend fun executeWithOutput(cmd: String): String = withContext(Dispatchers.IO) {
        runCommand(cmd, EXEC_TIMEOUT_MS).output
    }

    private fun commandFor(op: FreezeOperation): String = when (op) {
        is FreezeOperation.ShellCommand -> op.pkg
        else -> "am ${op.name} --user current ${op.pkg}"
    }

    private fun runCommand(command: String, timeoutMs: Long): ShellResult {
        if (executor != null) return executor.execute(command, timeoutMs)
        // Only old/no-context construction uses the compatibility route.
        return LegacyShizukuProcessExecutor.execute(command, timeoutMs)
    }

    companion object {
        private const val TAG = "ApexCore.Freeze"
        private const val EXEC_TIMEOUT_MS = 5_000L
        private const val BATCH_TIMEOUT_MS = 15_000L
    }
}
