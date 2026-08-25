package com.ivarna.apexcore.fps

import com.ivarna.apexcore.fps.model.FpsMethod
import com.ivarna.apexcore.fps.model.FpsSnapshot
import com.ivarna.apexcore.fps.model.abbrev
import com.ivarna.apexcore.fps.privilege.PrivilegeMode
import com.ivarna.apexcore.fps.privilege.PrivilegeModeStore
import com.ivarna.apexcore.fps.privilege.PrivilegeTier
import com.ivarna.apexcore.fps.source.DmaFenceFpsDataSource
import com.ivarna.apexcore.fps.source.FpsDaemonManager
import com.ivarna.apexcore.fps.source.GfxinfoFpsDataSource
import com.ivarna.apexcore.fps.source.SurfaceFlingerFpsDataSource
import com.ivarna.apexcore.fps.util.ForegroundApp
import com.ivarna.apexcore.fps.util.ForegroundAppResolver
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.mockito.kotlin.verify
import org.mockito.kotlin.never

class FpsRepositoryTest {

    private lateinit var dma: DmaFenceFpsDataSource
    private lateinit var sf: SurfaceFlingerFpsDataSource
    private lateinit var gfx: GfxinfoFpsDataSource
    private lateinit var resolver: ForegroundAppResolver
    private lateinit var daemon: FpsDaemonManager
    private lateinit var store: PrivilegeModeStore
    private lateinit var repo: FpsRepositoryImpl
    private lateinit var modeFlow: MutableStateFlow<PrivilegeMode>

    private fun snap(fps: Float, method: FpsMethod = FpsMethod.SURFACEFLINGER, tier: PrivilegeTier? = PrivilegeTier.STANDARD, histogram: List<Float> = emptyList()): FpsSnapshot {
        return FpsSnapshot(
            currentFps = fps,
            frametimeAvgMs = if (fps > 0) 1000f / fps else 0f,
            frametimeP1Ms = 0f,
            frametimeP01Ms = 0f,
            frametimeHistogram = histogram.ifEmpty { listOf(1000f / fps) },
            jankCount = 0,
            method = method,
            accessTier = tier,
            packageName = "com.test.game",
            surfaceName = "SurfaceView",
            timestampMs = System.currentTimeMillis(),
            measuredAtElapsedMs = android.os.SystemClock.elapsedRealtime(),
            diagnostics = "test",
            sourceDetail = "${method.abbrev()}:test",
            isStale = false,
            frameCount = 10
        )
    }

    @Before
    fun setUp() {
        dma = mock()
        sf = mock()
        gfx = mock()
        resolver = mock()
        daemon = mock()
        store = mock()
        modeFlow = MutableStateFlow(PrivilegeMode.AUTO)
        whenever(store.mode).thenReturn(modeFlow)
        whenever(daemon.ensureStarted()).thenReturn(false)
        com.ivarna.apexcore.fps.util.GpuVendorDetector.invalidate()
        repo = FpsRepositoryImpl(dma, sf, gfx, resolver, daemon, store, null)
    }

    @Test
    fun gameRouting_usesSfNotGfxinfo() = runTest {
        whenever(resolver.resolve()).thenReturn(ForegroundApp("com.game.foo", 123, 60f))
        whenever(resolver.isGameLikeSurface("com.game.foo")).thenReturn(true)
        whenever(sf.readFps()).thenReturn(snap(60f, FpsMethod.SURFACEFLINGER))
        whenever(gfx.readFps()).thenReturn(snap(60f, FpsMethod.GFXINFO))

        val result = repo.getFps()
        assertEquals(FpsMethod.SURFACEFLINGER, result.method)
        verify(gfx, never()).readFps()
    }

    @Test
    fun nonGameFallback_dmaToSfToGfx() = runTest {
        whenever(resolver.resolve()).thenReturn(ForegroundApp("com.android.settings", 123, 60f))
        whenever(resolver.isGameLikeSurface("com.android.settings")).thenReturn(false)
        whenever(dma.readFps()).thenReturn(null)
        whenever(sf.readFps()).thenReturn(null)
        whenever(gfx.readFps()).thenReturn(snap(60f, FpsMethod.GFXINFO))
        val result = repo.getFps()
        assertEquals(FpsMethod.GFXINFO, result.method)
    }

