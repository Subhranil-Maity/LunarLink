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
import com.subhranil.lunarexplorer.data.api.SortKey
import com.subhranil.lunarexplorer.data.api.SortOrder
import com.subhranil.lunarexplorer.data.api.errorOrNull
import com.subhranil.lunarexplorer.data.api.hasCode
import com.subhranil.lunarexplorer.data.session.ServerSession
import com.subhranil.lunarexplorer.data.session.SessionManager
import com.subhranil.lunarexplorer.data.settings.SettingsRepository
import com.subhranil.lunarexplorer.ui.UiText
import com.subhranil.lunarexplorer.ui.pluralText
import com.subhranil.lunarexplorer.ui.toUiText
import com.subhranil.lunarexplorer.ui.uiText
import com.subhranil.lunarexplorer.util.ServerPaths
import com.subhranil.lunarexplorer.util.uniqueName
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ExplorerViewModel(
    private val serverId: String,
    private val sessions: SessionManager,
    private val settings: SettingsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ExplorerUiState())
    val state: StateFlow<ExplorerUiState> = _state.asStateFlow()

    private val _messages = Channel<UiText>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    private var session: ServerSession? = null
    private var loadJob: Job? = null
    private var operationJob: Job? = null
    private var pendingConflict: CompletableDeferred<Pair<ConflictAction, Boolean>>? = null

    init {
        viewModelScope.launch {
            val defaults = settings.settings.first()
            _state.update {
                it.copy(sortKey = defaults.sortKey, sortOrder = defaults.sortOrder, showHidden = defaults.showHiddenFiles)
            }
            connect()
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
        _state.update { it.copy(serverName = connected.info.name) }
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
        _state.update { it.copy(folder = null, root = null, history = emptyList(), searchQuery = null, selection = emptySet()) }
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
        _state.update {
            val history = if (pushHistory && it.folder != null) it.history + it.folder.path else it.history
            it.copy(
                folder = FolderState(path),
                history = history,
                root = rootFor(path) ?: it.root,
                searchQuery = null,
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

    fun setSort(key: SortKey, order: SortOrder) {
        _state.update { it.copy(sortKey = key, sortOrder = order) }
        reloadFromStart()
    }

    fun setShowHidden(show: Boolean) {
        _state.update { it.copy(showHidden = show) }
        reloadFromStart()
    }

    private fun reloadFromStart() {
        val folder = _state.value.folder ?: return
        _state.update { it.copy(folder = folder.copy(isRefreshing = true)) }
        load(folder.path)
    }

    // ---------- search & selection ----------

    fun openSearch() = _state.update { it.copy(searchQuery = "", selection = emptySet()) }

    fun setSearchQuery(query: String) = _state.update { it.copy(searchQuery = query) }

    fun closeSearch() = _state.update { it.copy(searchQuery = null) }

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

    fun downloadDisabled() {
        dismissDialog()
        viewModelScope.launch { _messages.send(uiText(R.string.explorer_downloads_disabled)) }
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
            is ApiResult.Success -> _messages.send(success)
            is ApiResult.Failure -> _messages.send(result.error.toUiText())
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
        _messages.send(message)
    }

    private companion object {
        const val PAGE_SIZE = 200
        const val MAX_RENAME_ATTEMPTS = 20
    }
}
