package com.ivarna.apexcore.tune

import android.content.Context
import com.ivarna.apexcore.freeze.FreezeBackendResolver
import com.ivarna.apexcore.freeze.FreezeFramework
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

class RefreshCapabilitiesSyncTest {

    private val fakeShell = FakeTuneShell()
    private val context: Context = mock(Context::class.java)

    @Before
    fun setUp() {
        `when`(context.packageName).thenReturn("com.ivarna.apexcore")
        `when`(context.applicationContext).thenReturn(context)
        val resolver = FreezeBackendResolver(listOf(TestFreezeBackend("Root", 0, ready = true)))
        FreezeFramework.setResolverForTest(resolver)
        kotlinx.coroutines.runBlocking { FreezeFramework.detect() }
    }

    @org.junit.After
    fun tearDown() {
        FreezeFramework.setResolverForTest(null)
        FreezeFramework.setActiveBackendForTest(null)
    }

    @Test
    fun testProbeSyncUsesCacheWithinTtl() = runBlocking {
        val sconfigPath = "/sys/class/thermal/thermal_message/sconfig"
        fakeShell.existingPaths.add(sconfigPath)
        fakeShell.pathValues[sconfigPath] = "0"

        val probe = TuneProbe(context, fakeShell)
        val caps1 = probe.probeSync()
        val writesAfterFirst = fakeShell.writeAttempts.size

        // Second sync within TTL should be cached (no additional writes)
        val caps2 = probe.probeSync()
        val writesAfterSecond = fakeShell.writeAttempts.size

        assertEquals(caps1, caps2)
        assertEquals("Second probeSync within 60s should use cache and not add writes", writesAfterFirst, writesAfterSecond)
    }

    @Test
    fun testProbeSyncForceBypassesCache() = runBlocking {
        val sconfigPath = "/sys/class/thermal/thermal_message/sconfig"
        fakeShell.existingPaths.add(sconfigPath)
        fakeShell.pathValues[sconfigPath] = "0"

        val probe = TuneProbe(context, fakeShell)
        probe.probeSync()
        val writesAfterFirst = fakeShell.writeAttempts.size

        // Force should re-probe even within TTL
        probe.probeSync(force = true)
        val writesAfterForce = fakeShell.writeAttempts.size

        assertTrue("Force probe should perform additional writes", writesAfterForce > writesAfterFirst)
    }

    @Test
    fun testProbeSyncJoinsExistingJob() = runBlocking {
        val sconfigPath = "/sys/class/thermal/thermal_message/sconfig"
        fakeShell.existingPaths.add(sconfigPath)
        fakeShell.pathValues[sconfigPath] = "0"
        // Slow down writes to make probe take a bit
        fakeShell.sleepOnWriteMs = 30

        val probe = TuneProbe(context, fakeShell)
        probe.refreshCapabilities() // async
        // Immediate sync should join the async job and then respect cache or re-probe
        val caps = probe.probeSync()
        assertNotNull(caps)
        fakeShell.sleepOnWriteMs = 0
    }
}
