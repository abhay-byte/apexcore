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

class TuneProbeRootWriteVerifyTest {

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
    fun testExistsButWriteFailsIsMarkedUnavailable() = runBlocking {
        val sconfigPath = "/sys/class/thermal/thermal_message/sconfig"
        fakeShell.existingPaths.add(sconfigPath)
        fakeShell.pathValues[sconfigPath] = "0"
        // Force write to fail (e.g. SELinux denies chmod or write)
        fakeShell.failWritePaths.add(sconfigPath)

        FreezeFramework.detect()
        val probe = TuneProbe(context, fakeShell)

        val caps = probe.probeSync()

        val sconfigCap = caps[TuneId.THERMAL_SCONFIG]
        assertNotNull(sconfigCap)
        assertFalse("Node that fails write verification must be marked unavailable", sconfigCap!!.available)
    }
}
