package com.apexcore.app

import android.app.usage.StorageStatsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.UserHandle
import android.os.storage.StorageManager
import android.os.Process
import android.os.storage.StorageVolume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

data class AppStorage(
    val packageName: String,
    val label: String,
    val apkBytes: Long,
    val dataBytes: Long,
    val cacheBytes: Long,
    val isSystem: Boolean
) {
    val totalBytes: Long get() = apkBytes + dataBytes + cacheBytes
}

data class StorageReport(
    val totalBytes: Long,
    val freeBytes: Long,
    val usedBytes: Long,
    val apps: List<AppStorage>,
    val accurate: Boolean
) {
    val usedPercent: Int
        get() = if (totalBytes == 0L) 0 else ((usedBytes * 100) / totalBytes).toInt()
}

object StorageManager {

    suspend fun scan(context: Context): StorageReport = withContext(Dispatchers.IO) {
        val (total, free) = statFs()
        val pm = context.packageManager
        val usage = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val accurate = hasUsageStatsPermission(context)
        val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            .map { app ->
                val label = pm.getApplicationLabel(app).toString()
                val (apk, data, cache) = if (accurate && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    storageStats(context, app.packageName)
                } else {
                    Triple(appSizeFromApk(app), 0L, 0L)
                }
                AppStorage(
                    packageName = app.packageName,
                    label = label,
                    apkBytes = apk,
                    dataBytes = data,
                    cacheBytes = cache,
                    isSystem = (app.flags and ApplicationInfo.FLAG_SYSTEM) != 0 &&
                        (app.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0
                )
            }
            .sortedByDescending { it.totalBytes }
        StorageReport(
            totalBytes = total,
            freeBytes = free,
            usedBytes = total - free,
            apps = apps,
            accurate = accurate
        )
    }

    fun hasUsageStatsPermission(context: Context): Boolean {
        val usage = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()
        val stats = usage.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, now - 60_000, now)
        return stats.isNotEmpty()
    }

    private fun storageStats(context: Context, pkg: String): Triple<Long, Long, Long> {
        return try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                return Triple(appSizeFromApkFile(context, pkg), 0L, 0L)
            }
            val storageStatsManager = context.getSystemService(Context.STORAGE_STATS_SERVICE) as StorageStatsManager
            val uuid: UUID = StorageManager.UUID_DEFAULT
            val stats = storageStatsManager.queryStatsForPackage(uuid, pkg, android.os.Process.myUserHandle())
            val cacheBytes = try {
                storageStatsManager.queryStatsForPackage(uuid, pkg, android.os.Process.myUserHandle()).cacheBytes
            } catch (_: Throwable) {
                stats.cacheBytes
            }
            Triple(stats.appBytes, stats.dataBytes, cacheBytes)
        } catch (_: Throwable) {
            Triple(appSizeFromApkFile(context, pkg), 0L, 0L)
        }
    }

    private fun appSizeFromApk(app: ApplicationInfo): Long {
        return try {
            val src = File(app.sourceDir)
            if (src.exists()) dirSize(src) else 0L
        } catch (_: Throwable) { 0L }
    }

    private fun appSizeFromApkFile(context: Context, pkg: String): Long {
        return try {
            val app = context.packageManager.getApplicationInfo(pkg, 0)
            val src = File(app.sourceDir)
            if (src.exists()) dirSize(src) else 0L
        } catch (_: Throwable) { 0L }
    }

    private fun statFs(): Pair<Long, Long> {
        var total = 0L
        var free = 0L
        try {
            val data = StatFs(Environment.getDataDirectory().path)
            total += data.totalBytes
            free += data.availableBytes
        } catch (_: Throwable) { }
        try {
            val ext = Environment.getExternalStorageDirectory()
            if (ext != null) {
                val s = StatFs(ext.path)
                total += s.totalBytes
                free += s.availableBytes
            }
        } catch (_: Throwable) { }
        return total to free
    }

    private fun dirSize(dir: File): Long {
        var total = 0L
        try {
            if (dir.isFile) return dir.length()
            val children = dir.listFiles() ?: return 0L
            for (child in children) {
                total += if (child.isDirectory) dirSize(child) else child.length()
            }
        } catch (_: Throwable) { }
        return total
    }
}
