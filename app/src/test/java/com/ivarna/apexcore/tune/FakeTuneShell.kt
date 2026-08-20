package com.ivarna.apexcore.tune

import com.ivarna.apexcore.fps.privilege.PrivilegeTier
import com.ivarna.apexcore.fps.privilege.WriteResult

class FakeTuneShell : TuneShell {
    val existingPaths = mutableSetOf<String>()
    val pathValues = mutableMapOf<String, String>()
    val writtenValues = mutableMapOf<String, String>()
    val writeAttempts = mutableListOf<Triple<String, String, PrivilegeTier>>()
    val failWritePaths = mutableSetOf<String>()
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

    @Synchronized
    override fun exists(path: String, timeoutMs: Long): Boolean {
        return existingPaths.contains(path) || pathValues.containsKey(path)
    }

    @Synchronized
    override fun execute(command: String, tier: PrivilegeTier, timeoutMs: Long): com.ivarna.apexcore.fps.util.ShellResult {
        executedCommands.add(Triple(command, tier, timeoutMs))
        if (failCommands.any { command.contains(it) }) {
            return com.ivarna.apexcore.fps.util.ShellResult("Command failed", exitCode = 1)
        }
        return com.ivarna.apexcore.fps.util.ShellResult("Success", exitCode = 0)
    }
}
