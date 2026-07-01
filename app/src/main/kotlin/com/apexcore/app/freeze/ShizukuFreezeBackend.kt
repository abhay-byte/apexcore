package com.apexcore.app.freeze

import android.content.Context
import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class ShizukuFreezeBackend : FreezeBackend {
    override val name = "Shizuku"
    override val priority = 0

    override suspend fun isReady(): Boolean = withContext(Dispatchers.IO) {
        try {
            val cls = Class.forName("rikka.shizuku.Shizuku")
            val ping = cls.getMethod("pingBinder").invoke(null) as Boolean
            if (!ping) return@withContext false
            if (currentContext == null) return@withContext false
            val permMethod = cls.getMethod("checkSelfPermission", String::class.java)
            val granted = permMethod.invoke(null, "android.permission.ADB") as Int
            granted == 0
        } catch (t: Throwable) {
            Log.d(TAG, "Shizuku not available: ${t.message}")
            false
        }
    }

    override suspend fun execute(op: FreezeOperation): FreezeOperation.Result =
        withContext(Dispatchers.IO) {
            try {
                val cls = Class.forName("rikka.shizuku.Shizuku")
                val newProcess = cls.getMethod(
                    "newProcess",
                    Array<String>::class.java,
                    Array<String>::class.java,
                    String::class.java
                )
                val proc = newProcess.invoke(
                    null,
                    arrayOf("sh"),
                    null,
                    null
                ) as Process
                // Pipe command to stdin (Hail-aligned pattern)
                proc.outputStream.use { os ->
                    os.write("am ${op.name} --user current ${op.pkg}\n".toByteArray())
                    os.flush()
                }
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
                Log.w(TAG, "Shizuku exec failed: ${t.message}")
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
        private const val EXEC_TIMEOUT_MS = 5000L
        private const val POLL_INTERVAL_MS = 50L
        @Volatile var currentContext: Context? = null
    }
}
