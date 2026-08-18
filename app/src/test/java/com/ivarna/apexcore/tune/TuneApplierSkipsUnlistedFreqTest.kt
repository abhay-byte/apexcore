package com.ivarna.apexcore.tune

import android.content.Context
import com.ivarna.apexcore.fps.privilege.PrivilegeTier
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock

class TuneApplierSkipsUnlistedFreqTest {

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
    fun testOnlyWritesFromAvailableFrequencies() {
        val node = "/sys/devices/system/cpu/cpufreq/policy0/scaling_min_freq"
        val avail = "/sys/devices/system/cpu/cpufreq/policy0/scaling_available_frequencies"
        fakeShell.existingPaths.add(node)
        fakeShell.pathValues[node] = "100000"
        fakeShell.existingPaths.add(avail)
        fakeShell.pathValues[avail] = "100000 200000 300000"

        val count = applier.applyBundle(TuneId.CPU_FLOOR, TuneValue(on = true), PrivilegeTier.ROOT)
        assertEquals(1, count)
        assertEquals("200000", fakeShell.pathValues[node])
    }
}
