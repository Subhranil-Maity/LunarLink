package com.subhranil.lunarexplorer.ui.explorer

import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import com.subhranil.lunarexplorer.data.settings.ViewMode
import com.subhranil.lunarexplorer.ui.rememberHaptics
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.window.core.layout.WindowSizeClass
import com.subhranil.lunarexplorer.R
import com.subhranil.lunarexplorer.data.api.Entry
import com.subhranil.lunarexplorer.data.api.ServerFeature
import com.subhranil.lunarexplorer.ui.asString
import com.subhranil.lunarexplorer.ui.components.LoadingView
import com.subhranil.lunarexplorer.ui.components.MessageView
import com.subhranil.lunarexplorer.ui.downloads.DownloadsButton
import com.subhranil.lunarexplorer.ui.downloads.rememberNotificationPermissionRequest
import com.subhranil.lunarexplorer.ui.resolve
import com.subhranil.lunarexplorer.ui.settings.SortDialog
import com.subhranil.lunarexplorer.util.ServerPaths
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExplorerScreen(
    viewModel: ExplorerViewModel,
    activeDownloads: Int,
    onExit: () -> Unit,
    onOpenDownloads: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val requestNotifications = rememberNotificationPermissionRequest()
    val download: (List<Entry>) -> Unit = { entries ->
        requestNotifications()
        viewModel.download(entries)
    }
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    LaunchedEffect(viewModel) { viewModel.messages.collect { snackbar.showSnackbar(it.resolve(context)) } }
    val haptics = rememberHaptics()
    LaunchedEffect(viewModel, haptics) { viewModel.feedback.collect { haptics.result(it) } }

    val twoPane = currentWindowAdaptiveInfoV2().windowSizeClass
        .isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND)

    // ----- Back handling, lowest priority first (later handlers win). Dialogs and sheets handle their own. -----
    // 1. Folder history: a predictive gesture that previews going back. When there's no folder history,
    //    this is disabled and NavDisplay plays the system "back to Home" animation.
    var backProgress by remember { mutableFloatStateOf(0f) }
    PredictiveBackHandler(enabled = state.connection == Connection.Ready && state.canGoBack) { events ->
        try {
            events.collect { backProgress = it.progress }
            viewModel.back()
        } catch (e: CancellationException) {
            throw e
        } finally {
            backProgress = 0f
        }
    }
    // 2. Search, then 3. selection.
    BackHandler(enabled = state.search != null) { viewModel.closeSearch() }
    BackHandler(enabled = state.isSelecting) { viewModel.clearSelection() }

    when (val connection = state.connection) {
        Connection.Connecting -> SimpleScaffold(state.serverName, onExit) {
            LoadingView(stringResource(R.string.explorer_connecting, state.serverName))
        }
        is Connection.Failed -> SimpleScaffold(state.serverName, onExit) {
            MessageView(
                title = stringResource(R.string.explorer_connect_failed),
                body = connection.message.asString(),
                icon = painterResource(R.drawable.ic_cloud_off),
                isError = true,
                action = {
                    Row {
                        OutlinedButton(onClick = onExit) { Text(stringResource(R.string.action_back)) }
                        Button(onClick = viewModel::retryConnection, modifier = Modifier.padding(start = 12.dp)) {
                            Text(stringResource(R.string.action_retry))
                        }
                    }
                },
            )
        }
        Connection.Ready -> ReadyContent(
            state = state,
            viewModel = viewModel,
            snackbar = snackbar,
            twoPane = twoPane,
            backProgress = backProgress,
            activeDownloads = activeDownloads,
            onExit = onExit,
            onOpenDownloads = onOpenDownloads,
            onDownload = download,
        )
    }

    Dialogs(state, viewModel, onDownload = download)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SimpleScaffold(title: String, onExit: () -> Unit, content: @Composable () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onExit) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding -> Box(Modifier.padding(padding)) { content() } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReadyContent(
    state: ExplorerUiState,
    viewModel: ExplorerViewModel,
    snackbar: SnackbarHostState,
    twoPane: Boolean,
    backProgress: Float,
    activeDownloads: Int,
    onExit: () -> Unit,
    onOpenDownloads: () -> Unit,
    onDownload: (List<Entry>) -> Unit,
) {
    val folder = state.folder
    Scaffold(
        topBar = {
            Column {
                when {
                    state.isSelecting -> SelectionTopBar(state, viewModel, onDownload)
                    state.search != null -> SearchTopBar(
                        search = state.search,
                        canSearchSubfolders = ServerFeature.SEARCH in state.serverFeatures,
                        viewModel = viewModel,
                    )
                    else -> MainTopBar(state, viewModel, activeDownloads, onExit, onOpenDownloads)
                }
                if (folder != null && state.root != null) {
                    BreadcrumbBar(
                        crumbs = ServerPaths.crumbs(state.root.path, state.root.name, folder.path),
                        showLocations = !twoPane,
                        onLocations = viewModel::showLocations,
                        onCrumb = { viewModel.openPath(it.path) },
                    )
                    HorizontalDivider()
                }
            }
        },
        bottomBar = {
            when {
                state.progress != null -> ProgressBar(state.progress, onCancel = viewModel::cancelOperation)
                state.clipboard != null -> ClipboardBar(
                    clipboard = state.clipboard,
                    canPaste = folder != null && state.canWrite,
                    onPaste = viewModel::paste,
                    onCancel = viewModel::cancelClipboard,
                )
            }
        },
        floatingActionButton = {
            if (folder != null && state.canWrite && !state.isSelecting && state.clipboard == null && state.progress == null) {
                CreateFab(
                    onNewFolder = { viewModel.showDialog(ExplorerDialog.Create(folder = true)) },
                    onNewFile = { viewModel.showDialog(ExplorerDialog.Create(folder = false)) },
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        val folderPane: @Composable (Modifier) -> Unit = { modifier ->
            if (folder == null) {
                MessageView(
                    title = stringResource(R.string.explorer_pick_location),
                    icon = painterResource(R.drawable.folder_icon),
                    modifier = modifier,
                )
            } else {
                FolderPane(
                    state = state,
                    folder = folder,
                    viewModel = viewModel,
                    modifier = modifier.graphicsLayer {
                        // Predictive back preview: slide and fade slightly while the gesture is in progress.
                        translationX = backProgress * 64.dp.toPx()
                        alpha = 1f - backProgress * 0.4f
                    },
                )
            }
        }
        val locations: @Composable (Modifier) -> Unit = { modifier ->
            if (state.roots.isEmpty()) {
                MessageView(title = stringResource(R.string.explorer_no_roots), modifier = modifier)
            } else {
                LocationsList(state.roots, selected = state.root, onOpen = viewModel::openRoot, modifier = modifier)
            }
        }

        if (twoPane) {
            Row(
                Modifier
                    .padding(padding)
                    .fillMaxSize()
            ) {
                locations(Modifier.width(320.dp).fillMaxHeight())
                VerticalDivider()
                folderPane(Modifier.weight(1f).fillMaxHeight())
            }
        } else {
            val modifier = Modifier
                .padding(padding)
                .fillMaxSize()
            if (folder == null) locations(modifier) else folderPane(modifier)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainTopBar(
    state: ExplorerUiState,
    viewModel: ExplorerViewModel,
    activeDownloads: Int,
    onExit: () -> Unit,
    onOpenDownloads: () -> Unit,
) {
    val folder = state.folder
    var menu by remember { mutableStateOf(false) }
    TopAppBar(
        title = {
            Column {
                val title = when {
                    folder == null -> state.serverName
                    state.root != null && ServerPaths.trim(folder.path) == ServerPaths.trim(state.root.path) -> state.root.name
                    else -> folder.entry?.name ?: ServerPaths.name(folder.path)
                }
                Text(
                    title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (folder != null) {
                    Text(
                        state.serverName,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
        },
        navigationIcon = {
            // Same path as the back gesture: previous folder, then locations, then leave.
            IconButton(onClick = { if (!viewModel.back()) onExit() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
            }
        },
        actions = {
            if (folder != null) {
                IconButton(onClick = viewModel::openSearch) {
                    Icon(Icons.Default.Search, contentDescription = stringResource(R.string.explorer_search))
                }
                IconButton(onClick = { viewModel.showDialog(ExplorerDialog.Sort) }) {
                    Icon(painterResource(R.drawable.ic_sort), contentDescription = stringResource(R.string.explorer_sort))
                }
                val grid = state.viewMode == ViewMode.GRID
                IconButton(onClick = { viewModel.setViewMode(if (grid) ViewMode.LIST else ViewMode.GRID) }) {
                    Icon(
                        painterResource(if (grid) R.drawable.ic_view_list else R.drawable.ic_grid_view),
                        contentDescription = stringResource(if (grid) R.string.explorer_view_list else R.string.explorer_view_grid),
                    )
                }
            }
            DownloadsButton(activeDownloads, onClick = onOpenDownloads)
            Box {
                IconButton(onClick = { menu = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.action_more))
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.explorer_show_hidden)) },
                        trailingIcon = { Checkbox(checked = state.showHidden, onCheckedChange = null) },
                        onClick = { menu = false; viewModel.setShowHidden(!state.showHidden) },
                    )
                    if (folder != null) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_refresh)) },
                            leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null) },
                            onClick = { menu = false; viewModel.refresh() },
                        )
                    }
                }
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchTopBar(search: SearchState, canSearchSubfolders: Boolean, viewModel: ExplorerViewModel) {
    val query = search.query
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Column {
    TopAppBar(
        navigationIcon = {
            IconButton(onClick = viewModel::closeSearch) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.explorer_close_search))
            }
        },
        title = {
            TextField(
                value = query,
                onValueChange = viewModel::setSearchQuery,
                placeholder = {
                    Text(
                        stringResource(if (search.recursive) R.string.explorer_search_subfolders else R.string.explorer_search),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focus),
            )
        },
        actions = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { viewModel.setSearchQuery("") }) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.action_close))
                }
            }
        },
    )
    if (canSearchSubfolders) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = !search.recursive,
                onClick = { viewModel.setSearchRecursive(false) },
                label = { Text(stringResource(R.string.explorer_search_scope_folder)) },
            )
            FilterChip(
                selected = search.recursive,
                onClick = { viewModel.setSearchRecursive(true) },
                label = { Text(stringResource(R.string.explorer_search_scope_subfolders)) },
            )
        }
    }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectionTopBar(state: ExplorerUiState, viewModel: ExplorerViewModel, onDownload: (List<Entry>) -> Unit) {
    val selected = state.selectedEntries
    val haptics = rememberHaptics()
    TopAppBar(
        navigationIcon = {
            IconButton(onClick = viewModel::clearSelection) {
                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.explorer_clear_selection))
            }
        },
        title = { Text(pluralStringResource(R.plurals.explorer_items_selected, state.selection.size, state.selection.size)) },
        actions = {
            IconButton(onClick = viewModel::selectAll) {
                Icon(painterResource(R.drawable.ic_select_all), contentDescription = stringResource(R.string.explorer_select_all))
            }
            if (selected.any { !it.isDirLike }) {
                IconButton(onClick = { onDownload(selected) }) {
                    Icon(painterResource(R.drawable.download_icon), contentDescription = stringResource(R.string.explorer_download))
                }
            }
            IconButton(onClick = { haptics.contextClick(); viewModel.copyToClipboard(selected) }) {
                Icon(painterResource(R.drawable.copy_icon), contentDescription = stringResource(R.string.explorer_copy), Modifier.size(24.dp))
            }
            if (state.canDelete) {
                IconButton(onClick = { haptics.contextClick(); viewModel.moveToClipboard(selected) }) {
                    Icon(
                        painterResource(R.drawable.move_folder_icon),
                        contentDescription = stringResource(R.string.explorer_move),
                        modifier = Modifier.size(24.dp),
                    )
                }
                IconButton(onClick = { viewModel.showDialog(ExplorerDialog.Delete(selected)) }) {
                    Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.action_delete))
                }
            }
        },
        colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
    )
}

