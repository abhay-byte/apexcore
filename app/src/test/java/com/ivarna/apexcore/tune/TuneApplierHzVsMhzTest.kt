package com.ivarna.apexcore.tune

import android.content.Context
import com.ivarna.apexcore.fps.privilege.PrivilegeTier
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock

class TuneApplierHzVsMhzTest {

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
    fun testMinClockMhzWritesNativeUnits() {
        val node = "/sys/class/kgsl/kgsl-3d0/min_clock_mhz"
        val avail = "/sys/class/kgsl/kgsl-3d0/freq_table_mhz"
        fakeShell.existingPaths.add(node)
        fakeShell.pathValues[node] = "200"
        fakeShell.existingPaths.add(avail)
        fakeShell.pathValues[avail] = "200 400 600 800"

        val count = applier.applyBundle(TuneId.GPU_FLOOR, TuneValue(on = true), PrivilegeTier.ROOT)
        assertEquals(1, count)
        // 600 MHz written directly, not multiplied to Hz
        assertEquals("600", fakeShell.pathValues[node])
    }
}
