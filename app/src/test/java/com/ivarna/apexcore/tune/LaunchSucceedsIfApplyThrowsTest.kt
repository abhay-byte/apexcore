package com.ivarna.apexcore.tune

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.ivarna.apexcore.fps.privilege.PrivilegeModeStore
import com.ivarna.apexcore.fps.privilege.PrivilegeTier
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

        // Create a manager whose applier throws an exception during apply
        val throwingApplier = object : TuneApplier(context, fakeShell, snapshotStore) {
            override fun applyBundle(id: TuneId, intent: TuneValue, tier: PrivilegeTier): Int {
                throw RuntimeException("Simulated disk error during kernel write")
            }
        }

        val manager = TuneManager(
            appContext = context,
            shellGateway = gateway,
            prefs = prefs,
            snapshotStore = snapshotStore,
            probe = probe,
            applier = throwingApplier
        )
        TuneManager.setInstanceForTest(manager)

        prefs.setIntent(TuneId.THERMAL_SCONFIG, TuneValue(on = true))

        val result = GameLauncher.launch(context, "com.test.game")

        assertTrue("Game launch must succeed even if tuning throws an exception", result.success)
        assertEquals("com.test.game", result.launchedPkg)
    }
}
