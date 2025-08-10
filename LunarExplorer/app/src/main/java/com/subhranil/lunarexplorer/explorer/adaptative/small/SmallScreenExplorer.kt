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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch


@Composable
fun SmallScreenExplorer(
    modifier: Modifier = Modifier,
    isSelected: Boolean = false,
    appBar: @Composable (DrawerState) -> Unit,
    listPane: @Composable () -> Unit,
    detailPlane: @Composable () -> Unit,
    drawerState: DrawerState
) {

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
                    appBar(drawerState)
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