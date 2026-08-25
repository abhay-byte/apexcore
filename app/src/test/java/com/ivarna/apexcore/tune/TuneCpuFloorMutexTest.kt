package com.ivarna.apexcore.tune

import android.content.Context
import com.ivarna.apexcore.fps.privilege.PrivilegeModeStore
import com.ivarna.apexcore.fps.privilege.ShellGateway
import com.ivarna.apexcore.fps.util.ShellExecutor
import com.ivarna.apexcore.freeze.FreezeBackendResolver
import com.ivarna.apexcore.freeze.FreezeFramework
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

class TuneCpuFloorMutexTest {

    private val fakeShell = FakeTuneShell()
    private val fakeKv = FakeKeyValue()
    private val context: Context = mock(Context::class.java)

    @Before
    fun setUp() {
        `when`(context.packageName).thenReturn("com.ivarna.apexcore")
        `when`(context.applicationContext).thenReturn(context)

        val resolver = FreezeBackendResolver(listOf(TestFreezeBackend("Root", 0, ready = true)))
        FreezeFramework.setResolverForTest(resolver)
        runBlocking { FreezeFramework.detect() }
    }

    @After
    fun tearDown() {
        FreezeFramework.setResolverForTest(null)
        FreezeFramework.setActiveBackendForTest(null)
    }

    @Test
    fun testCpuFloorOnIgnoresSplitClusterIntents() = runBlocking {
        val policy0 = "/sys/devices/system/cpu/cpufreq/policy0/scaling_min_freq"
        val policy0Avail = "/sys/devices/system/cpu/cpufreq/policy0/scaling_available_frequencies"
        fakeShell.existingPaths.add(policy0)
        fakeShell.pathValues[policy0] = "300000"
        fakeShell.existingPaths.add(policy0Avail)
        fakeShell.pathValues[policy0Avail] = "300000 600000 900000"

        val prefs = TunePrefs(fakeKv)
        val snapshotStore = TuneSnapshotStore(context, fakeKv, fakeShell)
        val probe = TuneProbe(context, fakeShell)
        val applier = TuneApplier(context, fakeShell, snapshotStore)
        val executor = ShellExecutor()
        val store = mock(PrivilegeModeStore::class.java)
        val gateway = ShellGateway(executor, store)

        val manager = TuneManager(
            appContext = context,
            shellGateway = gateway,
            prefs = prefs,
            snapshotStore = snapshotStore,
            probe = probe,
            applier = applier
        )

        probe.probeSync()
        // Turn ON CPU_FLOOR (all clusters) AND CPU_FLOOR_LITTLE
        prefs.setIntent(TuneId.CPU_FLOOR, TuneValue(on = true))
        prefs.setIntent(TuneId.CPU_FLOOR_LITTLE, TuneValue(on = true))

        val report = manager.applyForSession("com.test.game")
        // CPU_FLOOR is applied; CPU_FLOOR_LITTLE is skipped due to mutex rule
        assertEquals("600000", fakeShell.pathValues[policy0])
    }
}
