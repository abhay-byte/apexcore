package com.ivarna.apexcore.ram

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.*
import android.util.Log
import com.ivarna.apexcore.freeze.FreezeFramework
import com.ivarna.apexcore.getSystemMemStats
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.coroutines.coroutineContext

class RamFillerManager(private val context: Context) {

    @Volatile
    var isRunning = false
        private set

    private val _progress = MutableStateFlow<RamFillProgress>(
        RamFillProgress.Done(RamFillResult(0, 0, 0, 0f, 0f, 0, 0, false, false, false))
    )
    val progress: StateFlow<RamFillProgress> = _progress.asStateFlow()

    private var runJob: Job? = null
    private var boundService: Messenger? = null
    private var serviceConnection: ServiceConnection? = null
    private var bound = false

    companion object {
        private const val TAG = "ApexCore.RamFiller"
        const val TIMEOUT_MS = 15_000L
        const val FREEZE_BUDGET_MS = 8_000L
    }

    suspend fun run(
        mode: RamFillMode = RamFillMode.STANDARD,
        preFreeze: Boolean = true
    ): RamFillResult = withContext(Dispatchers.Main) {
        if (isRunning) {
            Log.w(TAG, "run() already active, ignoring duplicate call")
            return@withContext RamFillResult(
                0, 0, 0, 0f, 0f, 0, 0,
                cancelled = true, oomTriggered = false, preFreezeRan = false,
                stopReason = StopReason.CANCEL
            )
        }
        isRunning = true
        runJob = coroutineContext[Job]
        val totalStart = System.currentTimeMillis()
        Log.i(TAG, "run() started mode=${mode.displayName} preFreeze=$preFreeze")

        try {
            var preFreezeRan = false
            if (preFreeze) {
                _progress.value = RamFillProgress.PreFreeze
                Log.i(TAG, "Pre-freeze starting")
                val freezeStart = System.currentTimeMillis()
                try {
                    withTimeout(FREEZE_BUDGET_MS) {
                        withContext(Dispatchers.IO) {
                            FreezeFramework.freezeAll(context)
                        }
                    }
                    preFreezeRan = true
                    val freezeDuration = System.currentTimeMillis() - freezeStart
                    Log.i(TAG, "Pre-freeze done in ${freezeDuration}ms")
                } catch (_: kotlinx.coroutines.TimeoutCancellationException) {
                    Log.w(TAG, "Pre-freeze timed out after ${FREEZE_BUDGET_MS}ms, continuing")
                } catch (t: Throwable) {
                    Log.w(TAG, "Pre-freeze failed: ${t.message}")
                }
            }

            val before = getSystemMemStats(context)
            val beforeAvailKb = before.ramTotalKb - before.ramUsedKb
            val beforeSwapFreeKb = before.swapTotalKb - before.swapUsedKb
            Log.i(TAG, "Before fill: avail=${beforeAvailKb}KB swapFree=${beforeSwapFreeKb}KB totalRamKb=${before.ramTotalKb}")

            val elapsedAlready = System.currentTimeMillis() - totalStart
            val remainingBudget = (TIMEOUT_MS - elapsedAlready).coerceAtLeast(5_000L)
            Log.i(TAG, "Binding to RamFillerService, remaining budget=${remainingBudget}ms")

            val serviceResult = bindAndWait(before.ramTotalKb, remainingBudget)

            val stopReason: StopReason
            val peakAllocatedMb: Long
            val peakRamPercent: Float
            val peakSwapPercent: Float
            val fillDurationMs: Long
            val chunkCount: Int
            val heapMaxMb: Long
            val oomTriggered: Boolean

            if (serviceResult != null) {
                peakAllocatedMb = serviceResult.peakAllocatedMb
                peakRamPercent = serviceResult.peakRamPercent
                peakSwapPercent = serviceResult.peakSwapPercent
                fillDurationMs = serviceResult.fillDurationMs
                stopReason = serviceResult.stopReason
                chunkCount = serviceResult.chunkCount
                heapMaxMb = serviceResult.heapMaxMb
                oomTriggered = stopReason == StopReason.OOM
            } else {
                peakAllocatedMb = 0
                peakRamPercent = 0f
                peakSwapPercent = 0f
                fillDurationMs = 0
                stopReason = StopReason.CANCEL
                chunkCount = 0
                heapMaxMb = 0
                oomTriggered = false
                Log.w(TAG, "Service bind/run failed or cancelled")
            }

            delay(200)
            val after = getSystemMemStats(context)
            val afterAvailKb = after.ramTotalKb - after.ramUsedKb
            val afterSwapFreeKb = after.swapTotalKb - after.swapUsedKb
            val freedKb = (afterAvailKb - beforeAvailKb).coerceAtLeast(0)
            val swapFreedKb = (afterSwapFreeKb - beforeSwapFreeKb).coerceAtLeast(0)
            val totalDurationMs = System.currentTimeMillis() - totalStart

            Log.i(TAG, "After fill: avail=${afterAvailKb}KB swapFree=${afterSwapFreeKb}KB freed=${freedKb}KB swapFreed=${swapFreedKb}KB")
            Log.i(TAG, "Stop: stopReason=$stopReason peakAllocatedMb=$peakAllocatedMb chk=$chunkCount peakRam=%.1f%% heapMaxMb=$heapMaxMb totalDur=$totalDurationMs".format(peakRamPercent * 100))

            val result = RamFillResult(
                freedKb = freedKb,
                swapFreedKb = swapFreedKb,
                peakAllocatedMb = peakAllocatedMb,
                peakRamPercent = peakRamPercent,
                peakSwapPercent = peakSwapPercent,
                totalDurationMs = totalDurationMs,
                fillDurationMs = fillDurationMs,
                cancelled = stopReason == StopReason.CANCEL,
                oomTriggered = oomTriggered,
                preFreezeRan = preFreezeRan,
                stopReason = stopReason,
                chunkCount = chunkCount,
                heapMaxMb = heapMaxMb
            )
            _progress.value = RamFillProgress.Done(result)
            result

        } catch (_: CancellationException) {
            val result = RamFillResult(
                freedKb = 0, swapFreedKb = 0, peakAllocatedMb = 0,
                peakRamPercent = 0f, peakSwapPercent = 0f,
                totalDurationMs = System.currentTimeMillis() - totalStart,
                fillDurationMs = 0, cancelled = true,
                oomTriggered = false, preFreezeRan = false,
                stopReason = StopReason.CANCEL
            )
            _progress.value = RamFillProgress.Done(result)
            result
        } finally {
            unbindService()
            isRunning = false
        }
    }

