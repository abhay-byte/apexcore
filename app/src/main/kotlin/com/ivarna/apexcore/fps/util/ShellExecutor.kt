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

    fun execute(command: String, useRoot: Boolean = false): ShellResult {
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
            val output = BufferedReader(InputStreamReader(process.inputStream))
                .readText()
                .trim()
            val exitCode = process.waitFor()
            ShellResult(output, exitCode)
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
