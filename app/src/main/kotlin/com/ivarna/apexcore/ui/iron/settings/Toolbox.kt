package com.ivarna.apexcore.ui.iron.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ivarna.apexcore.ui.iron.*

/* ═══ §7.7 THE TOOLBOX ═══════════════════════════════════════════════ */

data class RunningModeUi(
    val backend: String,
    val preferred: String,
    val fpsPrivilege: String,
    val gpuVendor: String,
)

data class DiagnosticUi(
    val name: String,
    val statusLine: String,
    val led: LedState,
    val actionLabel: String? = null,
    val action: (() -> Unit)? = null,
    val probing: Boolean = false,
)

@Composable
fun Toolbox(
    themeMode: ThemeMode,
    onThemeMode: (ThemeMode) -> Unit,
    paperInserts: Boolean,
    onPaperInserts: (Boolean) -> Unit,
    mechanicalMotion: String = "auto",
    onMechanicalMotion: (String) -> Unit = {},
    runningMode: RunningModeUi,
    diagnostics: List<DiagnosticUi>,
    versionName: String,
    onPrivacy: () -> Unit,
    onTour: () -> Unit,
) {
    val serial = rememberSerial()
    val skin = ironSkin()

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(12.dp))
        Text("TOOLBOX", style = IronType.Display.copy(fontSize = 26.sp), color = skin.text)
        Text("Appearance, access, and about", style = IronType.Caption, color = skin.textDim)
        Spacer(Modifier.height(8.dp))

        // ── APPEARANCE
        SectionHeader("APPEARANCE")
        IronSurface {
            Text("THEME", style = IronType.Label, color = skin.text)
            Text("Match the system, or pick a finish", style = IronType.Caption, color = skin.textDim)
            Spacer(Modifier.height(10.dp))
            MachinedSegment(listOf("SYSTEM", "VELLUM", "GRAPHITE"), themeMode.ordinal, onSelect = { i ->
                onThemeMode(ThemeMode.entries[i])
            })
        }
        Spacer(Modifier.height(12.dp))
        IronSurface {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("PAPER INSERTS", style = IronType.Label, color = skin.text)
                    Text("Bone paper surfaces in Graphite mode", style = IronType.Caption, color = skin.textDim)
                }
                MachinedToggle(paperInserts, onPaperInserts)
            }
        }
        Spacer(Modifier.height(12.dp))
        // ── MECHANICAL MOTION — spec §4.4: FULL / REDUCED only; auto never shown
        IronSurface {
            Text("MECHANICAL MOTION", style = IronType.Label, color = skin.text)
            Text(
                if (mechanicalMotion == "reduced") "Reduced — dials hold still"
                else "Full — every gauge moves",
                style = IronType.Caption, color = skin.textDim
            )
            Spacer(Modifier.height(10.dp))
            MachinedSegment(
                listOf("FULL", "REDUCED"),
                if (mechanicalMotion == "reduced") 1 else 0,
                onSelect = { i -> onMechanicalMotion(if (i == 1) "reduced" else "full") }
            )
        }

        // ── ACCESS
        SectionHeader("ACCESS")
        IronSurface {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LedDot(LedState.READY)
                Spacer(Modifier.width(8.dp))
                Text(runningMode.backend, style = IronType.Mono, color = skin.text)
            }
            Spacer(Modifier.height(10.dp))
            KeyValue("PREFERRED", runningMode.preferred)
            KeyValue("FPS PRIVILEGE", runningMode.fpsPrivilege)
            KeyValue("GPU", runningMode.gpuVendor)
            KeyValue("S/N", serial)
        }
        Spacer(Modifier.height(12.dp))
        EngravedText("ACCESS DIAGNOSTICS", IronType.Label, color = skin.textDim)
        Spacer(Modifier.height(6.dp))
        diagnostics.forEach { d ->
            LedRow(d)
            Spacer(Modifier.height(8.dp))
        }

        // ── LEGAL
        SectionHeader("LEGAL")
        ToolRow(
            "Privacy Policy",
            "How ApexCore handles your data — the Ledger",
            { RailGlyph(skin.textDim) },
            onPrivacy
        )

        // ── ABOUT
        SectionHeader("ABOUT")
        IronSurface {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "APEXCORE",
                    style = IronType.Title.copy(fontSize = 18.sp),
                    color = skin.text,
                    modifier = Modifier.weight(1f)
                )
                Text("MK·II", style = IronType.MonoSm, color = Iron.Brass400)
            }
            Spacer(Modifier.height(8.dp))
            KeyValue("VERSION", versionName)
            KeyValue("SERIAL", serial)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StampLabel("NO ADS", StampInk.Phosphor, slam = false)
                StampLabel("NO TRACKING", StampInk.Phosphor, slam = false)
            }
            Text(
                "MACHINED IN 1.2 MB",
                style = IronType.MonoSm,
                color = skin.textDim,
                modifier = Modifier.padding(top = 10.dp)
            )
        }
        Spacer(Modifier.height(12.dp))
        ToolRow(
            "App Tour",
            "Replay the Manual & access configuration",
            { CartridgeGlyph(skin.textDim) },
            onTour
        )

        SerialFooter(6, "TOOLBOX", serial)
    }
}

@Composable
private fun SectionHeader(title: String) {
    Row(
        Modifier.padding(top = 20.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        EngravedText(title, IronType.Label, color = ironSkin().textDim)
        Spacer(Modifier.width(10.dp))
        HorizontalDivider(Modifier.weight(1f), color = ironSkin().hairline, thickness = 1.dp)
    }
}

@Composable
private fun KeyValue(k: String, v: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(k, style = IronType.MonoSm, color = ironSkin().textDim)
        Text(v, style = IronType.MonoSm, color = ironSkin().text)
    }
}

@Composable
private fun LedRow(d: DiagnosticUi) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(60.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LedDot(d.led)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(d.name, style = IronType.Label, color = ironSkin().text)
            Text(d.statusLine, style = IronType.MonoSm, color = ironSkin().textDim)
        }
        if (d.probing) {
            LoadingNeedle()
        } else d.actionLabel?.let { l ->
            ChamferButton(
                l,
                { d.action?.invoke() },
                tall = false,
                variant = ChamferVariant.Outline
            )
        }
    }
}
