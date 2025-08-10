package com.subhranil.lunarexplorer.explorer.sidebar

import android.app.Activity
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.PermanentNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.java.KoinJavaComponent.get


// TODO: IN FUTURE THIS WILL BE IMPLEMENTED
@OptIn(ExperimentalMaterial3WindowSizeClassApi::class, ExperimentalMaterial3Api::class)
@Composable
fun AdaptiveListDetailScaffold(
    modifier: Modifier = Modifier,
    listPane: @Composable () -> Unit,
    detailPane: @Composable () -> Unit,
    isItemSelected: Boolean,
    onOpenDrawer: (() -> Unit)? = null,
    sidebarCollapsed: Boolean = false,
    onSidebarToggle: (() -> Unit)? = null
) {
    val activity = LocalActivity.current as Activity
    val windowSize = calculateWindowSizeClass(activity)
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    when (windowSize.widthSizeClass) {

        // ---------- SMALL SCREEN ----------
        WindowWidthSizeClass.Compact -> {
            if (!isItemSelected) {
                // Show list fullscreen
                Box(modifier = modifier.fillMaxSize()) {
                    listPane()
                }
            } else {
                // Show detail fullscreen with drawer
                ModalNavigationDrawer(
                    drawerState = drawerState,
                    drawerContent = {
                        Box(Modifier.fillMaxHeight()) {
                            listPane()
                        }
                    }
                ) {
                    Scaffold(
                        topBar = {
                            TopAppBar(
                                title = { Text("Dummy Title") },
                                navigationIcon = {
                                    IconButton(onClick = {
                                        scope.launch { drawerState.open() }
                                    }) {
                                        Icon(Icons.Default.Menu, contentDescription = "Menu")
                                    }
                                }
                            )
                        }
                    ) { padding ->
                        Box(
                            modifier = Modifier
                                .padding(padding)
                                .fillMaxSize()
                        ) {
                            detailPane()
                        }
                    }
                }
            }
        }

        // ---------- MEDIUM / LARGE SCREEN ----------
        WindowWidthSizeClass.Medium,
        WindowWidthSizeClass.Expanded -> {
            Row(modifier = modifier.fillMaxSize()) {

                if (!sidebarCollapsed) {
                    PermanentNavigationDrawer(
                        drawerContent = {
                            Box(Modifier.width(240.dp)) {
                                listPane()
                            }
                        }
                    ) { /* main content placed after */ }
                } else {
                    NavigationRail {
                        onSidebarToggle?.let { toggle ->
                            IconButton(onClick = toggle) {
                                Icon(Icons.Default.Menu, contentDescription = "Toggle Sidebar")
                            }
                        }
                        // Optional: add items from listPane as icons
                    }
                }

                Box(modifier = Modifier.weight(1f)) {
                    detailPane()
                }
            }
        }
    }
}
