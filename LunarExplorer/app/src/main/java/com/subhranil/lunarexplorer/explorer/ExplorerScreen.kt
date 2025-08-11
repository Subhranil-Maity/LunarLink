package com.subhranil.lunarexplorer.explorer

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.subhranil.lunarexplorer.explorer.actions.ExplorerTopAppBar
import com.subhranil.lunarexplorer.explorer.adaptative.AdaptativeExplorer
import com.subhranil.lunarexplorer.explorer.panes.DetailsPane
import com.subhranil.lunarexplorer.explorer.panes.ExplorerSideBar
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel


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

    if (state.showSearch) {
        BackHandler(enabled = true) {
            onAction(ExplorerActions.OnSearchDisMissed)
        }
    }else if (!state.pathHistory.isEmpty()){
        BackHandler(enabled = true) {
            onAction(ExplorerActions.OnBackPressed)
        }
    }
    HandleRequest(
        viewModel.pendingNameRequest,
        dismissNameRequest = { viewModel.dismissNameRequest() },
        confirmName = { viewModel.confirmName(it) },
    )
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    AdaptativeExplorer(
        modifier = modifier,
        isSelected = !state.pathHistory.isEmpty(),
        drawerState = drawerState,
        topAppBar = { shouldShowDrawer ->
            ExplorerTopAppBar(
                state = state,
                onAction = onAction,
                onDrawerButtonClicked = { scope.launch { drawerState.open() } },
                shouldShowDrawer = shouldShowDrawer
            )
        },
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

@Composable
fun HandleRequest(
    request: PendingNameRequest?,
    dismissNameRequest: () -> Unit,
    confirmName: (String) -> Unit
) {

    if (request != null) {
        var input by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { dismissNameRequest() },
            title = {
                Text(
                    if (request.type == NameRequestType.FILE)
                        "Enter file name"
                    else
                        "Enter folder name"
                )
            },
            text = {
                TextField(
                    value = input,
                    onValueChange = { input = it },
                    placeholder = {
                        Text(
                            if (request.type == NameRequestType.FILE)
                                "example.txt"
                            else
                                "New Folder"
                        )
                    }
                )
            },
            confirmButton = {
                Button(onClick = {
                    if (input != ""){
                        confirmName(input)
                    }
                }) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { dismissNameRequest() }) {
                    Text("Cancel")
                }
            }
        )
    }
}