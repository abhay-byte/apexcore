package com.ivarna.apexcore.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ivarna.apexcore.ui.theme.ZenDimens

/**
 * Solid glass card surface (normative v1 — no RenderEffect).
 */
@Composable
fun GlassCard(
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(ZenDimens.roundedLg)
    val fill = MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.92f)
    val borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .zenGlassBackground(
                shape = shape,
                fill = fill,
                borderColor = borderColor
            )
            .then(
                if (onClick != null) {
                    Modifier.clickable(onClick = onClick)
                } else {
                    Modifier
                }
            )
            .padding(ZenDimens.elementGap),
        content = content
    )
}
