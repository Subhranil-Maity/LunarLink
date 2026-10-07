package com.subhranil.lunarexplorer.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.subhranil.lunarexplorer.data.download.DownloadRepository
import com.subhranil.lunarexplorer.ui.downloads.DownloadsScreen
import com.subhranil.lunarexplorer.ui.downloads.DownloadsViewModel
import com.subhranil.lunarexplorer.ui.explorer.ExplorerScreen
import com.subhranil.lunarexplorer.ui.explorer.ExplorerViewModel
import com.subhranil.lunarexplorer.ui.home.HomeScreen
import com.subhranil.lunarexplorer.ui.settings.SettingsScreen
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import org.koin.core.parameter.parametersOf

@Serializable
data object HomeRoute : NavKey

@Serializable
data class ExplorerRoute(val serverId: String) : NavKey

@Serializable
data object SettingsRoute : NavKey

@Serializable
data object DownloadsRoute : NavKey

/** Pushes [key] unless it's already on top, so double taps don't stack duplicate screens. */
fun NavBackStack<NavKey>.navigate(key: NavKey) {
    if (lastOrNull() != key) add(key)
}

/** Pops the top screen, never the last one. */
fun NavBackStack<NavKey>.pop() {
    if (size > 1) removeAt(lastIndex)
}

/** [openDownloads] emits when a notification asks to show the Downloads screen. */
@Composable
fun AppNavigation(openDownloads: Flow<Unit> = emptyFlow()) {
    val backStack = rememberNavBackStack(HomeRoute)
    LaunchedEffect(openDownloads) { openDownloads.collect { backStack.navigate(DownloadsRoute) } }

    val downloads = koinInject<DownloadRepository>()
    val activeDownloadsFlow = remember(downloads) { downloads.records.map { list -> list.orEmpty().count { it.isActive } } }
    val activeDownloads by activeDownloadsFlow.collectAsStateWithLifecycle(0)
    val showDownloads = { backStack.navigate(DownloadsRoute) }

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
                    onOpenDownloads = showDownloads,
                    activeDownloads = activeDownloads,
                )
            }
            entry<ExplorerRoute> { key ->
                ExplorerScreen(
                    viewModel = koinViewModel<ExplorerViewModel> { parametersOf(key.serverId) },
                    activeDownloads = activeDownloads,
                    onExit = { backStack.pop() },
                    onOpenDownloads = showDownloads,
                )
            }
            entry<SettingsRoute> {
                SettingsScreen(onBack = { backStack.pop() })
            }
            entry<DownloadsRoute> {
                DownloadsScreen(viewModel = koinViewModel<DownloadsViewModel>(), onBack = { backStack.pop() })
            }
        },
    )
}
