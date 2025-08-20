package com.subhranil.lunarexplorer.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entry
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSavedStateNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.navigation3.ui.rememberSceneSetupNavEntryDecorator
import com.subhranil.lunarexplorer.explorer.ExplorerScreen
import com.subhranil.lunarexplorer.home.HomeScreen
import com.subhranil.lunarexplorer.settings.SettingsUi

@Composable
fun RootNavigation(
    modifier: Modifier = Modifier
) {
    val viewmodel = viewModel<RootNavigationViewModel>()
    val backstack = viewmodel.backStack

    NavDisplay(
        backStack = backstack,
        entryDecorators = listOf(
            // Add the default decorators for managing scenes and saving state
            rememberSceneSetupNavEntryDecorator(),
            rememberSavedStateNavEntryDecorator(),
            // Then add the view model store decorator
            rememberViewModelStoreNavEntryDecorator()
        ),
        entryProvider = entryProvider {
            entry <NavDestinations.HomeScreenDestination>{
                HomeScreen(modifier = modifier){
                    backstack.add(it)
                }
            }
            entry <NavDestinations.ExplorerScreenDestination>{
                ExplorerScreen(modifier = modifier){
                    backstack.removeLastOrNull()
                }
            }
            entry <NavDestinations.SettingsScreenDestination>{
                SettingsUi(modifier = modifier)
            }
        }
    )
}