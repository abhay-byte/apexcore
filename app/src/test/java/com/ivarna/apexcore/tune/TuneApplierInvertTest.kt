package com.ivarna.apexcore.tune

import android.content.Context
import com.ivarna.apexcore.fps.privilege.PrivilegeTier
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock

class TuneApplierInvertTest {

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
    fun testApplyThenRestoreInvertsAllWrites() {
        val cpuNode = "/sys/devices/system/cpu/cpufreq/policy0/scaling_min_freq"
        val cpuAvail = "/sys/devices/system/cpu/cpufreq/policy0/scaling_available_frequencies"
        fakeShell.existingPaths.add(cpuNode)
        fakeShell.pathValues[cpuNode] = "300000"
        fakeShell.existingPaths.add(cpuAvail)
        fakeShell.pathValues[cpuAvail] = "300000 600000 900000 1200000"

        val thermalNode = "/sys/class/thermal/thermal_message/sconfig"
        fakeShell.existingPaths.add(thermalNode)
        fakeShell.pathValues[thermalNode] = "0"

        // Apply CPU_FLOOR
        val appliedCpu = applier.applyBundle(TuneId.CPU_FLOOR, TuneValue(on = true), PrivilegeTier.ROOT)
        assertEquals(1, appliedCpu)
        assertEquals("900000", fakeShell.pathValues[cpuNode]) // Median of 300k, 600k, 900k, 1200k is index 4/2 = 2 (900000)

        // Apply THERMAL_SCONFIG
        val appliedThermal = applier.applyBundle(TuneId.THERMAL_SCONFIG, TuneValue(on = true), PrivilegeTier.ROOT)
        assertEquals(1, appliedThermal)
        assertEquals("13", fakeShell.pathValues[thermalNode])

        // Restore all
        val restored = applier.restoreAll(PrivilegeTier.ROOT)
        assertEquals(2, restored)
        assertEquals("300000", fakeShell.pathValues[cpuNode])
        assertEquals("0", fakeShell.pathValues[thermalNode])
        assertEquals(0, snapshotStore.size())
    }
}
