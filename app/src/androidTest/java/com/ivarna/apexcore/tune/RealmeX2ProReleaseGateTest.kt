package com.ivarna.apexcore.tune

import android.os.Build
import android.os.PowerManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Physical-device gate intentionally scoped to the requested realme X2 Pro.
 * It is read-only: stock/non-root devices must prove discovery and safe bounds
 * without mutating kernel state.
 */
@RunWith(AndroidJUnit4::class)
class RealmeX2ProReleaseGateTest {
    private val policiesRoot = File("/sys/devices/system/cpu/cpufreq")

    @Test
    fun runsOnlyOnRealmeX2Pro() {
        val model = Build.MODEL.orEmpty().lowercase()
        val device = Build.DEVICE.orEmpty().lowercase()
        assertTrue("This gate is only for realme X2 Pro: model=$model device=$device", model.contains("x2 pro") || device == "rmx1931")
    }

    @Test
    fun discoversEveryRealCpuPolicyAndBoundsAreOrdered() {
        val policies = policiesRoot.listFiles()
            ?.filter { it.isDirectory && it.name.matches(Regex("policy[0-9]+")) }
            ?.sortedBy { it.name.removePrefix("policy").toInt() }
            .orEmpty()
        assertFalse("realme X2 Pro must expose at least one CPUFreq policy", policies.isEmpty())
        for (policy in policies) {
            val min = policy.resolve("scaling_min_freq").readText().trim().toLong()
            val max = policy.resolve("scaling_max_freq").readText().trim().toLong()
            assertTrue("${policy.name}: min must not exceed max", min <= max)
            assertTrue("${policy.name}: max must be positive", max > 0L)
        }
    }

    @Test
    fun thermalStatusIsObservedWithoutDisablingProtection() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val power = context.getSystemService(PowerManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val status = power.currentThermalStatus
            assertTrue("thermal status must be within Android's defined range", status in 0..PowerManager.THERMAL_STATUS_EMERGENCY)
        }
        // No write to thermal sysfs or power/thermal commands is performed by this test.
        assertTrue(true)
    }
}
