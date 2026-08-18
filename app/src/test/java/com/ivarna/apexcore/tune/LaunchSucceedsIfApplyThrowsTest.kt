package com.ivarna.apexcore.tune

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.ivarna.apexcore.fps.privilege.PrivilegeModeStore
import com.ivarna.apexcore.fps.privilege.ShellGateway
import com.ivarna.apexcore.fps.util.ShellExecutor
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

class LaunchSucceedsIfApplyThrowsTest {

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
        TuneManager.setInstanceForTest(null)
    }

    @Test
    fun testLaunchSucceedsEvenIfTuneApplyFails() = runBlocking {
        val launchIntent = Intent("android.intent.action.MAIN")
        `when`(pm.getLaunchIntentForPackage("com.test.game")).thenReturn(launchIntent)

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

        // Make all writes fail
        fakeShell.failWritePaths.add("/sys/node")

        val result = GameLauncher.launch(context, "com.test.game")

        assertTrue("Game launch must succeed even if tuning throws/fails", result.success)
        assertEquals("com.test.game", result.launchedPkg)
    }
}
