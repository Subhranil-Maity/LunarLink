package com.subhranil.lunarexplorer.download

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.subhranil.lunarexplorer.manager.task.DownloadTask


import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import com.subhranil.lunarexplorer.R
import com.subhranil.lunarexplorer.icons.getIcon
import com.subhranil.lunarexplorer.manager.DownloadManager
import com.subhranil.lunarexplorer.models.EntryType
import kotlinx.coroutines.launch
import org.koin.compose.koinInject



@Composable
fun DownloadListScreen(
    tasks: SnapshotStateList<DownloadTask>, // The stateful list
    modifier: Modifier = Modifier
) {
    if (tasks.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "No downloads in this category.",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.Gray
            )
        }
    } else {
        LazyColumn(
            modifier = modifier.fillMaxSize().padding(8.dp),
        ) {
            items(tasks, key = { it.id }) { task ->
                DownloadTaskItem(task = task)
            }
        }
    }
}
