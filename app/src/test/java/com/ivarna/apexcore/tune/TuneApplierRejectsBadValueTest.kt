package com.ivarna.apexcore.tune

import android.content.Context
import com.ivarna.apexcore.fps.privilege.PrivilegeTier
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock

class TuneApplierRejectsBadValueTest {

    private val fakeShell = FakeTuneShell()
    private val fakeKv = FakeKeyValue()
    private val context: Context = mock(Context::class.java)
    private lateinit var snapshotStore: TuneSnapshotStore
    private lateinit var applier: TuneApplier

    @Before
    fun setUp() {
        snapshotStore = TuneSnapshotStore(context, fakeKv, fakeShell)
        applier = TuneApplier(context, fakeShell, snapshotStore)
    }

    @Test
    fun testIntentWithIllegalCharactersIsRejected() {
        val node = "/sys/class/kgsl/kgsl-3d0/idle_timer"
        fakeShell.existingPaths.add(node)
        fakeShell.pathValues[node] = "80"

        // Inject command separator in intent raw
        val count = applier.applyBundle(TuneId.GPU_ADRENO, TuneValue(on = true, raw = "2; rm -rf /"), PrivilegeTier.ROOT)
        assertEquals(0, count)
        assertNull(snapshotStore.getOriginal(node))
    }
}
