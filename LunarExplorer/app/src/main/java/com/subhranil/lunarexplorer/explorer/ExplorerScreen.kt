package com.subhranil.lunarexplorer.explorer

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.subhranil.lunarexplorer.GlobalStateHandler
import com.subhranil.lunarexplorer.explorer.actions.ExplorerTopAppBar
import com.subhranil.lunarexplorer.explorer.adaptative.AdaptativeExplorer
import com.subhranil.lunarexplorer.explorer.clipboard.ClipboardFab
import com.subhranil.lunarexplorer.explorer.handlers.PendingNameHandler
import com.subhranil.lunarexplorer.explorer.handlers.PendingRequestHandler
import com.subhranil.lunarexplorer.explorer.options.OptionPage
import com.subhranil.lunarexplorer.explorer.options.getExplorerOptions
import com.subhranil.lunarexplorer.explorer.panes.DetailsPane
import com.subhranil.lunarexplorer.explorer.panes.ExplorerSideBar
import com.subhranil.lunarexplorer.services.ConnectionManagerService
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject


@OptIn(ExperimentalMaterial3AdaptiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ExplorerScreen(
    modifier: Modifier = Modifier,
    viewModel: ExplorerViewModel = koinViewModel(),
    onExitExplorer: () -> Unit
) {
    val state = viewModel.state.collectAsStateWithLifecycle().value
    val onAction = viewModel::onAction
    viewModel.setExitCallBack {
        onExitExplorer()
    }
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

//    val dispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.snackbarMessage) {
        state.snackbarMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg.text)
            onAction(ExplorerActions.OnClearSnackBar)
        }
    }
    BackHandler(enabled = true) {
        when {
            state.showSearch -> {
                onAction(ExplorerActions.OnSearchDisMissed)
            }

            state.showOptions != null -> {
                onAction(ExplorerActions.OnOptionsDismiss)
            }

            state.pathHistory.isNotEmpty() -> {
                onAction(ExplorerActions.OnBackPressed)
            }

            else -> {
                // "super" call — let system handle back normally
//                dispatcher?.onBackPressed()
                onExitExplorer()
            }
        }
    }
    PendingNameHandler(
        viewModel.pendingNameRequest,
        dismissNameRequest = { viewModel.dismissNameRequest() },
        confirmName = { viewModel.confirmName(it) },
    )
    PendingRequestHandler(
        state = state
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
        },
        shouldShowExtraOptions = state.showOptions != null,
        extraOptionsPage = {
            OptionPage(
                state = state,
                onAction = onAction,
                options = getExplorerOptions()
            )
        },
        onAction = onAction,
        floatingActionButton = { isLarge ->
            ClipboardFab(
                clipboard = state.clipboard,
                isLarge = isLarge,
                onPasteClick = {
                    onAction(ExplorerActions.OnPaste)
                }
            )
        },
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState) { data ->
                Snackbar {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        state.snackbarMessage?.icon?.let { icon ->
                            Icon(icon, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(data.visuals.message)
                    }
                }
            }
        }
    )
}