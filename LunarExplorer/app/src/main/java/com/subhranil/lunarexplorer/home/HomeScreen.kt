package com.subhranil.lunarexplorer.home

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.subhranil.lunarexplorer.home.component.LinearAddLunarServer
import com.subhranil.lunarexplorer.home.component.LinearLunarServerConnect
import com.subhranil.lunarexplorer.home.selectors.LunarServerSelector
import com.subhranil.lunarexplorer.navigation.NavDestinations
import org.koin.androidx.compose.koinViewModel


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = koinViewModel(),
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
                        Icon(Icons.Default.Settings, contentDescription = null)

                    }
                },
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier.padding(innerPadding)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxWidth()
            ) {
                items(state.listOfActiveLunarHosts, key = { it.host }) { server ->
                    LinearLunarServerConnect(
                        host = server
                    ) {
                        onAction(HomeActions.OnSelectServer(server))
                        onChangeBackStack(NavDestinations.ExplorerScreenDestination)
                    }
                }
                item {
                    LinearAddLunarServer(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        onAction(HomeActions.OnShowAddNewServerDialog)
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
            Text("Downloads", style = MaterialTheme.typography.titleLarge)
            Text("No Downloads", style = MaterialTheme.typography.bodyMedium)
        }
    }

}