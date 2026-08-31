package com.ivarna.apexcore.ui.iron

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max
import kotlin.math.min

/**
 * Guards Iron semantic text/surface pairs against regressions below WCAG 4.5:1
 * for normal ~11–15sp status/body text.
 */
class IronContrastTest {

    private fun contrastRatio(a: Color, b: Color): Double {
        val l1 = a.luminance().toDouble()
        val l2 = b.luminance().toDouble()
        val lighter = max(l1, l2)
        val darker = min(l1, l2)
        return (lighter + 0.05) / (darker + 0.05)
    }

    private fun assertReadable(fg: Color, bg: Color, label: String, minRatio: Double = 4.5) {
        val ratio = contrastRatio(fg, bg)
        assertTrue(
            "$label contrast ${"%.2f".format(ratio)}:1 below $minRatio:1 (fg=$fg bg=$bg)",
            ratio >= minRatio,
        )
    }

    @Test
    fun graphitePrimaryAndSecondaryOnPlate() {
        val skin = IronSkin.Graphite
        assertReadable(skin.text, skin.plate, "Graphite primary text on plate")
        assertReadable(skin.textDim, skin.plate, "Graphite secondary text on plate")
    }

    @Test
    fun graphiteStatusOnPlate() {
        val skin = IronSkin.Graphite
        assertReadable(skin.dangerText(), skin.plate, "Graphite danger text on plate")
        assertReadable(skin.successText(), skin.plate, "Graphite success text on plate")
        assertReadable(skin.warningText(), skin.plate, "Graphite warning text on plate")
    }

    @Test
    fun vellumPrimaryAndSecondaryOnPaperPlate() {
        val skin = IronSkin.Vellum
        assertReadable(skin.text, skin.plate, "Vellum primary text on paper plate")
        assertReadable(skin.textDim, skin.plate, "Vellum secondary text on paper plate")
    }

    @Test
    fun vellumStatusOnPaperPlate() {
        val skin = IronSkin.Vellum
        assertReadable(skin.dangerText(), skin.plate, "Vellum danger text on paper plate")
        assertReadable(skin.warningText(), skin.plate, "Vellum warning text on paper plate")
        assertReadable(skin.successText(), skin.plate, "Vellum success text on paper plate")
    }

    @Test
    fun selectValueOnSelectSurface() {
        val g = IronSkin.Graphite
        assertReadable(g.text, g.inputSurface(), "Graphite select value on select surface")
        val v = IronSkin.Vellum
        assertReadable(v.text, v.inputSurface(), "Vellum select value on select surface")
    }

    @Test
    fun brightPaletteNotUsedAsVellumDanger() {
        // Signal500 / Ember500 fail ~4.5:1 on Bone100 — semantic helper must not return them for paper.
        val skin = IronSkin.Vellum
        assertTrue(skin.dangerText() != Iron.Signal500)
        assertTrue(skin.dangerText() != Iron.Ember500)
        assertReadable(skin.dangerText(), Iron.Bone100, "Vellum danger on Bone100")
    }
}
