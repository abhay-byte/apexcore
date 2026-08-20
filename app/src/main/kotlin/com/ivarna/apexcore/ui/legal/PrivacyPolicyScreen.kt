package com.ivarna.apexcore.ui.legal

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.material3.ColorScheme
import com.ivarna.apexcore.ui.components.zenFrostChild
import com.ivarna.apexcore.ui.theme.ZenDimens
import com.ivarna.apexcore.ui.theme.ZenType
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.haze

/**
 * Renders the bundled offline privacy policy as Markdown + inline/display LaTeX.
 * No WebView, no INTERNET permission — policy doc is bundled at build time.
 *
 * Parser is stdlib-only (regex + AnnotatedString). It handles:
 *  - headings   `#` / `##` / `###`
 *  - bullets    `* ` / `- `
 *  - inline     **bold**, *italic*, `code`, [label](url), tables (`| a | b |`)
 *  - LaTeX      `$inline$` and `$$display$$` → italic mono pill (not KaTeX layout)
 * ponytail: upgrade to WebView(KaTeX + marked) bundled offline for pixel-perfect
 * TeX when owner signs off; keeps offline and no INTERNET.
 */
@Composable
fun PrivacyPolicyScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    val rawLines = remember {
        try {
            context.assets.open("privacy_policy.md").bufferedReader().use { it.readText() }.lines()
        } catch (_: Throwable) {
            listOf("# Privacy Policy", "", "Privacy policy could not be loaded.")
        }
    }
    val scheme = MaterialTheme.colorScheme
    val hazeState = remember { HazeState() }

    val model = remember(rawLines, scheme) { PrivacyMarkdown.render(rawLines, scheme) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(scheme.surface)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .haze(hazeState)
                .padding(horizontal = ZenDimens.containerPadding)
        ) {
            item { Spacer(modifier = Modifier.windowInsetsTopHeight(WindowInsets.statusBars)) }
            item { Spacer(modifier = Modifier.height(ZenDimens.topBarClearance)) }
            item { Spacer(modifier = Modifier.height(ZenDimens.elementGap)) }

            items(model) { block ->
                when (block) {
                    is Md.Blank -> Spacer(modifier = Modifier.height(8.dp))
                    is Md.Heading -> when (block.level) {
                        1 -> Text(
                            text = block.text,
                            color = scheme.onSurface,
                            style = ZenType.display,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                        2 -> Text(
                            text = block.text,
                            color = scheme.onSurface,
                            style = ZenType.title,
                            modifier = Modifier.padding(top = 14.dp, bottom = 6.dp)
                        )
                        else -> Text(
                            text = block.text,
                            color = scheme.onSurface,
                            style = ZenType.titleSm,
                            modifier = Modifier.padding(top = 10.dp, bottom = 4.dp)
                        )
                    }
                    is Md.Para -> {
                        val style = ZenType.bodySm.copy(
                            color = if (block.bullet) scheme.onSurfaceVariant else scheme.onSurfaceVariant
                        )
                        // Bullet prefix baked into the AnnotatedString so link offsets shift uniformly.
                        val full = if (block.bullet) {
                            buildAnnotatedString {
                                append("•  ")
                                append(block.annotated)
                            }
                        } else {
                            block.annotated
                        }
                        if (block.links.isEmpty()) {
                            Text(
                                text = full,
                                style = style,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                        } else {
                            val uriHandler = LocalUriHandler.current
                            ClickableText(
                                text = full,
                                style = style,
                                onClick = { offset ->
                                    block.links[offset - if (block.bullet) 3 else 0]?.let { url ->
                                        uriHandler.openUri(url)
                                    }
                                },
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                        }
                    }
                    is Md.TableRow -> Text(
                        text = block.text,
                        color = scheme.onSurfaceVariant,
                        style = ZenType.bodySm.copy(
                            fontFamily = FontFamily.Monospace,
                            background = scheme.surfaceVariant
                        ),
                        maxLines = 1,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                    )
                    is Md.CodeBlock -> Text(
                        text = block.text,
                        color = scheme.onSurface,
                        style = ZenType.bodySm.copy(
                            fontFamily = FontFamily.Monospace,
                            background = scheme.surfaceVariant
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(ZenDimens.bottomNavClearance)) }
        }

        // Frosted Top App Bar (TuneScreen recipe)
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .zenFrostChild(hazeState, scheme.surface)
                .background(scheme.surface.copy(alpha = 0.85f))
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = scheme.onSurface
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Privacy Policy",
                        color = scheme.onSurface,
                        style = ZenType.title
                    )
                }
            }
        }
    }
}

/** Rendered markdown block model. */
internal sealed interface Md {
    data class Heading(val level: Int, val text: String) : Md
    data class Para(val annotated: AnnotatedString, val bullet: Boolean, val links: Map<Int, String>) : Md
    data class TableRow(val text: String) : Md
    data class CodeBlock(val text: String) : Md
    object Blank : Md
}

/** Parsed inline result: annotated string + link offsets (absolute into annotated). */
internal data class ParsedText(val annotated: AnnotatedString, val links: Map<Int, String>)

/** Pure, dependency-free Markdown/LaTeX subset parser — unit-testable without Compose runtime. */
internal object PrivacyMarkdown {

