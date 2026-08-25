package com.ivarna.apexcore.tune

import android.content.Context
import com.ivarna.apexcore.fps.privilege.PrivilegeTier
import com.ivarna.apexcore.tune.cpu.CpuPolicyDiscovery
import com.ivarna.apexcore.tune.gpu.GpuDevfreqDiscovery
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock

class T13DynamicBackendTest {
    private val shell = FakeTuneShell()
    private val context: Context = mock(Context::class.java)

    @Before
    fun setUp() {
        shell.commandOutputs["find /sys/devices/system/cpu/cpufreq"] =
            "/sys/devices/system/cpu/cpufreq/policy0\n/sys/devices/system/cpu/cpufreq/policy7\n"
        for (policy in listOf("policy0", "policy7")) {
            val base = "/sys/devices/system/cpu/cpufreq/$policy"
            shell.pathValues["$base/scaling_min_freq"] = "300000"
            shell.pathValues["$base/scaling_max_freq"] = "1800000"
            shell.pathValues["$base/scaling_governor"] = "schedutil"
            shell.pathValues["$base/scaling_available_governors"] = "schedutil performance"
            shell.pathValues["$base/scaling_available_frequencies"] = "300000 960000 1800000"
            shell.pathValues["$base/cpuinfo_max_freq"] = "1800000"
            shell.pathValues["$base/related_cpus"] = "0 1"
            shell.pathValues["$base/scaling_driver"] = "schedutil"
        }
    }

    @Test
    fun policiesAreDiscoveredFromLiveDirectoryListing() {
        val policies = CpuPolicyDiscovery.discover(shell, PrivilegeTier.SU_ROOT)
        assertEquals(listOf("policy0", "policy7"), policies.map { it.name })
        assertEquals(1_800_000L, policies.first().targetMaxKhz)
    }

    @Test
    fun cpuGovernorOptionsAreTheIntersection() {
        val first = CpuPolicyDiscovery.discover(shell, PrivilegeTier.SU_ROOT)
        val second = first.last().copy(availableGovernors = setOf("schedutil"))
        assertEquals(setOf("schedutil"), CpuPolicyDiscovery.governorIntersection(listOf(first.first(), second)))
    }

    @Test
    fun unrelatedDevfreqIsRejected() {
        shell.commandOutputs["find /sys/class/devfreq"] = "/sys/class/devfreq/ddr0\n"
        shell.pathValues["/sys/class/devfreq/ddr0/min_freq"] = "1"
        shell.pathValues["/sys/class/devfreq/ddr0/max_freq"] = "2"
        shell.pathValues["/sys/class/devfreq/ddr0/governor"] = "simple_ondemand"
        shell.pathValues["/sys/class/devfreq/ddr0/available_governors"] = "simple_ondemand"
        val discovered = GpuDevfreqDiscovery.discover(shell, PrivilegeTier.SU_ROOT)
        assertTrue(discovered.none { it.basePath.contains("ddr0") })
    }

    @Test
    fun sharedSnapshotOwnershipDefersRestoreUntilLastOwner() {
        val store = TuneSnapshotStore(context, FakeKeyValue(), shell)
        val path = "/sys/devices/system/cpu/cpufreq/policy0/scaling_max_freq"
        store.recordOriginal(path, "1800000", TuneId.CPU_LOCK_MAX, "tx-cpu", TuneBackendIdentity.SU_ROOT)
        store.recordOriginal(path, "1800000", TuneId.GPU_LOCK_MAX, "tx-gpu", TuneBackendIdentity.SU_ROOT)
        assertFalse(store.releaseOwner(path, TuneId.CPU_LOCK_MAX))
        assertEquals("1800000", store.getOriginal(path))
        assertTrue(store.releaseOwner(path, TuneId.GPU_LOCK_MAX))
    }

    @Test
    fun gameModeCapabilityUsesAdvertisedModesAndCurrentMode() {
        shell.commandOutputs["cmd game list-modes"] = "standard battery performance"
        shell.commandOutputs["cmd game get-mode"] = "performance"
        val controller = GameModeController(shell, TuneSnapshotStore(context, FakeKeyValue(), shell))
        val capability = controller.query("com.example.game", PrivilegeTier.SHIZUKU_SHELL)
        assertTrue(capability.supportsPerformance)
        assertEquals("performance", capability.currentMode)
        assertTrue("performance" in capability.availableModes)
    }
}
