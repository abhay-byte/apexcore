package com.ivarna.apexcore.tune

import android.content.Context
import com.ivarna.apexcore.fps.privilege.PrivilegeModeStore
import com.ivarna.apexcore.fps.privilege.ShellGateway
import com.ivarna.apexcore.fps.util.ShellExecutor
import com.ivarna.apexcore.freeze.FreezeBackendResolver
import com.ivarna.apexcore.freeze.FreezeFramework
import com.ivarna.apexcore.games.GameOverlayService
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

class TuneOrphanWhenOverlayRunningTest {

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
        GameOverlayService.isRunning = false
    }

    @Test
    fun testOverlayRunningPreventsOrphanRestoreAndRehydratesOwner() = runBlocking {
        fakeKv.putBoolean(TuneSnapshotStore.KEY_APPLIED, true)
        fakeKv.putString(TuneSnapshotStore.KEY_BOOT_ID, "boot-match-1")
        fakeKv.putString(TuneSnapshotStore.KEY_SNAPSHOT_JSON, "{\"/sys/node\":\"orig\"}")

        fakeShell.existingPaths.add("/proc/sys/kernel/random/boot_id")
        fakeShell.pathValues["/proc/sys/kernel/random/boot_id"] = "boot-match-1"
        fakeShell.pathValues["/sys/node"] = "tuned"

        // Simulate overlay running
        GameOverlayService.isRunning = true

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

        manager.recoverSession()

        assertTrue("SessionActive should rehydrate to true", manager.sessionActive.value)
        assertEquals("Owner should rehydrate to OVERLAY", TuneSessionOwner.OVERLAY, manager.owner)
        // Node should NOT have been restored
        assertEquals("tuned", fakeShell.pathValues["/sys/node"])
    }
}
