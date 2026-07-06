package com.ivarna.apexcore.freeze

import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class RootFreezeBackend : FreezeBackend {
    override val name = "Root"
    override val priority = 1

    @Volatile private var cached: Boolean? = null

    override suspend fun isReady(): Boolean = withContext(Dispatchers.IO) {
        cached?.let { return@withContext it }
        val ok = tryProbe()
        cached = ok
        ok
    }

    private fun tryProbe(): Boolean {
        return try {
            val proc = Runtime.getRuntime().exec(arrayOf("su", "-c", "id"))
            waitForProcess(proc, PROBE_TIMEOUT_MS)
            proc.exitValue() == 0
        } catch (_: Throwable) {
            false
        }
    }

    override suspend fun execute(op: FreezeOperation): FreezeOperation.Result =
        withContext(Dispatchers.IO) {
            try {
                val shellCmd = if (op is FreezeOperation.ShellCommand) {
                    op.pkg
                } else {
                    "am ${op.name} --user current ${op.pkg}"
                }
                val proc = Runtime.getRuntime().exec(
                    arrayOf("su", "-c", shellCmd)
                )
                val ok = waitForProcess(proc, EXEC_TIMEOUT_MS)
                if (!ok) {
                    proc.destroy()
                    return@withContext FreezeOperation.Result.Failure("timeout")
                }
                if (proc.exitValue() == 0) {
                    FreezeOperation.Result.Success
                } else {
                    FreezeOperation.Result.Failure("exit=${proc.exitValue()}")
                }
            } catch (t: Throwable) {
                Log.w(TAG, "Root exec failed: ${t.message}")
                cached = false
                FreezeOperation.Result.Failure(t.message ?: "unknown")
            }
        }

    private fun waitForProcess(proc: Process, timeoutMs: Long): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            proc.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
        } else {
            val deadline = System.currentTimeMillis() + timeoutMs
            while (System.currentTimeMillis() < deadline) {
                try {
                    proc.exitValue()
                    return true
                } catch (_: IllegalThreadStateException) {
                    Thread.sleep(POLL_INTERVAL_MS)
                }
            }
            false
        }
    }

    companion object {
        private const val TAG = "ApexCore.Freeze"
        private const val PROBE_TIMEOUT_MS = 3000L
        private const val EXEC_TIMEOUT_MS = 5000L
        private const val POLL_INTERVAL_MS = 50L
    }
}
