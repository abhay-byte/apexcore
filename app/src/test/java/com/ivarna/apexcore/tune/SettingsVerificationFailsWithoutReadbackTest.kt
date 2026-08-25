package com.ivarna.apexcore.tune

import android.content.Context
import com.ivarna.apexcore.fps.privilege.PrivilegeTier
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

class SettingsVerificationFailsWithoutReadbackTest {

    private val context: Context = mock(Context::class.java)

    @Before
    fun setUp() {
        `when`(context.packageName).thenReturn("com.ivarna.apexcore")
        `when`(context.applicationContext).thenReturn(context)
    }

    @Test
    fun testHeadsUpVerificationFails_RequiresRollbackAttempt() {
        val fakeShell = FakeTuneShell()
        val fakeKv = FakeKeyValue()
        val snapshotStore = TuneSnapshotStore(context, fakeKv, fakeShell)
        val applier = TuneApplier(context, fakeShell, snapshotStore)

        // Seed original value "1" via settings store
        fakeShell.setSettingsValue("global", "heads_up_notifications_enabled", "1")
        // Sequence: prev read -> "1" (correct), verify read after put 0 -> "1" (wrong, expected 0), rollback verify after put 1 -> "1" (correct)
        fakeShell.settingsGetSequence.addAll(listOf("1", "1", "1"))

        val applied = applier.applyBundle(TuneId.FOCUS_HEADSUP, TuneValue(on = true), PrivilegeTier.SHIZUKU_SHELL)
        assertEquals("Apply should fail verification and return 0", 0, applied)

        // Verify write happened
        assertTrue("Must have attempted put 0", fakeShell.executedCommands.any { it.first.contains("settings put global heads_up_notifications_enabled 0") })

        // Required fix: failed verification must attempt rollback to original value "1"
        val rollbackAttempt = fakeShell.executedCommands.any { it.first.contains("settings put global heads_up_notifications_enabled 1") }
        assertTrue("Failed verification must attempt rollback to original value", rollbackAttempt)

        // After successful rollback, snapshot should be removed (clean)
        assertNull("After successful rollback, snapshot should be removed", snapshotStore.getOriginal("settings://heads_up"))
    }

    @Test
    fun testHeadsUpVerificationFails_RollbackFails_SnapshotRetained() {
        val testShell = FakeTuneShell()
        val store = TuneSnapshotStore(context, FakeKeyValue(), testShell)
        val applier = TuneApplier(context, testShell, store)

        testShell.setSettingsValue("global", "heads_up_notifications_enabled", "1")
        // Sequence: prev "1", verify "unexpected" (fail), rollback verify "unexpected" (fail -> retain)
        testShell.settingsGetSequence.addAll(listOf("1", "unexpected", "unexpected"))

        val applied = applier.applyBundle(TuneId.FOCUS_HEADSUP, TuneValue(on = true), PrivilegeTier.SHIZUKU_SHELL)
        assertEquals(0, applied)
        // Rollback attempt must have happened
        assertTrue(testShell.executedCommands.any { it.first.contains("settings put global heads_up_notifications_enabled 1") })
        // Since rollback verification also fails (expected 1 got unexpected), snapshot must be retained for recovery
        assertNotNull("Snapshot must be retained if rollback fails", store.getOriginal("settings://heads_up"))
        assertEquals("1", store.getOriginal("settings://heads_up"))
    }

    @Test
    fun testPeakVerificationFails_KeepsSnapshotIfRollbackFails() {
        val testShell = FakeTuneShell()
        val store = TuneSnapshotStore(context, FakeKeyValue(), testShell)
        val applier = TuneApplier(context, testShell, store)

        // Seed peak/min values
        testShell.setSettingsValue("system", "peak_refresh_rate", "120")
        testShell.setSettingsValue("system", "min_refresh_rate", "60")
        // Sequence: curPeak "120", curMin "60", peakAfter "unexpected", minAfter "unexpected", rollbackPeak "unexpected", rollbackMin "unexpected"
        // Note: readSettingsTriad for peak tries system only (since we seeded system), so 1 get per triad call.
        testShell.settingsGetSequence.addAll(listOf("120", "60", "unexpected", "unexpected", "unexpected", "unexpected"))

        val applied = applier.applyBundle(TuneId.DISPLAY_PEAK, TuneValue(on = true), PrivilegeTier.SHIZUKU_SHELL)
        assertEquals(0, applied)
        // Must have attempted writes
        assertTrue(testShell.executedCommands.any { it.first.contains("peak_refresh_rate") })
        // At least one snapshot should be retained when rollback fails
        val hasPeak = store.getOriginal("settings://peak_refresh_rate") != null
        val hasMin = store.getOriginal("settings://min_refresh_rate") != null
        assertTrue("At least one snapshot must be retained when rollback fails", hasPeak || hasMin)
    }

    @Test
    fun testImmersiveVerificationFails_RollbackToEmptyUsesDelete() {
        val testShell = FakeTuneShell()
        val store = TuneSnapshotStore(context, FakeKeyValue(), testShell)
        val applier = TuneApplier(context, testShell, store)

        // Original is empty (no policy_control)
        // Sequence: prev "" (empty -> will be blank), verify "unexpected" (fail), rollback verify "" (after delete, empty)
        // First read returns empty blank: we need sequence first element "" to represent blank.
        testShell.settingsGetSequence.addAll(listOf("", "unexpected", ""))

        val applied = applier.applyBundle(TuneId.FOCUS_IMMERSIVE, TuneValue(on = true), PrivilegeTier.SHIZUKU_SHELL)
        assertEquals(0, applied)
        // Should attempt rollback via delete when original was blank
        val hasRollback = testShell.executedCommands.any { it.first.contains("policy_control") && (it.first.contains("delete") || it.first.contains("put")) }
        assertTrue("Immersive failure must attempt rollback", hasRollback)
    }

    @Test
    fun testMiuiVerificationFails_RetainsSnapshotIfRollbackFails() {
        val testShell = FakeTuneShell()
        val store = TuneSnapshotStore(context, FakeKeyValue(), testShell)
        val applier = TuneApplier(context, testShell, store)

        testShell.setSettingsValue("system", "refresh_rate_mode", "0")
        testShell.setSettingsValue("system", "miui_refresh_rate", "60")
        // Sequence: mode read "0", rate read "60", after put mode verify "unexpected", after put rate verify "unexpected", rollback mode verify "unexpected", rollback rate verify "unexpected"
        // But readSettingsWithFallback for mode and rate each does one get, so 2 initial reads, then 2 verify reads, then 2 rollback reads = 6
        testShell.settingsGetSequence.addAll(listOf("0", "60", "unexpected", "unexpected", "unexpected", "unexpected"))

        val applied = applier.applyBundle(TuneId.DISPLAY_MIUI, TuneValue(on = true), PrivilegeTier.SHIZUKU_SHELL)
        assertEquals("Should fail when both verify fail", 0, applied)
        // At least one snapshot retained
        val hasMode = store.getOriginal("settings://refresh_rate_mode") != null
        val hasRate = store.getOriginal("settings://miui_refresh_rate") != null
        assertTrue(hasMode || hasRate)
    }
}