@Composable
private fun CreateFab(onNewFolder: () -> Unit, onNewFile: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        FloatingActionButton(onClick = { expanded = true }) {
            Icon(Icons.Default.Add, contentDescription = stringResource(R.string.explorer_new))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.explorer_new_folder)) },
                leadingIcon = { Icon(painterResource(R.drawable.ic_create_new_folder), contentDescription = null) },
                onClick = { expanded = false; onNewFolder() },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.explorer_new_file)) },
                leadingIcon = { Icon(painterResource(R.drawable.ic_note_add), contentDescription = null) },
                onClick = { expanded = false; onNewFile() },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FolderPane(
    state: ExplorerUiState,
    folder: FolderState,
    viewModel: ExplorerViewModel,
    modifier: Modifier = Modifier,
) {
    val items = state.visibleItems
    val search = state.search
    val grid = state.viewMode == ViewMode.GRID
    val haptics = rememberHaptics()
    val listState = rememberLazyListState()
    val gridState = rememberLazyGridState()
    val itemCount by rememberUpdatedState(items.size)

    // Reset scroll position when the folder changes.
    LaunchedEffect(folder.path) {
        listState.scrollToItem(0)
        gridState.scrollToItem(0)
    }
    // Load the next page when close to the end.
    LaunchedEffect(listState, gridState, folder.path, grid) {
        snapshotFlow {
            val visible = if (grid) gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index
            else listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index
            visible ?: 0
        }
            .distinctUntilChanged()
            .filter { it >= itemCount - 20 }
            .collect { viewModel.loadMore() }
    }

    // A tick when a pull goes far enough to refresh on release (not for refreshes started elsewhere).
    val pullState = rememberPullToRefreshState()
    val refreshing by rememberUpdatedState(folder.isRefreshing)
    LaunchedEffect(pullState) {
        snapshotFlow { pullState.distanceFraction >= 1f }
            .distinctUntilChanged()
            .filter { it && !refreshing }
            .collect { haptics.threshold() }
    }

    val onClick: (Entry) -> Unit = { entry ->
        if (state.isSelecting) haptics.tick()
        viewModel.openEntry(entry)
    }
    val onLongClick: (Entry) -> Unit = { entry ->
        if (state.isSelecting) haptics.tick() else haptics.longPress()
        viewModel.toggleSelection(entry)
    }
    val onMore: (Entry) -> Unit = { entry -> viewModel.showDialog(ExplorerDialog.Actions(entry)) }
    /** Where a search result is, relative to the folder being searched. */
    val location: (Entry) -> String? = { entry ->
        if (search?.isServerSearch == true) {
            ServerPaths.parent(entry.path)?.let { ServerPaths.relative(it, folder.path) }?.ifEmpty { null }
        } else {
            null
        }
    }
    val truncatedNote = search?.isServerSearch == true && search.truncated
    val thumbnailServer = state.serverId.takeIf { state.thumbnails }

    PullToRefreshBox(
        isRefreshing = folder.isRefreshing,
        onRefresh = viewModel::refresh,
        state = pullState,
        modifier = modifier,
    ) {
        when {
            folder.isLoading && folder.items.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            search?.isServerSearch == true && search.isLoading && items.isEmpty() ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            search?.isServerSearch == true && search.error != null -> MessageView(
                title = stringResource(R.string.explorer_search_failed),
                body = search.error.asString(),
                icon = painterResource(R.drawable.ic_cloud_off),
                isError = true,
                action = { Button(onClick = viewModel::refresh) { Text(stringResource(R.string.action_retry)) } },
            )
            search?.recursive == true && search.query.isBlank() -> MessageView(
                title = stringResource(R.string.explorer_search_hint),
                icon = rememberVectorPainter(Icons.Default.Search),
            )
            folder.error != null -> MessageView(
                title = stringResource(R.string.explorer_load_failed),
                body = folder.error.asString(),
                icon = painterResource(R.drawable.ic_cloud_off),
                isError = true,
                action = { Button(onClick = viewModel::refresh) { Text(stringResource(R.string.action_retry)) } },
            )
            items.isEmpty() -> MessageView(
                title = if (search == null || search.query.isBlank()) stringResource(R.string.explorer_empty_folder)
                else stringResource(R.string.explorer_no_matches, search.query.trim()),
                icon = painterResource(R.drawable.folder_icon),
            )
            grid -> LazyVerticalGrid(
                columns = GridCells.Adaptive(GRID_CELL_MIN),
                state = gridState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 96.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(items, key = Entry::path) { entry ->
                    EntryTile(
                        entry = entry,
                        selected = entry.path in state.selection,
                        selecting = state.isSelecting,
                        onClick = { onClick(entry) },
                        onLongClick = { onLongClick(entry) },
                        onMore = { onMore(entry) },
                        modifier = Modifier.animateItem(),
                        location = location(entry),
                        thumbnailServer = thumbnailServer,
                    )
                }
                if (truncatedNote) {
                    item(span = { GridItemSpan(maxLineSpan) }) { TruncatedNote(items.size) }
                }
                if (folder.isLoadingMore) {
                    item(span = { GridItemSpan(maxLineSpan) }) { LoadingMore() }
                }
            }
            else -> LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 96.dp),
            ) {
                items(items, key = Entry::path) { entry ->
                    EntryRow(
                        entry = entry,
                        selected = entry.path in state.selection,
                        selecting = state.isSelecting,
                        onClick = { onClick(entry) },
                        onLongClick = { onLongClick(entry) },
                        onMore = { onMore(entry) },
                        modifier = Modifier.animateItem(),
                        location = location(entry),
                        thumbnailServer = thumbnailServer,
                    )
                }
                if (truncatedNote) {
                    item { TruncatedNote(items.size) }
                }
                if (folder.isLoadingMore) {
                    item { LoadingMore() }
                }
            }
        }
    }
}

