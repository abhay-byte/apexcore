package com.ivarna.apexcore.tune

import android.content.Context
import com.ivarna.apexcore.fps.privilege.PrivilegeModeStore
import com.ivarna.apexcore.fps.privilege.ShellGateway
import com.ivarna.apexcore.fps.util.ShellExecutor
import com.ivarna.apexcore.freeze.FreezeBackendResolver
import com.ivarna.apexcore.freeze.FreezeFramework
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

class WatchdogUnknownFailsTowardRestoreTest {

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
    fun testWatchdogRestoresWhenUnknownStreakExceedsThreshold() = runBlocking {
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
        TuneManager.setInstanceForTest(manager)

        probe.probeSync()
        prefs.setIntent(TuneId.THERMAL_SCONFIG, TuneValue(on = true))
        manager.applyForSession("com.test.game")

        assertEquals("13", fakeShell.pathValues[sconfigPath])
        assertTrue(manager.sessionActive.value)

        // Drive the real TuneSessionWatchdog loop with 3 null readings
        try {
            TuneSessionWatchdog.topPackageProvider = { null }
            TuneSessionWatchdog.gracePeriodOverrideMs = 10L
            TuneSessionWatchdog.pollIntervalOverrideMs = 10L

            TuneSessionWatchdog.arm(context, "com.test.game")

            // Wait for 3 polling intervals (10ms grace + 3*10ms polls + restore)
            var waited = 0
            while (manager.sessionActive.value && waited < 2000) {
                kotlinx.coroutines.delay(20)
                waited += 20
            }

            assertFalse("Session should be restored automatically by watchdog", manager.sessionActive.value)
            assertEquals("0", fakeShell.pathValues[sconfigPath])
            assertEquals(TuneSessionOwner.NONE, manager.owner)
        } finally {
            TuneSessionWatchdog.cancel()
            TuneSessionWatchdog.topPackageProvider = null
            TuneSessionWatchdog.gracePeriodOverrideMs = null
            TuneSessionWatchdog.pollIntervalOverrideMs = null
        }
    }
}
