package com.ivarna.apexcore

import android.app.ActivityManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class BoostResult(
    val freedMb: Long,
    val beforeAvailMb: Long,
    val afterAvailMb: Long,
    val killedApps: Int,
    val beforeLoadAvg: Float,
    val afterLoadAvg: Float
)

object BoostManager {

    private const val TAG = "ApexCore"

    suspend fun kick(context: Context): BoostResult = withContext(Dispatchers.IO) {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val pm = context.packageManager

        val beforeAvail = readMemAvailKb()
        val beforeLoad = readLoadAvg()
        Log.i(TAG, "Before avail=${beforeAvail}KB load=$beforeLoad")

        val allApps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        val userPackages = allApps
            .filter { pkg ->
                val isSystemOnly = (pkg.flags and ApplicationInfo.FLAG_SYSTEM) != 0 &&
                    (pkg.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0
                !isSystemOnly
            }
            .filter { it.packageName != context.packageName }
            .map { it.packageName }

        Log.i(TAG, "Found ${userPackages.size} user packages")

        val runningProcesses: List<ActivityManager.RunningAppProcessInfo> = am.runningAppProcesses?.toList() ?: emptyList()
        val runningPkgs = HashSet<String>()
        for (rap in runningProcesses) {
            val importance = rap.importance
            if (importance >= ActivityManager.RunningAppProcessInfo.IMPORTANCE_CACHED) {
                runningPkgs.add(rap.processName)
            }
        }
        Log.i(TAG, "Running background-eligible: ${runningPkgs.size}")

        var killed = 0
        for (pkg in userPackages) {
            try {
                if (runningPkgs.contains(pkg)) {
                    am.killBackgroundProcesses(pkg)
                    killed++
                    Log.i(TAG, "Killed: $pkg")
                }
            } catch (t: Throwable) {
                Log.w(TAG, "Kill failed for $pkg: ${t.message}")
            }
        }

        Thread.sleep(800)

        val afterAvail = readMemAvailKb()
        val afterLoad = readLoadAvg()
        val freedKb = (afterAvail - beforeAvail).coerceAtLeast(0)
        Log.i(TAG, "After avail=${afterAvail}KB load=$afterLoad killed=$killed freed=${freedKb}KB")

        BoostResult(
            freedMb = freedKb / 1024,
            beforeAvailMb = beforeAvail / 1024,
            afterAvailMb = afterAvail / 1024,
            killedApps = killed,
            beforeLoadAvg = beforeLoad,
            afterLoadAvg = afterLoad
        )
    }

    private fun readMemAvailKb(): Long {
        return try {
            java.io.File("/proc/meminfo").useLines { lines ->
                for (line in lines) {
                    if (line.startsWith("MemAvailable:")) {
                        return line.split(Regex("\\s+"))[1].toLongOrNull() ?: 0L
                    }
                }
                0L
            }
        } catch (_: Throwable) {
            0L
        }
    }

    private fun readLoadAvg(): Float {
        return try {
            java.io.File("/proc/loadavg").useLines { lines ->
                val first = lines.firstOrNull() ?: return 0f
                first.split(Regex("\\s+")).getOrNull(0)?.toFloatOrNull() ?: 0f
            }
        } catch (_: Throwable) {
            0f
        }
    }
}
