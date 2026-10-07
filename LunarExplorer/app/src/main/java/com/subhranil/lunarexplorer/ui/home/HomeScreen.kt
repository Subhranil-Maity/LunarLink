package com.subhranil.lunarexplorer.ui.home

import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.subhranil.lunarexplorer.R
import com.subhranil.lunarexplorer.ui.components.MessageView
import com.subhranil.lunarexplorer.ui.downloads.DownloadsButton
import com.subhranil.lunarexplorer.ui.resolve
import org.koin.androidx.compose.koinViewModel

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp, start = 4.dp),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenServer: (serverId: String) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenDownloads: () -> Unit,
    activeDownloads: Int,
    viewModel: HomeViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    LaunchedEffect(viewModel) {
        viewModel.messages.collect { snackbar.showSnackbar(it.resolve(context)) }
    }
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.home_title)) },
                actions = {
                    DownloadsButton(activeDownloads, onClick = onOpenDownloads)
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.action_settings))
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        floatingActionButton = {
            if (state.servers.isNotEmpty() || state.nearby.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = viewModel::openAddServer,
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.home_add_server)) },
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = viewModel::refresh,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when {
                !state.isLoaded -> Box(Modifier.fillMaxSize())
                state.servers.isEmpty() && state.nearby.isEmpty() -> MessageView(
                    title = stringResource(R.string.home_empty_title),
                    body = stringResource(R.string.home_empty_body),
                    icon = painterResource(R.drawable.ic_dns),
                    action = {
                        Button(onClick = viewModel::openAddServer) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Text(stringResource(R.string.home_add_server), Modifier.padding(start = 8.dp))
                        }
                    },
                )
                else -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 340.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (state.nearby.isNotEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }, key = "nearby-header") {
                            SectionTitle(stringResource(R.string.home_nearby))
                        }
                        items(state.nearby, key = { "nearby:${it.host}:${it.port}" }) { server ->
                            NearbyServerCard(
                                server = server,
                                onAdd = { viewModel.addNearby(server) },
                                modifier = Modifier.animateItem(),
                            )
                        }
                        if (state.servers.isNotEmpty()) {
                            item(span = { GridItemSpan(maxLineSpan) }, key = "saved-header") {
                                SectionTitle(stringResource(R.string.home_saved))
                            }
                        }
                    }
                    items(state.servers, key = { it.server.id }) { item ->
                        ServerCard(
                            item = item,
                            onOpen = { onOpenServer(item.server.id) },
                            onEdit = { viewModel.openEditServer(item.server) },
                            onDelete = { viewModel.requestDelete(item.server) },
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
            }
        }
    }

    state.editor?.let { editor ->
        ServerEditorSheet(
            state = editor,
            onDismiss = viewModel::dismissEditor,
            onHostChange = viewModel::onHostChange,
            onPortChange = viewModel::onPortChange,
            onUsernameChange = viewModel::onUsernameChange,
            onPasswordChange = viewModel::onPasswordChange,
            onTogglePassword = viewModel::togglePasswordVisible,
            onTest = viewModel::testConnection,
            onSave = viewModel::saveServer,
        )
    }

    state.pendingDelete?.let { server ->
        AlertDialog(
            onDismissRequest = viewModel::dismissDelete,
            title = { Text(stringResource(R.string.home_delete_title, server.name)) },
            text = { Text(stringResource(R.string.home_delete_body)) },
            confirmButton = {
                TextButton(onClick = viewModel::confirmDelete) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissDelete) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}
