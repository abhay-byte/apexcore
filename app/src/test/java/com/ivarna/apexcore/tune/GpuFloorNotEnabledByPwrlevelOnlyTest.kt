package com.ivarna.apexcore.tune

import android.content.Context
import com.ivarna.apexcore.freeze.FreezeBackendResolver
import com.ivarna.apexcore.freeze.FreezeFramework
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

class GpuFloorNotEnabledByPwrlevelOnlyTest {

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
    fun testPwrlevelNodeDoesNotEnableGpuFloor() = runBlocking {
        val pwrlevelPath = "/sys/class/kgsl/kgsl-3d0/min_pwrlevel"
        fakeShell.existingPaths.add(pwrlevelPath)
        fakeShell.pathValues[pwrlevelPath] = "3"

        FreezeFramework.detect()
        val probe = TuneProbe(context, fakeShell)

        val caps = probe.probeSync()

        assertTrue("GPU_PWRLEVEL should be available", caps[TuneId.GPU_PWRLEVEL]?.available == true)
        assertFalse("GPU_FLOOR must NOT be enabled by min_pwrlevel alone", caps[TuneId.GPU_FLOOR]?.available == true)
    }
}
