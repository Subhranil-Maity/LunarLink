package com.subhranil.lunarexplorer.explorer.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.subhranil.lunarexplorer.models.Drive

@Composable
fun LinearDrive(
    drive: Drive,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) {
    Row(
        modifier = modifier
//            .padding(8.dp, 2.dp)
            .fillMaxWidth()
            .clickable {
                onClick()
            }.height(60.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
//        Icon(
//            imageVector = ImageVector.vectorResource(id = R.drawable.storage_icon),
//            contentDescription = null,
//            tint = MaterialTheme.colorScheme.primary
//        )
        DriveIcon(label = drive.mountPoint)
        Spacer(modifier = Modifier.size(8.dp))
        Column {
            Text(text = drive.name, style = MaterialTheme.typography.titleMedium)
            LinearProgressIndicator(
                progress = {
                    (drive.usedSize.toDouble() / drive.totalSize.toDouble()).coerceIn(0.0, 1.0)
                        .toFloat()
                },
                modifier = Modifier.fillMaxWidth()
            )
        }


    }
}

@Composable
fun DriveIcon(
    label: String,
    backgroundColor: Color = MaterialTheme.colorScheme.primaryContainer,
    foregroundColor: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    size: Dp = 40.dp
) {
    Box(
        modifier = Modifier
            .size(size) // Adjust size as needed
            .clip(CircleShape)
            .background(backgroundColor), // Background color
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = foregroundColor,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}