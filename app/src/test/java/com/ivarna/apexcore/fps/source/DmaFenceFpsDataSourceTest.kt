package com.ivarna.apexcore.fps.source

import android.content.Context
import com.ivarna.apexcore.fps.model.FpsMethod
import com.ivarna.apexcore.fps.model.FpsSnapshot
import com.ivarna.apexcore.fps.privilege.PrivilegeTier
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class DmaFenceFpsDataSourceTest {

    private lateinit var context: Context
    private lateinit var dataSource: DmaFenceFpsDataSource

    @Before
    fun setUp() {
        context = mock()
        // Mock registerReceiver to avoid needing real context behavior
        // Return value ignored, just need to not throw
        whenever(context.registerReceiver(org.mockito.kotlin.any(), org.mockito.kotlin.any())).thenReturn(null)
        whenever(context.registerReceiver(org.mockito.kotlin.any(), org.mockito.kotlin.any(), org.mockito.kotlin.any())).thenReturn(null)
        // Make Context.getSystemService not needed
        dataSource = DmaFenceFpsDataSource(context)
    }

    private fun setLatestSnapshot(snapshot: FpsSnapshot?, elapsedMs: Long = android.os.SystemClock.elapsedRealtime()) {
        val f = DmaFenceFpsDataSource::class.java.getDeclaredField("latestSnapshot")
        f.isAccessible = true
        f.set(dataSource, snapshot)
        val fe = DmaFenceFpsDataSource::class.java.getDeclaredField("lastReceivedAtElapsedMs")
        fe.isAccessible = true
        fe.setLong(dataSource, elapsedMs)
    }

    private fun makeSnapshot(fps: Float = 60f): FpsSnapshot {
        return FpsSnapshot(
            currentFps = fps,
            frametimeAvgMs = 1000f / fps,
            frametimeP1Ms = 0f,
            frametimeP01Ms = 0f,
            frametimeHistogram = listOf(1000f / fps),
            jankCount = 0,
            method = FpsMethod.DMA_FENCE,
            accessTier = PrivilegeTier.SU_ROOT,
            packageName = "com.test.game",
            timestampMs = System.currentTimeMillis(),
            measuredAtElapsedMs = android.os.SystemClock.elapsedRealtime(),
            diagnostics = "DMA test",
            sourceDetail = "DMA:cmdbatch",
            isStale = false,
            frameCount = 1
        )
    }

    @Test
    fun staleSample_rejected() {
        // Set snapshot with old elapsed (10 seconds ago) > STALE_MS 6000
        val oldElapsed = android.os.SystemClock.elapsedRealtime() - 10_000L
        // Need snapshot's measuredAtElapsedMs also old
        val snap = makeSnapshot().copy(measuredAtElapsedMs = oldElapsed)
        setLatestSnapshot(snap, oldElapsed)
        // Now read should return null because age >6000
        kotlinx.coroutines.test.runTest {
            val result = dataSource.readFps()
            assertNull(result)
        }
    }

    @Test
    fun clearCache_works() {
        val snap = makeSnapshot()
        setLatestSnapshot(snap, android.os.SystemClock.elapsedRealtime())
        kotlinx.coroutines.test.runTest {
            assertNotNull(dataSource.readFps())
        }
        dataSource.clearCache()
        kotlinx.coroutines.test.runTest {
            assertNull(dataSource.readFps())
        }
    }

    @Test
    fun freshSample_returnedWithAge() {
        val snap = makeSnapshot(60f)
        val elapsed = android.os.SystemClock.elapsedRealtime()
        val snapWithElapsed = snap.copy(measuredAtElapsedMs = elapsed)
        setLatestSnapshot(snapWithElapsed, elapsed)
        kotlinx.coroutines.test.runTest {
            val result = dataSource.readFps()
            assertNotNull(result)
            assertEquals(60f, result!!.currentFps, 0.1f)
            // sampleAgeMs should be >=0 and small (since we set elapsed to now)
            assertTrue(result.sampleAgeMs >= 0)
            assertTrue(result.sampleAgeMs < 1000)
            assertEquals("DMA:cmdbatch", result.sourceDetail)
        }
    }
}
