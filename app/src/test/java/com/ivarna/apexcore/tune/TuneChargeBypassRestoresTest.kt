package com.ivarna.apexcore.tune

import android.content.Context
import com.ivarna.apexcore.fps.privilege.PrivilegeTier
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock

class TuneChargeBypassRestoresTest {

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
    fun testChargeBypassAppliesAndRestoresInputSuspend() {
        val path = "/sys/class/power_supply/battery/input_suspend"
        fakeShell.existingPaths.add(path)
        fakeShell.pathValues[path] = "0"

        val applied = applier.applyBundle(TuneId.CHARGE_BYPASS, TuneValue(on = true), PrivilegeTier.ROOT)
        assertEquals(1, applied)
        assertEquals("1", fakeShell.pathValues[path])

        val restored = applier.restoreBundle(TuneId.CHARGE_BYPASS, PrivilegeTier.ROOT)
        assertEquals(1, restored)
        assertEquals("0", fakeShell.pathValues[path])
    }
}
