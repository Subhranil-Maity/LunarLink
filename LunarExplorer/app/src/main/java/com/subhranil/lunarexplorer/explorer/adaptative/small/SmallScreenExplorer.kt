package com.subhranil.lunarexplorer.explorer.adaptative.small

import android.R.attr.maxWidth
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.subhranil.lunarexplorer.explorer.ExplorerActions
import kotlinx.coroutines.launch


@Composable
fun SmallScreenExplorer(
    modifier: Modifier = Modifier,
    isSelected: Boolean = false,
    appBar: @Composable () -> Unit,
    listPane: @Composable () -> Unit,
    detailPlane: @Composable () -> Unit,
    drawerState: DrawerState,
    onAction: (ExplorerActions) -> Unit,
    extraOptionsPage: @Composable () -> Unit = {},
    shouldShowExtraOptions: Boolean = false
) {
    ExtraOptionsDrawer(
        shouldShow = shouldShowExtraOptions,
        onAction = onAction,
        content = extraOptionsPage
    )

    if (!isSelected) {
        Box(modifier = modifier.fillMaxSize()) {
            listPane()
        }
        return
    }
    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            Box(
                Modifier
                    .fillMaxHeight()
                    .width(500.dp)
                    .background(MaterialTheme.colorScheme.background)
            ) {
                listPane()
            }
        },
        content = {
            Scaffold(
                topBar = {
                    appBar()
                }
            ) { padding ->
                Box(
                    modifier = Modifier
                        .padding(padding)
                        .fillMaxSize()
                ) {
                    detailPlane()
                }
            }
        },
    )
}