package com.ivarna.apexcore.ui.shell

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ivarna.apexcore.R
import com.ivarna.apexcore.ui.components.zenFrostChild
import com.ivarna.apexcore.ui.theme.PlusJakartaSans
import dev.chrisbanes.haze.HazeState

/**
 * App shell top bar: logo + "Apex Core" title + backend chip.
 *
 * Frost strip runs edge-to-edge under the **status/notification bar** as well,
 * using the same [zenFrostChild] recipe as [ZenBottomNav].
 */
@Composable
fun ZenTopBar(
    backendChip: @Composable () -> Unit,
    hazeState: HazeState,
    modifier: Modifier = Modifier
) {
    val surface = MaterialTheme.colorScheme.surface

    Column(
        modifier = modifier
            .fillMaxWidth()
            // Full strip including status-bar region is frosted
            .zenFrostChild(hazeState = hazeState, surface = surface)
    ) {
        // Push chrome below system icons; frost still covers this spacer
        Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(id = R.drawable.ic_app_logo),
                    contentDescription = "Apex Core",
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(8.dp))
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Apex Core",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 18.sp,
                    fontFamily = PlusJakartaSans,
                    fontWeight = FontWeight.Light
                )
            }
            backendChip()
        }
    }
}
