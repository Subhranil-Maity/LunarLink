package com.subhranil.lunarexplorer.ui.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.subhranil.lunarexplorer.ui.explorer.ExplorerScreen
import com.subhranil.lunarexplorer.ui.explorer.ExplorerViewModel
import com.subhranil.lunarexplorer.ui.home.HomeScreen
import com.subhranil.lunarexplorer.ui.settings.SettingsScreen
import kotlinx.serialization.Serializable
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@Serializable
data object HomeRoute : NavKey

@Serializable
data class ExplorerRoute(val serverId: String) : NavKey

@Serializable
data object SettingsRoute : NavKey

/** Pushes [key] unless it's already on top, so double taps don't stack duplicate screens. */
fun NavBackStack<NavKey>.navigate(key: NavKey) {
    if (lastOrNull() != key) add(key)
}

/** Pops the top screen, never the last one. */
fun NavBackStack<NavKey>.pop() {
    if (size > 1) removeAt(lastIndex)
}

@Composable
fun AppNavigation() {
    val backStack = rememberNavBackStack(HomeRoute)
    NavDisplay(
        backStack = backStack,
        onBack = { backStack.pop() },
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator(),
        ),
        entryProvider = entryProvider {
            entry<HomeRoute> {
                HomeScreen(
                    onOpenServer = { id -> backStack.navigate(ExplorerRoute(id)) },
                    onOpenSettings = { backStack.navigate(SettingsRoute) },
                )
            }
            entry<ExplorerRoute> { key ->
                ExplorerScreen(
                    viewModel = koinViewModel<ExplorerViewModel> { parametersOf(key.serverId) },
                    onExit = { backStack.pop() },
                )
            }
            entry<SettingsRoute> {
                SettingsScreen(onBack = { backStack.pop() })
            }
        },
    )
}
