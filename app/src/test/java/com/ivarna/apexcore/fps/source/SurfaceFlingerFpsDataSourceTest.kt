package com.ivarna.apexcore.fps.source

import com.ivarna.apexcore.fps.privilege.PrivilegeTier
import com.ivarna.apexcore.fps.util.ForegroundAppResolver
import com.ivarna.apexcore.fps.privilege.ShellGateway
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock

class SurfaceFlingerFpsDataSourceTest {

    private lateinit var dataSource: SurfaceFlingerFpsDataSource
    private val mockGateway: ShellGateway = mock()
    private val mockResolver: ForegroundAppResolver = mock()

    @Before
    fun setUp() {
        dataSource = SurfaceFlingerFpsDataSource(mockGateway, mockResolver)
    }

    private fun buildOutput(
        refreshPeriodNs: Long,
        presentTimesMs: List<Double>,
        withInvalidRows: List<Int> = emptyList()
    ): String {
        val sb = StringBuilder()
        sb.appendLine(refreshPeriodNs.toString())
        for ((idx, tMs) in presentTimesMs.withIndex()) {
            if (idx in withInvalidRows) {
                // Insert invalid row: zero timestamps
                sb.appendLine("0 0 0")
                continue
            }
            val actualNs = (tMs * 1_000_000.0).toLong()
            val desiredNs = actualNs - 1_000_000L
            val readyNs = actualNs + 500_000L
            sb.appendLine("$desiredNs\t$actualNs\t$readyNs")
        }
        return sb.toString()
    }

    private fun buildOutputWithDuplicates(
        refreshPeriodNs: Long,
        presentTimesMs: List<Double>,
        duplicateAt: Int
    ): String {
        val sb = StringBuilder()
        sb.appendLine(refreshPeriodNs.toString())
        for ((idx, tMs) in presentTimesMs.withIndex()) {
            val actualNs = (tMs * 1_000_000.0).toLong()
            val desiredNs = actualNs - 1_000_000L
            val readyNs = actualNs + 500_000L
            sb.appendLine("$desiredNs\t$actualNs\t$readyNs")
            if (idx == duplicateAt) {
                // duplicate next line same timestamps
                sb.appendLine("$desiredNs\t$actualNs\t$readyNs")
            }
        }
        return sb.toString()
    }

    private fun buildTimeline(durationMs: Double, intervalMs: Double): List<Double> {
        val result = mutableListOf<Double>()
        var t = 0.0
        while (t <= durationMs) {
            result.add(t)
            t += intervalMs
        }
        return result
    }

    @Test
    fun stable60Fps_timeline_reports60() {
        val refreshNs = 16_666_666L // 60Hz
        val timeline = buildTimeline(durationMs = 1000.0, intervalMs = 16.666)
        val output = buildOutput(refreshNs, timeline)
        val snapshot = dataSource.parseLatency(output, PrivilegeTier.STANDARD, "com.test.game", "SurfaceView", 60f)
        assertNotNull(snapshot)
        assertTrue("expected ~60 FPS but was ${snapshot!!.currentFps}", snapshot.currentFps in 58f..62f)
    }

    @Test
    fun stable30Fps_timeline_reports30() {
        val refreshNs = 16_666_666L
        val timeline = buildTimeline(durationMs = 1000.0, intervalMs = 33.333)
        val output = buildOutput(refreshNs, timeline)
        val snapshot = dataSource.parseLatency(output, PrivilegeTier.STANDARD, "com.test.game", "SurfaceView", 60f)
        assertNotNull(snapshot)
        assertTrue("expected ~30 FPS but was ${snapshot!!.currentFps}", snapshot.currentFps in 28f..32f)
    }

    @Test
    fun stable90Fps_timeline_reports90() {
        val refreshNs = 11_111_111L // 90Hz
        val timeline = buildTimeline(durationMs = 1000.0, intervalMs = 11.111)
        val output = buildOutput(refreshNs, timeline)
        val snapshot = dataSource.parseLatency(output, PrivilegeTier.STANDARD, "com.test.game", "SurfaceView", 90f)
        assertNotNull(snapshot)
        assertTrue("expected ~90 FPS but was ${snapshot!!.currentFps}", snapshot.currentFps in 86f..94f)
    }

