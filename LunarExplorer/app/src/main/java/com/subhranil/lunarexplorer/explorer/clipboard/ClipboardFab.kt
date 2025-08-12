package com.subhranil.lunarexplorer.explorer.clipboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import com.subhranil.lunarexplorer.R
import com.subhranil.lunarexplorer.explorer.ClipboardActionType
import com.subhranil.lunarexplorer.explorer.ClipboardState

@Composable
fun ClipboardFab(
    clipboard: ClipboardState?,
    isLarge: Boolean,
    onPasteClick: () -> Unit
) {
    if (clipboard != null && clipboard.items.isNotEmpty()) {
        Box(
            contentAlignment = Alignment.BottomEnd,
            modifier = Modifier.fillMaxSize().padding(16.dp)
        ) {
            // Ghost preview stack
            ClipboardPreview(clipboard)

            // Actual FAB
            FloatingActionButton(
                onClick = onPasteClick,
                modifier = Modifier
                    .size(if (isLarge) 72.dp else 56.dp) // adapt size
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val icon = when (clipboard.actionType) {
                        ClipboardActionType.COPY -> ImageVector.vectorResource(R.drawable.copy_icon)
                        ClipboardActionType.MOVE -> ImageVector.vectorResource(R.drawable.move_folder_icon)
                    }
                    Icon(icon, contentDescription = clipboard.actionType.name)
                    if (isLarge) {
                        Text("Paste")
                    }
                }
            }
        }
    }
}

@Composable
private fun ClipboardPreview(clipboard: ClipboardState) {
    val itemsToShow = clipboard.items.take(3).map { it.absolutePath }
    val moreCount = clipboard.items.size - itemsToShow.size

    Column(
        horizontalAlignment = Alignment.End,
        modifier = Modifier.padding(bottom = 80.dp, end = 8.dp) // above FAB
    ) {
        itemsToShow.forEachIndexed { index, item ->
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .offset(y = (-index * 8).dp) // stack effect
                    .shadow(2.dp, RoundedCornerShape(8.dp))
            ) {
                Text(
                    text = item.substringAfterLast("/"),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
        if (moreCount > 0) {
            Text(
                text = "+$moreCount more",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(top = 4.dp)
                    .offset(y = (-itemsToShow.size * 8).dp)
            )
        }
    }
}
