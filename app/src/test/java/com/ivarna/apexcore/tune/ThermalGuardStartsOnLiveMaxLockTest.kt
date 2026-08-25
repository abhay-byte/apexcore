package com.ivarna.apexcore.tune

import android.content.Context
import com.ivarna.apexcore.fps.privilege.PrivilegeModeStore
import com.ivarna.apexcore.fps.util.ShellExecutor
import com.ivarna.apexcore.fps.privilege.ShellGateway
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

class ThermalGuardStartsOnLiveMaxLockTest {

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

        // Setup CPU policies for CpuPolicyDiscovery
        fakeShell.commandOutputs["find /sys/devices/system/cpu/cpufreq"] =
            "/sys/devices/system/cpu/cpufreq/policy0\n/sys/devices/system/cpu/cpufreq/policy7\n"
        for (policy in listOf("policy0", "policy7")) {
            val base = "/sys/devices/system/cpu/cpufreq/$policy"
            fakeShell.pathValues["$base/scaling_min_freq"] = "300000"
            fakeShell.pathValues["$base/scaling_max_freq"] = "1800000"
            fakeShell.pathValues["$base/scaling_governor"] = "schedutil"
            fakeShell.pathValues["$base/scaling_available_governors"] = "schedutil performance"
            fakeShell.pathValues["$base/scaling_available_frequencies"] = "300000 960000 1800000"
            fakeShell.pathValues["$base/cpuinfo_max_freq"] = "1800000"
            fakeShell.pathValues["$base/related_cpus"] = if (policy == "policy0") "0 1 2 3" else "4 5 6 7"
            fakeShell.pathValues["$base/scaling_driver"] = "schedutil"
            fakeShell.existingPaths.add("$base/scaling_min_freq")
            fakeShell.existingPaths.add("$base/scaling_max_freq")
            fakeShell.existingPaths.add("$base/scaling_governor")
        }
        // For probeCpuLock etc, need those paths existing
        fakeShell.existingPaths.add("/sys/class/thermal/thermal_message/sconfig")
        fakeShell.pathValues["/sys/class/thermal/thermal_message/sconfig"] = "0"
    }

    @After
    fun tearDown() {
        FreezeFramework.setResolverForTest(null)
        FreezeFramework.setActiveBackendForTest(null)
        TuneManager.setInstanceForTest(null)
    }

    private fun createManager(): TuneManager {
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
        TuneManager.setInstanceForTest(manager)
        return manager
    }

    @Test
    fun testLiveMaxLockStartsGuardOnlyWhenVerified() = runBlocking {
        val manager = createManager()
        // Probe to make capabilities available
        manager.probe.probeSync(force = true)
        // Make a simple session active without max lock
        val prefs = manager.prefs
        prefs.setIntent(TuneId.THERMAL_SCONFIG, TuneValue(on = true))
        prefs.setIntent(TuneId.CPU_LOCK_MAX, TuneValue(on = false))
        prefs.setIntent(TuneId.GPU_LOCK_MAX, TuneValue(on = false))
        val report = manager.applyForSession("com.test.game")
        assertTrue("Session should be active after THERMAL_SCONFIG", manager.sessionActive.value)
        // Guard should not be started yet since no max lock verified
        assertFalse("Guard should not start without max lock", manager.isThermalGuardStarted())

        // Now live: turn on CPU_LOCK_MAX with successful writes -> count >0 -> guard should start
        fakeShell.failWritePaths.clear()
        val ok = manager.setIntent(TuneId.CPU_LOCK_MAX, TuneValue(on = true))
        assertTrue(ok)
        delay(400)
        assertTrue("Guard should start after verified live max-lock", manager.isThermalGuardStarted())
        assertTrue("Verified count should include CPU_LOCK_MAX", manager.verifiedComponents.value[TuneId.CPU_LOCK_MAX] == true)

        // Cleanup: turn off to stop guard
        manager.setIntent(TuneId.CPU_LOCK_MAX, TuneValue(on = false))
        delay(300)
        // After restore, guard should stop when no max owners remain
        // Note: restore may need tier, but we have Root
        assertFalse("Guard should stop after max lock off with no owners", manager.isThermalGuardStarted())
    }

    @Test
    fun testLiveMaxLockDoesNotStartGuardWhenVerificationFails() = runBlocking {
        val manager = createManager()
        manager.probe.probeSync(force = true)
        val prefs = manager.prefs
        prefs.setIntent(TuneId.THERMAL_SCONFIG, TuneValue(on = true))
        prefs.setIntent(TuneId.CPU_LOCK_MAX, TuneValue(on = false))
        manager.applyForSession("com.test.game")
        assertTrue(manager.sessionActive.value)
        assertFalse(manager.isThermalGuardStarted())

        // Make CPU lock writes fail
        for (policy in listOf("policy0", "policy7")) {
            val base = "/sys/devices/system/cpu/cpufreq/$policy"
            fakeShell.failWritePaths.add("$base/scaling_min_freq")
            fakeShell.failWritePaths.add("$base/scaling_max_freq")
        }
        manager.setIntent(TuneId.CPU_LOCK_MAX, TuneValue(on = true))
        delay(400)
        assertFalse("Guard must NOT start when live max-lock verification fails", manager.isThermalGuardStarted())
        assertTrue("Verified component should be false on failure", manager.verifiedComponents.value[TuneId.CPU_LOCK_MAX] == false)
    }
}
