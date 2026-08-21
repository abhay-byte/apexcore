package com.ivarna.apexcore.ui.iron.legal

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ivarna.apexcore.ui.iron.*

sealed interface MdBlock {
    data class Heading(val level: Int, val text: String) : MdBlock
    data class Paragraph(val spans: List<MdSpan>) : MdBlock
    data class Bullet(val spans: List<MdSpan>) : MdBlock
    data class CodeBlock(val lines: List<String>) : MdBlock
    data class Table(val header: List<String>, val rows: List<List<String>>) : MdBlock
}

data class MdSpan(
    val text: String,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val code: Boolean = false,
    val linkLabel: String? = null,
    val linkUrl: String? = null,
)

@Composable
fun TheLedger(
    blocks: List<MdBlock>,
    onLink: (String) -> Unit,
    onBack: () -> Unit,
) {
    Box(Modifier.fillMaxSize().background(Iron.Bone50).ironGrain(0.05f)) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BackArrow(Iron.Ink900, onBack)
                Spacer(Modifier.width(8.dp))
                Text(
                    "THE LEDGER", style = IronType.Display.copy(fontSize = 20.sp), color = Iron.Ink900,
                    modifier = Modifier.weight(1f)
                )
                StampLabel("PRINTED OFFLINE · NO NETWORK", StampInk.Brass, slam = false)
            }
            HorizontalDivider(color = Iron.Ink600.copy(alpha = 0.3f), thickness = 1.dp)

            if (blocks.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Ledger unavailable.", style = IronType.Body, color = Iron.Ink600)
                }
            } else {
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp)) {
                    items(blocks.size) { i -> Block(blocks[i], onLink) }
                    item {
                        Text(
                            "PLATE 09 · LEDGER · PRINTED OFFLINE", style = IronType.MonoSm,
                            color = Iron.Ink600, modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Block(b: MdBlock, onLink: (String) -> Unit) {
    when (b) {
        is MdBlock.Heading -> {
            when (b.level) {
                1 -> Text(b.text, style = IronType.Display.copy(fontSize = 26.sp, color = Iron.Ink900))
                2 -> {
                    Text(b.text, style = IronType.Title.copy(fontSize = 18.sp, color = Iron.Ink900))
                    Spacer(Modifier.height(4.dp))
                    HorizontalDivider(color = Iron.Ink600.copy(alpha = 0.3f), thickness = 1.dp)
                }
                else -> Text(b.text, style = IronType.Label.copy(color = Iron.Ink900))
            }
            Spacer(Modifier.height(10.dp))
        }
        is MdBlock.Paragraph -> {
            MdSpanText(b.spans, onLink)
            Spacer(Modifier.height(10.dp))
        }
        is MdBlock.Bullet -> {
            Row(Modifier.fillMaxWidth()) {
                Canvas(Modifier.size(10.dp).padding(top = 6.dp)) {
                    drawLine(
                        Iron.Brass400, Offset(0f, this.size.height / 2f),
                        Offset(this.size.width, this.size.height / 2f), 3.dp.toPx()
                    )
                }
                Spacer(Modifier.width(10.dp))
                MdSpanText(b.spans, onLink, Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
        }
        is MdBlock.CodeBlock -> {
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(IronShape.Plate)
                    .background(Iron.Anvil800)
                    .ironGrain(0.05f)
                    .padding(12.dp)
            ) {
                Column {
                    b.lines.forEach {
                        Text(it, style = IronType.Mono.copy(fontSize = 12.sp, color = Iron.Bone300))
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
        is MdBlock.Table -> {
            Column(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth()) {
                    b.header.forEach {
                        Text(
                            it, style = IronType.MonoSm, color = Iron.Ink900,
                            modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold
                        )
                    }
                }
                HorizontalDivider(
                    Modifier.padding(vertical = 4.dp),
                    color = Iron.Ink600.copy(alpha = 0.3f), thickness = 1.dp
                )
                b.rows.forEach { row ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                        row.forEach {
                            Text(it, style = IronType.MonoSm, color = Iron.Ink600, modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun MdSpanText(spans: List<MdSpan>, onLink: (String) -> Unit, modifier: Modifier = Modifier) {
    val annotated = remember(spans) {
        buildAnnotatedString {
            spans.forEach { s ->
                val start = length
                withStyle(
                    SpanStyle(
                        fontWeight = if (s.bold) FontWeight.Bold else null,
                        fontStyle = if (s.italic) FontStyle.Italic else null,
                        fontFamily = if (s.code) PlexMono else null,
                        fontSize = if (s.code) 13.sp else TextUnit.Unspecified,
                        color = if (s.linkUrl != null) Iron.Signal700 else Color.Unspecified,
                        textDecoration = if (s.linkUrl != null) TextDecoration.Underline else null,
                        background = if (s.code) Iron.Bone300.copy(alpha = 0.45f) else Color.Unspecified,
                    )
                ) { append(s.text) }
                if (s.linkUrl != null) addStringAnnotation("url", s.linkUrl, start, length)
            }
        }
    }
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    Text(
        annotated, style = IronType.Body, color = Iron.Ink900, modifier = modifier
            .pointerInput(annotated) {
                detectTapGestures { pos ->
                    layout?.getOffsetForPosition(pos)?.let { off ->
                        annotated.getStringAnnotations("url", off, off)
                            .firstOrNull()?.let { onLink(it.item) }
                    }
                }
            },
        onTextLayout = { layout = it },
    )
}
