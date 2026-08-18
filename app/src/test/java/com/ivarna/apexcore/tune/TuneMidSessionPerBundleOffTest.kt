package com.ivarna.apexcore.tune

import android.content.Context
import com.ivarna.apexcore.fps.privilege.PrivilegeModeStore
import com.ivarna.apexcore.fps.privilege.ShellGateway
import com.ivarna.apexcore.fps.util.ShellExecutor
import com.ivarna.apexcore.freeze.FreezeBackendResolver
import com.ivarna.apexcore.freeze.FreezeFramework
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

class TuneMidSessionPerBundleOffTest {

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
    fun testMidSessionTurningOffOneBundleOnlyRestoresThatBundle() = runBlocking {
        val cpuNode = "/sys/devices/system/cpu/cpufreq/policy0/scaling_min_freq"
        val cpuAvail = "/sys/devices/system/cpu/cpufreq/policy0/scaling_available_frequencies"
        fakeShell.existingPaths.add(cpuNode)
        fakeShell.pathValues[cpuNode] = "300000"
        fakeShell.existingPaths.add(cpuAvail)
        fakeShell.pathValues[cpuAvail] = "300000 600000 900000"

        val sconfigPath = "/sys/class/thermal/thermal_message/sconfig"
        fakeShell.existingPaths.add(sconfigPath)
        fakeShell.pathValues[sconfigPath] = "0"

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
        prefs.setIntent(TuneId.THERMAL_SCONFIG, TuneValue(on = true))

        manager.applyForSession("com.test.game")
        assertEquals("600000", fakeShell.pathValues[cpuNode])
        assertEquals("13", fakeShell.pathValues[sconfigPath])

        // Mid-session: turn OFF CPU_FLOOR
        manager.setIntent(TuneId.CPU_FLOOR, TuneValue(on = false))
        delay(150) // Allow IO job to finish

        // CPU_FLOOR must restore to original
        assertEquals("300000", fakeShell.pathValues[cpuNode])
        // THERMAL_SCONFIG must REMAIN tuned to 13
        assertEquals("13", fakeShell.pathValues[sconfigPath])
        // Session remains active
        assertTrue(manager.sessionActive.value)
    }
}
