package com.ivarna.apexcore.tune

import android.content.Context
import android.content.pm.PackageManager
import com.ivarna.apexcore.freeze.FreezeBackendResolver
import com.ivarna.apexcore.freeze.FreezeFramework
import com.ivarna.apexcore.games.GameLauncher
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

class LaunchFailDoesNotApplyTest {

    private val fakeShell = FakeTuneShell()
    private val fakeKv = FakeKeyValue()
    private val context: Context = mock(Context::class.java)
    private val pm: PackageManager = mock(PackageManager::class.java)

    @Before
    fun setUp() {
        `when`(context.packageName).thenReturn("com.ivarna.apexcore")
        `when`(context.applicationContext).thenReturn(context)
        `when`(context.packageManager).thenReturn(pm)

        val resolver = FreezeBackendResolver(listOf(TestFreezeBackend("Root", 0, ready = true)))
        FreezeFramework.setResolverForTest(resolver)
    }

    @After
    fun tearDown() {
        FreezeFramework.setResolverForTest(null)
    }

    @Test
    fun testStartActivityFailureDoesNotApplyTune() = runBlocking {
        val sconfigPath = "/sys/class/thermal/thermal_message/sconfig"
        fakeShell.existingPaths.add(sconfigPath)
        fakeShell.pathValues[sconfigPath] = "0"

        // Simulate no launch intent / failure
        `when`(pm.getLaunchIntentForPackage("com.missing.game")).thenReturn(null)

        val result = GameLauncher.launch(context, "com.missing.game")

        assertFalse("Launch must fail", result.success)
        // Tune must not have been applied
        assertEquals("0", fakeShell.pathValues[sconfigPath])
        assertEquals(0, fakeShell.writeAttempts.size)
    }
}
