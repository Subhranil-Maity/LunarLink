package com.subhranil.lunarexplorer.explorer

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.subhranil.lunarexplorer.explorer.adaptative.AdaptativeAppBarState
import com.subhranil.lunarexplorer.explorer.adaptative.AdaptativeExplorer
import com.subhranil.lunarexplorer.explorer.components.LinearEntry
import com.subhranil.lunarexplorer.explorer.panes.DetailsPane
import com.subhranil.lunarexplorer.explorer.panes.ExplorerSideBar
import com.subhranil.lunarexplorer.models.EntryType
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject


@OptIn(ExperimentalMaterial3AdaptiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ExplorerScreen(
    modifier: Modifier = Modifier,
    viewModel: ExplorerViewModel = koinViewModel()
) {
    val state = viewModel.state.collectAsStateWithLifecycle().value
    val onAction = viewModel::onAction
    state.error?.let { error ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.errorContainer),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(error)
            OutlinedButton(
                modifier = Modifier.clip(RectangleShape),
                onClick = {
                    viewModel.consumeError()
                }
            ) {
                Text("Go Back")
            }
        }
        return
    }
    if (!state.isConnected) {
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
    BackHandler(enabled = !state.pathHistory.isEmpty()) {
        onAction(ExplorerActions.OnBackPressed)
    }
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    AdaptativeExplorer(
        modifier = modifier,
        isSelected = !state.pathHistory.isEmpty(),
        drawerState = drawerState,
        adaptativeAppBarState = AdaptativeAppBarState(
            title = {
                if (state.pathHistory.isEmpty()) {
                    Text("Select A Path")
                } else {
                    Column {
                        if (state.currentFolderInfo != null){
                            if (state.currentFolderInfo.name != "unknown") {
                                Text(state.currentFolderInfo.name)
                                Text(state.currentFolderInfo.absolutePath, style = MaterialTheme.typography.labelSmall)
                            } else{
                                Text(state.currentFolderInfo.absolutePath)
                            }
                        }else{
                            Text(state.pathHistory.last(), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        ),
        otherBackgroundColor = MaterialTheme.colorScheme.surfaceContainer,
        listPane = {
            ExplorerSideBar(state = state) {
                scope.launch {
                    drawerState.close()
                }
                viewModel.onAction(it)
            }
        },
        detailPlane = {
            DetailsPane(
                state = state,
                onAction = onAction
            )
        }
    )
}