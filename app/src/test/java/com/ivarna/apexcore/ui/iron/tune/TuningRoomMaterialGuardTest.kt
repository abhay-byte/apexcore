package com.ivarna.apexcore.ui.iron.tune

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Source-level guard: T13 Tuning Room / MainScreen must not call raw unthemed Material controls.
 */
class TuningRoomMaterialGuardTest {

    private fun readSrc(relative: String): String {
        val candidates = listOf(
            File("src/main/kotlin/$relative"),
            File("app/src/main/kotlin/$relative"),
        )
        val f = candidates.firstOrNull { it.exists() }
            ?: error("Missing source $relative (cwd=${File(".").absolutePath})")
        return f.readText()
    }

    @Test
    fun tuningRoomUsesIronControlsNotRawMaterial() {
        val src = readSrc("com/ivarna/apexcore/ui/iron/tune/TuningRoom.kt")
        assertFalse("TuningRoom must not use raw AlertDialog", src.contains("AlertDialog"))
        assertFalse(
            "TuningRoom must not use raw material3.DropdownMenu",
            src.contains("material3.DropdownMenu") || src.contains("androidx.compose.material3.DropdownMenu"),
        )
        assertFalse(
            "TuningRoom must not use raw material3.DropdownMenuItem",
            src.contains("material3.DropdownMenuItem") || src.contains("androidx.compose.material3.DropdownMenuItem"),
        )
        assertFalse(
            "TuningRoom must not use raw material3.Slider",
            src.contains("material3.Slider") || src.contains("androidx.compose.material3.Slider"),
        )
        assertTrue("TuningRoom must use IronSlider", src.contains("IronSlider"))

        val sliderSrc = readSrc("com/ivarna/apexcore/ui/iron/IronSlider.kt")
        assertFalse(
            "IronSlider must not import Material3 Slider",
            Regex("""import\s+androidx\.compose\.material3\.Slider\b""").containsMatchIn(sliderSrc),
        )
        assertFalse("IronSlider must not use SliderDefaults", sliderSrc.contains("SliderDefaults"))
        assertTrue("IronSlider must draw a Canvas ruler", sliderSrc.contains("Canvas"))
        assertTrue("IronSlider must use pointerInput gestures", sliderSrc.contains("pointerInput"))
        assertTrue("IronSlider must draw ruler ticks", sliderSrc.contains("majorTick"))
        assertTrue("TuningRoom must use IronSelectField or IronDropdownMenu", src.contains("IronSelectField") || src.contains("IronDropdownMenu"))
        assertTrue("TuningRoom must use IronSurface for picker", src.contains("IronSurface"))
        assertFalse("TuneRow must not alpha whole unavailable rows", src.contains("Modifier.alpha(0.55f)"))
    }

    @Test
    fun mainScreenUsesIronConfirmDialog() {
        val src = readSrc("com/ivarna/apexcore/ui/shell/MainScreen.kt")
        assertTrue("MainScreen must use IronConfirmDialog", src.contains("IronConfirmDialog"))
        assertFalse(
            "MainScreen high-power disclosure must not use AlertDialog",
            src.contains("AlertDialog"),
        )
    }
}
