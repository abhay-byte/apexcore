package com.ivarna.apexcore.tune

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
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

class OverlayBoostAndHomePurgeDoNotTuneTest {

    private val fakeShell = FakeTuneShell()
    private val fakeKv = FakeKeyValue()
    private val context: Context = mock(Context::class.java)
    private val pm: PackageManager = mock(PackageManager::class.java)
    private val prefsMock: SharedPreferences = mock(SharedPreferences::class.java)

    @Before
    fun setUp() {
        `when`(context.packageName).thenReturn("com.ivarna.apexcore")
        `when`(context.applicationContext).thenReturn(context)
        `when`(context.packageManager).thenReturn(pm)
        `when`(pm.getInstalledApplications(PackageManager.GET_META_DATA)).thenReturn(emptyList())
        `when`(context.getSharedPreferences("apexcore_whitelist", Context.MODE_PRIVATE)).thenReturn(prefsMock)
        `when`(prefsMock.getStringSet("pinned_packages", emptySet())).thenReturn(emptySet())

        val resolver = FreezeBackendResolver(listOf(TestFreezeBackend("Root", 0, ready = true)))
        FreezeFramework.setResolverForTest(resolver)
    }

    @After
    fun tearDown() {
        FreezeFramework.setResolverForTest(null)
        TuneManager.setInstanceForTest(null)
    }

    @Test
    fun testHomePurgeAndOverlayBoostDoNotCallTuneApply() = runBlocking {
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

        // Run freezeAll (Home Purge / Overlay BOOST)
        FreezeFramework.freezeAll(context)

        // Verify no tune paths were modified
        assertEquals("0", fakeShell.pathValues[sconfigPath])
        assertFalse("Session should NOT be active from freeze", manager.sessionActive.value)
        assertEquals(0, fakeShell.writeAttempts.size)
    }
}
