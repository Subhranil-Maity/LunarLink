package com.subhranil.lunarexplorer.explorer.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.subhranil.lunarexplorer.formatInstant
import com.subhranil.lunarexplorer.models.Drive
import com.subhranil.lunarexplorer.models.LunarMetaData
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
@Composable
fun DirInfo(
    modifier: Modifier = Modifier,
    metaData: LunarMetaData
) {
    val (date, time) = formatInstant(metaData.lastModified)

    Column {
        HorizontalDivider(
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.secondary
        )
        Spacer(Modifier.height(5.dp))

        SmallText("Folder: ${metaData.name}")
        SmallText("Type: ${metaData.entryType}")
        SmallText("Last Modified: $date $time")
    }
}

@OptIn(ExperimentalTime::class)
@Composable
fun DriveInfo(modifier: Modifier = Modifier, drive: Drive, metaData: LunarMetaData) {
    val (date, time) = formatInstant(metaData.lastModified)
    Column {
        HorizontalDivider(
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.secondary
        )
        Spacer(Modifier.height(5.dp))
        SmallText("Name: ${drive.name}")
        SmallText("Disk Type: ${drive.diskType}")
        SmallText("FS Type: ${drive.fsType}")
        SmallText("Last Modified: $date $time")
    }
}

@Composable
fun SmallText(text: String, modifier: Modifier = Modifier) = Text(
    modifier = modifier,
    text = text,
    style = MaterialTheme.typography.bodySmall,
    overflow = TextOverflow.Ellipsis,
    softWrap = false
)

