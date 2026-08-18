package com.ivarna.apexcore.tune

import org.junit.Assert.*
import org.junit.Test

class TuneEmptyCategoryHiddenTest {

    @Test
    fun testEmptyCategoryHiddenFromVisibleList() {
        val caps = mutableMapOf<TuneId, TuneCapability>()
        for (spec in TuneSpecs.all) {
            val isGpu = spec.category == TuneCategory.GPU && spec.id == TuneId.GPU_FLOOR
            caps[spec.id] = TuneCapability(
                id = spec.id,
                available = isGpu,
                needsRoot = false,
                writablePaths = if (isGpu) listOf("/sys/class/kgsl/kgsl-3d0/devfreq/min_freq") else emptyList(),
                subtitle = "Test"
            )
        }

        val visibleCategories = TuneCategory.values().filter { cat ->
            val specs = TuneSpecs.byCategory[cat].orEmpty()
            specs.any { spec -> caps[spec.id]?.available == true }
        }

        assertEquals(1, visibleCategories.size)
        assertEquals(TuneCategory.GPU, visibleCategories.first())
        assertFalse("CPU category must be hidden if 0 options available", visibleCategories.contains(TuneCategory.CPU))
        assertFalse("Network category must be hidden if 0 options available", visibleCategories.contains(TuneCategory.NETWORK))
    }
}
