package com.ivarna.apexcore.tune

import android.app.usage.UsageStatsManager
import android.content.Context
import android.util.Log
import kotlinx.coroutines.*

/**
 * Primary session watchdog when overlay draw-over permission is not granted.
 * Monitors top-resumed package via UsageStats and restores kernel state when the game exits.
 * Fails toward restoration after 3 consecutive unknown readings.
 */
object TuneSessionWatchdog {

    private const val TAG = "ApexCore.TuneWatchdog"
    private const val GRACE_PERIOD_MS = 8000L
    private const val POLL_INTERVAL_MS = 5000L
    private const val MAX_UNKNOWN_STREAK = 3

    private var watchdogJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @Volatile
    var topPackageProvider: ((Context) -> String?)? = null
    @Volatile
    var gracePeriodOverrideMs: Long? = null
    @Volatile
    var pollIntervalOverrideMs: Long? = null

    fun arm(context: Context, gamePkg: String) {
        val appCtx = context.applicationContext
        val tuneManager = TuneManager.get(appCtx)
        tuneManager.setOwner(TuneSessionOwner.WATCHDOG)

        val graceMs = gracePeriodOverrideMs ?: GRACE_PERIOD_MS
        val pollMs = pollIntervalOverrideMs ?: POLL_INTERVAL_MS

        watchdogJob?.cancel()
        watchdogJob = scope.launch {
            Log.i(TAG, "Watchdog armed for $gamePkg with ${graceMs}ms grace period")
            delay(graceMs)

            var unknownStreak = 0
            while (isActive && tuneManager.sessionActive.value && tuneManager.owner == TuneSessionOwner.WATCHDOG) {
                delay(pollMs)
                val top = topPackageProvider?.invoke(appCtx) ?: queryUsageStatsTop(appCtx)
                Log.d(TAG, "Watchdog poll: top=$top, game=$gamePkg, streak=$unknownStreak")

                when {
                    top == null -> {
                        unknownStreak++
                        if (unknownStreak >= MAX_UNKNOWN_STREAK) {
                            Log.w(TAG, "Unknown streak reached $MAX_UNKNOWN_STREAK -> triggering restore")
                            break
                        }
                    }
                    top == gamePkg -> {
                        unknownStreak = 0
                    }
                    top == appCtx.packageName -> {
                        Log.i(TAG, "Returned to ApexCore -> session ended")
                        break
                    }
                    else -> {
                        Log.i(TAG, "Top app changed to $top -> session ended")
                        break
                    }
                }
            }

            if (tuneManager.owner == TuneSessionOwner.WATCHDOG) {
                Log.i(TAG, "Watchdog triggering restoreSession")
                tuneManager.restoreSession()
            }
        }
    }

    fun cancel() {
        watchdogJob?.cancel()
        watchdogJob = null
    }

    @android.annotation.SuppressLint("MissingPermission")
    fun queryUsageStatsTop(context: Context): String? {
        return try {
            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
                ?: return null
            val time = System.currentTimeMillis()
            val stats = usm.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                time - 1000 * 60,
                time
            )
            if (!stats.isNullOrEmpty()) {
                val sorted = stats.sortedByDescending { it.lastTimeUsed }
                sorted.firstOrNull()?.packageName
            } else {
                null
            }
        } catch (t: Throwable) {
            Log.w(TAG, "queryUsageStatsTop error: ${t.message}")
            null
        }
    }
}
