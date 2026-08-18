package com.ivarna.apexcore.ui.tune

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ivarna.apexcore.tune.*
import com.ivarna.apexcore.ui.components.zenGlassBackground
import com.ivarna.apexcore.ui.theme.PlusJakartaSans
import com.ivarna.apexcore.ui.theme.ZenDimens

@Composable
fun TuneCategorySection(
    category: TuneCategory,
    specs: List<TuneSpec>,
    capabilities: Map<TuneId, TuneCapability>,
    intents: Map<TuneId, TuneValue>,
    enabled: Boolean,
    onIntentChange: (TuneId, TuneValue) -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(ZenDimens.roundedLg)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .zenGlassBackground(
                shape = shape,
                fill = scheme.surfaceContainerLow.copy(alpha = 0.9f),
                borderColor = scheme.outlineVariant.copy(alpha = 0.4f)
            )
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = category.displayName.uppercase(),
                color = scheme.primary,
                fontSize = 11.sp,
                fontFamily = PlusJakartaSans,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            val availableCount = specs.count { capabilities[it.id]?.available == true }
            val countText = if (availableCount > 0) "$availableCount/${specs.size} supported" else "Not supported"
            Text(
                text = countText,
                color = if (availableCount > 0) scheme.onSurfaceVariant else scheme.error.copy(alpha = 0.8f),
                fontSize = 10.sp,
                fontFamily = PlusJakartaSans,
                fontWeight = FontWeight.Medium
            )
        }

        specs.forEachIndexed { index, spec ->
            if (index > 0) {
                HorizontalDivider(
                    color = scheme.outlineVariant.copy(alpha = 0.2f),
                    thickness = 1.dp,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }
            val cap = capabilities[spec.id]
            val intent = intents[spec.id] ?: TuneValue(on = false, raw = null)
            TuneOptionRow(
                spec = spec,
                capability = cap,
                intent = intent,
                enabled = enabled,
                onIntentChange = { newIntent ->
                    onIntentChange(spec.id, newIntent)
                }
            )
        }
    }
}
