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
                .redirectErrorStream(false)
                .start()
            val stdout = StringBuilder()
            val stderr = StringBuilder()
            val outReader = BufferedReader(InputStreamReader(process.inputStream))
            val errReader = BufferedReader(InputStreamReader(process.errorStream))
            val outThread = Thread {
                try {
                    var line: String?
                    while (outReader.readLine().also { line = it } != null) {
                        if (stdout.isNotEmpty()) stdout.append('\n')
                        stdout.append(line)
                    }
                } catch (_: Throwable) {}
            }.apply { isDaemon = true }
            val errThread = Thread {
                try {
                    var line: String?
                    while (errReader.readLine().also { line = it } != null) {
                        if (stderr.isNotEmpty()) stderr.append('\n')
                        stderr.append(line)
                    }
                } catch (_: Throwable) {}
            }.apply { isDaemon = true }
            outThread.start()
            errThread.start()

            val finished = process.waitFor(safeTimeout, TimeUnit.MILLISECONDS)
            if (!finished) {
                process.destroy()
                process.destroyForcibly()
                outThread.interrupt()
                errThread.interrupt()
                return Bundle().apply {
                    putString(KEY_OUTPUT, "error: timeout")
                    putString(KEY_STDOUT, "error: timeout")
                    putString(KEY_STDERR, "")
                    putInt(KEY_EXIT_CODE, -1)
                }
            }

            outThread.join(200L)
            errThread.join(200L)
            val outStr = stdout.toString().trim()
            val errStr = stderr.toString().trim()
            Bundle().apply {
                putString(KEY_STDOUT, outStr)
                putString(KEY_STDERR, errStr)
                putString(KEY_OUTPUT, if (errStr.isNotEmpty()) "$outStr\n[stderr] $errStr".trim() else outStr)
                putInt(KEY_EXIT_CODE, process.exitValue())
            }
        } catch (t: Throwable) {
            Bundle().apply {
                putString(KEY_OUTPUT, "error: ${t.message ?: "executor failure"}")
                putString(KEY_STDOUT, "error: ${t.message ?: "executor failure"}")
                putString(KEY_STDERR, "")
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
        const val KEY_STDOUT = "stdout"
        const val KEY_STDERR = "stderr"
        const val KEY_EXIT_CODE = "exitCode"
    }
}
