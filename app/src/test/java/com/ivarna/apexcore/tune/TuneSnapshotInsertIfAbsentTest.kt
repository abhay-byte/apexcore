package com.ivarna.apexcore.tune

import android.content.Context
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock

class TuneSnapshotInsertIfAbsentTest {

    private val fakeShell = FakeTuneShell()
    private val fakeKv = FakeKeyValue()
    private val context: Context = mock(Context::class.java)
    private lateinit var snapshotStore: TuneSnapshotStore

    @Before
    fun setUp() {
        snapshotStore = TuneSnapshotStore(context, fakeKv, fakeShell)
    }

    @Test
    fun testSecondApplyPreservesOriginalValue() {
        val path = "/sys/class/kgsl/kgsl-3d0/devfreq/min_freq"

        snapshotStore.recordOriginal(path, "200000000")
        assertEquals("200000000", snapshotStore.getOriginal(path))

        // Second write attempt with new value
        snapshotStore.recordOriginal(path, "500000000")
        // Original value must NOT change
        assertEquals("200000000", snapshotStore.getOriginal(path))
    }
}
