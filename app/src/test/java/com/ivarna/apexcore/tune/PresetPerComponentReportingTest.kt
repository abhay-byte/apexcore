package com.ivarna.apexcore.tune

import android.content.Context
import com.ivarna.apexcore.fps.privilege.PrivilegeModeStore
import com.ivarna.apexcore.fps.privilege.ShellGateway
import com.ivarna.apexcore.fps.util.ShellExecutor
import com.ivarna.apexcore.freeze.FreezeBackendResolver
import com.ivarna.apexcore.freeze.FreezeFramework
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

class PresetPerComponentReportingTest {

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

        fakeShell.commandOutputs["find /sys/devices/system/cpu/cpufreq"] =
            "/sys/devices/system/cpu/cpufreq/policy0\n/sys/devices/system/cpu/cpufreq/policy4\n/sys/devices/system/cpu/cpufreq/policy7\n"
        for (policy in listOf("policy0", "policy4", "policy7")) {
            val base = "/sys/devices/system/cpu/cpufreq/$policy"
            fakeShell.pathValues["$base/scaling_min_freq"] = "300000"
            fakeShell.pathValues["$base/scaling_max_freq"] = "1800000"
            fakeShell.pathValues["$base/scaling_governor"] = "schedutil"
            fakeShell.pathValues["$base/scaling_available_governors"] = "schedutil performance"
            fakeShell.pathValues["$base/scaling_available_frequencies"] = "300000 960000 1800000"
            fakeShell.pathValues["$base/cpuinfo_max_freq"] = "1800000"
            fakeShell.pathValues["$base/related_cpus"] = "0"
            fakeShell.pathValues["$base/scaling_driver"] = "schedutil"
            fakeShell.existingPaths.add("$base/scaling_min_freq")
            fakeShell.existingPaths.add("$base/scaling_max_freq")
        }
        fakeShell.existingPaths.add("/sys/class/thermal/thermal_message/sconfig")
        fakeShell.pathValues["/sys/class/thermal/thermal_message/sconfig"] = "0"
        // Make GPU unavailable by not adding gpu devfreq paths
        // Game mode unavailable by not setting cmd game outputs
        fakeShell.commandOutputs["cmd game list-modes com.test.game"] = "standard battery"
    }

    @After
    fun tearDown() {
        FreezeFramework.setResolverForTest(null)
        FreezeFramework.setActiveBackendForTest(null)
        TuneManager.setInstanceForTest(null)
    }

    private fun setProbeCapabilities(probe: TuneProbe, caps: Map<TuneId, TuneCapability>) {
        try {
            val field = TuneProbe::class.java.getDeclaredField("_capabilities")
            field.isAccessible = true
            @Suppress("UNCHECKED_CAST")
            val flow = field.get(probe) as MutableStateFlow<Map<TuneId, TuneCapability>>
            flow.value = caps
        } catch (e: Exception) {
            throw RuntimeException(e)
        }
    }

    @Test
    fun testPresetReportsPerComponentTruthNotRawCount() = runBlocking {
        val prefs = TunePrefs(fakeKv)
        val snapshotStore = TuneSnapshotStore(context, fakeKv, fakeShell)
        val probe = TuneProbe(context, fakeShell)
        val applier = TuneApplier(context, fakeShell, snapshotStore)
        val executor = ShellExecutor()
        val store = mock(PrivilegeModeStore::class.java)
        val gateway = ShellGateway(executor, store)
        val manager = TuneManager(context, gateway, prefs, snapshotStore, probe, applier)
        TuneManager.setInstanceForTest(manager)

        // Probe once to establish baseline then override to desired caps
        probe.probeSync(force = true)

        // Build capabilities where only CPU_LOCK_MAX is available (and maybe thermal)
        // Preset ids: GAME_MODE_PERFORMANCE, CPU_GOVERNOR, CPU_LOCK_MAX, GPU_GOVERNOR, GPU_LOCK_MAX
        val fakeCaps = mutableMapOf<TuneId, TuneCapability>()
        for (id in TuneId.values()) {
            fakeCaps[id] = TuneCapability(id, false, false, emptyList(), "Not available")
        }
        // Only CPU_LOCK_MAX is truly available
        fakeCaps[TuneId.CPU_LOCK_MAX] = TuneCapability(
            id = TuneId.CPU_LOCK_MAX,
            available = true,
            needsRoot = false,
            writablePaths = listOf(
                "/sys/devices/system/cpu/cpufreq/policy0/scaling_min_freq",
                "/sys/devices/system/cpu/cpufreq/policy0/scaling_max_freq"
            ),
            subtitle = "Available",
            availableOptions = emptyList()
        )
        // Make THERMAL_SCONFIG available to allow session to be considered active (but not part of preset)
        fakeCaps[TuneId.THERMAL_SCONFIG] = TuneCapability(
            id = TuneId.THERMAL_SCONFIG,
            available = true,
            needsRoot = false,
            writablePaths = listOf("/sys/class/thermal/thermal_message/sconfig"),
            subtitle = "Available"
        )
        setProbeCapabilities(probe, fakeCaps)

        // Set intents for preset ids to ON (even though most are not available, preset will filter)
        prefs.setIntent(TuneId.GAME_MODE_PERFORMANCE, TuneValue(on = true))
        prefs.setIntent(TuneId.CPU_GOVERNOR, TuneValue(on = true, raw = "performance"))
        prefs.setIntent(TuneId.CPU_LOCK_MAX, TuneValue(on = true))
        prefs.setIntent(TuneId.GPU_GOVERNOR, TuneValue(on = true, raw = "performance"))
        prefs.setIntent(TuneId.GPU_LOCK_MAX, TuneValue(on = true))

        val presetManager = TunePresetManager(manager)
        val report = presetManager.applyMaximumPerformance("com.test.game")

        // Preset should request only supported components: only CPU_LOCK_MAX is supported -> requested =1
        // But also need to check our available logic: GAME_MODE supportsPerformance is false, governors need "performance" in availableOptions (empty), so they are not supported.
        assertEquals("Only CPU_LOCK_MAX should be counted as requested", 1, report.requested)

        // Applied should be 1 if CPU lock succeeded (3 policies *2 =6 writes but counts as 1 component)
        // This checks that we didn't falsely count raw writes as components.
        assertEquals(1, report.applied)

        // Per-component truth: CPU_LOCK_MAX verified true, others false
        val cpuLockComp = report.components.find { it.id == TuneId.CPU_LOCK_MAX }
        assertNotNull(cpuLockComp)
        assertTrue("CPU_LOCK_MAX should be verified", cpuLockComp!!.verified)

        val gameModeComp = report.components.find { it.id == TuneId.GAME_MODE_PERFORMANCE }
        assertNotNull(gameModeComp)
        assertFalse("GAME_MODE_PERFORMANCE should NOT be verified when not supported", gameModeComp!!.verified)

        val gpuGovComp = report.components.find { it.id == TuneId.GPU_GOVERNOR }
        assertNotNull(gpuGovComp)
        assertFalse(gpuGovComp!!.verified)

        // Also ensure report.components size is 5 (all preset ids)
        assertEquals(5, report.components.size)
        // Partial should be false when applied == requested (1==1)
        assertFalse(report.partial)

        // Now test falsified bug would have been 6 writes -> applied 5 (coerceAtMost) and first 5 components marked verified incorrectly
        // Our fixed code should have applied 1, not 5 or 6
        assertTrue("Applied must not be falsified 6 from 3 policies*2", report.applied != 6)
    }

    @Test
    fun testPresetPartialWhenSomeComponentsFail() = runBlocking {
        val prefs = TunePrefs(FakeKeyValue())
        val snapshotStore = TuneSnapshotStore(context, FakeKeyValue(), fakeShell)
        val probe = TuneProbe(context, fakeShell)
        val applier = TuneApplier(context, fakeShell, snapshotStore)
        val executor = ShellExecutor()
        val store = mock(PrivilegeModeStore::class.java)
        val gateway = ShellGateway(executor, store)
        val manager = TuneManager(context, gateway, prefs, snapshotStore, probe, applier)
        TuneManager.setInstanceForTest(manager)
        probe.probeSync(force = true)
        val fakeCaps = mutableMapOf<TuneId, TuneCapability>()
        for (id in TuneId.values()) fakeCaps[id] = TuneCapability(id, false, false, emptyList(), "Not available")
        fakeCaps[TuneId.CPU_LOCK_MAX] = TuneCapability(TuneId.CPU_LOCK_MAX, true, false, listOf("a"), "Available")
        fakeCaps[TuneId.GPU_LOCK_MAX] = TuneCapability(TuneId.GPU_LOCK_MAX, true, false, listOf("b"), "Available")
        setProbeCapabilities(probe, fakeCaps)

        // Make GPU lock fail by adding its paths to failWritePaths (needs gpu discovery)
        // For this test, GPU lock will be considered available but write will fail (since no gpu descriptor, apply returns 0)
        // So preset should have requested 2, applied 1 (CPU succeeds, GPU fails), partial true
        prefs.setIntent(TuneId.CPU_LOCK_MAX, TuneValue(on = true))
        prefs.setIntent(TuneId.GPU_LOCK_MAX, TuneValue(on = true))
        prefs.setIntent(TuneId.GAME_MODE_PERFORMANCE, TuneValue(on = false))
        prefs.setIntent(TuneId.CPU_GOVERNOR, TuneValue(on = false))
        prefs.setIntent(TuneId.GPU_GOVERNOR, TuneValue(on = false))

        // Need to make GPU descriptor unavailable: since we didn't set gpu paths, GpuDevfreqDiscovery will return null, so GPU lock apply returns 0
        val presetManager = TunePresetManager(manager)
        val report = presetManager.applyMaximumPerformance("com.test.game")
        assertEquals(2, report.requested)
        assertEquals(1, report.applied) // only CPU
        assertTrue(report.partial)
        val gpuComp = report.components.find { it.id == TuneId.GPU_LOCK_MAX }!!
        assertFalse(gpuComp.verified)
    }
}
