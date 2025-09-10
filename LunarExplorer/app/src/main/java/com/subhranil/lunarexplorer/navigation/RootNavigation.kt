package com.subhranil.lunarexplorer.navigation

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entry
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSavedStateNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.navigation3.ui.rememberSceneSetupNavEntryDecorator
import com.subhranil.lunarexplorer.explorer.ExplorerScreen
import com.subhranil.lunarexplorer.explorer.ExplorerViewModel
import com.subhranil.lunarexplorer.home.HomeScreen
import com.subhranil.lunarexplorer.services.rememberConnectionService
import com.subhranil.lunarexplorer.settings.SettingsUi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun RootNavigation(
    modifier: Modifier = Modifier
) {
    val viewmodel = viewModel<RootNavigationViewModel>()
    val backstack = viewmodel.backStack
    val context = LocalContext.current

    val connectionService = rememberConnectionService()
    if (connectionService == null) {
        Toast.makeText(context, "Service is null", Toast.LENGTH_SHORT).show()
    }

    // Observe connection changes
//    LaunchedEffect(connectionService?.getLunarConnection()) {
//        val connection = connectionService?.getLunarConnection()
//        if (connection == null) {
//            // Connection is lost -> navigate safely to HomeScreen
//            if (backstack.lastOrNull() !is NavDestinations.HomeScreenDestination) {
//                backstack.clear()
//                backstack.add(NavDestinations.HomeScreenDestination)
//            } else if (backstack.size > 1) {
//                // Remove last only if there are more than 1 to avoid empty backstack
//                backstack.removeLast()
//            }
//        }
//    }

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
        }
    )
}