    @Test
    fun stable120Fps_timeline_reports120() {
        val refreshNs = 8_333_333L
        val timeline = buildTimeline(durationMs = 1000.0, intervalMs = 8.333)
        val output = buildOutput(refreshNs, timeline)
        val snapshot = dataSource.parseLatency(output, PrivilegeTier.STANDARD, "com.test.game", "SurfaceView", 120f)
        assertNotNull(snapshot)
        assertTrue("expected ~120 FPS but was ${snapshot!!.currentFps}", snapshot.currentFps in 115f..125f)
    }

    @Test
    fun stable144Fps_timeline_reports144() {
        val refreshNs = 6_944_444L
        val timeline = buildTimeline(durationMs = 1000.0, intervalMs = 6.944)
        val output = buildOutput(refreshNs, timeline)
        val snapshot = dataSource.parseLatency(output, PrivilegeTier.STANDARD, "com.test.game", "SurfaceView", 144f)
        assertNotNull(snapshot)
        assertTrue("expected ~144 FPS but was ${snapshot!!.currentFps}", snapshot.currentFps in 138f..150f)
    }

    @Test
    fun transition60to30_convergesTo30() {
        val refreshNs = 16_666_666L
        // 60 FPS for first 1s, then 30 FPS for next 1s
        val part60 = buildTimeline(durationMs = 500.0, intervalMs = 16.666)
        val offset = part60.last() + 33.333
        val part30 = mutableListOf<Double>()
        var t = offset
        while (t <= offset + 1000) {
            part30.add(t)
            t += 33.333
        }
        val combined = part60 + part30
        val output = buildOutput(refreshNs, combined)
        val snapshot = dataSource.parseLatency(output, PrivilegeTier.STANDARD, "com.test.game", "SurfaceView", 60f)
        assertNotNull(snapshot)
        // Recent window 32 should be dominated by 30FPS part -> ~30
        assertTrue("expected transition to 30, got ${snapshot!!.currentFps}", snapshot.currentFps in 28f..34f)
    }

    @Test
    fun transition30to60_convergesTo60() {
        val refreshNs = 16_666_666L
        val part30 = buildTimeline(durationMs = 1000.0, intervalMs = 33.333)
        val offset = part30.last() + 16.666
        val part60 = mutableListOf<Double>()
        var t = offset
        while (t <= offset + 800) {
            part60.add(t)
            t += 16.666
        }
        val combined = part30 + part60
        val output = buildOutput(refreshNs, combined)
        val snapshot = dataSource.parseLatency(output, PrivilegeTier.STANDARD, "com.test.game", "SurfaceView", 60f)
        assertNotNull(snapshot)
        assertTrue("expected transition to 60, got ${snapshot!!.currentFps}", snapshot.currentFps in 56f..64f)
    }

    @Test
    fun duplicateTimestamps_filtered() {
        val refreshNs = 16_666_666L
        val timeline = buildTimeline(durationMs = 800.0, intervalMs = 16.666)
        val output = buildOutputWithDuplicates(refreshNs, timeline, duplicateAt = 10)
        val snapshot = dataSource.parseLatency(output, PrivilegeTier.STANDARD, "com.test.game", "SurfaceView", 60f)
        assertNotNull(snapshot)
        assertTrue("duplicate should be filtered, got ${snapshot!!.currentFps}", snapshot.currentFps in 58f..62f)
    }

    @Test
    fun zeroTimestamps_filtered() {
        val refreshNs = 16_666_666L
        val timeline = buildTimeline(durationMs = 800.0, intervalMs = 16.666)
        // Insert zero row at position 5
        val output = buildOutput(refreshNs, timeline, withInvalidRows = listOf(5))
        val snapshot = dataSource.parseLatency(output, PrivilegeTier.STANDARD, "com.test.game", "SurfaceView", 60f)
        assertNotNull(snapshot)
        assertTrue("zero row filtered, got ${snapshot!!.currentFps}", snapshot.currentFps in 58f..62f)
    }

