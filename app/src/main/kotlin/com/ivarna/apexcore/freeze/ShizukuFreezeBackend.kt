package com.ivarna.apexcore.freeze

import android.content.pm.PackageManager
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import java.io.ByteArrayOutputStream
import java.io.OutputStream

class ShizukuFreezeBackend : FreezeBackend {
    override val name = "Shizuku"
    override val priority = 0

    override suspend fun isReady(): Boolean = withContext(Dispatchers.IO) {
        try {
            if (!Shizuku.pingBinder()) return@withContext false
            val check = Shizuku.checkSelfPermission()
            check == PackageManager.PERMISSION_GRANTED
        } catch (t: Throwable) {
            Log.d(TAG, "Shizuku not ready: ${t.message}")
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
                val proc = newShizukuProcess(arrayOf("sh", "-c", "$shellCmd 2>&1"))
                    ?: return@withContext FreezeOperation.Result.Failure("newProcess=null")

                val stdout = ByteArrayOutputStream()
                readAll(proc.inputStream, stdout)

                val exit = waitExit(proc, EXEC_TIMEOUT_MS)
                when {
                    exit == null -> FreezeOperation.Result.Failure("timeout")
                    exit == 0 || exit == 255 -> FreezeOperation.Result.Success
                    else -> FreezeOperation.Result.Failure("exit=$exit")
                }
            } catch (t: Throwable) {
                Log.w(TAG, "Shizuku exec failed: ${t.message}")
                FreezeOperation.Result.Failure(t.message ?: "unknown")
            }
        }

    override suspend fun executeMany(ops: List<FreezeOperation>): List<FreezeOperation.Result> =
        withContext(Dispatchers.IO) {
            val forceStopOps = ops.filterIsInstance<FreezeOperation.ForceStop>()
            val otherOps = ops.filter { it !is FreezeOperation.ForceStop }

            val results: ArrayList<FreezeOperation.Result> = ArrayList(ops.map { FreezeOperation.Result.Failure("pending") })

            if (forceStopOps.isNotEmpty()) {
                val script = buildString {
                    appendLine("#!/bin/sh")
                    for ((i, op) in forceStopOps.withIndex()) {
                        appendLine("am force-stop --user current ${op.pkg} 2>&1 && echo OK:$i || echo FAIL:$i")
                    }
                }
                val proc = newShizukuProcess(arrayOf("sh", "-c", script))
                if (proc != null) {
                    val stdout = ByteArrayOutputStream()
                    readAll(proc.inputStream, stdout)
                    waitExit(proc, BATCH_TIMEOUT_MS)

                    val output = stdout.toString()
                    for (line in output.lines()) {
                        when {
                            line.startsWith("OK:") -> {
                                val idx = line.substringAfter("OK:").trim().toIntOrNull()
                                if (idx != null && idx < forceStopOps.size) {
                                    val origIdx = ops.indexOf(forceStopOps[idx])
                                    if (origIdx >= 0) results[origIdx] = FreezeOperation.Result.Success
                                }
                            }
                            line.startsWith("FAIL:") -> {
                                val idx = line.substringAfter("FAIL:").trim().toIntOrNull()
                                if (idx != null && idx < forceStopOps.size) {
                                    val origIdx = ops.indexOf(forceStopOps[idx])
                                    if (origIdx >= 0) results[origIdx] = FreezeOperation.Result.Failure("force-stop-returned-error")
                                }
                            }
                        }
                    }

                    for (i in forceStopOps.indices) {
                        val origIdx = ops.indexOf(forceStopOps[i])
                        if (origIdx >= 0 && results[origIdx].let { it is FreezeOperation.Result.Failure && it.reason == "pending" }) {
                            results[origIdx] = FreezeOperation.Result.Failure("no-output-line")
                        }
                    }
                } else {
                    for (op in forceStopOps) {
                        val idx = ops.indexOf(op)
                        if (idx >= 0) results[idx] = FreezeOperation.Result.Failure("newProcess=null")
                    }
                }
            }

            for (op in otherOps) {
                val idx = ops.indexOf(op)
                if (idx >= 0) {
                    results[idx] = execute(op)
                }
            }

            results
        }

    private fun newShizukuProcess(cmd: Array<String>): Process? {
        return try {
            val cls = Class.forName("rikka.shizuku.Shizuku")
            val method = findNewProcessMethod(cls)
            method.isAccessible = true
            method.invoke(null, cmd, null, null) as? Process
        } catch (t: Throwable) {
            Log.w(TAG, "Shizuku newProcess reflection failed: ${t.message}")
            null
        }
    }

    private fun findNewProcessMethod(cls: Class<*>): java.lang.reflect.Method {
        val targetTypes = arrayOf(Array<String>::class.java, Array<String>::class.java, String::class.java)

        try { return cls.getDeclaredMethod("newProcess", *targetTypes) }
        catch (_: NoSuchMethodException) {}

        try { return cls.getMethod("newProcess", *targetTypes) }
        catch (_: NoSuchMethodException) {}

        for (method in cls.declaredMethods) {
            if (method.name == "newProcess" && method.parameterTypes.size == 3) {
                Log.i(TAG, "Found newProcess with params: ${method.parameterTypes.joinToString()}")
                return method
            }
        }

        for (method in cls.methods) {
            if (method.name == "newProcess" && method.parameterTypes.size == 3) {
                Log.i(TAG, "Found public newProcess with params: ${method.parameterTypes.joinToString()}")
                return method
            }
        }

        throw NoSuchMethodException("rikka.shizuku.Shizuku.newProcess with 3 args not found")
    }

    private fun readAll(input: java.io.InputStream, output: OutputStream) {
        try {
            val buf = ByteArray(8192)
            var n: Int
            while (input.read(buf).also { n = it } != -1) {
                output.write(buf, 0, n)
            }
        } catch (_: Throwable) {}
    }

    private fun waitExit(proc: Process, timeoutMs: Long): Int? {
        try {
            return proc.exitValue()
        } catch (_: IllegalThreadStateException) {
        } catch (_: IllegalArgumentException) {
        } catch (_: Throwable) {}

        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            try {
                return proc.exitValue()
            } catch (_: IllegalThreadStateException) {
            } catch (_: IllegalArgumentException) {
            } catch (_: Throwable) {
                return null
            }
            try {
                Thread.sleep(POLL_INTERVAL_MS)
            } catch (_: InterruptedException) {
                return null
            }
        }
        try { proc.destroy() } catch (_: Throwable) {}
        return null
    }

    companion object {
        private const val TAG = "ApexCore.Freeze"
        private const val EXEC_TIMEOUT_MS = 5000L
        private const val BATCH_TIMEOUT_MS = 15000L
        private const val POLL_INTERVAL_MS = 50L
    }
}
