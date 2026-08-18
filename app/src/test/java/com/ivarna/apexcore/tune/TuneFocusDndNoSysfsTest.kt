package com.ivarna.apexcore.tune

import android.content.Context
import com.ivarna.apexcore.fps.privilege.PrivilegeTier
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock

class TuneFocusDndNoSysfsTest {

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
    fun testFocusDndNeverCallsSysfsWrite() {
        applier.applyBundle(TuneId.FOCUS_DND, TuneValue(on = true), PrivilegeTier.ROOT)

        for (attempt in fakeShell.writeAttempts) {
            val path = attempt.first
            assertFalse("FOCUS_DND must never write to sysfs/proc: $path", path.startsWith("/sys") || path.startsWith("/proc"))
        }
    }
}
