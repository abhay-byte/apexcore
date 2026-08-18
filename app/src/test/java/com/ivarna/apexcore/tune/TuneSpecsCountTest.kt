package com.ivarna.apexcore.tune

import org.junit.Assert.*
import org.junit.Test

class TuneSpecsCountTest {

    @Test
    fun testExactly36IdsAnd10Categories() {
        assertEquals("Must have exactly 36 TuneId enum entries", 36, TuneId.values().size)
        assertEquals("Must have exactly 10 TuneCategory entries", 10, TuneCategory.values().size)
        assertEquals("TuneSpecs.all must have exactly 36 specs", 36, TuneSpecs.all.size)

        val uniqueIds = TuneSpecs.all.map { it.id }.toSet()
        assertEquals("All 36 specs must have unique TuneIds", 36, uniqueIds.size)

        val categoriesRepresented = TuneSpecs.all.map { it.category }.toSet()
        assertEquals("All 10 categories must be represented in TuneSpecs", 10, categoriesRepresented.size)
    }

    @Test
    fun testUniqueGroupIdsPerNodeFamily() {
        val gpuFloorGroups = TuneCatalog.nodesByTuneId[TuneId.GPU_FLOOR]!!.map { it.groupId }.toSet()
        val gpuHoldGroups = TuneCatalog.nodesByTuneId[TuneId.GPU_HOLD]!!.map { it.groupId }.toSet()

        // GPU_FLOOR and GPU_HOLD must NOT share groupIds
        for (g in gpuFloorGroups) {
            assertFalse("GPU_HOLD must not share group '$g' with GPU_FLOOR", gpuHoldGroups.contains(g))
        }
    }
}
