package com.ivarna.apexcore.thermal

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.HardwarePropertiesManager
import android.os.PowerManager
import java.io.File

data class ThermalSnapshot(
    val batteryTempCelsius: Float,
    val cpuTempCelsius: Float,
    val thermalStatus: Int = 0,
    val thermalHeadroom: Float = 1.0f
) {
    enum class BatteryTier(val label: String, val description: String) {
        BEST("Best", "Peak cool performance (<25°C)"),
        OPTIMAL("Optimal", "Nominal thermal range (25-30°C)"),
        WARM("Warm", "Elevated heat load (30-35°C)"),
        HOT("Hot", "Thermal throttling likely (35-40°C)"),
        SEVERE_THROTTLE("Severe Throttle", "Extreme heat (>40°C)")
    }

    val batteryTier: BatteryTier
        get() = when {
            batteryTempCelsius < 25.0f -> BatteryTier.BEST
            batteryTempCelsius < 30.0f -> BatteryTier.OPTIMAL
            batteryTempCelsius < 35.0f -> BatteryTier.WARM
            batteryTempCelsius < 40.0f -> BatteryTier.HOT
            else -> BatteryTier.SEVERE_THROTTLE
        }

    val cpuStatusDescription: String
        get() = when {
            cpuTempCelsius < 55.0f -> "Cool · Low Load"
            cpuTempCelsius < 75.0f -> "Nominal Gaming"
            cpuTempCelsius < 88.0f -> "Turbo Spike · Heavy"
            else -> "Hotspot Throttle (>88°C)"
        }
}

object ThermalMonitor {

    fun getSnapshot(context: Context): ThermalSnapshot {
        val batteryTemp = getBatteryTemperature(context)
        val cpuTemp = getCpuTemperature(context, fallbackBattery = batteryTemp)
        val (thermalStatus, headroom) = getPowerManagerThermal(context)

        return ThermalSnapshot(
            batteryTempCelsius = batteryTemp,
            cpuTempCelsius = cpuTemp,
            thermalStatus = thermalStatus,
            thermalHeadroom = headroom
        )
    }

    private fun getBatteryTemperature(context: Context): Float {
        return try {
            val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val tempTenths = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 280) ?: 280
            tempTenths / 10.0f
        } catch (_: Throwable) {
            28.0f
        }
    }

    private fun getCpuTemperature(context: Context, fallbackBattery: Float): Float {
        // 1. Try HardwarePropertiesManager (Standard Android API 24+)
        try {
            val hw = context.getSystemService(Context.HARDWARE_PROPERTIES_SERVICE) as? HardwarePropertiesManager
            val temps = hw?.getDeviceTemperatures(
                HardwarePropertiesManager.DEVICE_TEMPERATURE_CPU,
                HardwarePropertiesManager.TEMPERATURE_CURRENT
            )
            if (temps != null && temps.isNotEmpty()) {
                val valid = temps.filter { it in 15.0f..115.0f }
                if (valid.isNotEmpty()) {
                    return valid.maxOrNull() ?: valid.average().toFloat()
                }
            }
        } catch (_: Throwable) {}

        // 2. Try direct sysfs thermal zone files
        val thermalPaths = listOf(
            "/sys/class/thermal/thermal_zone0/temp",
            "/sys/class/thermal/thermal_zone1/temp",
            "/sys/devices/virtual/thermal/thermal_zone0/temp",
            "/sys/devices/system/cpu/cpu0/cpufreq/cpu_temp"
        )
        for (path in thermalPaths) {
            try {
                val f = File(path)
                if (f.exists() && f.canRead()) {
                    val raw = f.readText().trim().toFloatOrNull()
                    if (raw != null) {
                        val temp = if (raw > 1000f) raw / 1000f else raw
                        if (temp in 20f..120f) return temp
                    }
                }
            } catch (_: Throwable) {}
        }

        // 3. Fallback: CPU junction runs higher than battery by load delta
        return (fallbackBattery + 14.5f).coerceAtMost(92.0f)
    }

    private fun getPowerManagerThermal(context: Context): Pair<Int, Float> {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
                val status = pm?.currentThermalStatus ?: 0
                val headroom = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    pm?.getThermalHeadroom(30) ?: 1.0f
                } else 1.0f
                return Pair(status, headroom)
            } catch (_: Throwable) {}
        }
        return Pair(0, 1.0f)
    }
}
