package com.ivarna.apexcore.ui.iron.tune

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ivarna.apexcore.ui.iron.ChamferButton
import com.ivarna.apexcore.ui.iron.IronTheme
import com.ivarna.apexcore.ui.iron.ThemeMode
import com.ivarna.apexcore.ui.iron.window.IronHeight
import com.ivarna.apexcore.ui.iron.window.IronWidth
import com.ivarna.apexcore.ui.iron.window.IronWindow
import com.ivarna.apexcore.ui.iron.window.LocalIronWindow
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TuningRoomLayoutTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun sampleCategories(): List<TuneCategoryUi> = listOf(
        TuneCategoryUi(
            name = "CPU",
            options = listOf(
                TuneOptionUi(
                    key = "CPU_GOVERNOR",
                    title = "CPU governor",
                    description = "Select governor",
                    available = true,
                    reason = null,
                    checked = false,
                    onToggle = {},
                )
            )
        ),
        TuneCategoryUi(
            name = "GPU",
            options = listOf(
                TuneOptionUi(
                    key = "GPU_FLOOR",
                    title = "GPU frequency floor",
                    description = "Raise floor",
                    available = true,
                    reason = null,
                    checked = false,
                    onToggle = {},
                )
            )
        ),
        TuneCategoryUi(
            name = "DISPLAY",
            options = listOf(
                TuneOptionUi(
                    key = "DISPLAY_PEAK",
                    title = "Peak refresh",
                    description = "Lock peak",
                    available = false,
                    reason = "Needs Root",
                    checked = false,
                    onToggle = {},
                )
            )
        ),
    )

    private fun setPhoneTuningRoom(
        gameOptions: List<Pair<String, String>> = listOf("com.example.game" to "Example Game"),
        selectedGamePkg: String? = "com.example.game",
        categories: List<TuneCategoryUi> = sampleCategories(),
        probeError: String? = null,
    ) {
        composeRule.setContent {
            IronTheme(themeMode = ThemeMode.GRAPHITE, paperInserts = false, reducedMotionOverride = true) {
                CompositionLocalProvider(
                    LocalIronWindow provides IronWindow(IronWidth.COMPACT, IronHeight.EXPANDED)
                ) {
                    Box(Modifier.size(412.dp, 915.dp)) {
                        TuningRoom(
                            categories = categories,
                            sessionActive = false,
                            sessionElapsedS = 0,
                            sessionApplied = 0,
                            isProbing = false,
                            onProbe = {},
                            onBack = {},
                            selectedGamePkg = selectedGamePkg,
                            gameOptions = gameOptions,
                            onGamePkgSelect = {},
                            onMaximumPerformance = {},
                            presetReport = null,
                            probeError = probeError,
                        )
                    }
                }
            }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun gamePickerKeepsSelectedLabelVisible() {
        setPhoneTuningRoom()

        composeRule.onNodeWithText("Example Game").assertIsDisplayed()
        composeRule.onNodeWithText("CHANGE").assertIsDisplayed()

        val label = composeRule.onNodeWithText("Example Game").fetchSemanticsNode().boundsInRoot
        val change = composeRule.onNodeWithText("CHANGE").fetchSemanticsNode().boundsInRoot
        assertTrue("selected label width must be > 0", label.width > 0f)
        assertTrue("CHANGE must not consume entire row", change.width < 412f * 0.70f)
    }

    @Test
    fun maximumPerformanceTextStaysVisible() {
        setPhoneTuningRoom()

        composeRule.onNodeWithText("Maximum Performance").assertIsDisplayed()
        composeRule.onNodeWithText(
            "OEM Game Mode + CPU/GPU performance governor + max locks (verified only)",
            substring = true,
        ).assertIsDisplayed()
        composeRule.onNodeWithText("APPLY").assertIsDisplayed()

        val title = composeRule.onNodeWithText("Maximum Performance").fetchSemanticsNode().boundsInRoot
        assertTrue("Maximum Performance title width must be > 0", title.width > 0f)
    }

    @Test
    fun firstTuningCategoryVisibleInPhoneViewport() {
        setPhoneTuningRoom()

        composeRule.onNodeWithText("CPU").assertIsDisplayed()
        val header = composeRule.onNodeWithText("CPU").fetchSemanticsNode().boundsInRoot
        assertTrue("first category must be within phone viewport", header.top < 915f)
        assertTrue("first category must have positive width", header.width > 0f)
    }

    @Test
    fun longTextDoesNotExplodeCardHeight() {
        val longName = "Very Long Example Game Name That Should Ellipsize Instead Of Expanding Forever ".repeat(3)
        setPhoneTuningRoom(
            gameOptions = listOf("com.example.game" to longName),
            selectedGamePkg = "com.example.game",
        )

        val change = composeRule.onNodeWithText("CHANGE").fetchSemanticsNode().boundsInRoot
        val apply = composeRule.onNodeWithText("APPLY").fetchSemanticsNode().boundsInRoot
        assertTrue("CHANGE row should stay compact", change.height < 120f)
        assertTrue("APPLY card should not balloon to full screen", apply.top < 600f)
        composeRule.onNodeWithText("CPU").assertIsDisplayed()
    }

    @Test
    fun chamferButtonIntrinsicSizingInWeightedRow() {
        composeRule.setContent {
            IronTheme(themeMode = ThemeMode.GRAPHITE, paperInserts = false, reducedMotionOverride = true) {
                Row(Modifier.width(400.dp).testTag("row")) {
                    Text("LEFT", Modifier.weight(1f).testTag("left"))
                    ChamferButton("ACTION", onClick = {}, tall = false, modifier = Modifier.testTag("action"))
                }
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("left").assertIsDisplayed()
        composeRule.onNodeWithText("ACTION").assertIsDisplayed()

        val left = composeRule.onNodeWithTag("left").fetchSemanticsNode().boundsInRoot
        val action = composeRule.onNodeWithText("ACTION").fetchSemanticsNode().boundsInRoot
        val row = composeRule.onNodeWithTag("row").fetchSemanticsNode().boundsInRoot
        assertTrue("LEFT must have positive width", left.width > 40f)
        assertTrue("ACTION must not consume entire Row", action.width < row.width * 0.70f)
        assertTrue("LEFT + ACTION should both fit", left.width + action.width < row.width + 1f)
    }

    @Test
    fun probeFailureShowsWarningAndCategories() {
        setPhoneTuningRoom(
            categories = sampleCategories().map { cat ->
                cat.copy(options = cat.options.map { it.copy(available = false, reason = "Capability probe failed") })
            },
            probeError = "Simulated probe failure",
        )

        composeRule.onNodeWithText("CAPABILITY PROBE FAILED").assertIsDisplayed()
        composeRule.onNodeWithText("Controls shown unavailable. Tap PROBE to retry.").assertIsDisplayed()
        composeRule.onNodeWithText("CPU governor").assertIsDisplayed()
    }
}
