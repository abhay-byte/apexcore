package com.ivarna.apexcore.ui.iron.games

import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GameLaunchCoordinatorTest {

    private fun fakeApp(pkg: String = "com.example.game") = AppCardData(
        name = "Example",
        pkg = pkg,
        demand = Demand.MEDIUM,
        tint = Color.Gray,
        icon = {},
    )

    private fun TestScope.makeCoordinator(
        freeze: suspend (String, (Int, Int) -> Unit) -> FreezeOutcome = { _, onFrozen ->
            onFrozen(0, 0)
            FreezeOutcome.Ok(0, 0)
        },
        launchIntent: (String) -> Boolean = { true },
        attachRail: (String) -> Unit = {},
        reducedMotion: () -> Boolean = { false },
    ): GameLaunchCoordinator = GameLaunchCoordinator(
        freeze = freeze,
        launchIntent = launchIntent,
        attachRail = attachRail,
        reducedMotion = reducedMotion,
        // Use the test scope (not backgroundScope) so delays are driven by advanceTimeBy.
        scope = this,
        monoNowMs = { testScheduler.currentTime },
    )

    @Test
    fun `part phase is visible before launch intent fires`() = runTest {
        lateinit var coordinator: GameLaunchCoordinator
        var phaseAtIntent: LaunchPhase? = null

        coordinator = makeCoordinator(
            freeze = { _, onFrozen ->
                onFrozen(0, 0)
                FreezeOutcome.Ok(0, 0)
            },
            launchIntent = {
                phaseAtIntent = coordinator.state.phase
                true
            },
        )

        coordinator.launch(fakeApp())
        advanceUntilIdle()

        assertEquals(LaunchPhase.PART, phaseAtIntent)
    }

    @Test
    fun `launch lead is not skipped`() = runTest {
        var intentFired = false
        val coordinator = makeCoordinator(
            launchIntent = {
                intentFired = true
                true
            },
        )

        coordinator.launch(fakeApp())
        // Through WIND + PRESS + empty FREEZE min hold, stop just before PART lead completes.
        advanceTimeBy(
            LaunchTiming.WIND_MS +
                LaunchTiming.PRESS_MS +
                LaunchTiming.MIN_EMPTY_FREEZE_MS +
                LaunchTiming.PART_LEAD_MS - 1,
        )
        runCurrent()
        assertFalse(intentFired)
        assertEquals(LaunchPhase.PART, coordinator.state.phase)

        advanceTimeBy(1)
        runCurrent()
        assertTrue(intentFired)
    }

    @Test
    fun `blocked freeze never launches`() = runTest {
        var intentFired = false
        var railAttached = false
        val coordinator = makeCoordinator(
            freeze = { _, _ -> FreezeOutcome.Blocked("no elevation") },
            launchIntent = {
                intentFired = true
                true
            },
            attachRail = { railAttached = true },
        )

        coordinator.launch(fakeApp())
        advanceUntilIdle()

        assertFalse(intentFired)
        assertFalse(railAttached)
        assertEquals(LaunchPhase.IDLE, coordinator.state.phase)
    }

    @Test
    fun `cancel during WIND prevents freeze and launch`() = runTest {
        var freezeCalled = false
        var intentFired = false
        val coordinator = makeCoordinator(
            freeze = { _, _ ->
                freezeCalled = true
                FreezeOutcome.Ok(0, 0)
            },
            launchIntent = {
                intentFired = true
                true
            },
        )

        coordinator.launch(fakeApp())
        advanceTimeBy(LaunchTiming.WIND_MS / 2)
        runCurrent()
        assertEquals(LaunchPhase.WIND, coordinator.state.phase)

        coordinator.cancel()
        advanceUntilIdle()

        assertFalse(freezeCalled)
        assertFalse(intentFired)
        assertEquals(LaunchPhase.IDLE, coordinator.state.phase)
    }

    @Test
    fun `zero targets is valid and launches once`() = runTest {
        var intentCount = 0
        val coordinator = makeCoordinator(
            freeze = { _, onFrozen ->
                onFrozen(0, 0)
                FreezeOutcome.Ok(0, 0)
            },
            launchIntent = {
                intentCount++
                true
            },
        )

        coordinator.launch(fakeApp())
        advanceUntilIdle()

        assertEquals(1, intentCount)
        assertEquals(LaunchPhase.IDLE, coordinator.state.phase)
    }

    @Test
    fun `rail timing remains relative to PART`() = runTest {
        var partAt = -1L
        var intentAt = -1L
        var railAt = -1L

        val coordinator = makeCoordinator(
            freeze = { _, onFrozen ->
                onFrozen(0, 0)
                FreezeOutcome.Ok(0, 0)
            },
            launchIntent = {
                intentAt = testScheduler.currentTime
                true
            },
            attachRail = {
                railAt = testScheduler.currentTime
            },
        )

        coordinator.launch(fakeApp())
        advanceTimeBy(LaunchTiming.WIND_MS + LaunchTiming.PRESS_MS + LaunchTiming.MIN_EMPTY_FREEZE_MS)
        runCurrent()
        assertEquals(LaunchPhase.PART, coordinator.state.phase)
        partAt = testScheduler.currentTime

        advanceUntilIdle()

        assertTrue("intent should fire after PART", intentAt >= partAt)
        assertEquals(
            "intent lead from PART",
            LaunchTiming.PART_LEAD_MS,
            intentAt - partAt,
        )
        assertEquals(
            "rail from PART start",
            LaunchTiming.RAIL_ATTACH_MS,
            railAt - partAt,
        )
    }

    @Test
    fun `double launch ignored while busy`() = runTest {
        val pkgs = mutableListOf<String>()
        val coordinator = makeCoordinator(
            freeze = { pkg, onFrozen ->
                pkgs += pkg
                onFrozen(0, 0)
                FreezeOutcome.Ok(0, 0)
            },
        )

        coordinator.launch(fakeApp("com.first"))
        runCurrent()
        coordinator.launch(fakeApp("com.second"))
        advanceUntilIdle()

        assertEquals(listOf("com.first"), pkgs)
    }

    @Test
    fun `failed launch intent enters FAILED then resets`() = runTest {
        val coordinator = makeCoordinator(
            launchIntent = { false },
        )

        coordinator.launch(fakeApp())
        advanceTimeBy(
            LaunchTiming.WIND_MS +
                LaunchTiming.PRESS_MS +
                LaunchTiming.MIN_EMPTY_FREEZE_MS +
                LaunchTiming.PART_LEAD_MS,
        )
        runCurrent()
        assertEquals(LaunchPhase.FAILED, coordinator.state.phase)
        assertEquals("LAUNCH FAILED", coordinator.state.errorTitle)

        advanceUntilIdle()
        assertEquals(LaunchPhase.IDLE, coordinator.state.phase)
        assertNull(coordinator.state.errorTitle)
    }
}
