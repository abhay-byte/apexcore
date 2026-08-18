package com.ivarna.apexcore.tune

import org.junit.Assert.*
import org.junit.Test

class TuneEmptyCategoryHiddenTest {

    @Test
    fun testAllCategoriesDisplayedWithSupportStatus() {
        val caps = mutableMapOf<TuneId, TuneCapability>()
        for (spec in TuneSpecs.all) {
            val isGpu = spec.category == TuneCategory.GPU && spec.id == TuneId.GPU_FLOOR
            caps[spec.id] = TuneCapability(
                id = spec.id,
                available = isGpu,
                needsRoot = false,
                writablePaths = if (isGpu) listOf("/sys/class/kgsl/kgsl-3d0/devfreq/min_freq") else emptyList(),
                subtitle = if (isGpu) "Available on this kernel" else "Not supported on this kernel"
            )
        }

        val allCategories = TuneCategory.values().toList()
        assertEquals(10, allCategories.size)
        assertTrue(allCategories.contains(TuneCategory.GPU))
        assertTrue(allCategories.contains(TuneCategory.CPU))
        assertTrue(allCategories.contains(TuneCategory.NETWORK))

        // GPU category has 1 supported option
        val gpuSpecs = TuneSpecs.byCategory[TuneCategory.GPU].orEmpty()
        val gpuSupported = gpuSpecs.count { caps[it.id]?.available == true }
        assertEquals(1, gpuSupported)

        // CPU category has 0 supported options
        val cpuSpecs = TuneSpecs.byCategory[TuneCategory.CPU].orEmpty()
        val cpuSupported = cpuSpecs.count { caps[it.id]?.available == true }
        assertEquals(0, cpuSupported)
    }
}
