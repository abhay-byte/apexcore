package com.ivarna.apexcore.tune

import android.content.Context
import com.ivarna.apexcore.fps.privilege.PrivilegeTier
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

class SettingsApplyRestoreTest {

    private val fakeShell = FakeTuneShell()
    private val fakeKv = FakeKeyValue()
    private val context: Context = mock(Context::class.java)

    @Before
    fun setUp() {
        `when`(context.packageName).thenReturn("com.ivarna.apexcore")
        `when`(context.applicationContext).thenReturn(context)
    }

    @Test
    fun testFocusHeadsUpAppliesAndRestoresViaSettings() = runBlocking {
        val snapshotStore = TuneSnapshotStore(context, fakeKv, fakeShell)
        val applier = TuneApplier(context, fakeShell, snapshotStore)

        // Apply FOCUS_HEADSUP
        val applyCount = applier.applyBundle(TuneId.FOCUS_HEADSUP, TuneValue(on = true), PrivilegeTier.ROOT)
        assertEquals(1, applyCount)

        val appliedCmd = fakeShell.executedCommands.find { it.first == "settings put global heads_up_notifications_enabled 0" }
        assertNotNull("Must execute 'settings put global heads_up_notifications_enabled 0'", appliedCmd)

        // Restore FOCUS_HEADSUP
        val restoreCount = applier.restoreBundle(TuneId.FOCUS_HEADSUP, PrivilegeTier.ROOT)
        assertEquals(1, restoreCount)

        val restoreCmd = fakeShell.executedCommands.find { it.first.startsWith("settings put global heads_up_notifications_enabled") }
        assertNotNull("Must execute settings restore command for heads-up", restoreCmd)
        // Verified path now does extra reads: apply put + verify get + restore put + verify get + initial read
        val putCount = fakeShell.executedCommands.count { it.first.contains("settings put") && it.first.contains("heads_up_notifications_enabled") }
        assertEquals(2, putCount)
        assertTrue("Must have verified readback via settings get", fakeShell.executedCommands.any { it.first.contains("settings get") && it.first.contains("heads_up_notifications_enabled") })
    }

    @Test
    fun testFocusImmersiveAppliesAndRestoresViaSettings() = runBlocking {
        val snapshotStore = TuneSnapshotStore(context, fakeKv, fakeShell)
        val applier = TuneApplier(context, fakeShell, snapshotStore)

        // Apply FOCUS_IMMERSIVE
        val applyCount = applier.applyBundle(TuneId.FOCUS_IMMERSIVE, TuneValue(on = true), PrivilegeTier.ROOT)
        assertEquals(1, applyCount)

        val appliedCmd = fakeShell.executedCommands.find { it.first.contains("policy_control 'immersive.full=*'") }
        assertNotNull("Must execute 'settings put global policy_control immersive.full=*'", appliedCmd)

        // Restore FOCUS_IMMERSIVE
        val restoreCount = applier.restoreBundle(TuneId.FOCUS_IMMERSIVE, PrivilegeTier.ROOT)
        assertEquals(1, restoreCount)

        val restoreCmd = fakeShell.executedCommands.find { it.first.contains("policy_control") && (it.first.contains("delete") || it.first.contains("put")) }
        assertNotNull("Must execute restore command for policy_control", restoreCmd)
    }
}
