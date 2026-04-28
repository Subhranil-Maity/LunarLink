package com.subhranil.lunarexplorer.download

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.subhranil.lunarexplorer.R
import com.subhranil.lunarexplorer.icons.getIcon
import com.subhranil.lunarexplorer.manager.DownloadManager
import com.subhranil.lunarexplorer.manager.task.DownloadTask
import org.koin.compose.koinInject


@Composable
fun DownloadTaskItem(
    task: DownloadTask,
    modifier: Modifier = Modifier
) {
    val taskState by task.state.collectAsState()

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 1. Progress Indicator and Icon
            Box(
                modifier = Modifier.size(48.dp),
                contentAlignment = Alignment.Center
            ) {
                when (val state = taskState) {
                    is DownloadTask.DownloadState.IN_PROGRESS -> {
                        val safeProgress = state.progress
                            .takeIf { it.isFinite() }
                            ?.coerceIn(0f, 1f)
                            ?: 0f
                        val animatedProgress by animateFloatAsState(
                            targetValue = safeProgress,
                            animationSpec = tween(durationMillis = 500, easing = LinearEasing)
                        )
                        CircularProgressIndicator(
                            progress = animatedProgress,
                            modifier = Modifier.size(48.dp),
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 4.dp
                        )
                    }
                    else -> {}
                }
                val (iconVector, tintColor) = getIcon(task.fileName)
                Icon(
                    imageVector = iconVector,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = tintColor
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            // 2. File Name and Status Text
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = task.fileName,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                when (val state = taskState) {
                    is DownloadTask.DownloadState.IN_PROGRESS -> {
                        val safeProgress = state.progress
                            .takeIf { it.isFinite() }
                            ?.coerceIn(0f, 1f)
                            ?: 0f
                        Text(
                            text = "Downloading • ${"%.0f".format(safeProgress * 100)}%",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                    }
                    is DownloadTask.DownloadState.PAUSED -> {
                        val safeProgress = state.pausedAt
                            .takeIf { it.isFinite() }
                            ?.coerceIn(0f, 1f)
                            ?: 0f
                        Text(
                            text = "Paused • ${"%.0f".format(safeProgress * 100)}%",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFFFA500)
                        )
                    }
                    is DownloadTask.DownloadState.PENDING -> {
                        Text(
                            text = "Pending...",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                    }
                    is DownloadTask.DownloadState.COMPLETED -> {
                        Text(
                            text = "Download complete",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Green
                        )
                    }
                    is DownloadTask.DownloadState.FAILED -> {
                        Text(
                            text = "Download failed",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    is DownloadTask.DownloadState.CANCELED -> {
                        Text(
                            text = "Download canceled",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Red
                        )
                    }
                }
            }

            // 3. Action Button
            Box(modifier = Modifier.size(40.dp)) {
                when (taskState) {
                    is DownloadTask.DownloadState.IN_PROGRESS -> {
                        IconButton(onClick = { task.pause() }) {
                            Icon(ImageVector.vectorResource(R.drawable.pause_icon), contentDescription = "Pause", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                    is DownloadTask.DownloadState.PAUSED -> {
                        IconButton(onClick = { task.resume() }) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "Resume", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                    DownloadTask.DownloadState.COMPLETED -> {
                        // Open file on click
                    }
                    is DownloadTask.DownloadState.FAILED, DownloadTask.DownloadState.CANCELED -> {
                        IconButton(onClick = { /* TODO: Retry logic */ }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Retry", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                    else -> {} // No action button for pending
                }
            }
        }
    }
}
