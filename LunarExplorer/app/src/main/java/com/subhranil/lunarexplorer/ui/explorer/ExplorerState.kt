package com.subhranil.lunarexplorer.ui.explorer

import com.subhranil.lunarexplorer.data.api.Entry
import com.subhranil.lunarexplorer.data.api.Permission
import com.subhranil.lunarexplorer.data.api.Root
import com.subhranil.lunarexplorer.data.api.SortKey
import com.subhranil.lunarexplorer.data.api.SortOrder
import com.subhranil.lunarexplorer.ui.UiText
import com.subhranil.lunarexplorer.util.PathStyle

sealed interface Connection {
    data object Connecting : Connection
    data object Ready : Connection
    data class Failed(val message: UiText) : Connection
}

data class FolderState(
    val path: String,
    val entry: Entry? = null,
    val items: List<Entry> = emptyList(),
    val total: Int = 0,
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val error: UiText? = null,
) {
    val canLoadMore: Boolean get() = !isLoading && error == null && items.size < total
}

enum class ClipboardMode { COPY, MOVE }

data class Clipboard(val mode: ClipboardMode, val entries: List<Entry>, val sourceRoot: Root)

enum class ConflictAction { REPLACE, KEEP_BOTH, SKIP }

sealed interface ExplorerDialog {
    data class Create(val folder: Boolean) : ExplorerDialog
    data class Rename(val entry: Entry) : ExplorerDialog
    data class Delete(val entries: List<Entry>) : ExplorerDialog
    data class Conflict(val name: String) : ExplorerDialog
    data class Details(val entry: Entry) : ExplorerDialog
    data class Actions(val entry: Entry) : ExplorerDialog
    data object Sort : ExplorerDialog
}

data class Progress(val done: Int, val total: Int)

data class ExplorerUiState(
    val serverName: String = "",
    val connection: Connection = Connection.Connecting,
    val roots: List<Root> = emptyList(),
    val root: Root? = null,
    /** `null` shows the list of locations (roots). */
    val folder: FolderState? = null,
    /** Folders visited before the current one, for Back. */
    val history: List<String> = emptyList(),
    val sortKey: SortKey = SortKey.NAME,
    val sortOrder: SortOrder = SortOrder.ASC,
    val showHidden: Boolean = true,
    /** `null` when search is closed. */
    val searchQuery: String? = null,
    val selection: Set<String> = emptySet(),
    val clipboard: Clipboard? = null,
    val dialog: ExplorerDialog? = null,
    val progress: Progress? = null,
) {
    val pathStyle: PathStyle get() = PathStyle.of(folder?.path ?: root?.path ?: "/")
    val canWrite: Boolean get() = root?.can(Permission.WRITE) == true
    val canDelete: Boolean get() = root?.can(Permission.DELETE) == true
    val isSelecting: Boolean get() = selection.isNotEmpty()
    val canGoBack: Boolean get() = folder != null

    val visibleItems: List<Entry>
        get() {
            val items = folder?.items.orEmpty()
            val query = searchQuery?.trim().orEmpty()
            return if (query.isEmpty()) items else items.filter { it.name.contains(query, ignoreCase = true) }
        }

    val selectedEntries: List<Entry>
        get() = folder?.items.orEmpty().filter { it.path in selection }
}
