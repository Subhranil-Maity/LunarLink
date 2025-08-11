package com.subhranil.lunarexplorer.explorer

import com.subhranil.lunarexplorer.models.LunarMetaData
import com.subhranil.lunarexplorer.models.Mounts
import com.subhranil.lunarexplorer.models.defaultMounts

data class ExplorerState(
    val isConnected: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null,
    val mounts: Mounts = defaultMounts(),
    val pathHistory: List<String> = emptyList(),
    val currentFolderContent: List<LunarMetaData> = emptyList(),
    val currentFolderInfo: LunarMetaData? = null,
    val filteredFolderContent: List<LunarMetaData> = emptyList(),
    val showSearch: Boolean = false
)


enum class AppBarActionEnum{
    SEARCH,
    CREATE,
    CREATE_FILE,
    CREATE_FOLDER
}
enum class NameRequestType {
    FILE,
    FOLDER
}

data class PendingNameRequest(
    val type: NameRequestType,
    val basePath: String,
    val onConfirm: (String) -> Unit
)
