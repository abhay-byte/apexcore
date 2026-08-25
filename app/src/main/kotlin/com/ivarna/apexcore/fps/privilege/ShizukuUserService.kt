package com.ivarna.apexcore.fps.privilege

import android.os.Bundle
import android.system.Os
import androidx.annotation.Keep
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

/**
 * Code that Shizuku starts in its root (UID 0) or ADB shell (UID 2000)
 * process. It deliberately exposes only a bounded, output-returning command
 * operation; it is not an interactive terminal or a general app service.
 */
@Keep
class ShizukuUserService : IPrivilegedExecutor.Stub() {

    override fun execute(command: String, timeoutMs: Long): Bundle {
        val safeTimeout = timeoutMs.coerceIn(50L, 30_000L)
        return try {
            val process = ProcessBuilder("sh", "-c", command)
                .redirectErrorStream(true)
                .start()
            val output = StringBuilder()
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val readerThread = Thread {
                try {
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        if (output.isNotEmpty()) output.append('\n')
                        output.append(line)
                    }
                } catch (_: Throwable) {
                    // The process result still carries the useful timeout/exit code.
                }
            }.apply { isDaemon = true }
            readerThread.start()

            val finished = process.waitFor(safeTimeout, TimeUnit.MILLISECONDS)
            if (!finished) {
                process.destroy()
                process.destroyForcibly()
                readerThread.interrupt()
                return Bundle().apply {
                    putString(KEY_OUTPUT, "error: timeout")
                    putInt(KEY_EXIT_CODE, -1)
                }
            }

            readerThread.join(200L)
            Bundle().apply {
                putString(KEY_OUTPUT, output.toString().trim())
                putInt(KEY_EXIT_CODE, process.exitValue())
            }
        } catch (t: Throwable) {
            Bundle().apply {
                putString(KEY_OUTPUT, "error: ${t.message ?: "executor failure"}")
                putInt(KEY_EXIT_CODE, -1)
            }
        }
    }

    override fun uid(): Int = try {
        Os.getuid()
    } catch (_: Throwable) {
        -1
    }

    override fun destroy() {
        System.exit(0)
    }

    companion object {
        const val KEY_OUTPUT = "output"
        const val KEY_EXIT_CODE = "exitCode"
    }
}
