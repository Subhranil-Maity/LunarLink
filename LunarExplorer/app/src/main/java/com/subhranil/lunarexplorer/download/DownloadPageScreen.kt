package com.subhranil.lunarexplorer.download

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import com.subhranil.lunarexplorer.manager.DownloadManager
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DownloadPageScreen(
    modifier: Modifier = Modifier,
    downloadManager: DownloadManager = koinInject()
) {
    val pagerState = rememberPagerState(initialPage = 0) { 4 }
    val coroutineScope = rememberCoroutineScope()

    val pages = listOf(
        "Active" to @Composable { DownloadListScreen(tasks = downloadManager.getActiveTasks()) },
        "Queued" to @Composable{ DownloadListScreen(tasks = downloadManager.getQueuedTasks()) },
        "Completed" to @Composable{ DownloadListScreen(tasks = downloadManager.getCompletedTasks()) },
        "Failed" to @Composable{ DownloadListScreen(tasks = downloadManager.getFailedTasks()) }
    )

    Column(modifier = modifier.fillMaxSize()) {
        TabRow(
            selectedTabIndex = pagerState.currentPage,
            modifier = Modifier.fillMaxWidth()
        ) {
            pages.forEachIndexed { index, item ->
                Tab(
                    selected = pagerState.currentPage == index,
                    onClick = {
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(index)
                        }
                    },
                    text = { Text(item.first) }
                )
            }
        }
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f)
        ) { page ->
            pages[page].second()
        }
    }
}