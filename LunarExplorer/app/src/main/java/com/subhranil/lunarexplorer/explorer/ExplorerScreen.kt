package com.subhranil.lunarexplorer.explorer

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.navigation.NavigableListDetailPaneScaffold
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.subhranil.lunarexplorer.explorer.adaptative.AdaptativeAppBarState
import com.subhranil.lunarexplorer.explorer.adaptative.AdaptativeExplorer
import com.subhranil.lunarexplorer.explorer.components.LinearEntry
import com.subhranil.lunarexplorer.explorer.sidebar.AdaptiveListDetailScaffold
import com.subhranil.lunarexplorer.explorer.sidebar.ExplorerSideBar
import kotlinx.coroutines.launch
import org.koin.compose.koinInject


@OptIn(ExperimentalMaterial3AdaptiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ExplorerScreen(
    modifier: Modifier = Modifier,
    viewModel: ExplorerViewModel = koinInject<ExplorerViewModel>()
) {
    val state = viewModel.state.collectAsStateWithLifecycle().value
    val onAction = viewModel::onAction
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
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    AdaptativeExplorer(
        modifier = modifier,
        isSelected = !state.pathHistory.isEmpty(),
        drawerState = drawerState,
        adaptativeAppBarState = AdaptativeAppBarState(
            title = {
                if (state.pathHistory.isEmpty()){
                    Text("Select A Path")
                }else{
                    Text(state.pathHistory.last())
                }
            }
        ),
        listPane = {
            ExplorerSideBar(mounts = state.mounts) {
                scope.launch {
                    drawerState.close()
                }
                viewModel.onAction(it)
            }
        },
        detailPlane = {
            if (state.isLoading) {
                Row(
                    Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                val scrollState = rememberScrollState()
                if (!state.pathHistory.isEmpty()) {
                    Column(
                        modifier = Modifier.verticalScroll(scrollState)
                    ) {
//                    state.pathHistory.forEach {
//                        Text(it)
//                    }
                        state.currentFolderContent.forEach {
                            LinearEntry(metaData = it) {
                                onAction(ExplorerActions.GoToPath(it.absolutePath))
                            }
                        }
                    }
                }
            }
        }
    )

}