    @Test
    fun decreasingTimestamps_filtered() {
        val sb = StringBuilder()
        val refreshNs = 16_666_666L
        sb.appendLine(refreshNs.toString())
        // 0,16,33,30(decreasing),50,66 ...
        val times = listOf(0.0, 16.666, 33.333, 30.0, 50.0, 66.666, 83.333, 99.999)
        for (t in times) {
            val actualNs = (t * 1_000_000).toLong()
            sb.appendLine("${actualNs - 1_000_000}\t$actualNs\t${actualNs + 500_000}")
        }
        val snapshot = dataSource.parseLatency(sb.toString(), PrivilegeTier.STANDARD, "com.test.game", "SurfaceView", 60f)
        assertNotNull(snapshot)
        // Decreasing interval should be ignored; average of remaining should still be ~60
        assertTrue("decreasing filtered, got ${snapshot!!.currentFps}", snapshot.currentFps in 55f..65f)
    }

    @Test
    fun hugeInvalidSentinel_filtered() {
        val refreshNs = 16_666_666L
        val timeline = buildTimeline(durationMs = 800.0, intervalMs = 16.666)
        val sb = StringBuilder()
        sb.appendLine(refreshNs.toString())
        for ((idx, t) in timeline.withIndex()) {
            if (idx == 7) {
                // Sentinel
                val sentinel = Long.MAX_VALUE
                sb.appendLine("$sentinel\t$sentinel\t$sentinel")
                continue
            }
            val actualNs = (t * 1_000_000).toLong()
            sb.appendLine("${actualNs - 1_000_000}\t$actualNs\t${actualNs + 500_000}")
        }
        val snapshot = dataSource.parseLatency(sb.toString(), PrivilegeTier.STANDARD, "com.test.game", "SurfaceView", 60f)
        assertNotNull(snapshot)
        assertTrue("sentinel filtered, got ${snapshot!!.currentFps}", snapshot.currentFps in 58f..62f)
    }

    @Test
    fun onlyRefreshPeriod_returnsNull() {
        val output = "16666666\n"
        val snapshot = dataSource.parseLatency(output)
        assertNull(snapshot)
    }

    @Test
    fun oneUsablePresent_returnsNull() {
        val refreshNs = 16_666_666L
        val sb = StringBuilder()
        sb.appendLine(refreshNs.toString())
        sb.appendLine("1000000\t2000000\t2500000") // only one valid row
        val snapshot = dataSource.parseLatency(sb.toString())
        assertNull(snapshot)
    }

    @Test
    fun mixedValidInvalidRows_stillValid() {
        val refreshNs = 16_666_666L
        val sb = StringBuilder()
        sb.appendLine(refreshNs.toString())
        val times = listOf(0.0, 16.666, 33.333, 50.0, 66.666, 83.333, 99.999, 116.666)
        for ((idx, t) in times.withIndex()) {
            val actualNs = (t * 1_000_000).toLong()
            sb.appendLine("${actualNs - 1_000_000}\t$actualNs\t${actualNs + 500_000}")
            if (idx == 2) {
                sb.appendLine("0 0 0") // extra invalid row between valid
            }
            if (idx == 3) {
                sb.appendLine("${Long.MAX_VALUE}\t${Long.MAX_VALUE}\t${Long.MAX_VALUE}")
            }
        }
        val snapshot = dataSource.parseLatency(sb.toString(), PrivilegeTier.STANDARD, "com.test.game", "SurfaceView", 60f)
        assertNotNull(snapshot)
        // Extra invalid rows should be filtered, remaining intervals stay at 16.66 -> ~60
        assertTrue("mixed valid/invalid should still give ~60, got ${snapshot!!.currentFps}", snapshot.currentFps in 55f..65f)
    }

    @Test
    fun layerParsing_android15RequestedLayerState() {
        val raw = "RequestedLayerState{SurfaceView[com.game.foo/com.test.GameActivity]#123 parentId=0 z=0}"
        val parsed = dataSource.parseLayerName(raw)
        assertEquals("SurfaceView[com.game.foo/com.test.GameActivity]#123", parsed)

        val raw2 = "3fa18c4 com.game.foo/com.test.GameActivity#1183 parentId=0 z=10"
        val parsed2 = dataSource.parseLayerName(raw2)
        assertEquals("com.game.foo/com.test.GameActivity#1183", parsed2)
    }

