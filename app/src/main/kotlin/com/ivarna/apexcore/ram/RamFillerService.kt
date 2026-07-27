package com.ivarna.apexcore.ram

import android.app.Service
import android.content.Intent
import android.os.*
import android.util.Log
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean

class RamFillerService : Service() {

    companion object {
        const val TAG = "ApexCore.RamFiller"
        const val MSG_START = 1
        const val MSG_CANCEL = 2
        const val MSG_PROGRESS = 3
        const val MSG_DONE = 4

        private const val TARGET_RAM = 0.90f
        private const val CHUNK_MAX = 64 * 1024 * 1024
        private const val CHUNK_START = 16 * 1024 * 1024
        private const val PAGE_SIZE = 4096
        private const val HOLD_MS = 400L
        private const val DEFAULT_TIMEOUT_MS = 15_000L
        private const val DELAY_MS = 16L

        const val KEY_TOTAL_RAM_KB = "totalRamKb"
        const val KEY_TIMEOUT_MS = "timeoutMs"
        const val KEY_ALLOCATED_MB = "allocatedMb"
        const val KEY_RAM_PERCENT = "ramPercent"
        const val KEY_SWAP_PERCENT = "swapPercent"
        const val KEY_ELAPSED_MS = "elapsedMs"
        const val KEY_CHUNK_COUNT = "chunkCount"
        const val KEY_PHASE = "phase"
        const val KEY_STOP_REASON = "stopReason"
        const val KEY_PEAK_ALLOCATED_MB = "peakAllocatedMb"
        const val KEY_PEAK_RAM = "peakRamPercent"
        const val KEY_PEAK_SWAP = "peakSwapPercent"
        const val KEY_TOTAL_DURATION = "totalDurationMs"
        const val KEY_FILL_DURATION = "fillDurationMs"
        const val KEY_HEAP_MAX_MB = "heapMaxMb"
        const val KEY_HOLD_REMAINING_MS = "holdRemainingMs"

        const val PHASE_FILLING = "FILLING"
        const val PHASE_HOLDING = "HOLDING"
        const val PHASE_RELEASING = "RELEASING"
    }

    private lateinit var bgThread: HandlerThread
    private lateinit var bgHandler: Handler
    private lateinit var messenger: Messenger

