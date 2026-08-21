package com.ivarna.apexcore.ui.iron.home

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ivarna.apexcore.freeze.FreezeFramework
import com.ivarna.apexcore.getSystemMemStats
import com.ivarna.apexcore.thermal.ThermalMonitor
import com.ivarna.apexcore.ui.iron.BenchPhase
import com.ivarna.apexcore.ui.iron.LedState
import com.ivarna.apexcore.ui.iron.WorkOrderData
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class BenchViewModel(app: Application) : AndroidViewModel(app) {

    data class Mem(
        val ramUsedMb: Int,
        val ramTotalMb: Int,
        val swapUsedMb: Int,
        val swapTotalMb: Int
    )

    data class Ui(
        val phase: BenchPhase = BenchPhase.IDLE,
        val elevated: Boolean = false,
        val backendName: String = "…",
        val backendLed: LedState = LedState.CHECKING,
        val mem: Mem = Mem(0, 1, 0, 1),
        val freedFraction: Float = 0f,
        val lastOrder: WorkOrderData? = null,
        val batteryC: Int = 30,
        val cpuC: Int = 38,
    )

    private val _ui = MutableStateFlow(Ui())
    val ui: StateFlow<Ui> = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            while (isActive) {
                val stats = getSystemMemStats(app)
                val totalMb = (stats.ramTotalKb / 1024L).toInt().coerceAtLeast(1)
                val usedMb = (stats.ramUsedKb / 1024L).toInt().coerceAtLeast(0)
                val swapTotalMb = (stats.swapTotalKb / 1024L).toInt().coerceAtLeast(1)
                val swapUsedMb = (stats.swapUsedKb / 1024L).toInt().coerceAtLeast(0)

                _ui.update {
                    it.copy(mem = Mem(usedMb, totalMb, swapUsedMb, swapTotalMb))
                }
                delay(1000)
            }
        }
        viewModelScope.launch {
            while (isActive) {
                val snap = ThermalMonitor.getSnapshot(app)
                _ui.update { it.copy(batteryC = snap.batteryTempCelsius.toInt(), cpuC = snap.cpuTempCelsius.toInt()) }
                delay(1000)
            }
        }
        redetect()
    }

    fun redetect() {
        viewModelScope.launch {
            val backend = FreezeFramework.detect()
            val ready = backend != null
            _ui.update {
                it.copy(
                    elevated = ready,
                    backendName = (backend?.name ?: "SETUP REQUIRED").uppercase(),
                    backendLed = when {
                        ready -> LedState.READY
                        else -> LedState.BLOCKED
                    },
                )
            }
        }
    }

    fun boost(context: Context) = viewModelScope.launch {
        _ui.update { it.copy(phase = BenchPhase.BOOSTING) }
        val res = FreezeFramework.freezeAll(context)
        val freedRamMb = res.freedKb / 1024f
        val freedSwapMb = res.swapFreedKb / 1024f
        val totalFreedGb = (freedRamMb + freedSwapMb) / 1024f

        val order = WorkOrderData(
            freedGb = totalFreedGb,
            freedRamGb = freedRamMb / 1024f,
            freedSwapGb = freedSwapMb / 1024f,
            apps = res.killed,
            durationS = res.durationMs / 1000f,
            skipped = res.skipped,
            failed = res.failed,
        )
        _ui.update {
            it.copy(
                phase = BenchPhase.RESULT,
                lastOrder = order,
                freedFraction = (order.freedGb / (it.mem.ramTotalMb / 1024f)).coerceIn(0f, 0.5f)
            )
        }
    }

    fun reset() = _ui.update { it.copy(phase = BenchPhase.IDLE) }
}
