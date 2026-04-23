package com.subhranil.lunarexplorer.explorer

import com.subhranil.lunarexplorer.explorer.clipboard.NameConflictRequest
import com.subhranil.lunarexplorer.explorer.message.SnackbarMessage
import com.subhranil.lunarexplorer.models.LunarMetaData
import com.subhranil.lunarexplorer.models.Mounts
import com.subhranil.lunarexplorer.models.defaultMounts

data class ExplorerState(
    val isConnected: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null,
    val hideDotFiles: Boolean = false,
    val mounts: Mounts = defaultMounts(),
    val pathHistory: List<String> = emptyList(),
    val currentFolderContent: List<LunarMetaData> = emptyList(),
    val currentFolderInfo: LunarMetaData? = null,
    val filteredFolderContent: List<LunarMetaData> = emptyList(),
    val showSearch: Boolean = false,
    val showOptions: LunarMetaData? = null,
    val clipboard: ClipboardState? = null,
    val pendingNameConflict: NameConflictRequest? = null,
    val snackbarMessage: SnackbarMessage? = null
)
data class PendingNameRequest(
    val type: NameRequestType,
    val basePath: String,
    val action: NameRequestAction = NameRequestAction.CREATE,
    val currentName: String? = null,  // for rename, prefill this
    val onConfirm: (String) -> Unit
)

enum class NameRequestType {
    FILE,
    FOLDER
}

enum class NameRequestAction {
    CREATE,
    RENAME
}


enum class AppBarActionEnum{
    SEARCH,
    CREATE,
    CREATE_FILE,
    CREATE_FOLDER
}

enum class ClipboardActionType {
    COPY,
    MOVE
}

data class ClipboardState(
    val actionType: ClipboardActionType,
    val items: List<LunarMetaData>
)
