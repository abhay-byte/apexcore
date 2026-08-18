package com.ivarna.apexcore.tune

import com.ivarna.apexcore.freeze.FreezeBackend
import com.ivarna.apexcore.freeze.FreezeBackendResolver
import com.ivarna.apexcore.freeze.FreezeFramework
import com.ivarna.apexcore.freeze.FreezeOperation
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import android.content.Context

class TestFreezeBackend(
    override val name: String = "Root",
    override val priority: Int = 0,
    private val ready: Boolean = true
) : FreezeBackend {
    override suspend fun isReady(): Boolean = ready
    override suspend fun execute(op: FreezeOperation): FreezeOperation.Result =
        FreezeOperation.Result.Success
}

class TuneProbePerTuneIdTest {

    private val fakeShell = FakeTuneShell()
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
    fun testPhase1CandidatesProbeAcrossTuneIds() = runBlocking {
        val cpuNode = "/sys/devices/system/cpu/cpufreq/policy0/scaling_min_freq"
        val thermalNode = "/sys/class/thermal/thermal_message/sconfig"

        fakeShell.existingPaths.add(cpuNode)
        fakeShell.pathValues[cpuNode] = "300000"

        fakeShell.existingPaths.add(thermalNode)
        fakeShell.pathValues[thermalNode] = "0"

        FreezeFramework.detect()
        val probe = TuneProbe(context, fakeShell)

        val caps = probe.probeSync()

        assertTrue("CPU_FLOOR should be marked available", caps[TuneId.CPU_FLOOR]?.available == true)
        assertTrue("THERMAL_SCONFIG should be marked available", caps[TuneId.THERMAL_SCONFIG]?.available == true)
        assertFalse("Unconfigured sysfs nodes should remain unavailable", caps[TuneId.VM_SWAPPINESS]?.available == true)
    }
}