private val GRID_CELL_MIN = 112.dp

@Composable
private fun TruncatedNote(count: Int) {
    Text(
        stringResource(R.string.explorer_search_truncated, count),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(16.dp),
    )
}

@Composable
private fun LoadingMore() {
    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(Modifier.size(24.dp))
    }
}

@Composable
private fun Dialogs(state: ExplorerUiState, viewModel: ExplorerViewModel, onDownload: (List<Entry>) -> Unit) {
    val existingNames = remember(state.folder?.items) { state.folder?.items.orEmpty().map { it.name }.toSet() }
    when (val dialog = state.dialog) {
        null -> Unit
        is ExplorerDialog.Create -> NameDialog(
            title = stringResource(if (dialog.folder) R.string.dialog_new_folder else R.string.dialog_new_file),
            confirmLabel = stringResource(R.string.dialog_create),
            initial = "",
            style = state.pathStyle,
            existingNames = existingNames,
            onConfirm = { viewModel.create(it, dialog.folder) },
            onDismiss = viewModel::dismissDialog,
        )
        is ExplorerDialog.Rename -> NameDialog(
            title = stringResource(R.string.dialog_rename),
            confirmLabel = stringResource(R.string.explorer_rename),
            initial = dialog.entry.name,
            style = state.pathStyle,
            existingNames = existingNames,
            onConfirm = { viewModel.rename(dialog.entry, it) },
            onDismiss = viewModel::dismissDialog,
        )
        is ExplorerDialog.Delete -> DeleteDialog(
            entries = dialog.entries,
            onConfirm = { permanent -> viewModel.delete(dialog.entries, permanent) },
            onDismiss = viewModel::dismissDialog,
        )
        is ExplorerDialog.Conflict -> ConflictDialog(dialog.name, onChoice = viewModel::resolveConflict)
        is ExplorerDialog.Details -> DetailsDialog(dialog.entry, onDismiss = viewModel::dismissDialog)
        is ExplorerDialog.Sort -> SortDialog(
            key = state.sortKey,
            order = state.sortOrder,
            onChange = viewModel::setSort,
            onDismiss = viewModel::dismissDialog,
        )
        is ExplorerDialog.Actions -> EntryActionsSheet(
            entry = dialog.entry,
            canWrite = state.canWrite,
            canDelete = state.canDelete,
            onDismiss = viewModel::dismissDialog,
            onDetails = { viewModel.showDialog(ExplorerDialog.Details(dialog.entry)) },
            onRename = { viewModel.showDialog(ExplorerDialog.Rename(dialog.entry)) },
            onCopy = { viewModel.copyToClipboard(listOf(dialog.entry)) },
            onMove = { viewModel.moveToClipboard(listOf(dialog.entry)) },
            onDelete = { viewModel.showDialog(ExplorerDialog.Delete(listOf(dialog.entry))) },
            onDownload = { onDownload(listOf(dialog.entry)) },
        )
    }
}
