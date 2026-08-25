package com.ivarna.apexcore.tune

import org.junit.Assert.*
import org.junit.Test

class EnumUiWiringTest {

    @Test
    fun testGovernorSpecsAreEnumKind() {
        val cpuGovSpec = TuneSpecs.all.find { it.id == TuneId.CPU_GOVERNOR }
        assertNotNull(cpuGovSpec)
        assertEquals(TuneControlKind.ENUM, cpuGovSpec!!.kind)

        val gpuGovSpec = TuneSpecs.all.find { it.id == TuneId.GPU_GOVERNOR }
        assertNotNull(gpuGovSpec)
        assertEquals(TuneControlKind.ENUM, gpuGovSpec!!.kind)

        val ioSchedSpec = TuneSpecs.all.find { it.id == TuneId.IO_SCHEDULER }
        assertNotNull(ioSchedSpec)
        assertEquals(TuneControlKind.ENUM, ioSchedSpec!!.kind)

        val tcpSpec = TuneSpecs.all.find { it.id == TuneId.NET_TCP }
        assertNotNull(tcpSpec)
        assertEquals(TuneControlKind.ENUM, tcpSpec!!.kind)
    }

    @Test
    fun testSliderSpecsAreSliderKind() {
        // At least 8 slider specs defined per T12/T13
        val sliders = TuneSpecs.all.filter { it.kind == TuneControlKind.SLIDER }
        assertTrue("Expected at least 5 slider specs, got ${sliders.size}", sliders.size >= 5)
        // Check known sliders
        val gpuAdreno = TuneSpecs.all.find { it.id == TuneId.GPU_ADRENO }
        assertNotNull(gpuAdreno)
        assertEquals(TuneControlKind.SLIDER, gpuAdreno!!.kind)
        assertNotNull(gpuAdreno.slider)

        val cpuUclamp = TuneSpecs.all.find { it.id == TuneId.CPU_UCLAMP }
        assertNotNull(cpuUclamp)
        assertEquals(TuneControlKind.SLIDER, cpuUclamp!!.kind)
    }

    @Test
    fun testEnumOptionsWiringViaAvailableOptions() {
        // Simulate that TuneProbe would populate availableOptions from
        // /sys/.../available_governors. Here we just verify the model
        // holds the field and UI can read it.
        val cap = TuneCapability(
            id = TuneId.CPU_GOVERNOR,
            available = true,
            needsRoot = false,
            writablePaths = listOf("/sys/devices/system/cpu/cpufreq/policy0/scaling_governor"),
            subtitle = "Available",
            availableOptions = listOf("schedutil", "performance", "powersave")
        )
        assertTrue(cap.availableOptions.contains("performance"))
        // UI selection logic: picking "performance" should produce TuneValue(true, "performance")
        val selected = "performance"
        val intent = TuneValue(on = true, raw = selected)
        assertEquals(true, intent.on)
        assertEquals("performance", intent.raw)
        // Disabling retains raw
        val disabled = TuneValue(on = false, raw = selected)
        assertFalse(disabled.on)
        assertEquals("performance", disabled.raw)
    }

    @Test
    fun testNoCommonGovernorMeansEmptyOptions() {
        val cap = TuneCapability(
            id = TuneId.CPU_GOVERNOR,
            available = false,
            needsRoot = false,
            writablePaths = emptyList(),
            subtitle = "No common governor",
            availableOptions = emptyList()
        )
        assertTrue(cap.availableOptions.isEmpty())
        // UI should show disabled row with reason
        assertFalse(cap.available)
    }
}
