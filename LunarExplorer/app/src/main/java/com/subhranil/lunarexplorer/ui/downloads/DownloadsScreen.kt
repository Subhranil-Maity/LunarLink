package com.subhranil.lunarexplorer.ui.downloads

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.text.format.DateUtils
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.subhranil.lunarexplorer.R
import com.subhranil.lunarexplorer.data.api.Entry
import com.subhranil.lunarexplorer.data.api.EntryKind
import com.subhranil.lunarexplorer.data.download.DownloadFailure
import com.subhranil.lunarexplorer.data.download.DownloadState
import com.subhranil.lunarexplorer.ui.UiText
import com.subhranil.lunarexplorer.ui.asString
import com.subhranil.lunarexplorer.ui.components.EntryIcon
import com.subhranil.lunarexplorer.ui.components.MessageView
import com.subhranil.lunarexplorer.ui.toUiText
import com.subhranil.lunarexplorer.ui.uiText
import com.subhranil.lunarexplorer.util.formatBytes
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(viewModel: DownloadsViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val openFailed = stringResource(R.string.downloads_open_failed)
    var menu by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.downloads_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    if (state.active.isNotEmpty() || state.completed.isNotEmpty()) {
                        Box {
                            IconButton(onClick = { menu = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.action_more))
                            }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                if (state.active.isNotEmpty()) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.downloads_pause_all)) },
                                        onClick = { menu = false; viewModel.pauseAll() },
                                    )
                                }
                                if (state.completed.isNotEmpty()) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.downloads_clear_finished)) },
                                        onClick = { menu = false; viewModel.clearCompleted() },
                                    )
                                }
                            }
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        val modifier = Modifier
            .padding(padding)
            .fillMaxSize()
        when {
            state.isLoading -> Box(modifier, contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            state.isEmpty -> MessageView(
                title = stringResource(R.string.downloads_empty_title),
                body = stringResource(R.string.downloads_empty_body),
                icon = painterResource(R.drawable.download_icon),
                modifier = modifier,
            )
            else -> LazyColumn(modifier, contentPadding = PaddingValues(bottom = 24.dp)) {
                val open: (DownloadItem) -> Unit = { item ->
                    if (!openFile(context, item)) scope.launch { snackbar.showSnackbar(openFailed) }
                }
                section(R.string.downloads_section_active, state.active, viewModel, open)
                section(R.string.downloads_section_paused, state.paused, viewModel, open)
                section(R.string.downloads_section_failed, state.failed, viewModel, open)
                section(R.string.downloads_section_completed, state.completed, viewModel, open)
            }
        }
    }
}

private fun LazyListScope.section(
    title: Int,
    items: List<DownloadItem>,
    viewModel: DownloadsViewModel,
    onOpen: (DownloadItem) -> Unit,
) {
    if (items.isEmpty()) return
    item(key = "header-$title") {
        Text(
            stringResource(title),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
        )
    }
    items(items, key = DownloadItem::id) { item ->
        DownloadRow(item, viewModel, onOpen, Modifier.animateItem())
    }
}

@Composable
private fun DownloadRow(
    item: DownloadItem,
    viewModel: DownloadsViewModel,
    onOpen: (DownloadItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val record = item.record
    val completed = record.state == DownloadState.COMPLETED
    ListItem(
        modifier = modifier.then(if (completed) Modifier.clickable { onOpen(item) } else Modifier),
        leadingContent = { EntryIcon(Entry(record.name, record.remotePath, EntryKind.FILE)) },
        headlineContent = { Text(record.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Column {
                Text(statusText(item), maxLines = 2, overflow = TextOverflow.Ellipsis)
                when (record.state) {
                    DownloadState.RUNNING -> LinearProgressIndicator(
                        progress = { item.progress },
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    )
                    DownloadState.QUEUED -> if (item.bytesDone > 0) {
                        LinearProgressIndicator(
                            progress = { item.progress },
                            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                        )
                    } else {
                        LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 6.dp))
                    }
                    DownloadState.PAUSED -> LinearProgressIndicator(
                        progress = { item.progress },
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    )
                    else -> Unit
                }
            }
        },
        trailingContent = {
            Row {
                when (record.state) {
                    DownloadState.QUEUED, DownloadState.RUNNING -> IconButton(onClick = { viewModel.pause(item.id) }) {
                        Icon(painterResource(R.drawable.ic_pause), contentDescription = stringResource(R.string.downloads_pause))
                    }
                    DownloadState.PAUSED -> IconButton(onClick = { viewModel.resume(item.id) }) {
                        Icon(Icons.Default.PlayArrow, contentDescription = stringResource(R.string.downloads_resume))
                    }
                    DownloadState.FAILED -> IconButton(onClick = { viewModel.resume(item.id) }) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.downloads_retry))
                    }
                    DownloadState.COMPLETED -> Unit
                }
                IconButton(onClick = { viewModel.remove(item.id) }) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = stringResource(
                            if (completed) R.string.downloads_remove else R.string.downloads_cancel
                        ),
                    )
                }
            }
        },
    )
}

@Composable
private fun statusText(item: DownloadItem): String {
    val record = item.record
    val progress = stringResource(R.string.downloads_bytes_of, formatBytes(item.bytesDone), formatBytes(record.size))
    return when (record.state) {
        DownloadState.RUNNING -> {
            val speed = item.bytesPerSecond?.takeIf { it > 0 }
            if (speed == null) {
                progress
            } else {
                val remaining = ((record.size - item.bytesDone).coerceAtLeast(0) / speed)
                listOf(
                    progress,
                    stringResource(R.string.downloads_speed, formatBytes(speed)),
                    stringResource(R.string.downloads_eta, DateUtils.formatElapsedTime(remaining)),
                ).joinToString(" · ")
            }
        }
        DownloadState.QUEUED -> if (item.waitingForNetwork) {
            stringResource(R.string.downloads_waiting_network, progress)
        } else {
            "${stringResource(R.string.downloads_queued)} · ${record.serverName}"
        }
        DownloadState.PAUSED -> progress
        DownloadState.FAILED -> record.failure?.toUiText()?.asString() ?: stringResource(R.string.downloads_section_failed)
        DownloadState.COMPLETED -> "${formatBytes(record.size)} · ${record.serverName}"
    }
}

private fun DownloadFailure.toUiText(): UiText =
    if (code == DownloadFailure.STORAGE) uiText(R.string.error_storage, message) else toLunarError().toUiText()

/** Opens a downloaded file in another app. Returns false if no app can open it. */
private fun openFile(context: Context, item: DownloadItem): Boolean {
    val uri = item.record.contentUri?.toUri() ?: return false
    val intent = Intent(Intent.ACTION_VIEW)
        .setDataAndType(uri, context.contentResolver.getType(uri))
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    return try {
        context.startActivity(intent)
        true
    } catch (e: ActivityNotFoundException) {
        false
    }
}
