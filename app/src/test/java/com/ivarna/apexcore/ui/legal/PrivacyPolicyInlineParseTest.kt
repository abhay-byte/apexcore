package com.ivarna.apexcore.ui.legal

import com.ivarna.apexcore.ui.iron.legal.MdBlock
import com.ivarna.apexcore.ui.iron.legal.MdSpan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Unit coverage for Markdown/Ledger parsing. */
class PrivacyPolicyInlineParseTest {

    private fun parseSpans(text: String): List<MdSpan> {
        val spans = mutableListOf<MdSpan>()
        val regex = Regex("""(\*\*([^*]+)\*\*|\*([^*]+)\*|`([^`]+)`|\[([^\]]+)\]\(([^)]+)\))""")
        var lastIdx = 0
        regex.findAll(text).forEach { match ->
            val start = match.range.first
            val end = match.range.last + 1
            if (start > lastIdx) {
                spans.add(MdSpan(text.substring(lastIdx, start)))
            }
            val bold = match.groups[2]?.value
            val italic = match.groups[3]?.value
            val code = match.groups[4]?.value
            val linkLabel = match.groups[5]?.value
            val linkUrl = match.groups[6]?.value

            when {
                bold != null -> spans.add(MdSpan(bold, bold = true))
                italic != null -> spans.add(MdSpan(italic, italic = true))
                code != null -> spans.add(MdSpan(code, code = true))
                linkLabel != null && linkUrl != null -> spans.add(MdSpan(linkLabel, linkLabel = linkLabel, linkUrl = linkUrl))
                else -> spans.add(MdSpan(match.value))
            }
            lastIdx = end
        }
        if (lastIdx < text.length) {
            spans.add(MdSpan(text.substring(lastIdx)))
        }
        return if (spans.isEmpty()) listOf(MdSpan(text)) else spans
    }

    private fun parseMarkdownLines(lines: List<String>): List<MdBlock> {
        val blocks = mutableListOf<MdBlock>()
        var inCode = false
        val codeLines = mutableListOf<String>()
        val tableRows = mutableListOf<List<String>>()

        fun flushCode() {
            if (codeLines.isNotEmpty()) {
                blocks.add(MdBlock.CodeBlock(codeLines.toList()))
                codeLines.clear()
            }
        }

        fun flushTable() {
            if (tableRows.isNotEmpty()) {
                val header = tableRows.first()
                val rows = if (tableRows.size > 1) tableRows.drop(1) else emptyList()
                blocks.add(MdBlock.Table(header, rows))
                tableRows.clear()
            }
        }

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.startsWith("```")) {
                if (inCode) {
                    flushCode()
                    inCode = false
                } else {
                    flushTable()
                    inCode = true
                }
                continue
            }

            if (inCode) {
                codeLines.add(line)
                continue
            }

            if (trimmed.startsWith("|") && trimmed.endsWith("|")) {
                if (trimmed.replace("|", "").replace("-", "").replace(":", "").isBlank()) {
                    continue
                }
                val cols = trimmed.split("|").map { it.trim() }.filter { it.isNotEmpty() }
                tableRows.add(cols)
                continue
            } else {
                flushTable()
            }

            if (trimmed.isBlank()) {
                continue
            }

            if (trimmed.startsWith("#")) {
                val level = trimmed.takeWhile { it == '#' }.length
                val headingText = trimmed.drop(level).trim()
                blocks.add(MdBlock.Heading(level, headingText))
                continue
            }

            if (trimmed.startsWith("* ") || trimmed.startsWith("- ")) {
                val bulletText = trimmed.drop(2).trim()
                blocks.add(MdBlock.Bullet(parseSpans(bulletText)))
                continue
            }

            blocks.add(MdBlock.Paragraph(parseSpans(trimmed)))
        }
        flushCode()
        flushTable()
        return blocks
    }

    @Test
    fun bold_span() {
        val spans = parseSpans("**bold** text")
        assertEquals(2, spans.size)
        assertEquals("bold", spans[0].text)
        assertTrue(spans[0].bold)
        assertEquals(" text", spans[1].text)
    }

    @Test
    fun italic_span() {
        val spans = parseSpans("a *ital* b")
        assertEquals(3, spans.size)
        assertEquals("a ", spans[0].text)
        assertEquals("ital", spans[1].text)
        assertTrue(spans[1].italic)
        assertEquals(" b", spans[2].text)
    }

    @Test
    fun inline_code_span() {
        val spans = parseSpans("use `su` here")
        assertEquals(3, spans.size)
        assertEquals("use ", spans[0].text)
        assertEquals("su", spans[1].text)
        assertTrue(spans[1].code)
        assertEquals(" here", spans[2].text)
    }

    @Test
    fun link_span() {
        val spans = parseSpans("see [docs](https://example.com/a) now")
        assertEquals(3, spans.size)
        assertEquals("see ", spans[0].text)
        assertEquals("docs", spans[1].linkLabel)
        assertEquals("https://example.com/a", spans[1].linkUrl)
        assertEquals(" now", spans[2].text)
    }

    @Test
    fun render_heading_bullet_table() {
        val blocks = parseMarkdownLines(
            listOf("# Title", "", "## Section", "| a | b |", "|---|---|", "| 1 | 2 |", "- item", "plain")
        )
        assertEquals(5, blocks.size)
        assertEquals(MdBlock.Heading(1, "Title"), blocks[0])
        assertEquals(MdBlock.Heading(2, "Section"), blocks[1])
        assertTrue(blocks[2] is MdBlock.Table)
        val table = blocks[2] as MdBlock.Table
        assertEquals(listOf("a", "b"), table.header)
        assertEquals(listOf(listOf("1", "2")), table.rows)
        assertTrue(blocks[3] is MdBlock.Bullet)
        assertTrue(blocks[4] is MdBlock.Paragraph)
    }

    @Test
    fun render_code_fence_block() {
        val blocks = parseMarkdownLines(listOf("```", "adb shell", "echo hi", "```", "after"))
        assertEquals(2, blocks.size)
        assertTrue(blocks[0] is MdBlock.CodeBlock)
        val code = blocks[0] as MdBlock.CodeBlock
        assertEquals(listOf("adb shell", "echo hi"), code.lines)
        assertTrue(blocks[1] is MdBlock.Paragraph)
    }
}