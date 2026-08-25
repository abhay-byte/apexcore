package com.ivarna.apexcore.tune

import com.ivarna.apexcore.fps.privilege.PrivilegeTier
import com.ivarna.apexcore.fps.privilege.WriteResult

class FakeTuneShell : TuneShell {
    val existingPaths = mutableSetOf<String>()
    val pathValues = mutableMapOf<String, String>()
    val writtenValues = mutableMapOf<String, String>()
    val writeAttempts = mutableListOf<Triple<String, String, PrivilegeTier>>()
    val failWritePaths = mutableSetOf<String>()
    val commandOutputs = mutableMapOf<String, String>()
    var sleepOnReadMs: Long = 0L
    var sleepOnWriteMs: Long = 0L

    @Synchronized
    override fun read(path: String, timeoutMs: Long): String? {
        if (sleepOnReadMs > 0) {
            Thread.sleep(sleepOnReadMs)
        }
        return if (existingPaths.contains(path) || pathValues.containsKey(path)) {
            pathValues[path] ?: ""
        } else {
            null
        }
    }

    @Synchronized
    override fun write(path: String, value: String, tier: PrivilegeTier, timeoutMs: Long): WriteResult {
        if (sleepOnWriteMs > 0) {
            Thread.sleep(sleepOnWriteMs)
        }
        writeAttempts.add(Triple(path, value, tier))
        if (failWritePaths.contains(path)) {
            return WriteResult(
                ok = false,
                verified = false,
                readback = null,
                tier = tier,
                error = "EACCES write failed"
            )
        }
        writtenValues[path] = value
        pathValues[path] = value
        existingPaths.add(path)
        return WriteResult(
            ok = true,
            verified = true,
            readback = value,
            tier = tier,
            error = null
        )
    }

    val executedCommands = mutableListOf<Triple<String, PrivilegeTier, Long>>()
    val failCommands = mutableSetOf<String>()
    private val settingsStore = mutableMapOf<String, String>()
    // For precise verification-failure tests: queue overrides for next settings get calls
    val settingsGetSequence = mutableListOf<String>()

    @Synchronized
    override fun exists(path: String, timeoutMs: Long): Boolean {
        return existingPaths.contains(path) || pathValues.containsKey(path)
    }

    @Synchronized
    override fun execute(command: String, tier: PrivilegeTier, timeoutMs: Long): com.ivarna.apexcore.fps.util.ShellResult {
        executedCommands.add(Triple(command, tier, timeoutMs))
        if (failCommands.any { command.contains(it) }) {
            return com.ivarna.apexcore.fps.util.ShellResult("Command failed", exitCode = 1, stderr = null)
        }
        // Stateful emulation for `settings` commands so verified readback works in unit tests.
        if (command.contains("settings ")) {
            // Handle chained commands with &&
            val parts = command.split("&&").map { it.trim() }
            var lastOutput = "Success"
            for (part in parts) {
                when {
                    part.startsWith("settings put") -> {
                        // settings put <namespace> <key> <value>
                        val tokens = part.split(Regex("\\s+"))
                        if (tokens.size >= 5) {
                            val ns = tokens[2]
                            val key = tokens[3]
                            val value = tokens.drop(4).joinToString(" ").trim().removeSurrounding("'").removeSurrounding("\"")
                            settingsStore["$ns:$key"] = value
                        }
                        lastOutput = "Success"
                    }
                    part.startsWith("settings get") -> {
                        val tokens = part.split(Regex("\\s+"))
                        if (tokens.size >= 4) {
                            val ns = tokens[2]
                            val key = tokens[3]
                            lastOutput = if (settingsGetSequence.isNotEmpty()) {
                                settingsGetSequence.removeAt(0)
                            } else {
                                val stored = settingsStore["$ns:$key"] ?: "null"
                                commandOutputs.entries.firstOrNull { part.contains(it.key) }?.value ?: stored
                            }
                        }
                    }
                    part.startsWith("settings delete") -> {
                        val tokens = part.split(Regex("\\s+"))
                        if (tokens.size >= 4) {
                            val ns = tokens[2]
                            val key = tokens[3]
                            settingsStore.remove("$ns:$key")
                        }
                        lastOutput = "Success"
                    }
                    else -> {
                        val output = commandOutputs.entries.firstOrNull { part.contains(it.key) }?.value ?: "Success"
                        lastOutput = output
                    }
                }
                // if any part was a get, return its output immediately when it's the only part
                if (parts.size == 1 && part.startsWith("settings get")) {
                    return com.ivarna.apexcore.fps.util.ShellResult(lastOutput, exitCode = 0, stderr = null)
                }
            }
            // For put chained, return Success; for get chained, return last get output
            if (command.contains("settings get")) {
                return com.ivarna.apexcore.fps.util.ShellResult(lastOutput, exitCode = 0, stderr = null)
            }
            val output = commandOutputs.entries.firstOrNull { command.contains(it.key) }?.value ?: "Success"
            // If command was purely put/delete, prefer Success over settingsStore get result
            if (command.contains("settings put") || command.contains("settings delete")) {
                return com.ivarna.apexcore.fps.util.ShellResult(output, exitCode = 0, stderr = null)
            }
            return com.ivarna.apexcore.fps.util.ShellResult(lastOutput, exitCode = 0, stderr = null)
        }
        val output = commandOutputs.entries.firstOrNull { command.contains(it.key) }?.value ?: "Success"
        return com.ivarna.apexcore.fps.util.ShellResult(output, exitCode = 0, stderr = null)
    }

    fun setSettingsValue(namespace: String, key: String, value: String) {
        settingsStore["$namespace:$key"] = value
    }
}
