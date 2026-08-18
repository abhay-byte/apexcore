package com.ivarna.apexcore.tune

import android.content.Context
import com.ivarna.apexcore.fps.privilege.PrivilegeTier
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock

class TuneApplierGroupIdOneWriteTest {

    private val fakeShell = FakeTuneShell()
    private val fakeKv = FakeKeyValue()
    private val context: Context = mock(Context::class.java)
    private lateinit var snapshotStore: TuneSnapshotStore
    private lateinit var applier: TuneApplier

    @Before
    fun setUp() {
        snapshotStore = TuneSnapshotStore(context, fakeKv, fakeShell)
        applier = TuneApplier(context, fakeShell, snapshotStore)
    }

    @Test
    fun testTwoGpuMinNodesOnlyOneWritten() {
        val node1 = "/sys/class/kgsl/kgsl-3d0/devfreq/min_freq"
        val avail1 = "/sys/class/kgsl/kgsl-3d0/gpu_available_frequencies"
        val node2 = "/sys/class/kgsl/kgsl-3d0/min_gpuclk"

        fakeShell.existingPaths.add(node1)
        fakeShell.pathValues[node1] = "200000000"
        fakeShell.existingPaths.add(avail1)
        fakeShell.pathValues[avail1] = "200000000 400000000 600000000"

        fakeShell.existingPaths.add(node2)
        fakeShell.pathValues[node2] = "200000000"

        val count = applier.applyBundle(TuneId.GPU_FLOOR, TuneValue(on = true), PrivilegeTier.ROOT)
        assertEquals(1, count)
        // Only node1 was modified
        assertEquals("400000000", fakeShell.pathValues[node1])
        assertEquals("200000000", fakeShell.pathValues[node2])
    }
}
