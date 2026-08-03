package com.ivarna.apexcore.fps.source

import android.content.Context
import com.ivarna.apexcore.fps.privilege.ShellGateway
import com.ivarna.apexcore.fps.privilege.PrivilegeTier
import java.io.File

class FpsDaemonManager(
    private val context: Context,
    private val shellGateway: ShellGateway
) {
    private val daemonPath = "/data/local/tmp/apexcore_fps_daemon.sh"
    private val countAwkPath = "/data/local/tmp/apexcore_fps_count.awk"
    private val pidPath = "/data/local/tmp/apexcore_fps_daemon.pid"
    private val logPath = "/data/local/tmp/apexcore_fps.log"
    private val hashPath = "/data/local/tmp/apexcore_fps_daemon.hash"

    @Volatile
    private var running = false

    fun ensureStarted(): Boolean {
        val policy = shellGateway.currentPolicy()
        if (PrivilegeTier.ROOT !in policy.chain(listOf(PrivilegeTier.ROOT, PrivilegeTier.SHIZUKU, PrivilegeTier.STANDARD))) return false
        if (!shellGateway.canRoot()) return false

        // Always refresh scripts so APK updates (hybrid FPS path) take effect.
        deployScripts()
        val wantHash = scriptHash()
        val haveHash = shellGateway.execute("cat $hashPath 2>/dev/null", PrivilegeTier.ROOT)
            .output.trim()

        // Running process keeps old script in memory — restart if asset hash changed.
        if (isDaemonAlive() && wantHash.isNotEmpty() && wantHash == haveHash) {
            running = true
            return true
        }

        stopExistingDaemon()
        // Also nuke orphan trace consumers that starve our sampler
        shellGateway.execute(
            "for p in \$(ps -A -o PID=); do " +
                "cmd=\$(tr '\\0' ' ' < /proc/\$p/cmdline 2>/dev/null) || continue; " +
                "case \"\$cmd\" in *trace_pipe*|*apexcore_fps*|*adb_app_fps*) " +
                "kill -9 \$p 2>/dev/null;; esac; done",
            PrivilegeTier.ROOT
        )
        val startCmd =
            "setsid sh $daemonPath > $logPath 2>&1 < /dev/null & " +
                "echo \$! > $pidPath; " +
                "echo $wantHash > $hashPath; " +
                "sleep 0.4; " +
                "kill -0 \$(cat $pidPath) 2>/dev/null && echo started"
        val result = shellGateway.execute(startCmd, PrivilegeTier.ROOT)
        running = result.isSuccess && result.output.contains("started")
        return running && isDaemonAlive()
    }

    private fun scriptHash(): String =
        shellGateway.execute("md5sum $daemonPath 2>/dev/null | cut -d' ' -f1", PrivilegeTier.ROOT)
            .output.trim()

    fun stop() {
        stopExistingDaemon()
        running = false
    }

    private fun stopExistingDaemon() {
        val pidResult = shellGateway.execute("cat $pidPath 2>/dev/null", PrivilegeTier.ROOT)
        val pid = pidResult.output.trim()
        if (pid.isNotBlank()) {
            // Kill process group: daemon + timeout/cat child holding trace_pipe
            shellGateway.execute(
                "kill -TERM -$pid 2>/dev/null; kill -TERM $pid 2>/dev/null; " +
                    "sleep 0.15; kill -KILL -$pid 2>/dev/null; kill -KILL $pid 2>/dev/null",
                PrivilegeTier.ROOT
            )
        }
        shellGateway.execute("rm -f $pidPath", PrivilegeTier.ROOT)
    }

    fun isRunning(): Boolean = running && isDaemonAlive()

    private fun isDaemonAlive(): Boolean {
        val pidResult = shellGateway.execute("cat $pidPath 2>/dev/null", PrivilegeTier.ROOT)
        val pid = pidResult.output.trim()
        if (pid.isBlank()) return false
        val check = shellGateway.execute("kill -0 $pid 2>/dev/null && echo alive", PrivilegeTier.ROOT)
        return check.output.contains("alive")
    }

    private fun deployScripts() {
        deployAsset("scripts/fps_daemon.sh", daemonPath, executable = true)
        deployAsset("scripts/fps_count.awk", countAwkPath, executable = false)
    }

    private fun deployAsset(assetPath: String, remotePath: String, executable: Boolean) {
        context.assets.open(assetPath).use { input ->
            val name = File(assetPath).name
            val tmp = File(context.cacheDir, name)
            tmp.writeBytes(input.readBytes())
            shellGateway.execute("cp ${tmp.absolutePath} $remotePath", PrivilegeTier.ROOT)
            if (executable) {
                shellGateway.execute("chmod 755 $remotePath", PrivilegeTier.ROOT)
            } else {
                shellGateway.execute("chmod 644 $remotePath", PrivilegeTier.ROOT)
            }
        }
    }
}
