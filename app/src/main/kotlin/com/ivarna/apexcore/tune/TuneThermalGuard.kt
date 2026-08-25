package com.ivarna.apexcore.tune

import android.content.Context
import android.os.Build
import android.os.PowerManager
import com.ivarna.apexcore.thermal.ThermalMonitor
import java.util.concurrent.Executor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Mandatory safety guard for high-power max-lock primitives. */
class TuneThermalGuard(
    context: Context,
    private val scope: CoroutineScope,
    private val onSevere: () -> Unit
) {
    private val appContext = context.applicationContext
    private val powerManager = appContext.getSystemService(Context.POWER_SERVICE) as? PowerManager
    private var pollJob: Job? = null
    private var thermalStatusListener: PowerManager.OnThermalStatusChangedListener? = null
    @Volatile var severe: Boolean = false
        private set

    fun start() {
        if (pollJob != null) return
        val executor = Executor { command -> scope.launch { command.run() } }
        val statusListener = PowerManager.OnThermalStatusChangedListener { status -> handle(status) }
        thermalStatusListener = statusListener
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try { powerManager?.addThermalStatusListener(executor, statusListener) } catch (_: Throwable) { }
        }
        pollJob = scope.launch {
            while (isActive) {
                val status = try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) powerManager?.currentThermalStatus ?: 0 else 0
                } catch (_: Throwable) { 0 }
                handle(status)
                // Also sample the existing monitor for devices with unreliable
                // listener delivery; no temperature threshold is invented here.
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                    handle(ThermalMonitor.getSnapshot(appContext).thermalStatus)
                }
                delay(POLL_MS)
            }
        }
    }

    fun stop() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try { thermalStatusListener?.let { powerManager?.removeThermalStatusListener(it) } } catch (_: Throwable) { }
        }
        thermalStatusListener = null
        pollJob?.cancel()
        pollJob = null
        severe = false
    }

    private fun handle(status: Int) {
        if (status >= PowerManager.THERMAL_STATUS_SEVERE) {
            if (!severe) {
                severe = true
                onSevere()
            }
        } else if (status < PowerManager.THERMAL_STATUS_SEVERE) {
            // Do not re-lock automatically while a session remains hot.
            severe = false
        }
    }

    companion object { private const val POLL_MS = 5_000L }
}
