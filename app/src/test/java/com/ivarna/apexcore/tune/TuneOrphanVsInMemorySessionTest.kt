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

class TuneOrphanVsInMemorySessionTest {

    private val fakeShell = FakeTuneShell()
    private val fakeKv = FakeKeyValue()
    private val context: Context = mock(Context::class.java)

    @Before
    fun setUp() {
        `when`(context.packageName).thenReturn("com.ivarna.apexcore")
        `when`(context.applicationContext).thenReturn(context)

        val resolver = FreezeBackendResolver(listOf(TestFreezeBackend("Root", 0, ready = true)))
        FreezeFramework.setResolverForTest(resolver)
    }

    @After
    fun tearDown() {
        FreezeFramework.setResolverForTest(null)
    }

    @Test
    fun testInMemoryActiveSessionDoesNotTriggerOrphanRestore() = runBlocking {
        val cpuNode = "/sys/devices/system/cpu/cpufreq/policy0/scaling_min_freq"
        val cpuAvail = "/sys/devices/system/cpu/cpufreq/policy0/scaling_available_frequencies"
        fakeShell.existingPaths.add(cpuNode)
        fakeShell.pathValues[cpuNode] = "300000"
        fakeShell.existingPaths.add(cpuAvail)
        fakeShell.pathValues[cpuAvail] = "300000 600000 900000"

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
        prefs.setIntent(TuneId.CPU_FLOOR, TuneValue(on = true))

        val report = manager.applyForSession("com.test.game")
        assertTrue("Session should be active", report.sessionActive)
        assertEquals("600000", fakeShell.pathValues[cpuNode])

        // Trigger recoverSession while in-memory session is active
        manager.recoverSession()

        // Node must remain tuned, NOT restored
        assertEquals("600000", fakeShell.pathValues[cpuNode])
        assertTrue("Session must remain active", manager.sessionActive.value)
    }
}
