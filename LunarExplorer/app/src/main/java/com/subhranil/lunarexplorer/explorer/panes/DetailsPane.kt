package com.subhranil.lunarexplorer.explorer.panes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.subhranil.lunarexplorer.explorer.ExplorerActions
import com.subhranil.lunarexplorer.explorer.ExplorerState
import com.subhranil.lunarexplorer.explorer.components.LinearEntry
import com.subhranil.lunarexplorer.models.EntryType

@Composable
fun DetailsPane(
    modifier: Modifier = Modifier,
    state: ExplorerState,
    onAction: (ExplorerActions) -> Unit
) {
    if (state.isLoading) {
        Row(
            Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            CircularProgressIndicator()
        }
    } else {
        val scrollState = rememberScrollState()
        val lazyState = rememberLazyListState()
        if (!state.pathHistory.isEmpty()) {
//            Column(
//                modifier.verticalScroll(scrollState)
//            ) {
//                state.currentFolderContent.forEach {
//                    LinearEntry(metaData = it) {
//                        if (it.entryType == EntryType.DIRECTORY) {
//                            onAction(ExplorerActions.GoToPath(it.absolutePath))
//                        }
//                    }
//                }
//            }
            LazyColumn(
                modifier = Modifier,
                state = lazyState,
            ) {
                items(
                    items = state.currentFolderContent,
                    key = { it.absolutePath }
                ) {
                    LinearEntry(metaData = it) {
                        if (it.entryType == EntryType.DIRECTORY) {
                            onAction(ExplorerActions.GoToPath(it.absolutePath))
                        }
                    }
                }
            }
        }
    }
}