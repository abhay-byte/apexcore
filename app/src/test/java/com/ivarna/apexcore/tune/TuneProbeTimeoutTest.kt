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

class TuneProbeTimeoutTest {

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
    fun testProbeRespectsDeadlineWithoutThrowing() = runBlocking {
        for (spec in TuneSpecs.all) {
            val node = TuneCatalog.nodesByTuneId[spec.id]?.firstOrNull() ?: continue
            fakeShell.existingPaths.add(node.path)
            fakeShell.pathValues[node.path] = "1"
        }

        fakeShell.sleepOnReadMs = 50L

        FreezeFramework.detect()
        val probe = TuneProbe(context, fakeShell)

        val start = System.currentTimeMillis()
        val caps = probe.probeSync()
        val duration = System.currentTimeMillis() - start

        assertNotNull(caps)
        assertTrue("Probe must complete within reasonable time envelope", duration < 5000L)
    }
}
