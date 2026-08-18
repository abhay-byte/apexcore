package com.ivarna.apexcore.tune

import android.content.Context
import com.ivarna.apexcore.fps.privilege.PrivilegeTier
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock

class TuneApplierNoThermalDisableTest {

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
    fun testNeverWritesMsmThermalOrThrottlingPaths() {
        for (id in TuneId.values()) {
            applier.applyBundle(id, TuneValue(on = true), PrivilegeTier.ROOT)
        }

        for (attempt in fakeShell.writeAttempts) {
            val path = attempt.first
            assertFalse("Must never write msm_thermal: $path", path.contains("msm_thermal"))
            assertFalse("Must never write throttling: $path", path.contains("throttling"))
            assertFalse("Must never write /proc/ged: $path", path.contains("/proc/ged"))
            assertFalse("Must never write /dev/mali0: $path", path.contains("/dev/mali0"))
        }
    }
}
