package com.subhranil.lunarexplorer.explorer.actions


import android.R.attr.maxWidth
import android.annotation.SuppressLint
import android.widget.Toast
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.subhranil.lunarexplorer.explorer.ExplorerActions
import com.subhranil.lunarexplorer.explorer.ExplorerState

@SuppressLint("UnusedBoxWithConstraintsScope")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExplorerTopAppBar(
    state: ExplorerState,
    onAction: (ExplorerActions) -> Unit,
    onDrawerButtonClicked: () -> Unit,
    shouldShowDrawer: Boolean,

    ) {
    var showMenu by remember { mutableStateOf(false) }
    var submenuFor by remember { mutableStateOf<AppBarAction?>(null) }
    val context = LocalContext.current
    val actions = getActionButtons()
    BoxWithConstraints {
        val maxActionsInBar = if (maxWidth > 400.dp) 3 else 1
        val inlineActions = actions.take(maxActionsInBar)
        val overflowActions = actions.drop(maxActionsInBar)
        TopAppBar(
            modifier = Modifier,
            actions = {
                // Inline actions
                inlineActions.forEach { action ->
                    IconButton(onClick = {
                        if (action.subActions.isNotEmpty()) {
                            submenuFor = action
//                            showSubMenu = true
                        } else {
//                            Toast.makeText(context, action.label, Toast.LENGTH_SHORT).show()
                             onAction(ExplorerActions.OnActionButtonPressed(action.type))
                        }
                    }) {
                        Icon(action.icon, contentDescription = action.label)
                    }
                }

                // Overflow menu
                if (overflowActions.isNotEmpty()) {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More")
                    }

                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = {
                            showMenu = false
                            submenuFor = null
//                            showSubMenu = false
                        }
                    ) {
                        overflowActions.forEach { action ->
                            if (action.subActions.isEmpty()) {
                                DropdownMenuItem(
                                    text = { Text(action.label) },
                                    onClick = {
                                        showMenu = false
//                                        Toast.makeText(
//                                            context,
//                                            action.label,
//                                            Toast.LENGTH_SHORT
//                                        ).show()
                                        onAction(ExplorerActions.OnActionButtonPressed(action.type))
                                    }
                                )
                            } else {
                                DropdownMenuItem(
                                    text = { Text("${action.label} ➤") },
                                    onClick = {
                                        submenuFor = action
                                        showMenu = false
//                                        showSubMenu = true
                                    }
                                )
                            }
//                            }
                        }

                    }



                }
                // Submenu (opens to the right)
                if (submenuFor != null) {
                    DropdownMenu(
                        expanded = true,
                        onDismissRequest = {
                            submenuFor = null
//                            showSubMenu = false
                        },
                        offset = DpOffset(150.dp, 0.dp) // shift to the right
                    ) {
                        submenuFor!!.subActions.forEach { subAction ->
                            DropdownMenuItem(
                                text = { Text(subAction.label) },
                                onClick = {
                                    submenuFor = null
                                    showMenu = false
//                                    showSubMenu = false
//                                    Toast.makeText(context, subAction.label, Toast.LENGTH_SHORT)
//                                        .show()
                                    onAction(ExplorerActions.OnActionButtonPressed(subAction.type))
                                }
                            )
                        }
                    }
                }

            },
            title = {
                if (state.pathHistory.isEmpty()) {
                    Text("Select A Path")
                } else {
                    Column {
                        if (state.currentFolderInfo != null) {
                            if (state.currentFolderInfo.name != "unknown") {
                                Text(state.currentFolderInfo.name)
                                Text(
                                    state.currentFolderInfo.absolutePath,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            } else {
                                Text(state.currentFolderInfo.absolutePath)
                            }
                        } else {
                            Text(
                                state.pathHistory.last(),
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
            },
            navigationIcon = {
                if (shouldShowDrawer) {
                    IconButton(
                        onClick = {
                            onDrawerButtonClicked()
                        },
                    ) {
                        Icon(Icons.Default.Menu, contentDescription = "Menu")
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
        )
    }
}