package com.ivarna.apexcore.ui.iron

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ToolRow(
    title: String,
    subtitle: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    val clack = rememberClack()
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.985f else 1f, IronMotion.machined(), label = "row")
    val skin = ironSkin()
    val paper = LocalPaperSurfaces.current
    val plate = if (paper) Iron.Bone100 else Iron.Anvil800
    val iconBg = if (paper) {
        if (pressed) Iron.Bone50 else Iron.Bone100
    } else {
        if (pressed) Iron.Anvil950 else Iron.Anvil700
    }

    Row(
        modifier
            .fillMaxWidth()
            .height(64.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(IronShape.Plate)
            .background(plate)
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClick = {
                    clack.row()
                    onClick()
                },
                onLongClick = onLongClick?.let { cb ->
                    {
                        clack.longPress()
                        cb()
                    }
                },
                onDoubleClick = null,
            )
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(IronShape.Slot)
                .background(iconBg)
                .padding(8.dp),
            contentAlignment = Alignment.Center
        ) { icon() }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = IronType.Title.copy(fontSize = 16.sp, lineHeight = 20.sp), color = skin.text)
            Text(subtitle, style = IronType.Caption, color = skin.textDim)
        }
        trailing?.invoke() ?: ChevronGlyph(skin.textDim)
    }
}
