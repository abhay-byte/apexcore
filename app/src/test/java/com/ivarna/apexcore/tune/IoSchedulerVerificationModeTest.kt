package com.ivarna.apexcore.tune

import org.junit.Assert.*
import org.junit.Test

class IoSchedulerVerificationModeTest {

    @Test
    fun testIoSchedulerNodesUseActiveTokenMode() {
        val nodes = TuneCatalog.nodesByTuneId[TuneId.IO_SCHEDULER]
        assertNotNull("IO_SCHEDULER must have catalog entries", nodes)
        assertTrue("IO_SCHEDULER must have at least one node", nodes!!.isNotEmpty())
        for (node in nodes) {
            assertEquals(
                "IO_SCHEDULER node ${node.path} must use IO_SCHEDULER_ACTIVE_TOKEN",
                VerificationMode.IO_SCHEDULER_ACTIVE_TOKEN,
                node.verificationMode
            )
        }
    }

    @Test
    fun testApplySingleNodeStoresCorrectVerificationMode() {
        // Verify that TuneNode definition carries correct mode even when valueKind is ENUM
        // (default for ENUM is GOVERNOR_TOKEN, but IO_SCHEDULER overrides)
        val ioNode = TuneCatalog.nodesByTuneId[TuneId.IO_SCHEDULER]!!.first()
        assertEquals(TuneValueKind.ENUM, ioNode.valueKind)
        assertEquals(VerificationMode.IO_SCHEDULER_ACTIVE_TOKEN, ioNode.verificationMode)
        // Non-IO enum nodes should remain GOVERNOR_TOKEN
        val gpuGov = TuneCatalog.nodesByTuneId[TuneId.GPU_GOVERNOR]?.firstOrNull()
        if (gpuGov != null) {
            assertEquals(VerificationMode.GOVERNOR_TOKEN, gpuGov.verificationMode)
        }
    }
}
