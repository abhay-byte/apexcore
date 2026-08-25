package com.ivarna.apexcore.fps.privilege

import android.util.Log
import com.ivarna.apexcore.fps.util.ShellResult
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Compatibility-only bridge for older Shizuku runtimes. New executions go
 * through [ShizukuExecutorClient]; this class can be removed when the minimum
 * supported Shizuku API no longer needs migration support.
 */
internal object LegacyShizukuProcessExecutor {
    fun execute(command: String, timeoutMs: Long): ShellResult {
        return try {
            val process = newProcess(arrayOf("sh", "-c", command))
                ?: return ShellResult("error: legacy newProcess unavailable", -1)
            val output = BufferedReader(InputStreamReader(process.inputStream)).readText().trim()
            val deadline = System.currentTimeMillis() + timeoutMs
            var exit: Int? = null
            while (System.currentTimeMillis() < deadline) {
                try {
                    exit = process.exitValue()
                    break
                } catch (_: IllegalThreadStateException) {
                    Thread.sleep(20L)
                }
            }
            if (exit == null) process.destroy()
            ShellResult(output, exit ?: -1)
        } catch (t: Throwable) {
            Log.w(TAG, "Legacy Shizuku process failed: ${t.message}")
            ShellResult("error: ${t.message}", -1)
        }
    }

    @Suppress("DEPRECATION")
    private fun newProcess(command: Array<String>): Process? {
        return try {
            val method = Class.forName("rikka.shizuku.Shizuku").declaredMethods.firstOrNull {
                it.name == "newProcess" && it.parameterTypes.size == 3
            } ?: return null
            method.isAccessible = true
            method.invoke(null, command, null, null) as? Process
        } catch (_: Throwable) {
            null
        }
    }

    private const val TAG = "ApexCore.ShizukuLegacy"
}
