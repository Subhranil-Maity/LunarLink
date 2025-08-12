package com.subhranil.lunarexplorer.explorer.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.subhranil.lunarexplorer.icons.getIcon
import com.subhranil.lunarexplorer.models.EntryType
import com.subhranil.lunarexplorer.models.LunarMetaData
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlin.time.toJavaInstant

@OptIn(ExperimentalTime::class)
@Composable
fun LinearEntry(
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
    onLongPress: () -> Unit = {},
    metaData: LunarMetaData,
) {
    val (icon, color) = getIcon(metaData)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
//            .clickable { onClick() }
            .combinedClickable(
                onClick = {
                    onClick()
                },
                onLongClick = {
                    onLongPress()
                }
            )
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Icon
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier
                .size(40.dp)
                .padding(end = 12.dp)
        )

        // Name + Size
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 8.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = metaData.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (metaData.entryType != EntryType.DIRECTORY) {
                Text(
                    text = bytesToHumanReadable(metaData.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Last modified (right-aligned)
        val (date, time) = formatInstant(metaData.lastModified)

        Column(
            modifier = Modifier,
            horizontalAlignment = Alignment.End
        ) {
            Text(
                text = date,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = time,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

    }
}



@OptIn(ExperimentalTime::class)
@Composable
fun formatInstant(instant: Instant): Pair<String, String> {
    val dateFormatter = remember {
        DateTimeFormatter.ofPattern("yyyy-MM-dd")
            .withZone(ZoneId.systemDefault())
    }
    val timeFormatter = remember {
        DateTimeFormatter.ofPattern("HH:mm")
            .withZone(ZoneId.systemDefault())
    }

    val javaInstant = instant.toJavaInstant()
    val dateStr = dateFormatter.format(javaInstant)
    val timeStr = timeFormatter.format(javaInstant)

    return dateStr to timeStr
}


fun bytesToHumanReadable(bytes: Long): String {
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var bytes = bytes
    var unit = 0
    while (bytes > 1024) {
        bytes /= 1024
        unit++
    }
    return "$bytes ${units[unit]}"
}