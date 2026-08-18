package com.ivarna.apexcore.tune

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class TunePrefsNoDummyTrueMigrationTest {

    private val fakeKv = FakeKeyValue()
    private lateinit var prefs: TunePrefs

    @Before
    fun setUp() {
        prefs = TunePrefs(fakeKv)
    }

    @Test
    fun testDummyTrueValuesAreNeverMigratedAndDummyKeysAreRemoved() {
        // Set old dummy keys to true
        fakeKv.putBoolean("dummy_opt_gpu_render", true)
        fakeKv.putBoolean("dummy_opt_cpu_thread", true)
        fakeKv.putBoolean("dummy_opt_opengl", true)
        fakeKv.putBoolean("dummy_opt_kernel", true)

        assertFalse(prefs.isMigratedV1())

        // Run one-shot deletion
        prefs.deleteDummyKeysIfNeeded()

        assertTrue(prefs.isMigratedV1())

        // Dummy keys must be removed
        assertFalse(fakeKv.map.containsKey("dummy_opt_gpu_render"))
        assertFalse(fakeKv.map.containsKey("dummy_opt_cpu_thread"))
        assertFalse(fakeKv.map.containsKey("dummy_opt_opengl"))
        assertFalse(fakeKv.map.containsKey("dummy_opt_kernel"))

        // None of the 36 real tune options should have been turned ON
        for (spec in TuneSpecs.all) {
            assertFalse(
                "Tune option ${spec.id} must NOT have been migrated to true",
                prefs.getIntent(spec.id).on
            )
        }

        // Second run is a no-op
        prefs.deleteDummyKeysIfNeeded()
        assertTrue(prefs.isMigratedV1())
    }
}
