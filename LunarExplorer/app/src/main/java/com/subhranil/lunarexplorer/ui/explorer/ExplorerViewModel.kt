package com.subhranil.lunarexplorer.ui.explorer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.subhranil.lunarexplorer.R
import com.subhranil.lunarexplorer.data.api.ApiResult
import com.subhranil.lunarexplorer.data.api.Entry
import com.subhranil.lunarexplorer.data.api.EntryKind
import com.subhranil.lunarexplorer.data.api.ErrorCode
import com.subhranil.lunarexplorer.data.api.ListQuery
import com.subhranil.lunarexplorer.data.api.LunarError
import com.subhranil.lunarexplorer.data.api.Permission
import com.subhranil.lunarexplorer.data.api.Root
import com.subhranil.lunarexplorer.data.api.ServerFeature
import com.subhranil.lunarexplorer.data.api.SortKey
import com.subhranil.lunarexplorer.data.api.SortOrder
import com.subhranil.lunarexplorer.data.api.errorOrNull
import com.subhranil.lunarexplorer.data.download.DownloadRepository
import com.subhranil.lunarexplorer.data.api.hasCode
import com.subhranil.lunarexplorer.data.session.ServerSession
import com.subhranil.lunarexplorer.data.session.SessionManager
import com.subhranil.lunarexplorer.data.settings.SettingsRepository
import com.subhranil.lunarexplorer.data.settings.ViewMode
import com.subhranil.lunarexplorer.ui.Feedback
import com.subhranil.lunarexplorer.ui.UiText
import com.subhranil.lunarexplorer.ui.pluralText
import com.subhranil.lunarexplorer.ui.toUiText
import com.subhranil.lunarexplorer.ui.uiText
import com.subhranil.lunarexplorer.util.ServerPaths
import com.subhranil.lunarexplorer.util.uniqueName
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ExplorerViewModel(
    private val serverId: String,
    private val sessions: SessionManager,
    private val settings: SettingsRepository,
    private val downloads: DownloadRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ExplorerUiState(serverId = serverId))
    val state: StateFlow<ExplorerUiState> = _state.asStateFlow()

    private val _messages = Channel<UiText>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    /** Outcomes of user actions, for haptic feedback. */
    private val _feedback = Channel<Feedback>(Channel.BUFFERED)
    val feedback = _feedback.receiveAsFlow()

    private var session: ServerSession? = null
    private var loadJob: Job? = null
    private var operationJob: Job? = null
    private var searchJob: Job? = null

    // Values this screen saved itself, so their echo from the settings flow (or an older value still
    // in flight) doesn't undo a newer choice.
    private val sortEcho = Echo<Pair<SortKey, SortOrder>>()
    private val hiddenEcho = Echo<Boolean>()
    private val viewModeEcho = Echo<ViewMode>()
    private var pendingConflict: CompletableDeferred<Pair<ConflictAction, Boolean>>? = null

    init {
        // Follow the saved browsing settings, including changes made on the Settings screen while open.
        viewModelScope.launch {
            var connecting = false
            settings.settings.collect { prefs ->
                val before = _state.value
                val takeSort = sortEcho.accept(prefs.sortKey to prefs.sortOrder)
                val takeHidden = hiddenEcho.accept(prefs.showHiddenFiles)
                val takeViewMode = viewModeEcho.accept(prefs.viewMode)
                _state.update {
                    it.copy(
                        sortKey = if (takeSort) prefs.sortKey else it.sortKey,
                        sortOrder = if (takeSort) prefs.sortOrder else it.sortOrder,
                        showHidden = if (takeHidden) prefs.showHiddenFiles else it.showHidden,
                        viewMode = if (takeViewMode) prefs.viewMode else it.viewMode,
                        showThumbnails = prefs.showThumbnails,
                    )
                }
                val after = _state.value
                if (!connecting) {
                    connecting = true
                    launch { connect() }
                } else {
                    val sortChanged = before.sortKey != after.sortKey || before.sortOrder != after.sortOrder
                    val hiddenChanged = before.showHidden != after.showHidden
                    if (sortChanged || hiddenChanged) reloadFromStart()
                    if (hiddenChanged) runServerSearch()
                }
            }
        }
    }

    override fun onCleared() {
        sessions.disconnect(serverId)
    }

    // ---------- connection ----------

    fun retryConnection() {
        viewModelScope.launch { connect() }
    }

    private suspend fun connect() {
        _state.update { it.copy(connection = Connection.Connecting) }
        val connected = when (val r = sessions.connect(serverId)) {
            is ApiResult.Failure -> {
                _state.update { it.copy(connection = Connection.Failed(r.error.toUiText())) }
                return
            }
            is ApiResult.Success -> r.value
        }
        session = connected
        _state.update { it.copy(serverName = connected.info.name, serverFeatures = connected.info.features) }
        when (val r = connected.roots()) {
            is ApiResult.Failure -> _state.update { it.copy(connection = Connection.Failed(r.error.toUiText())) }
            is ApiResult.Success -> {
                _state.update { it.copy(connection = Connection.Ready, roots = r.value) }
                // Re-open the folder that was showing (e.g. after a retry).
                _state.value.folder?.let { load(it.path) }
            }
        }
    }

    // ---------- navigation ----------

    fun openRoot(root: Root) {
        _state.update { it.copy(root = root, history = emptyList()) }
        showFolder(root.path, pushHistory = false)
    }

    fun openEntry(entry: Entry) {
        if (_state.value.isSelecting) {
            toggleSelection(entry)
        } else if (entry.isDirLike) {
            showFolder(entry.path, pushHistory = true)
        } else {
            showDialog(ExplorerDialog.Actions(entry))
        }
    }

    fun openPath(path: String) {
        if (path == _state.value.folder?.path) return
        showFolder(path, pushHistory = true)
    }

    fun showLocations() {
        loadJob?.cancel()
        searchJob?.cancel()
        _state.update { it.copy(folder = null, root = null, history = emptyList(), search = null, selection = emptySet()) }
    }

    /** Goes to the previously visited folder, or to the locations list. Returns false if there's nowhere to go. */
    fun back(): Boolean {
        val s = _state.value
        val previous = s.history.lastOrNull()
        when {
            previous != null -> {
                _state.update { it.copy(history = it.history.dropLast(1)) }
                showFolder(previous, pushHistory = false)
            }
            s.folder != null -> showLocations()
            else -> return false
        }
        return true
    }

    private fun showFolder(path: String, pushHistory: Boolean) {
        searchJob?.cancel()
        _state.update {
            val history = if (pushHistory && it.folder != null) it.history + it.folder.path else it.history
            it.copy(
                folder = FolderState(path),
                history = history,
                root = rootFor(path) ?: it.root,
                search = null,
                selection = emptySet(),
            )
        }
        load(path)
    }

    private fun rootFor(path: String): Root? = _state.value.roots
        .filter { ServerPaths.isSameOrInside(path, it.path) }
        .maxByOrNull { it.path.length }

    // ---------- loading ----------

    fun refresh() {
        val folder = _state.value.folder ?: return
        _state.update { it.copy(folder = folder.copy(isRefreshing = true)) }
        load(folder.path)
        runServerSearch()
    }

    fun loadMore() {
        val folder = _state.value.folder ?: return
        if (!folder.canLoadMore || folder.isLoadingMore || loadJob?.isActive == true) return
        _state.update { it.copy(folder = folder.copy(isLoadingMore = true)) }
        load(folder.path, offset = folder.items.size)
    }

    private fun load(path: String, offset: Int = 0) {
        val session = session ?: return
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val s = _state.value
            val query = ListQuery(
                offset = offset,
                limit = PAGE_SIZE,
                sort = s.sortKey,
                order = s.sortOrder,
                showHidden = s.showHidden,
            )
            val result = session.list(path, query)
            _state.update { current ->
                val folder = current.folder?.takeIf { it.path == path } ?: return@update current
                val updated = when (result) {
                    is ApiResult.Success -> folder.copy(
                        entry = result.value.entry,
                        items = if (offset == 0) result.value.items else folder.items + result.value.items,
                        total = result.value.total,
                        isLoading = false,
                        isRefreshing = false,
                        isLoadingMore = false,
                        error = null,
                    )
                    is ApiResult.Failure -> folder.copy(
                        isLoading = false,
                        isRefreshing = false,
                        isLoadingMore = false,
                        // Keep showing items we already have; report the error as a message instead.
                        error = if (folder.items.isEmpty()) result.error.toUiText() else null,
                    )
                }
                current.copy(folder = updated)
            }
            if (result is ApiResult.Failure && _state.value.folder?.items?.isNotEmpty() == true) {
                _messages.send(result.error.toUiText())
            }
        }
    }

    // Each of these also becomes the new default. The settings flow echoes the same values back,
    // which is a no-op because the state already matches.

    fun setSort(key: SortKey, order: SortOrder) {
        _state.update { it.copy(sortKey = key, sortOrder = order) }
        reloadFromStart()
        sortEcho.pending = key to order
        viewModelScope.launch { settings.setSort(key, order) }
    }

    fun setShowHidden(show: Boolean) {
        _state.update { it.copy(showHidden = show) }
        reloadFromStart()
        runServerSearch()
        hiddenEcho.pending = show
        viewModelScope.launch { settings.setShowHiddenFiles(show) }
    }

    fun setViewMode(mode: ViewMode) {
        _state.update { it.copy(viewMode = mode) }
        viewModeEcho.pending = mode
        viewModelScope.launch { settings.setViewMode(mode) }
    }

    private fun reloadFromStart() {
        val folder = _state.value.folder ?: return
        _state.update { it.copy(folder = folder.copy(isRefreshing = true)) }
        load(folder.path)
    }

    // ---------- search & selection ----------

    fun openSearch() = _state.update { it.copy(search = SearchState(), selection = emptySet()) }

    fun setSearchQuery(query: String) {
        _state.update { s -> s.copy(search = s.search?.copy(query = query)) }
        runServerSearch(debounce = true)
    }

    /** Switches between filtering this folder and searching its subfolders on the server. */
    fun setSearchRecursive(recursive: Boolean) {
        if (recursive && ServerFeature.SEARCH !in _state.value.serverFeatures) return
        _state.update { s -> s.copy(search = s.search?.copy(recursive = recursive), selection = emptySet()) }
        runServerSearch()
    }

    fun closeSearch() {
        searchJob?.cancel()
        _state.update { it.copy(search = null) }
    }

    /** (Re)starts the server search for the current query, or clears its results when there's nothing to search. */
    private fun runServerSearch(debounce: Boolean = false) {
        searchJob?.cancel()
        val s = _state.value
        val search = s.search ?: return
        val folder = s.folder ?: return
        val session = session ?: return
        if (!search.isServerSearch) {
            _state.update { it.copy(search = it.search?.copy(results = emptyList(), isLoading = false, truncated = false, error = null)) }
            return
        }
        _state.update { it.copy(search = it.search?.copy(isLoading = true, error = null)) }
        searchJob = viewModelScope.launch {
            if (debounce) delay(SEARCH_DEBOUNCE_MS)
            val query = search.query.trim()
            val result = session.search(folder.path, query, s.showHidden)
            _state.update { current ->
                val latest = current.search?.takeIf { it.isServerSearch && it.query.trim() == query } ?: return@update current
                val updated = when (result) {
                    is ApiResult.Success -> latest.copy(
                        results = result.value.items,
                        truncated = result.value.truncated,
                        isLoading = false,
                        error = null,
                    )
                    is ApiResult.Failure -> latest.copy(results = emptyList(), isLoading = false, error = result.error.toUiText())
                }
                current.copy(search = updated)
            }
        }
    }

    fun toggleSelection(entry: Entry) = _state.update {
        val selection = if (entry.path in it.selection) it.selection - entry.path else it.selection + entry.path
        it.copy(selection = selection)
    }

    fun selectAll() = _state.update { it.copy(selection = it.visibleItems.map(Entry::path).toSet()) }

    fun clearSelection() = _state.update { it.copy(selection = emptySet()) }

    // ---------- dialogs ----------

    fun showDialog(dialog: ExplorerDialog) = _state.update { it.copy(dialog = dialog) }

    fun dismissDialog() {
        if (_state.value.dialog is ExplorerDialog.Conflict) {
            resolveConflict(ConflictAction.SKIP, applyToAll = false)
        } else {
            _state.update { it.copy(dialog = null) }
        }
    }

    // ---------- downloads ----------

    /** Queues the files among [entries] for download. Folders are skipped. */
    fun download(entries: List<Entry>) {
        val files = entries.filterNot { it.isDirLike }
        _state.update { it.copy(dialog = null, selection = emptySet()) }
        viewModelScope.launch {
            if (files.isEmpty()) {
                _messages.send(uiText(R.string.explorer_download_folders_only))
                return@launch
            }
            downloads.enqueue(serverId, _state.value.serverName, files)
            _feedback.send(Feedback.SUCCESS)
            _messages.send(pluralText(R.plurals.explorer_download_started, files.size))
            if (files.size < entries.size) _messages.send(uiText(R.string.explorer_download_folders_skipped))
        }
    }

    // ---------- create & rename ----------

    fun create(name: String, folder: Boolean) {
        val session = session ?: return
        val parent = _state.value.folder?.path ?: return
        _state.update { it.copy(dialog = null) }
        viewModelScope.launch {
            val path = ServerPaths.join(parent, name)
            val result = if (folder) session.createDir(path) else session.createFile(path)
            report(result, uiText(R.string.explorer_created, name))
        }
    }

    fun rename(entry: Entry, newName: String) {
        val session = session ?: return
        _state.update { it.copy(dialog = null, selection = emptySet()) }
        if (newName == entry.name) return
        val parent = ServerPaths.parent(entry.path) ?: return
        viewModelScope.launch {
            val result = session.move(entry.path, ServerPaths.join(parent, newName), overwrite = false)
            report(result, uiText(R.string.explorer_renamed, newName))
        }
    }

    private suspend fun report(result: ApiResult<*>, success: UiText) {
        when (result) {
            is ApiResult.Success -> {
                _feedback.send(Feedback.SUCCESS)
                _messages.send(success)
            }
            is ApiResult.Failure -> {
                _feedback.send(Feedback.ERROR)
                _messages.send(result.error.toUiText())
            }
        }
        refresh()
    }

    // ---------- delete ----------

    fun delete(entries: List<Entry>, permanent: Boolean) {
        val session = session ?: return
        _state.update { it.copy(dialog = null, selection = emptySet()) }
        runOperation(entries.size) { tick ->
            val failures = mutableListOf<LunarError>()
            entries.forEach { entry ->
                val recursive = entry.kind == EntryKind.DIR
                val result = session.delete(entry.path, permanent, recursive)
                if (result is ApiResult.Failure) failures += result.error
                tick()
            }
            summary(entries.size, failures, R.plurals.explorer_done_delete)
        }
    }

    // ---------- clipboard ----------

    fun copyToClipboard(entries: List<Entry>) = setClipboard(ClipboardMode.COPY, entries)

    fun moveToClipboard(entries: List<Entry>) = setClipboard(ClipboardMode.MOVE, entries)

    private fun setClipboard(mode: ClipboardMode, entries: List<Entry>) {
        val root = _state.value.root ?: return
        if (entries.isEmpty()) return
        _state.update { it.copy(clipboard = Clipboard(mode, entries, root), selection = emptySet(), dialog = null) }
    }

    fun cancelClipboard() = _state.update { it.copy(clipboard = null) }

    fun paste() {
        val session = session ?: return
        val s = _state.value
        val clipboard = s.clipboard ?: return
        val destination = s.folder?.path ?: return
        val destinationRoot = s.root ?: return
        val canPaste = destinationRoot.can(Permission.WRITE) &&
            (clipboard.mode == ClipboardMode.COPY || clipboard.sourceRoot.can(Permission.DELETE))
        if (!canPaste) {
            viewModelScope.launch { _messages.send(uiText(R.string.explorer_paste_not_allowed)) }
            return
        }
        _state.update { it.copy(clipboard = null) }
        val taken = s.folder.items.map { it.name }.toMutableSet()

        runOperation(clipboard.entries.size) { tick ->
            val failures = mutableListOf<LunarError>()
            var applyToAll: ConflictAction? = null
            for (entry in clipboard.entries) {
                val outcome = transfer(session, clipboard.mode, entry, destination, taken) { name ->
                    applyToAll ?: askConflict(name).let { (action, all) ->
                        if (all) applyToAll = action
                        action
                    }
                }
                outcome?.let { failures += it }
                tick()
            }
            val done = if (clipboard.mode == ClipboardMode.COPY) R.plurals.explorer_done_copy else R.plurals.explorer_done_move
            summary(clipboard.entries.size, failures, done)
        }
    }

    /** Copies or moves one entry into [destination]. Returns the error, or null on success/skip. */
    private suspend fun transfer(
        session: ServerSession,
        mode: ClipboardMode,
        entry: Entry,
        destination: String,
        taken: MutableSet<String>,
        onConflict: suspend (String) -> ConflictAction,
    ): LunarError? {
        if (entry.kind == EntryKind.DIR && ServerPaths.isSameOrInside(destination, entry.path)) {
            _messages.send(uiText(R.string.explorer_into_itself))
            return null
        }
        val sameFolder = ServerPaths.parent(entry.path) == ServerPaths.trim(destination)
        // Moving an item into the folder it's already in does nothing.
        if (mode == ClipboardMode.MOVE && sameFolder) return null

        suspend fun run(target: String, overwrite: Boolean) =
            if (mode == ClipboardMode.COPY) session.copy(entry.path, target, overwrite)
            else session.move(entry.path, target, overwrite)

        /** Retries with "name (1)", "name (2)", ... until the server accepts the name. */
        suspend fun keepBoth(): LunarError? {
            taken += entry.name
            repeat(MAX_RENAME_ATTEMPTS) {
                val name = uniqueName(entry.name, taken)
                val failure = run(ServerPaths.join(destination, name), overwrite = false).errorOrNull()
                if (failure == null || !failure.hasCode(ErrorCode.ALREADY_EXISTS)) return failure
                taken += name
            }
            return LunarError.Api(ErrorCode.ALREADY_EXISTS, "", 409)
        }

        // Copying into the same folder always makes a copy next to the original.
        if (sameFolder) return keepBoth()

        val result = run(ServerPaths.join(destination, entry.name), overwrite = false)
        if (result is ApiResult.Failure && result.error.hasCode(ErrorCode.ALREADY_EXISTS)) {
            return when (onConflict(entry.name)) {
                ConflictAction.SKIP -> null
                ConflictAction.REPLACE -> run(ServerPaths.join(destination, entry.name), overwrite = true).errorOrNull()
                ConflictAction.KEEP_BOTH -> keepBoth()
            }
        }
        return result.errorOrNull()
    }

    private suspend fun askConflict(name: String): Pair<ConflictAction, Boolean> {
        val deferred = CompletableDeferred<Pair<ConflictAction, Boolean>>()
        pendingConflict = deferred
        _state.update { it.copy(dialog = ExplorerDialog.Conflict(name)) }
        return deferred.await()
    }

    fun resolveConflict(action: ConflictAction, applyToAll: Boolean) {
        _state.update { it.copy(dialog = null) }
        pendingConflict?.complete(action to applyToAll)
        pendingConflict = null
    }

    // ---------- helpers ----------

    private fun runOperation(total: Int, block: suspend (tick: () -> Unit) -> Unit) {
        if (operationJob?.isActive == true) return
        operationJob = viewModelScope.launch {
            _state.update { it.copy(progress = Progress(0, total)) }
            try {
                block {
                    _state.update { s -> s.copy(progress = s.progress?.let { p -> p.copy(done = p.done + 1) }) }
                }
            } finally {
                _state.update { it.copy(progress = null) }
                refresh()
            }
        }
    }

    private suspend fun summary(total: Int, failures: List<LunarError>, successRes: Int) {
        val message = if (failures.isEmpty()) {
            pluralText(successRes, total)
        } else {
            val first = failures.first().toUiText()
            pluralText(R.plurals.explorer_done_partial, failures.size, failures.size, first)
        }
        _feedback.send(if (failures.isEmpty()) Feedback.SUCCESS else Feedback.ERROR)
        _messages.send(message)
    }

    /** Skips settings values until the one this screen last saved comes back. */
    private class Echo<T : Any> {
        var pending: T? = null

        /** True if [value] from the settings flow should be applied. */
        fun accept(value: T): Boolean {
            val expected = pending ?: return true
            if (value == expected) pending = null
            return false
        }
    }

    private companion object {
        const val PAGE_SIZE = 200
        const val MAX_RENAME_ATTEMPTS = 20
        const val SEARCH_DEBOUNCE_MS = 300L
    }
}
