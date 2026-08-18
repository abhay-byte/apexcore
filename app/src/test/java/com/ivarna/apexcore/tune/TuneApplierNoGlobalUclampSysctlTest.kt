package com.ivarna.apexcore.tune

import android.content.Context
import com.ivarna.apexcore.fps.privilege.PrivilegeTier
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock

class TuneApplierNoGlobalUclampSysctlTest {

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
    fun testCpuUclampNeverWritesSchedUtilClampMinSysctl() {
        val topAppUclamp = "/dev/cpuctl/top-app/cpu.uclamp.min"
        fakeShell.existingPaths.add(topAppUclamp)
        fakeShell.pathValues[topAppUclamp] = "0"

        applier.applyBundle(TuneId.CPU_UCLAMP, TuneValue(on = true, raw = "10"), PrivilegeTier.ROOT)

        for (attempt in fakeShell.writeAttempts) {
            val path = attempt.first
            assertFalse("Must never write /proc/sys/kernel/sched_util_clamp_min: $path", path.contains("sched_util_clamp_min"))
        }
    }
}