    private val cancelled = AtomicBoolean(false)
    private val chunks = mutableListOf<ByteBuffer>()
    private var replyTo: Messenger? = null
    private var fillThread: Thread? = null

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "Service created pid=${android.os.Process.myPid()}")
        bgThread = HandlerThread("RamFillerBg")
        bgThread.start()
        bgHandler = object : Handler(bgThread.looper) {
            override fun handleMessage(msg: Message) {
                when (msg.what) {
                    MSG_START -> {
                        cancelled.set(true)
                        fillThread?.interrupt()
                        try { fillThread?.join(500L) } catch (_: Throwable) {}
                        cancelled.set(false)
                        fillThread = Thread { doFill(msg) }.apply { start() }
                    }
                    MSG_CANCEL -> {
                        cancelled.set(true)
                        fillThread?.interrupt()
                    }
                }
            }
        }
        messenger = Messenger(bgHandler)
    }

    override fun onBind(intent: Intent?): IBinder = messenger.binder

    override fun onDestroy() {
        cancelled.set(true)
        fillThread?.interrupt()
        releaseAll()
        bgThread.quitSafely()
        super.onDestroy()
    }

    private fun doFill(msg: Message) {
        replyTo = msg.replyTo ?: return
        val totalRamKb = msg.data.getLong(KEY_TOTAL_RAM_KB, 0)
        val timeoutMs = msg.data.getLong(KEY_TIMEOUT_MS, DEFAULT_TIMEOUT_MS)
        if (totalRamKb <= 0) {
            Log.w(TAG, "Invalid totalRamKb, aborting")
            sendDone(0, 0f, 0f, 0, 0, StopReason.BUDGET, 0, 0)
            return
        }

        val heapMaxMb = Runtime.getRuntime().maxMemory() / (1024 * 1024)
        Log.i(TAG, "doFill start totalRamKb=$totalRamKb timeoutMs=$timeoutMs heapMaxMb=$heapMaxMb")

        chunks.clear()
        val availFloorKb = maxOf((totalRamKb * 0.10f).toLong(), 256 * 1024L)
        val targetRamKb = (totalRamKb * TARGET_RAM).toLong()
        val startTime = System.currentTimeMillis()
        var totalAllocatedKb = 0L
        var peakRamPercent = 0f
        var peakSwapPercent = 0f
        var chunkCount = 0
        var stopReason = StopReason.RAM_CAP
        var oomTriggered = false
        var preferredChunk = CHUNK_START

        try {
            while (!cancelled.get()) {
                val elapsed = System.currentTimeMillis() - startTime
                if (elapsed >= timeoutMs) {
                    stopReason = StopReason.TIMEOUT
                    Log.i(TAG, "Stop: TIMEOUT elapsed=${elapsed}ms chunks=$chunkCount allocatedKb=$totalAllocatedKb")
                    break
                }

                val stats = readMemInfo()
                val ramUsedKb = stats.ramTotalKb - stats.ramAvailableKb
                val ramPercent = if (stats.ramTotalKb > 0) ramUsedKb.toFloat() / stats.ramTotalKb else 0f
                val swapUsedKb = stats.swapTotalKb - stats.swapFreeKb
                val swapPercent = if (stats.swapTotalKb > 0) swapUsedKb.toFloat() / stats.swapTotalKb else 0f

                peakRamPercent = maxOf(peakRamPercent, ramPercent)
                peakSwapPercent = maxOf(peakSwapPercent, swapPercent)

                if (stats.ramAvailableKb <= availFloorKb) {
                    stopReason = StopReason.RAM_CAP
                    Log.i(TAG, "Stop: RAM_CAP avail=${stats.ramAvailableKb}KB floor=${availFloorKb}KB ramPercent=${"%.1f".format(ramPercent * 100)}% chunks=$chunkCount allocatedKb=$totalAllocatedKb")
                    break
                }

                if (ramPercent >= 0.95f) {
                    stopReason = StopReason.RAM_CAP
                    Log.i(TAG, "Stop: RAM_CAP hard safety at ${"%.1f".format(ramPercent * 100)}%")
                    break
                }

                sendProgress(totalAllocatedKb / 1024, ramPercent, swapPercent, elapsed, chunkCount, PHASE_FILLING)

                val ramHeadroomKb = (targetRamKb - ramUsedKb).coerceAtLeast(0)
                val ramHeadroomBytes = ramHeadroomKb * 1024L
                if (ramHeadroomBytes < PAGE_SIZE) {
                    stopReason = if (chunkCount == 0) StopReason.RAM_CAP else StopReason.BUDGET
                    Log.i(TAG, "Stop: ${stopReason.name} headroom=${ramHeadroomBytes}B chunks=$chunkCount allocatedKb=$totalAllocatedKb")
                    break
                }

                var chunkSize = minOf(ramHeadroomBytes, preferredChunk.toLong(), CHUNK_MAX.toLong()).toInt()
                if (chunkSize < PAGE_SIZE) {
                    stopReason = if (chunkCount == 0) StopReason.RAM_CAP else StopReason.BUDGET
                    Log.i(TAG, "Stop: ${stopReason.name} chunkSize=${chunkSize}B too small chunks=$chunkCount allocatedKb=$totalAllocatedKb")
                    break
                }

                val chunk = try {
                    val buf = ByteBuffer.allocateDirect(chunkSize)
                    var offset = 0
                    while (offset < chunkSize) {
                        buf.put(offset, 0x42.toByte())
                        offset += PAGE_SIZE
                    }
                    if (preferredChunk < CHUNK_MAX) {
                        preferredChunk = (preferredChunk * 2).coerceAtMost(CHUNK_MAX)
                    }
                    buf
                } catch (e: OutOfMemoryError) {
                    preferredChunk /= 2
                    Log.w(TAG, "OOM chunkSize=${chunkSize}, shrinking to ${preferredChunk}, retry")
                    if (preferredChunk >= PAGE_SIZE) {
                        continue
                    }
                    stopReason = StopReason.OOM
                    oomTriggered = true
                    Log.i(TAG, "Stop: OOM cannot shrink below page size, allocatedKb=$totalAllocatedKb chunks=$chunkCount")
                    break
                }

                chunks.add(chunk)
                totalAllocatedKb += chunkSize / 1024L
                chunkCount++
                Log.i(TAG, "Chunk OK size=${chunkSize / 1024}KB total=${totalAllocatedKb}KB avail=${stats.ramAvailableKb}KB ramPercent=${"%.1f".format(ramPercent * 100)}%")
                Thread.sleep(DELAY_MS)
            }

            if (cancelled.get()) {
                stopReason = StopReason.CANCEL
                Log.i(TAG, "Stop: CANCEL chunks=$chunkCount allocatedKb=$totalAllocatedKb")
            }

            if (!cancelled.get() && stopReason != StopReason.OOM) {
                val holdStart = System.currentTimeMillis()
                while (!cancelled.get()) {
                    val remaining = HOLD_MS - (System.currentTimeMillis() - holdStart)
                    if (remaining <= 0) break
                    sendProgress(totalAllocatedKb / 1024, peakRamPercent, peakSwapPercent,
                        System.currentTimeMillis() - startTime, chunkCount, PHASE_HOLDING,
                        holdRemainingMs = remaining)
                    Thread.sleep(minOf(remaining, 16L))
                }
            }

            val peakAllocatedMb = totalAllocatedKb / 1024L
            val fillDurationMs = System.currentTimeMillis() - startTime

            sendProgress(peakAllocatedMb, peakRamPercent, peakSwapPercent,
                System.currentTimeMillis() - startTime, chunkCount, PHASE_RELEASING)
            Log.i(TAG, "Releasing $chunkCount chunks peakAllocatedMb=$peakAllocatedMb")
            releaseAll()
            Thread.sleep(400)
            System.gc()

            val totalDurationMs = System.currentTimeMillis() - startTime
            Log.i(TAG, "Done: stopReason=$stopReason peakAllocatedMb=$peakAllocatedMb peakRam=${"%.1f".format(peakRamPercent * 100)}% peakSwap=${"%.1f".format(peakSwapPercent * 100)}% chunks=$chunkCount heapMaxMb=$heapMaxMb totalDur=$totalDurationMs")

            sendDone(
                peakAllocatedMb = peakAllocatedMb,
                peakRamPercent = peakRamPercent,
                peakSwapPercent = peakSwapPercent,
                totalDurationMs = totalDurationMs,
                fillDurationMs = fillDurationMs,
                stopReason = stopReason,
                chunkCount = chunkCount,
                heapMaxMb = heapMaxMb
            )
        } catch (e: Exception) {
            Log.e(TAG, "Fill error: ${e.message}", e)
            releaseAll()
            sendDone(0, peakRamPercent, peakSwapPercent,
                System.currentTimeMillis() - startTime, 0, StopReason.BUDGET, chunkCount, heapMaxMb)
        }
    }

    private fun sendProgress(
        allocatedMb: Long, ramPercent: Float, swapPercent: Float,
        elapsedMs: Long, chunkCount: Int, phase: String,
        holdRemainingMs: Long = 0
    ) {
        val msg = Message.obtain(null, MSG_PROGRESS).apply {
            arg1 = chunkCount
            data = Bundle().apply {
                putLong(KEY_ALLOCATED_MB, allocatedMb)
                putFloat(KEY_RAM_PERCENT, ramPercent)
                putFloat(KEY_SWAP_PERCENT, swapPercent)
                putLong(KEY_ELAPSED_MS, elapsedMs)
                putString(KEY_PHASE, phase)
                putLong(KEY_HOLD_REMAINING_MS, holdRemainingMs)
            }
        }
        try { replyTo?.send(msg) } catch (_: Throwable) {}
    }

    private fun sendDone(
        peakAllocatedMb: Long, peakRamPercent: Float, peakSwapPercent: Float,
        totalDurationMs: Long, fillDurationMs: Long, stopReason: StopReason,
        chunkCount: Int, heapMaxMb: Long
    ) {
        val msg = Message.obtain(null, MSG_DONE).apply {
            data = Bundle().apply {
                putLong(KEY_PEAK_ALLOCATED_MB, peakAllocatedMb)
                putFloat(KEY_PEAK_RAM, peakRamPercent)
                putFloat(KEY_PEAK_SWAP, peakSwapPercent)
                putLong(KEY_TOTAL_DURATION, totalDurationMs)
                putLong(KEY_FILL_DURATION, fillDurationMs)
                putString(KEY_STOP_REASON, stopReason.name)
                putInt(KEY_CHUNK_COUNT, chunkCount)
                putLong(KEY_HEAP_MAX_MB, heapMaxMb)
            }
        }
        try { replyTo?.send(msg) } catch (_: Throwable) {}
    }

    private fun releaseAll() {
        chunks.clear()
        System.gc()
    }

    data class MemSnapshot(
        val ramTotalKb: Long,
        val ramAvailableKb: Long,
        val swapTotalKb: Long,
        val swapFreeKb: Long
    )

    private fun readMemInfo(): MemSnapshot {
        var ramTotal = 0L; var ramAvail = 0L
        var swapTotal = 0L; var swapFree = 0L
        try {
            java.io.File("/proc/meminfo").useLines { lines ->
                for (line in lines) {
                    when {
                        line.startsWith("MemTotal:") -> ramTotal = parseKb(line)
                        line.startsWith("MemAvailable:") -> ramAvail = parseKb(line)
                        line.startsWith("SwapTotal:") -> swapTotal = parseKb(line)
                        line.startsWith("SwapFree:") -> swapFree = parseKb(line)
                    }
                }
            }
        } catch (_: Throwable) {}
        return MemSnapshot(ramTotal, ramAvail, swapTotal, swapFree)
    }

    private fun parseKb(line: String): Long =
        line.split(Regex("\\s+"))[1].toLongOrNull() ?: 0L
}
