package com.ivarna.apexcore.ui.iron.games

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ivarna.apexcore.ui.iron.IronTheme
import com.ivarna.apexcore.ui.iron.ThemeMode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ShutterOverlayFreezeIconTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun fakeApp() = AppCardData(
        name = "Example Game",
        pkg = "com.example.game",
        demand = Demand.HIGH,
        tint = Color.Gray,
        icon = {
            Box(Modifier.size(40.dp)) {
                Text("ICON")
            }
        },
    )

    @Test
    fun iconVisibleDuringOptimizedFreeze() {
        composeRule.mainClock.autoAdvance = true
        composeRule.setContent {
            IronTheme(themeMode = ThemeMode.GRAPHITE, paperInserts = false, reducedMotionOverride = false) {
                ShutterOverlay(
                    state = LaunchState(
                        phase = LaunchPhase.FREEZE,
                        app = fakeApp(),
                        frozenCount = 0,
                        totalTargets = 0,
                    ),
                    onCancel = {},
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("launch_app_icon").assertIsDisplayed()
        composeRule.onNodeWithTag("launch_readout").assertTextEquals("OPTIMIZED")
    }

    @Test
    fun iconVisibleWithFreezeProgress() {
        composeRule.mainClock.autoAdvance = true
        composeRule.setContent {
            IronTheme(themeMode = ThemeMode.GRAPHITE, paperInserts = false, reducedMotionOverride = false) {
                ShutterOverlay(
                    state = LaunchState(
                        phase = LaunchPhase.FREEZE,
                        app = fakeApp(),
                        frozenCount = 4,
                        totalTargets = 10,
                    ),
                    onCancel = {},
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("launch_app_icon").assertIsDisplayed()
        composeRule.onNodeWithTag("launch_readout").assertTextEquals("FREEZING · 4 / 10")
    }

    @Test
    fun failedStateRestoresIcon() {
        var state by mutableStateOf(
            LaunchState(
                phase = LaunchPhase.PART,
                app = fakeApp(),
            ),
        )

        composeRule.mainClock.autoAdvance = true
        composeRule.setContent {
            IronTheme(themeMode = ThemeMode.GRAPHITE, paperInserts = false, reducedMotionOverride = false) {
                ShutterOverlay(state = state, onCancel = {})
            }
        }
        composeRule.waitForIdle()

        state = LaunchState(
            phase = LaunchPhase.FAILED,
            app = fakeApp(),
            errorTitle = "LAUNCH FAILED",
            errorDetail = "no launchable activity",
        )
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("launch_app_icon").assertIsDisplayed()
    }
}
