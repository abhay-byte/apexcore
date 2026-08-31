package com.ivarna.apexcore.ui.iron.tune

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ivarna.apexcore.tune.TuneControlKind
import com.ivarna.apexcore.ui.iron.IronConfirmDialog
import com.ivarna.apexcore.ui.iron.IronDialogSeverity
import com.ivarna.apexcore.ui.iron.IronSlider
import com.ivarna.apexcore.ui.iron.IronTheme
import com.ivarna.apexcore.ui.iron.ThemeMode
import com.ivarna.apexcore.ui.iron.window.IronHeight
import com.ivarna.apexcore.ui.iron.window.IronWidth
import com.ivarna.apexcore.ui.iron.window.IronWindow
import com.ivarna.apexcore.ui.iron.window.LocalIronWindow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TuningRoomThemeControlsTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun setTuningRoom(
        theme: ThemeMode,
        categories: List<TuneCategoryUi>,
        selectedGamePkg: String? = "com.example.samba",
        gameOptions: List<Pair<String, String>> = listOf("com.example.samba" to "SambaS3"),
        showMaxPerf: Boolean = true,
    ) {
        composeRule.setContent {
            IronTheme(themeMode = theme, paperInserts = theme == ThemeMode.VELLUM, reducedMotionOverride = true) {
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
                            onMaximumPerformance = if (showMaxPerf) ({}) else null,
                            presetReport = null,
                        )
                    }
                }
            }
        }
    }

    private fun enumCategories(enabled: Boolean, available: Boolean = true): List<TuneCategoryUi> {
        val opts = listOf("schedutil", "performance", "powersave", "ondemand", "conservative")
        return listOf(
            TuneCategoryUi(
                name = "CPU",
                options = listOf(
                    TuneOptionUi(
                        key = "CPU_GOVERNOR",
                        title = "CPU governor",
                        description = "Select governor",
                        available = available,
                        reason = if (available) null else "Needs Root",
                        kind = TuneControlKind.ENUM,
                        checked = enabled,
                        onToggle = {},
                        enumOptions = opts,
                        selectedEnum = "performance",
                        onEnumSelect = {},
                    )
                )
            )
        )
    }

    @Test
    fun graphiteGamePickerShowsSelectedGame() {
        setTuningRoom(ThemeMode.GRAPHITE, enumCategories(enabled = true))
        composeRule.onNodeWithText("SambaS3").assertIsDisplayed()
        composeRule.onNodeWithText("CHANGE").assertIsDisplayed()
    }

    @Test
    fun vellumGamePickerShowsSelectedGame() {
        setTuningRoom(ThemeMode.VELLUM, enumCategories(enabled = true))
        composeRule.onNodeWithText("SambaS3").assertIsDisplayed()
        composeRule.onNodeWithText("CHANGE").assertIsDisplayed()
    }

    @Test
    fun graphiteEnumMenuShowsOptions() {
        setTuningRoom(ThemeMode.GRAPHITE, enumCategories(enabled = true))
        composeRule.onNodeWithText("performance").assertIsDisplayed()
        composeRule.onNodeWithText("performance").performClick()
        composeRule.onNodeWithText("schedutil").assertIsDisplayed()
        composeRule.onNodeWithText("ondemand").assertIsDisplayed()
        composeRule.onNodeWithText("conservative").assertIsDisplayed()
    }

    @Test
    fun vellumEnumValueReadableWhenToggleOff() {
        setTuningRoom(ThemeMode.VELLUM, enumCategories(enabled = false))
        composeRule.onNodeWithText("performance").assertIsDisplayed()
    }

    @Test
    fun ironSliderCommitOnlyOnFinish() {
        var changeCount = 0
        var finishCount = 0
        composeRule.setContent {
            IronTheme(themeMode = ThemeMode.GRAPHITE, paperInserts = false, reducedMotionOverride = true) {
                var value by remember { mutableFloatStateOf(10f) }
                IronSlider(
                    value = value,
                    onValueChange = { v ->
                        value = v
                        changeCount++
                    },
                    onValueChangeFinished = { finishCount++ },
                    valueRange = 0f..100f,
                    enabled = true,
                )
            }
        }
        composeRule.onNodeWithTag("iron_slider").assertExists()
        composeRule.onNodeWithTag("iron_slider").performTouchInput { swipeRight() }
        composeRule.waitForIdle()
        assertTrue("onValueChange should fire during drag", changeCount >= 1)
        assertEquals("onValueChangeFinished should fire once", 1, finishCount)
    }

    @Test
    fun highPowerDialogShowsIronActions() {
        var confirmed = false
        var dismissed = false
        composeRule.setContent {
            IronTheme(themeMode = ThemeMode.GRAPHITE, paperInserts = false, reducedMotionOverride = true) {
                var visible by remember { mutableStateOf(true) }
                IronConfirmDialog(
                    visible = visible,
                    title = "High-power disclosure",
                    body = "Maximum clocks can increase heat and battery drain.",
                    confirmLabel = "Acknowledge",
                    dismissLabel = "Cancel",
                    severity = IronDialogSeverity.Warning,
                    onConfirm = { confirmed = true; visible = false },
                    onDismiss = { dismissed = true; visible = false },
                )
            }
        }
        composeRule.onNodeWithText("High-power disclosure").assertIsDisplayed()
        composeRule.onNodeWithText("CANCEL").assertIsDisplayed()
        composeRule.onNodeWithText("ACKNOWLEDGE").assertIsDisplayed()
        composeRule.onNodeWithText("ACKNOWLEDGE").performClick()
        assertTrue(confirmed)
        assertTrue(!dismissed)
    }
}
