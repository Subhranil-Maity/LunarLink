package com.subhranil.lunarexplorer.explorer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.navigation.NavigableListDetailPaneScaffold
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.subhranil.lunarexplorer.explorer.sidebar.AdaptiveListDetailScaffold
import com.subhranil.lunarexplorer.explorer.sidebar.ExplorerSideBar
import org.koin.compose.koinInject


@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun ExplorerScreen(
    modifier: Modifier = Modifier,
    viewModel: ExplorerViewModel = koinInject<ExplorerViewModel>()
) {
    val state = viewModel.state.collectAsStateWithLifecycle().value
    val onAction = viewModel::onAction
    if (!state.isConnected){
        Column(
            modifier = modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CircularProgressIndicator()
            Spacer(Modifier.height(5.dp))
            Text("Connecting....")
        }
        return
    }
    val scaffoldNavigator = rememberListDetailPaneScaffoldNavigator<String>()
    NavigableListDetailPaneScaffold(
        modifier = modifier,
        navigator = scaffoldNavigator,
        listPane = {
            ExplorerSideBar(mounts = state.mounts){
                viewModel.onAction(it)
            }
        },
        detailPane = {
            if (!state.pathHistory.isEmpty()) {
                Column {
                    state.pathHistory.forEach {
                        Text(it)
                    }
                }
            }
            Text("Empty")
        }
    )

}