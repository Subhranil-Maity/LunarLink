package com.subhranil.lunarexplorer.navigation

import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.ViewModel

class RootNavigationViewModel: ViewModel() {
    val backStack = mutableStateListOf<NavDestinations>(NavDestinations.HomeScreenDestination)
}