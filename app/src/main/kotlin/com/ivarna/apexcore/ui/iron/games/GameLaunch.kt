package com.ivarna.apexcore.ui.iron.games

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/* ═══ §7.5 THE LAUNCH CEREMONY — state machine + timeline ════════════ */

enum class LaunchPhase { IDLE, WIND, PRESS, FREEZE, PART, FAILED }

data class LaunchState(
    val phase: LaunchPhase = LaunchPhase.IDLE,
    val app: AppCardData? = null,
    val frozenCount: Int = 0,
    val totalTargets: Int = 0,
    val errorTitle: String? = null,
    val errorDetail: String? = null,
)

sealed interface FreezeOutcome {
    data class Ok(val frozen: Int, val total: Int) : FreezeOutcome
    data class Blocked(val reason: String) : FreezeOutcome
    data class Failed(val reason: String) : FreezeOutcome
}

object LaunchTiming {
    const val WIND_MS = 160L
    const val PRESS_MS = 100L          // hold long enough for squash spring to read
    const val PART_MS = 280L
    const val RAIL_ATTACH_MS = 520L
    const val FAIL_HOLD_MS = 900L
    const val FREEZE_TIMEOUT_MS = 6_000L
    /** Floor so an instant / empty freeze still shows the seam before PART. */
    const val MIN_FREEZE_MS = 320L
}

/**
 * Owns the launch sequence. No View/haptic dependencies — haptics fire in the
 * overlay on phase transitions (VM-safe, unit-testable with a TestDispatcher).
 */
class GameLaunchCoordinator(
    private val freeze: suspend (targetPkg: String, onFrozen: (frozen: Int, total: Int) -> Unit) -> FreezeOutcome,
    private val launchIntent: (pkg: String) -> Boolean,
    private val attachRail: (pkg: String) -> Unit,
    private val reducedMotion: () -> Boolean = { false },
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
) {
    var state by mutableStateOf(LaunchState())
        private set

    private var job: Job? = null

    val busy: Boolean get() = job?.isActive == true
    val cancellable: Boolean
        get() = state.phase == LaunchPhase.WIND || state.phase == LaunchPhase.PRESS

    fun launch(app: AppCardData) {
        if (busy) return
        job = scope.launch {
            try {
                sequence(app)
            } catch (e: CancellationException) {
                state = LaunchState()
                throw e
            } catch (t: Throwable) {
                fail("LAUNCH ABORTED", t.message ?: "unexpected error")
            }
        }
    }

    fun cancel() {
        job?.cancel()
        state = LaunchState()
    }

    private suspend fun sequence(app: AppCardData) {
        state = LaunchState(phase = LaunchPhase.WIND, app = app)

        if (reducedMotion()) {
            delay(200)
            state = state.copy(phase = LaunchPhase.PRESS)
            delay(60)
        } else {
            delay(LaunchTiming.WIND_MS)
            state = state.copy(phase = LaunchPhase.PRESS)
            delay(LaunchTiming.PRESS_MS)
        }
        state = state.copy(phase = LaunchPhase.FREEZE)

        val freezeStarted = System.nanoTime()
        val outcome = withTimeoutOrNull(LaunchTiming.FREEZE_TIMEOUT_MS) {
            try {
                freeze(app.pkg) { frozen, total ->
                    state = state.copy(frozenCount = frozen, totalTargets = total)
                }
            } catch (e: Exception) {
                FreezeOutcome.Failed(e.message ?: "unknown error")
            }
        } ?: FreezeOutcome.Failed("freeze timed out")

        when (outcome) {
            is FreezeOutcome.Blocked -> return fail("FREEZE BLOCKED", outcome.reason)
            is FreezeOutcome.Failed -> return fail("LAUNCH ABORTED", outcome.reason)
            is FreezeOutcome.Ok -> state = state.copy(
                frozenCount = outcome.frozen,
                totalTargets = outcome.total,
            )
        }

        // Keep the seam on screen for a readable beat even when freeze was instant.
        val freezeElapsedMs = (System.nanoTime() - freezeStarted) / 1_000_000L
        val remain = LaunchTiming.MIN_FREEZE_MS - freezeElapsedMs
        if (remain > 0) delay(remain)

        val launched = try {
            launchIntent(app.pkg)
        } catch (_: Exception) {
            false
        }
        if (!launched) return fail("LAUNCH FAILED", "no launchable activity")
        state = state.copy(phase = LaunchPhase.PART)

        delay(LaunchTiming.RAIL_ATTACH_MS - LaunchTiming.PART_MS)
        attachRail(app.pkg)

        // Let the iris finish under the incoming game activity.
        delay(LaunchTiming.PART_MS + 120)
        state = LaunchState()
    }

    private suspend fun fail(title: String, detail: String) {
        state = state.copy(
            phase = LaunchPhase.FAILED,
            errorTitle = title,
            errorDetail = detail,
        )
        delay(LaunchTiming.FAIL_HOLD_MS)
        state = LaunchState()
    }
}
