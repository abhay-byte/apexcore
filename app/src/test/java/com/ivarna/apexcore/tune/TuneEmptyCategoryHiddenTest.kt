package com.ivarna.apexcore.tune

import org.junit.Assert.*
import org.junit.Test

class TuneEmptyCategoryHiddenTest {

    @Test
    fun testEmptyCategoriesOmitted() {
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

        // Only categories with >= 1 available option are visible
        val visibleCategories = TuneCategory.values().filter { cat ->
            val specs = TuneSpecs.byCategory[cat].orEmpty()
            specs.any { caps[it.id]?.available == true }
        }

        assertEquals(1, visibleCategories.size)
        assertTrue("GPU category has available options and must be visible", visibleCategories.contains(TuneCategory.GPU))
        assertFalse("CPU category has 0 available options and must be omitted", visibleCategories.contains(TuneCategory.CPU))
        assertFalse("NETWORK category has 0 available options and must be omitted", visibleCategories.contains(TuneCategory.NETWORK))
    }
}

