package com.ivarna.apexcore.ui.iron.tune

import com.ivarna.apexcore.tune.TuneCapability
import com.ivarna.apexcore.tune.TuneId
import com.ivarna.apexcore.tune.TuneSpecs
import com.ivarna.apexcore.tune.TuneValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TuneCategoryBuilderTest {

    @Test
    fun probeFailureWithEmptyCapsStillReturnsAllSpecsUnavailable() {
        val categories = buildTuneCategories(
            caps = emptyMap(),
            selectedPkg = "com.example.game",
            sessionPkg = null,
            probeFailure = "boom",
            gameModeCapability = { null },
            intentOf = { TuneValue(false, null) },
            onToggle = { _, _, _ -> },
            onEnumSelect = { _, _ -> },
            onSliderChange = { _, _ -> },
        )

        val options = categories.flatMap { it.options }
        assertEquals(TuneSpecs.all.size, options.size)
        assertTrue(options.isNotEmpty())
        assertTrue(options.all { !it.available })
        assertTrue(options.any { it.reason == "boom" })
        assertTrue(options.any { it.title == TuneSpecs.all.first().title })
    }

    @Test
    fun availableCapabilitySurfacesAsAvailableWhenProbeOk() {
        val id = TuneId.GPU_FLOOR
        val categories = buildTuneCategories(
            caps = mapOf(
                id to TuneCapability(
                    id = id,
                    available = true,
                    needsRoot = false,
                    writablePaths = listOf("/sys/fake"),
                    subtitle = "Available on this kernel",
                )
            ),
            selectedPkg = null,
            sessionPkg = null,
            probeFailure = null,
            intentOf = { TuneValue(false, null) },
            onToggle = { _, _, _ -> },
            onEnumSelect = { _, _ -> },
            onSliderChange = { _, _ -> },
        )
        val opt = categories.flatMap { it.options }.first { it.key == id.name }
        assertTrue(opt.available)
        assertEquals(null, opt.reason)
    }

    @Test
    fun missingCapabilityWithoutProbeErrorUsesKernelUnavailableReason() {
        val categories = buildTuneCategories(
            caps = emptyMap(),
            selectedPkg = null,
            sessionPkg = null,
            probeFailure = null,
            intentOf = { TuneValue(false, null) },
            onToggle = { _, _, _ -> },
            onEnumSelect = { _, _ -> },
            onSliderChange = { _, _ -> },
        )
        val opt = categories.flatMap { it.options }.first { it.key == TuneId.GPU_FLOOR.name }
        assertFalse(opt.available)
        assertEquals("Not available on kernel", opt.reason)
    }
}
