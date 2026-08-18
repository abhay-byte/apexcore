package com.ivarna.apexcore.tune

import android.content.Context
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock

class TuneSnapshotBootIdTest {

    private val fakeShell = FakeTuneShell()
    private val fakeKv = FakeKeyValue()
    private val context: Context = mock(Context::class.java)
    private lateinit var snapshotStore: TuneSnapshotStore

    @Before
    fun setUp() {
        snapshotStore = TuneSnapshotStore(context, fakeKv, fakeShell)
    }

    @Test
    fun testBootIdMismatchRejectsMatch() {
        fakeKv.putString(TuneSnapshotStore.KEY_BOOT_ID, "old-boot-id-123")
        fakeKv.putString(TuneSnapshotStore.KEY_BOOT_COUNT, "10")

        fakeShell.existingPaths.add("/proc/sys/kernel/random/boot_id")
        fakeShell.pathValues["/proc/sys/kernel/random/boot_id"] = "new-boot-id-456"

        assertFalse("Different boot_id must indicate reboot", snapshotStore.isBootMatching())
    }

    @Test
    fun testMatchingBootIdAccepted() {
        fakeKv.putString(TuneSnapshotStore.KEY_BOOT_ID, "boot-id-abc")
        fakeShell.existingPaths.add("/proc/sys/kernel/random/boot_id")
        fakeShell.pathValues["/proc/sys/kernel/random/boot_id"] = "boot-id-abc"

        assertTrue("Identical boot_id must match", snapshotStore.isBootMatching())
    }
}
