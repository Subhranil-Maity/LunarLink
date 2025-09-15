package com.subhranil.lunarexplorer.navigation

import androidx.navigation3.runtime.NavKey

sealed class NavDestinations: NavKey {
    data object HomeScreenDestination: NavDestinations()
    data object SettingsScreenDestination: NavDestinations()
    data object ExplorerScreenDestination: NavDestinations()
    data object DownloadsScreenDestination: NavDestinations()
}