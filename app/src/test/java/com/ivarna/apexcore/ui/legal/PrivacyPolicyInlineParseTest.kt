package com.ivarna.apexcore.ui.legal

import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Unit coverage for the stdlib Markdown/LaTeX subset parser (PrivacyPolicyScreen). */
class PrivacyPolicyInlineParseTest {

    private val scheme = lightColorScheme()

    @Test
    fun bold_span() {
        val p = PrivacyMarkdown.parseInline("**bold** text", scheme)
        assertEquals("bold text", p.annotated.text)
        val bold = p.annotated.spanStyles.filter { it.item.fontWeight == FontWeight.Bold }
        assertEquals(1, bold.size)
        assertEquals(0, bold[0].start)
        assertEquals(4, bold[0].end)
    }

    @Test
    fun italic_span() {
        val p = PrivacyMarkdown.parseInline("a *ital* b", scheme)
        assertEquals("a ital b", p.annotated.text)
        val ital = p.annotated.spanStyles.filter { it.item.fontStyle == FontStyle.Italic }
        assertEquals(1, ital.size)
        assertEquals(2, ital[0].start)
        assertEquals(6, ital[0].end)
    }

    @Test
    fun inline_code_span() {
        val p = PrivacyMarkdown.parseInline("use `su` here", scheme)
        assertEquals("use su here", p.annotated.text)
        val code = p.annotated.spanStyles.filter { it.item.fontFamily != null }
        assertEquals(1, code.size)
        assertEquals(4, code[0].start)
        assertEquals(6, code[0].end)
    }

    @Test
    fun inline_math_span() {
        val p = PrivacyMarkdown.parseInline("mass \$E=mc^2\$ energy", scheme)
        assertEquals("mass E=mc^2 energy", p.annotated.text)
        val math = p.annotated.spanStyles.filter { it.item.fontStyle == FontStyle.Italic }
        assertEquals(1, math.size)
        assertEquals(5, math[0].start)
        assertEquals(11, math[0].end)
    }

    @Test
    fun display_math_span() {
        val p = PrivacyMarkdown.parseInline("Formula:\n\$\$x = \\frac{-b \\pm \\sqrt{b^2-4ac}}{2a}\$\$\nEnd", scheme)
        assertTrue(p.annotated.text.contains("x = \\frac{-b \\pm \\sqrt{b^2-4ac}}{2a}"))
        val math = p.annotated.spanStyles.filter { it.item.fontStyle == FontStyle.Italic }
        assertEquals(1, math.size)
    }

    @Test
    fun link_offsets_and_url() {
        val p = PrivacyMarkdown.parseInline("see [docs](https://example.com/a) now", scheme)
        assertEquals("see docs now", p.annotated.text)
        // links maps every offset of the label to its URL — distinct URLs count is what matters.
        assertEquals(setOf("https://example.com/a"), p.links.values.toSet())
        assertEquals(4, p.links.size)
        val underline = p.annotated.spanStyles.filter { it.item.textDecoration == TextDecoration.Underline }
        assertEquals(1, underline.size)
        assertEquals(4, underline[0].start)
        assertEquals(8, underline[0].end)
    }

    @Test
    fun mixed_inline_order() {
        val p = PrivacyMarkdown.parseInline("**B** and `c` and \$m\$", scheme)
        assertEquals("B and c and m", p.annotated.text)
        assertEquals(3, p.annotated.spanStyles.size)
        val bold = p.annotated.spanStyles.first { it.item.fontWeight == FontWeight.Bold }
        assertEquals(0, bold.start)
    }

    @Test
    fun render_heading_bullet_table_blank() {
        val blocks = PrivacyMarkdown.render(
            listOf("# Title", "", "## Section", "| a | b |", "- item", "plain"),
            scheme
        )
        assertEquals(6, blocks.size)
        assertEquals(Md.Heading(1, "Title"), blocks[0])
        assertEquals(Md.Blank, blocks[1])
        assertEquals(Md.Heading(2, "Section"), blocks[2])
        assertTrue(blocks[3] is Md.TableRow)
        val bullet = blocks[4] as Md.Para
        assertTrue(bullet.bullet)
        assertEquals("item", bullet.annotated.text)
        val plain = blocks[5] as Md.Para
        assertTrue(!plain.bullet)
    }

    @Test
    fun render_code_fence_block() {
        val blocks = PrivacyMarkdown.render(listOf("```", "adb shell", "echo hi", "```", "after"), scheme)
        assertEquals(2, blocks.size)
        val code = blocks[0] as Md.CodeBlock
        assertTrue(code.text.contains("adb shell"))
        assertTrue(blocks[1] is Md.Para)
    }

    @Test
    fun private_wording_no_repo() {
        val raw = PrivacyMarkdown.render(
            listOf("**Developer:** ApexCore (private repository). Contact via in-app support or the store listing."),
            scheme
        )
        val para = raw.single() as Md.Para
        assertTrue(para.annotated.text.contains("private repository"))
        // No live links → no url map
        assertEquals(0, para.links.size)
    }
}