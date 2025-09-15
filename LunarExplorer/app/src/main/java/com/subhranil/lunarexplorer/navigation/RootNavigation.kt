package com.subhranil.lunarexplorer.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entry
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSavedStateNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.navigation3.ui.rememberSceneSetupNavEntryDecorator
import com.subhranil.lunarexplorer.download.DownloadListScreen
import com.subhranil.lunarexplorer.download.DownloadPageScreen
import com.subhranil.lunarexplorer.explorer.ExplorerScreen
import com.subhranil.lunarexplorer.home.HomeScreen
import com.subhranil.lunarexplorer.services.rememberConnectionService
import com.subhranil.lunarexplorer.settings.SettingsUi

@Composable
fun RootNavigation(
    modifier: Modifier = Modifier,
    viewModel: RootNavigationViewModel
) {
//    val viewModel = viewModel<RootNavigationViewModel>()
    val backstack = viewModel.backStack
    val context = LocalContext.current

    val connectionService = rememberConnectionService()

    NavDisplay(
        backStack = backstack,
        entryDecorators = listOf(
            rememberSceneSetupNavEntryDecorator(),
            rememberSavedStateNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator()
        ),
        entryProvider = entryProvider {
            entry<NavDestinations.HomeScreenDestination> {
                HomeScreen(
                    modifier = modifier,
                    connectionService = connectionService
                ) { destination ->
                    backstack.add(destination)
                }
            }
            entry<NavDestinations.ExplorerScreenDestination> {
                ExplorerScreen(
                    modifier = modifier,
                ) {
                    // Safe pop: only remove if backstack has more than 1 element
                    if (backstack.size > 1) backstack.removeLastOrNull()
                }
            }
            entry<NavDestinations.SettingsScreenDestination> {
                SettingsUi(modifier = modifier)
            }
            entry(NavDestinations.DownloadsScreenDestination){
                DownloadPageScreen(
                    modifier = modifier
                )
            }
        }
    )
}