    @Test
    fun sourceCurrentFps_notOverwrittenByHistogram() = runTest {
        whenever(resolver.resolve()).thenReturn(ForegroundApp("com.game.foo", 123, 60f))
        whenever(resolver.isGameLikeSurface("com.game.foo")).thenReturn(true)
        val hist = List(100) { 16.6f }
        whenever(sf.readFps()).thenReturn(snap(30f, FpsMethod.SURFACEFLINGER, histogram = hist))
        val result = repo.getFps()
        assertTrue("expected ~30 but was ${result.currentFps}", result.currentFps in 28f..32f)
    }

    @Test
    fun medianSmoothing_suppressesSingleSpike() = runTest {
        whenever(resolver.resolve()).thenReturn(ForegroundApp("com.game.foo", 123, 60f))
        whenever(resolver.isGameLikeSurface("com.game.foo")).thenReturn(true)
        whenever(sf.readFps()).thenReturn(snap(60f), snap(60f), snap(118f), snap(60f))
        val r1 = repo.getFps()
        val r2 = repo.getFps()
        val r3 = repo.getFps()
        assertTrue("spike suppressed, got ${r3.currentFps}", r3.currentFps in 58f..62f)
        val r4 = repo.getFps()
        assertTrue("still suppressed, got ${r4.currentFps}", r4.currentFps in 58f..62f)
    }

    @Test
    fun medianSmoothing_respondsToPersistentChange() = runTest {
        whenever(resolver.resolve()).thenReturn(ForegroundApp("com.game.foo", 123, 60f))
        whenever(resolver.isGameLikeSurface("com.game.foo")).thenReturn(true)
        whenever(sf.readFps()).thenReturn(snap(60f), snap(60f), snap(30f), snap(30f), snap(30f))
        repo.getFps()
        repo.getFps()
        repo.getFps()
        val r4 = repo.getFps()
        assertTrue("should start responding, got ${r4.currentFps}", r4.currentFps in 28f..35f)
        val r5 = repo.getFps()
        assertTrue("should be 30, got ${r5.currentFps}", r5.currentFps in 28f..32f)
    }

    @Test
    fun targetPackageChange_clearsStale() = runTest {
        whenever(resolver.resolve()).thenReturn(ForegroundApp("com.game.foo", 123, 60f))
        whenever(resolver.isGameLikeSurface("com.game.foo")).thenReturn(true)
        whenever(sf.readFps()).thenReturn(snap(60f))
        repo.setTargetPackage("com.game.foo")
        val r1 = repo.getFps()
        assertTrue(r1.currentFps > 0)
        repo.setTargetPackage("com.game.bar")
        whenever(sf.readFps()).thenReturn(null)
        whenever(dma.readFps()).thenReturn(null)
        whenever(gfx.readFps()).thenReturn(null)
        whenever(resolver.resolve()).thenReturn(ForegroundApp("com.game.bar", 123, 60f))
        whenever(resolver.isGameLikeSurface("com.game.bar")).thenReturn(true)
        val r2 = repo.getFps()
        assertEquals(FpsMethod.NONE, r2.method)
        assertTrue(r2.currentFps == 0f)
    }

    @Test
    fun privilegeChange_clearsHeldData() = runTest {
        whenever(resolver.resolve()).thenReturn(ForegroundApp("com.game.foo", 123, 60f))
        whenever(resolver.isGameLikeSurface("com.game.foo")).thenReturn(true)
        whenever(sf.readFps()).thenReturn(snap(60f, tier = PrivilegeTier.SU_ROOT))
        val r1 = repo.getFps()
        assertEquals(60f, r1.currentFps, 1f)
        modeFlow.value = PrivilegeMode.SHIZUKU
        repo.onPrivilegeModeChanged()
        whenever(sf.readFps()).thenReturn(null)
        whenever(dma.readFps()).thenReturn(null)
        val r2 = repo.getFps()
        assertEquals(FpsMethod.NONE, r2.method)
    }

    @Test
    fun sourceChange_resetsBuffer() = runTest {
        whenever(resolver.resolve()).thenReturn(ForegroundApp("com.test.app", 123, 60f))
        whenever(resolver.isGameLikeSurface("com.test.app")).thenReturn(false)
        whenever(dma.readFps()).thenReturn(snap(60f, FpsMethod.DMA_FENCE))
        whenever(sf.readFps()).thenReturn(snap(60f, FpsMethod.SURFACEFLINGER))
        val r1 = repo.getFps()
        assertEquals(FpsMethod.DMA_FENCE, r1.method)
        whenever(dma.readFps()).thenReturn(null)
        val r2 = repo.getFps()
        assertEquals(FpsMethod.SURFACEFLINGER, r2.method)
    }
}
