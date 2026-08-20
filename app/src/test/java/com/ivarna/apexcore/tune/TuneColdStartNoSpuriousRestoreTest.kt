package com.ivarna.apexcore.tune

import android.content.Context
import com.ivarna.apexcore.fps.privilege.PrivilegeModeStore
import com.ivarna.apexcore.fps.privilege.ShellGateway
import com.ivarna.apexcore.fps.util.ShellExecutor
import com.ivarna.apexcore.freeze.FreezeBackendResolver
import com.ivarna.apexcore.freeze.FreezeFramework
import com.ivarna.apexcore.games.GameOverlayService
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

/**
 * Regression: observeBackendChanges must NOT restore on the cold-start initial
 * null emission of FreezeFramework.activeBackend. Recovery owns cold start
 * (boot-match + orphan/rehydrate decision); the collector only reacts to a real
 * drop from a previously elevated backend.
 */
class TuneColdStartNoSpuriousRestoreTest {

    private val fakeShell = FakeTuneShell()
    private val fakeKv = FakeKeyValue()
    private val context: Context = mock(Context::class.java)

    @Before
    fun setUp() {
        `when`(context.packageName).thenReturn("com.ivarna.apexcore")
        `when`(context.applicationContext).thenReturn(context)
    }

    @After
    fun tearDown() {
        FreezeFramework.setResolverForTest(null)
        FreezeFramework.setActiveBackendForTest(null)
        GameOverlayService.isRunning = false
    }

    @Test
    fun testColdStartInitialNullBackendDoesNotSpuriouslyRestore() = runBlocking {
        // Persisted state from a previous process death mid-session:
        // snapshot dirty, boot matches, overlay still running.
        fakeKv.putBoolean(TuneSnapshotStore.KEY_APPLIED, true)
        fakeKv.putString(TuneSnapshotStore.KEY_BOOT_ID, "boot-match-1")
        fakeKv.putString(TuneSnapshotStore.KEY_SNAPSHOT_JSON, "{\"/sys/node\":\"orig\"}")

        fakeShell.existingPaths.add("/proc/sys/kernel/random/boot_id")
        fakeShell.pathValues["/proc/sys/kernel/random/boot_id"] = "boot-match-1"
        fakeShell.pathValues["/sys/node"] = "tuned"

        // Overlay running -> recovery must REHYDRATE, never restore.
        GameOverlayService.isRunning = true

        // Cold start: no backend resolved yet; empty resolver keeps it null.
        FreezeFramework.setResolverForTest(FreezeBackendResolver(emptyList()))
        FreezeFramework.setActiveBackendForTest(null)

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

        // Let async recovery + backend collector settle.
        delay(500)

        assertTrue("Session should rehydrate, not restore, on cold start", manager.sessionActive.value)
        assertEquals("Owner should be OVERLAY after rehydrate", TuneSessionOwner.OVERLAY, manager.owner)
        assertEquals(
            "Initial null backend emission must not trigger a mid-game restore",
            "tuned",
            fakeShell.pathValues["/sys/node"]
        )
    }
}