    fun cancel() {
        runJob?.cancel()
        boundService?.let { svc ->
            try {
                svc.send(Message.obtain(null, RamFillerService.MSG_CANCEL))
            } catch (_: Throwable) {}
        }
    }

    private data class ServiceFillResult(
        val peakAllocatedMb: Long,
        val peakRamPercent: Float,
        val peakSwapPercent: Float,
        val fillDurationMs: Long,
        val stopReason: StopReason,
        val chunkCount: Int,
        val heapMaxMb: Long
    )

    private suspend fun bindAndWait(totalRamKb: Long, timeoutMs: Long): ServiceFillResult? {
        val deferred = CompletableDeferred<ServiceFillResult?>()

        var lastKnownAllocatedMb = 0L
        var lastKnownChunkCount = 0
        var lastKnownPeakRamPercent = 0f
        var lastKnownPeakSwapPercent = 0f
        var lastKnownHeapMaxMb = 0L
        var lastKnownElapsedMs = 0L

        val handler = object : Handler(Looper.getMainLooper()) {
            override fun handleMessage(msg: Message) {
                when (msg.what) {
                    RamFillerService.MSG_PROGRESS -> {
                        val data = msg.data
                        lastKnownAllocatedMb = data.getLong(RamFillerService.KEY_ALLOCATED_MB, lastKnownAllocatedMb)
                        lastKnownPeakRamPercent = data.getFloat(RamFillerService.KEY_RAM_PERCENT, lastKnownPeakRamPercent)
                        lastKnownPeakSwapPercent = data.getFloat(RamFillerService.KEY_SWAP_PERCENT, lastKnownPeakSwapPercent)
                        lastKnownChunkCount = msg.arg1
                        lastKnownElapsedMs = data.getLong(RamFillerService.KEY_ELAPSED_MS, lastKnownElapsedMs)

                        val phase = data.getString(RamFillerService.KEY_PHASE, RamFillerService.PHASE_FILLING)
                            ?: RamFillerService.PHASE_FILLING
                        when (phase) {
                            RamFillerService.PHASE_HOLDING -> {
                                val holdRemaining = data.getLong(RamFillerService.KEY_HOLD_REMAINING_MS, 0)
                                _progress.value = RamFillProgress.Holding(remainingMs = holdRemaining)
                            }
                            RamFillerService.PHASE_RELEASING -> {
                                _progress.value = RamFillProgress.Releasing
                            }
                            else -> {
                                _progress.value = RamFillProgress.Filling(
                                    allocatedMb = lastKnownAllocatedMb,
                                    ramUsagePercent = lastKnownPeakRamPercent,
                                    swapUsagePercent = lastKnownPeakSwapPercent,
                                    elapsedMs = lastKnownElapsedMs,
                                    chunkCount = lastKnownChunkCount
                                )
                            }
                        }
                    }
                    RamFillerService.MSG_DONE -> {
                        val data = msg.data
                        val result = ServiceFillResult(
                            peakAllocatedMb = data.getLong(RamFillerService.KEY_PEAK_ALLOCATED_MB, 0),
                            peakRamPercent = data.getFloat(RamFillerService.KEY_PEAK_RAM, 0f),
                            peakSwapPercent = data.getFloat(RamFillerService.KEY_PEAK_SWAP, 0f),
                            fillDurationMs = data.getLong(RamFillerService.KEY_FILL_DURATION, 0),
                            stopReason = try {
                                StopReason.valueOf(data.getString(RamFillerService.KEY_STOP_REASON, "RAM_CAP") ?: "RAM_CAP")
                            } catch (_: Throwable) { StopReason.RAM_CAP },
                            chunkCount = data.getInt(RamFillerService.KEY_CHUNK_COUNT, 0),
                            heapMaxMb = data.getLong(RamFillerService.KEY_HEAP_MAX_MB, 0)
                        )
                        deferred.complete(result)
                    }
                }
            }
        }
        val replyMessenger = Messenger(handler)

        val conn = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                boundService = Messenger(binder)
                bound = true
                try {
                    val msg = Message.obtain(null, RamFillerService.MSG_START).apply {
                        replyTo = replyMessenger
                        data = Bundle().apply {
                            putLong(RamFillerService.KEY_TOTAL_RAM_KB, totalRamKb)
                            putLong(RamFillerService.KEY_TIMEOUT_MS, timeoutMs)
                        }
                    }
                    boundService?.send(msg)
                } catch (e: Throwable) {
                    Log.e(TAG, "Failed to send MSG_START: ${e.message}")
                    if (!deferred.isCompleted) {
                        deferred.complete(null)
                    }
                }
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                boundService = null
                bound = false
                if (!deferred.isCompleted) {
                    if (lastKnownAllocatedMb > 0) {
                        Log.w(TAG, "Service disconnected mid-run with progress; inferring BUDGET")
                        deferred.complete(
                            ServiceFillResult(
                                peakAllocatedMb = lastKnownAllocatedMb,
                                peakRamPercent = lastKnownPeakRamPercent,
                                peakSwapPercent = lastKnownPeakSwapPercent,
                                fillDurationMs = lastKnownElapsedMs,
                                stopReason = StopReason.BUDGET,
                                chunkCount = lastKnownChunkCount,
                                heapMaxMb = lastKnownHeapMaxMb
                            )
                        )
                    } else {
                        Log.w(TAG, "Service disconnected before completion")
                        deferred.complete(null)
                    }
                }
            }
        }
        serviceConnection = conn

        try {
            bound = context.bindService(Intent(context, RamFillerService::class.java), conn, Context.BIND_AUTO_CREATE)
            if (!bound) {
                Log.e(TAG, "bindService returned false")
                if (!deferred.isCompleted) {
                    deferred.complete(null)
                }
            }
        } catch (e: Throwable) {
            Log.e(TAG, "bindService threw: ${e.message}")
            if (!deferred.isCompleted) {
                deferred.complete(null)
            }
        }

        val result: ServiceFillResult? = try {
            withTimeoutOrNull(timeoutMs) { deferred.await() }
        } catch (_: CancellationException) {
            null
        }

        if (result == null && !deferred.isCompleted) {
            boundService?.let { svc ->
                try {
                    svc.send(Message.obtain(null, RamFillerService.MSG_CANCEL))
                } catch (_: Throwable) {}
            }
            val graceResult = try {
                withTimeoutOrNull(1_500L) { deferred.await() }
            } catch (_: CancellationException) {
                null
            }
            if (graceResult != null) {
                return graceResult
            }
            if (lastKnownAllocatedMb > 0) {
                val syntheticReason = if (coroutineContext.isActive) StopReason.TIMEOUT else StopReason.CANCEL
                Log.w(TAG, "No DONE after timeout/cancel; synthesizing $syntheticReason from last progress")
                return ServiceFillResult(
                    peakAllocatedMb = lastKnownAllocatedMb,
                    peakRamPercent = lastKnownPeakRamPercent,
                    peakSwapPercent = lastKnownPeakSwapPercent,
                    fillDurationMs = lastKnownElapsedMs,
                    stopReason = syntheticReason,
                    chunkCount = lastKnownChunkCount,
                    heapMaxMb = lastKnownHeapMaxMb
                )
            }
        }

        return result
    }

    private fun unbindServiceInternal() {
        try {
            if (bound) {
                context.unbindService(serviceConnection ?: return)
                bound = false
            }
        } catch (_: Throwable) {}
        try {
            context.stopService(Intent(context, RamFillerService::class.java))
        } catch (_: Throwable) {}
        serviceConnection = null
        boundService = null
    }

    private fun unbindService() = unbindServiceInternal()
}
