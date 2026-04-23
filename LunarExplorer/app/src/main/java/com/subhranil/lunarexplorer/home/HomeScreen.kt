package com.subhranil.lunarexplorer.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.subhranil.lunarexplorer.home.component.LinearAddLunarServer
import com.subhranil.lunarexplorer.home.component.LinearLunarServerConnect
import com.subhranil.lunarexplorer.home.selectors.LunarServerSelector
import com.subhranil.lunarexplorer.navigation.NavDestinations
import com.subhranil.lunarexplorer.services.ConnectionManagerService
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = koinViewModel(),
    connectionService: ConnectionManagerService?,
    onChangeBackStack: (NavDestinations) -> Unit
) {
    val state = viewModel.state.collectAsStateWithLifecycle().value
    val onAction = viewModel::onAction

    LunarServerSelector(
        shouldShow = state.showAddServerDialog,
        onAction = onAction
    )

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text("Lunar Explorer", style = MaterialTheme.typography.titleLarge)
                },
                actions = {
                    IconButton(
                        onClick = {
                            onChangeBackStack(NavDestinations.SettingsScreenDestination)
                        }
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("Available Servers", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "Select a saved server to connect, or add a new one.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (state.listOfActiveLunarHosts.isEmpty()) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text("No servers yet", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Add your first LunarLink server using IP address or domain name.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            FilledTonalButton(
                                onClick = { onAction(HomeActions.OnShowAddNewServerDialog) }
                            ) {
                                Text("Add Server")
                            }
                        }
                    }
                }
            }

            items(state.listOfActiveLunarHosts, key = { it.host }) { server ->
                LinearLunarServerConnect(
                    host = server
                ) {
                    connectionService?.let { svc ->
                        onAction(HomeActions.OnSelectServer(server, svc))
                        svc.updateNotificationToConnected(server.deviceName)
                        onChangeBackStack(NavDestinations.ExplorerScreenDestination)
                    }
                }
            }

            item {
                LinearAddLunarServer(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    onAction(HomeActions.OnShowAddNewServerDialog)
                }
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("Downloads", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "View active, queued, completed and failed downloads.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        FilledTonalButton(
                            onClick = { onChangeBackStack(NavDestinations.DownloadsScreenDestination) }
                        ) {
                            Text("Open Downloads")
                        }
                    }
                }
            }

            if (connectionService == null) {
                item {
                    Text(
                        text = "Connection service is not ready yet.",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}
