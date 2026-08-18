package com.ivarna.apexcore.ui.tune

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ivarna.apexcore.tune.*
import com.ivarna.apexcore.ui.theme.PlusJakartaSans
import com.ivarna.apexcore.ui.theme.ZenDimens

@Composable
fun TuneOptionRow(
    spec: TuneSpec,
    capability: TuneCapability?,
    intent: TuneValue,
    enabled: Boolean,
    onIntentChange: (TuneValue) -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val isAvailable = capability?.available == true
    val isRowInteractive = enabled && isAvailable
    val alpha = if (isRowInteractive) 1f else 0.45f

    var showDropdown by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .alpha(alpha)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                Text(
                    text = spec.title,
                    color = scheme.onSurface,
                    fontSize = 14.sp,
                    fontFamily = PlusJakartaSans,
                    fontWeight = FontWeight.SemiBold
                )
                val subtitleText = capability?.subtitle ?: spec.description
                Text(
                    text = subtitleText,
                    color = scheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    fontFamily = PlusJakartaSans,
                    lineHeight = 14.sp,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }

            when (spec.kind) {
                TuneControlKind.SWITCH -> {
                    Switch(
                        checked = intent.on && isAvailable,
                        enabled = isRowInteractive,
                        onCheckedChange = { isChecked ->
                            onIntentChange(TuneValue(on = isChecked, raw = intent.raw))
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = scheme.onPrimary,
                            checkedTrackColor = scheme.primary,
                            uncheckedThumbColor = scheme.outline,
                            uncheckedTrackColor = scheme.surfaceContainerHighest
                        )
                    )
                }
                TuneControlKind.SLIDER -> {
                    Switch(
                        checked = intent.on && isAvailable,
                        enabled = isRowInteractive,
                        onCheckedChange = { isChecked ->
                            val defaultRaw = spec.defaultVal ?: spec.slider?.start?.toString() ?: "0"
                            onIntentChange(TuneValue(on = isChecked, raw = intent.raw ?: defaultRaw))
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = scheme.onPrimary,
                            checkedTrackColor = scheme.primary,
                            uncheckedThumbColor = scheme.outline,
                            uncheckedTrackColor = scheme.surfaceContainerHighest
                        )
                    )
                }
                TuneControlKind.ENUM -> {
                    Switch(
                        checked = intent.on && isAvailable,
                        enabled = isRowInteractive,
                        onCheckedChange = { isChecked ->
                            val defaultOption = capability?.availableOptions?.firstOrNull() ?: spec.defaultVal ?: "Default"
                            onIntentChange(TuneValue(on = isChecked, raw = intent.raw ?: defaultOption))
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = scheme.onPrimary,
                            checkedTrackColor = scheme.primary,
                            uncheckedThumbColor = scheme.outline,
                            uncheckedTrackColor = scheme.surfaceContainerHighest
                        )
                    )
                }
            }
        }

        // Expanded control for SLIDER
        if (spec.kind == TuneControlKind.SLIDER && intent.on && isAvailable) {
            val range = spec.slider ?: 0..100
            val currentVal = intent.raw?.toIntOrNull() ?: spec.defaultVal?.toIntOrNull() ?: range.first
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, start = 4.dp, end = 4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Value: $currentVal",
                        color = scheme.primary,
                        fontSize = 12.sp,
                        fontFamily = PlusJakartaSans,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Range: ${range.first} – ${range.last}",
                        color = scheme.onSurfaceVariant,
                        fontSize = 10.sp,
                        fontFamily = PlusJakartaSans
                    )
                }
                Slider(
                    value = currentVal.toFloat(),
                    onValueChange = { newVal ->
                        onIntentChange(TuneValue(on = true, raw = newVal.toInt().toString()))
                    },
                    valueRange = range.first.toFloat()..range.last.toFloat(),
                    steps = if (range.last - range.first in 1..20) range.last - range.first - 1 else 0,
                    enabled = isRowInteractive,
                    colors = SliderDefaults.colors(
                        thumbColor = scheme.primary,
                        activeTrackColor = scheme.primary,
                        inactiveTrackColor = scheme.surfaceContainerHighest
                    )
                )
            }
        }

        // Expanded control for ENUM
        if (spec.kind == TuneControlKind.ENUM && intent.on && isAvailable) {
            val options = capability?.availableOptions?.takeIf { it.isNotEmpty() }
                ?: listOfNotNull(spec.defaultVal, "Default").distinct()
            val selectedOption = intent.raw ?: options.firstOrNull() ?: "Default"

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            ) {
                OutlinedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = isRowInteractive) { showDropdown = true },
                    shape = RoundedCornerShape(ZenDimens.roundedSm)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = selectedOption,
                            color = scheme.primary,
                            fontSize = 12.sp,
                            fontFamily = PlusJakartaSans,
                            fontWeight = FontWeight.SemiBold
                        )
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                            tint = scheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                DropdownMenu(
                    expanded = showDropdown,
                    onDismissRequest = { showDropdown = false }
                ) {
                    for (opt in options) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = opt,
                                    color = if (opt == selectedOption) scheme.primary else scheme.onSurface,
                                    fontSize = 12.sp,
                                    fontFamily = PlusJakartaSans,
                                    fontWeight = if (opt == selectedOption) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            onClick = {
                                onIntentChange(TuneValue(on = true, raw = opt))
                                showDropdown = false
                            }
                        )
                    }
                }
            }
        }
    }
}
