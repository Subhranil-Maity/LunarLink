package com.subhranil.lunarexplorer.explorer

sealed class ExplorerActions {
    data object Refresh : ExplorerActions()
    data class GoToPath(val path: String): ExplorerActions()
}