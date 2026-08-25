package com.ivarna.apexcore.fps.privilege

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import com.ivarna.apexcore.fps.util.ShellResult
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import rikka.shizuku.Shizuku

/** Reusable client for ApexCore's Shizuku UserService executor. */
class ShizukuExecutorClient(context: Context) {
    private val appContext = context.applicationContext
    private val lock = Any()
    private val args = Shizuku.UserServiceArgs(
        ComponentName(appContext, ShizukuUserService::class.java)
    ).daemon(true).tag(TAG).version(SERVICE_VERSION).processNameSuffix("apexcore-exec")

    @Volatile
    private var remote: IPrivilegedExecutor? = null
    @Volatile
    private var connection: ServiceConnection? = null

    fun uid(timeoutMs: Long = BIND_TIMEOUT_MS): Int? {
        val executor = ensureBound(timeoutMs) ?: return null
        return try {
            executor.uid().takeIf { it >= 0 }
        } catch (t: Throwable) {
            Log.w(TAG, "Unable to query UserService uid: ${t.message}")
            invalidate()
            null
        }
    }

    fun execute(command: String, timeoutMs: Long): ShellResult {
        val executor = ensureBound(timeoutMs.coerceAtMost(BIND_TIMEOUT_MS))
            ?: return ShellResult("error: user service unavailable", -1)
        return try {
            val result = executor.execute(command, timeoutMs)
            ShellResult(
                result.getString(ShizukuUserService.KEY_OUTPUT).orEmpty(),
                result.getInt(ShizukuUserService.KEY_EXIT_CODE, -1)
            )
        } catch (t: Throwable) {
            Log.w(TAG, "UserService execution failed: ${t.message}")
            invalidate()
            ShellResult("error: ${t.message ?: "user service failure"}", -1)
        }
    }

    fun close() {
        synchronized(lock) {
            val currentConnection = connection
            remote = null
            connection = null
            if (currentConnection != null) {
                try {
                    Shizuku.unbindUserService(args, currentConnection, true)
                } catch (t: Throwable) {
                    Log.d(TAG, "UserService unbind skipped: ${t.message}")
                }
            }
        }
    }

    private fun ensureBound(timeoutMs: Long): IPrivilegedExecutor? {
        remote?.let { return it }
        synchronized(lock) {
            remote?.let { return it }
            if (!Shizuku.pingBinder()) return null

            val latch = CountDownLatch(1)
            val serviceConnection = object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                    remote = IPrivilegedExecutor.Stub.asInterface(service)
                    latch.countDown()
                }

                override fun onServiceDisconnected(name: ComponentName?) {
                    invalidate()
                }

                override fun onBindingDied(name: ComponentName?) {
                    invalidate()
                }

                override fun onNullBinding(name: ComponentName?) {
                    latch.countDown()
                }
            }
            connection = serviceConnection
            return try {
                Shizuku.bindUserService(args, serviceConnection)
                latch.await(timeoutMs.coerceIn(100L, BIND_TIMEOUT_MS), TimeUnit.MILLISECONDS)
                remote
            } catch (t: Throwable) {
                Log.w(TAG, "UserService bind failed: ${t.message}")
                connection = null
                null
            }
        }
    }

    private fun invalidate() {
        synchronized(lock) {
            remote = null
        }
    }

    companion object {
        private const val TAG = "ApexCore.ShizukuExec"
        private const val SERVICE_VERSION = 1
        private const val BIND_TIMEOUT_MS = 2_500L
    }
}