    private val displayMathRegex = Regex("""\$\$(.+?)\$\$""", RegexOption.DOT_MATCHES_ALL)
    private val linkRegex = Regex("""\[([^\]]+)\]\(([^)]+)\)""")
    private val boldRegex = Regex("""\*\*(.+?)\*\*""")
    private val italicRegex = Regex("""(?<!\*)\*(?!\*)(.+?)(?<!\*)\*(?!\*)""")
    private val codeRegex = Regex("""`([^`]+)`""")
    private val inlineMathRegex = Regex("""(?<!\$)\$(?!\$)(.+?)(?<!\$)\$(?!\$)""")
    private val tableRowRegex = Regex("""^\s*\|.+\|\s*$""")
    private val headingRegex = Regex("""^(#{1,4})\s+(.+)$""")

    private enum class Kind { DISPLAY_MATH, INLINE_MATH, LINK, BOLD, ITALIC, CODE }

    private data class Match(val start: Int, val end: Int, val kind: Kind, val content: String, val label: String, val url: String?)

    /** Renders raw asset lines into display blocks, tracking code fences across lines. */
    fun render(lines: List<String>, scheme: ColorScheme): List<Md> {
        val out = mutableListOf<Md>()
        var inCode = false
        val codeBuf = StringBuilder()
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.startsWith("```")) {
                if (inCode) {
                    out += Md.CodeBlock(codeBuf.toString().trimEnd())
                    codeBuf.setLength(0)
                    inCode = false
                } else {
                    inCode = true
                }
                continue
            }
            if (inCode) {
                codeBuf.appendLine(line)
                continue
            }
            if (trimmed.isBlank()) {
                out += Md.Blank
                continue
            }
            val heading = headingRegex.matchEntire(trimmed)
            if (heading != null) {
                out += Md.Heading(level = heading.groupValues[1].length, text = heading.groupValues[2].trim())
                continue
            }
            if (tableRowRegex.matches(trimmed)) {
                out += Md.TableRow(trimmed)
                continue
            }
            val bullet = trimmed.startsWith("* ") || trimmed.startsWith("- ")
            val content = if (bullet) trimmed.removePrefix("* ").removePrefix("- ").trim() else trimmed
            val parsed = parseInline(content, scheme)
            out += Md.Para(annotated = parsed.annotated, bullet = bullet, links = parsed.links)
        }
        if (inCode && codeBuf.isNotBlank()) out += Md.CodeBlock(codeBuf.toString().trim())
        return out
    }

    /**
     * Single-pass inline parser. Displays `$$…$$` and links take precedence, then
     * **bold**, *italic*, `code`, `$…$` — earliest match wins, ties by priority so
     * nested `**bold _x_**` stays well-formed.
     */
    fun parseInline(input: String, scheme: ColorScheme): ParsedText {
        val builder = AnnotatedString.Builder()
        val links = mutableMapOf<Int, String>()
        var pos = 0
        while (pos < input.length) {
            val m = nextMatch(input, pos)
            if (m == null) {
                builder.append(input.substring(pos))
                break
            }
            if (m.start > pos) builder.append(input.substring(pos, m.start))
            when (m.kind) {
                Kind.BOLD -> builder.withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { builder.append(m.content) }
                Kind.ITALIC -> builder.withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { builder.append(m.content) }
                Kind.CODE -> builder.withStyle(
                    SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        background = scheme.surfaceVariant
                    )
                ) { builder.append(m.content) }
                Kind.INLINE_MATH, Kind.DISPLAY_MATH -> builder.withStyle(
                    SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        fontStyle = FontStyle.Italic,
                        background = scheme.surfaceVariant
                    )
                ) { builder.append(m.content) }
                Kind.LINK -> {
                    val start = builder.length
                    builder.withStyle(
                        SpanStyle(
                            color = scheme.primary,
                            textDecoration = TextDecoration.Underline
                        )
                    ) {
                        builder.append(m.label)
                    }
                    for (i in start until builder.length) links[i] = m.url.orEmpty()
                }
            }
            pos = m.end
        }
        return ParsedText(builder.toAnnotatedString(), links)
    }

    private fun nextMatch(input: String, from: Int): Match? {
        var best: Match? = null
        fun consider(pattern: Regex, kind: Kind, build: (MatchResult) -> Match) {
            val r = pattern.find(input, from) ?: return
            val b = best
            if (b == null || r.range.first < b.start ||
                (r.range.first == b.start && priorityOf(kind) < priorityOf(b.kind))
            ) {
                best = build(r)
            }
        }
        consider(displayMathRegex, Kind.DISPLAY_MATH) {
            Match(it.range.first, it.range.last + 1, Kind.DISPLAY_MATH, it.groupValues[1], "", null)
        }
        consider(linkRegex, Kind.LINK) {
            Match(it.range.first, it.range.last + 1, Kind.LINK, "", it.groupValues[1], it.groupValues[2])
        }
        consider(boldRegex, Kind.BOLD) {
            Match(it.range.first, it.range.last + 1, Kind.BOLD, it.groupValues[1], "", null)
        }
        consider(italicRegex, Kind.ITALIC) {
            Match(it.range.first, it.range.last + 1, Kind.ITALIC, it.groupValues[1], "", null)
        }
        consider(codeRegex, Kind.CODE) {
            Match(it.range.first, it.range.last + 1, Kind.CODE, it.groupValues[1], "", null)
        }
        consider(inlineMathRegex, Kind.INLINE_MATH) {
            Match(it.range.first, it.range.last + 1, Kind.INLINE_MATH, it.groupValues[1], "", null)
        }
        return best
    }

    private fun priorityOf(kind: Kind): Int = when (kind) {
        Kind.DISPLAY_MATH -> 0
        Kind.LINK -> 1
        Kind.BOLD -> 2
        Kind.ITALIC -> 3
        Kind.CODE -> 4
        Kind.INLINE_MATH -> 5
    }
}