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

class TuneFailedRestoreSnapshotRetainedTest {

    private val fakeShell = FakeTuneShell()
    private val fakeKv = FakeKeyValue()
    private val context: Context = mock(Context::class.java)

    @Before
    fun setUp() {
        `when`(context.packageName).thenReturn("com.ivarna.apexcore")
        `when`(context.applicationContext).thenReturn(context)

        val resolver = FreezeBackendResolver(listOf(TestFreezeBackend("Root", 0, ready = true)))
        FreezeFramework.setResolverForTest(resolver)
        runBlocking {
            FreezeFramework.detect()
        }
    }

    @After
    fun tearDown() {
        FreezeFramework.setResolverForTest(null)
    }

    @Test
    fun testFailedRestoreRetainsSnapshotAndKeepsAppliedTrue() = runBlocking {
        val chargePath = "/sys/class/qcom-battery/bypass_charging_enable"
        fakeShell.existingPaths.add(chargePath)
        fakeShell.pathValues[chargePath] = "0"

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
        prefs.setIntent(TuneId.CHARGE_BYPASS, TuneValue(on = true))
        manager.applyForSession("com.test.game")

        assertTrue(manager.sessionActive.value)
        assertTrue(prefs.isApplied())
        assertEquals("1", fakeShell.pathValues[chargePath])
        assertEquals("0", snapshotStore.getOriginal(chargePath))

        // Make the restore write fail
        fakeShell.failWritePaths.add(chargePath)

        val report = manager.restoreSession()

        // Verify snapshot was NOT wiped and prefs.isApplied() stays true so recoverSession can retry
        assertTrue("tune_applied must stay true if restore failed", prefs.isApplied())
        assertEquals("0", snapshotStore.getOriginal(chargePath))
        assertTrue(snapshotStore.getAllOriginals().containsKey(chargePath))
    }
}
