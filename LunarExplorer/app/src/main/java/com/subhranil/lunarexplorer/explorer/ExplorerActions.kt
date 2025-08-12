package com.subhranil.lunarexplorer.explorer

import com.subhranil.lunarexplorer.explorer.options.ExplorerOptions
import com.subhranil.lunarexplorer.models.LunarMetaData

sealed class ExplorerActions {
    data object Refresh : ExplorerActions()
    data object OnClearSnackBar : ExplorerActions()
    data object OnBackPressed : ExplorerActions()
    data class GoToPath(val path: String): ExplorerActions()
    data class OnLongPressed(val metaData: LunarMetaData): ExplorerActions()
    data class OnActionButtonPressed(val actionType: AppBarActionEnum): ExplorerActions()
    data class OnSearchTextChange(val search: String): ExplorerActions()
    data class OnItemClick(val metaData: LunarMetaData): ExplorerActions()

    data object OnSearchDisMissed: ExplorerActions()
    data object OnOptionsDismiss: ExplorerActions()
    data object OnPaste: ExplorerActions()
    data class OnOptionClicked(val explorerOptions: ExplorerOptions, val metaData: LunarMetaData): ExplorerActions()
}