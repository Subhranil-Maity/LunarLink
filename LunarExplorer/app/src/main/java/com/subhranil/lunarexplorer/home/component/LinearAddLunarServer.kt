package com.subhranil.lunarexplorer.home.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlin.math.min

@Preview
@Composable
fun LinearAddLunarServer(
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) {
    FadedPlusBox(modifier = modifier.clickable(onClick = onClick))
}

@Composable
fun FadedPlusBox(
    modifier: Modifier = Modifier,
    cornerRadiusDp: Int = 10
) {
    val shape = RoundedCornerShape(cornerRadiusDp.dp)
    val surface = MaterialTheme.colorScheme.surface
    val borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
    val primary = MaterialTheme.colorScheme.primary

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .height(70.dp)
            .border(BorderStroke(1.dp, borderColor), shape)
            .drawBehind {
                // soft radial fade/glow behind the icon
                val r = min(size.width, size.height) * 0.6f
                val center = Offset(size.width / 2f, size.height / 2f)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(primary.copy(alpha = 0.32f), Color.Transparent),
                        center = center,
                        radius = r
                    ),
                    radius = r,
                    center = center
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Rounded.Add,
            contentDescription = "Add",
            tint = primary
        )
    }
}
