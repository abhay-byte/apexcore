package com.ivarna.apexcore.fps.util

import java.io.BufferedReader
import java.io.InputStreamReader

data class ShellResult(
    val output: String,
    val exitCode: Int
) {
    val isSuccess: Boolean get() = exitCode == 0
}

/**
 * Ported from factualstats ShellExecutor — su / sh shell runner.
 */
class ShellExecutor {
    private var hasSu: Boolean? = null

    fun execute(command: String, useRoot: Boolean = false, timeoutMs: Long = 8_000L): ShellResult {
        val useSu = useRoot && checkIfSuAvailable()
        val shellCmd = if (useSu) {
            listOf("su", "-c", command)
        } else {
            listOf("sh", "-c", command)
        }
        return try {
            val process = ProcessBuilder()
                .command(shellCmd)
                .redirectErrorStream(true)
                .start()

            val outputBuilder = StringBuilder()
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val readerThread = Thread {
                try {
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        if (outputBuilder.isNotEmpty()) outputBuilder.append("\n")
                        outputBuilder.append(line)
                    }
                } catch (_: Throwable) {}
            }
            readerThread.start()

            val deadline = System.currentTimeMillis() + timeoutMs
            var finished = false
            while (System.currentTimeMillis() < deadline) {
                try {
                    process.exitValue()
                    finished = true
                    break
                } catch (_: IllegalThreadStateException) {
                    try {
                        Thread.sleep(15)
                    } catch (_: InterruptedException) {
                        break
                    }
                }
            }

            if (!finished) {
                try {
                    process.destroy()
                } catch (_: Throwable) {}
                try {
                    Thread.sleep(200)
                } catch (_: InterruptedException) {}
                try {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                        process.destroyForcibly()
                    }
                } catch (_: Throwable) {}
                try {
                    readerThread.interrupt()
                    reader.close()
                } catch (_: Throwable) {}
                return ShellResult("error: timeout", -1)
            }

            readerThread.join(200)
            try {
                reader.close()
            } catch (_: Throwable) {}
            val exitCode = process.exitValue()
            ShellResult(outputBuilder.toString().trim(), exitCode)
        } catch (e: Exception) {
            ShellResult("error: ${e.message}", -1)
        }
    }

    fun isSuAvailable(): Boolean = checkIfSuAvailable()

    fun checkIfSuAvailable(): Boolean {
        hasSu?.let { return it }
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-v"))
            process.destroy()
            hasSu = true
            true
        } catch (_: Exception) {
            hasSu = false
            false
        }
    }
}
