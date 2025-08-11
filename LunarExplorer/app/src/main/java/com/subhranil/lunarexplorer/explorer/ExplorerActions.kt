package com.subhranil.lunarexplorer.explorer

sealed class ExplorerActions {
    data object Refresh : ExplorerActions()
    data object OnBackPressed : ExplorerActions()
    data class GoToPath(val path: String): ExplorerActions()
    data class OnActionButtonPressed(val actionType: AppBarActionEnum): ExplorerActions()
    data class OnSearchTextChange(val search: String): ExplorerActions()
    data object OnSearchDisMissed: ExplorerActions()
}