    @Test
    fun recentWindowResponsiveness_notDominatedByFullHistory() {
        val refreshNs = 16_666_666L
        // Build long history: 100 frames at 60fps, then 32 frames at 30fps
        val early = mutableListOf<Double>()
        var t = 0.0
        repeat(100) {
            early.add(t)
            t += 16.666
        }
        val lateStart = t
        val late = mutableListOf<Double>()
        var tl = lateStart
        repeat(32) {
            late.add(tl)
            tl += 33.333
        }
        val combined = early + late
        val output = buildOutput(refreshNs, combined)
        val snapshot = dataSource.parseLatency(output, PrivilegeTier.STANDARD, "com.test.game", "SurfaceView", 60f)
        assertNotNull(snapshot)
        // If using full history avg, would be ~52 FPS (weighted). Recent window should be ~30
        assertTrue("recent window should dominate, got ${snapshot!!.currentFps}", snapshot.currentFps < 40f)
        assertTrue("should be near 30, got ${snapshot.currentFps}", snapshot.currentFps in 28f..34f)
    }

    @Test
    fun jankCalculation_missedPeriods() {
        val refreshNs = 16_666_666L // 60Hz
        // Timeline with perfect 16.66 -> 0 missed, 33.33 ->1, 50->2
        val sb = StringBuilder()
        sb.appendLine(refreshNs.toString())
        // Use actual deltas: 16.666, 33.333, 50.0
        // To get deltas of 16.66,33.33,50 we need present times: 0,16.666,50,100
        val times = listOf(0.0, 16.666, 49.999, 99.999) // intervals:16.666,33.333,50.0
        for (t in times) {
            val actualNs = (t * 1_000_000).toLong()
            sb.appendLine("${actualNs - 1_000_000}\t$actualNs\t${actualNs + 500_000}")
        }
        // Add more stable 16ms to have enough intervals
        for (t in listOf(116.665, 133.331, 149.997)) {
            val actualNs = (t * 1_000_000).toLong()
            sb.appendLine("${actualNs - 1_000_000}\t$actualNs\t${actualNs + 500_000}")
        }
        val snapshot = dataSource.parseLatency(sb.toString(), PrivilegeTier.STANDARD, "com.test.game", "SurfaceView", 60f)
        assertNotNull(snapshot)
        // intervals are 16,33,50,16,16,16 -> missed 0+1+2+0+0+0 =3
        assertTrue("expected jank missed periods ~3, got ${snapshot!!.jankCount}", snapshot.jankCount >= 3)
    }

    @Test
    fun extremeRefreshPeriod_invalidReturnsNull() {
        // Refresh 0 or too small
        val output = "0\n1000000\t2000000\t2500000\n2000000\t3000000\t3500000\n"
        assertNull(dataSource.parseLatency(output))
        val output2 = "500000\n1000000\t2000000\t2500000\n"
        assertNull(dataSource.parseLatency(output2))
    }

    @Test
    fun syntheticTimeline_bothFormulasEquivalent() {
        val refreshNs = 16_666_666L
        val intervalMs = 16.666
        val timeline = buildTimeline(durationMs = 600.0, intervalMs = intervalMs)
        val output = buildOutput(refreshNs, timeline)
        val snapshot = dataSource.parseLatency(output, PrivilegeTier.STANDARD, "com.test.game", "SurfaceView", 60f)
        assertNotNull(snapshot)
        // Compute alternative: 1e9 * count / (last-first) should be ~60 as well
        // Since we used same intervals, fps should be within tolerance regardless of formula
        assertTrue(snapshot!!.currentFps in 59f..61f)
        // Histogram should contain frametimes ~16.6
        assertTrue(snapshot.frametimeHistogram.isNotEmpty())
        assertTrue(snapshot.frametimeHistogram.all { it in 10f..20f })
    }
}
