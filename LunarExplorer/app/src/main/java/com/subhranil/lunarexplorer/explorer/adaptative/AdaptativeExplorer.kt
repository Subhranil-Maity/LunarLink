package com.subhranil.lunarexplorer.explorer.adaptative

import android.app.Activity
import androidx.activity.compose.LocalActivity
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import com.subhranil.lunarexplorer.explorer.adaptative.small.LargeScreenExplorer
import com.subhranil.lunarexplorer.explorer.adaptative.small.SmallScreenExplorer
import kotlinx.coroutines.launch


data class AdaptativeAppBarState(
    val title: @Composable () -> Unit
)

@OptIn(ExperimentalMaterial3WindowSizeClassApi::class, ExperimentalMaterial3Api::class)
@Composable
fun AdaptativeExplorer(
    modifier: Modifier = Modifier,
    isSelected: Boolean = false,
    drawerState: DrawerState,
//    appBar: @Composable () -> Unit,
    adaptativeAppBarState: AdaptativeAppBarState,
    listPane: @Composable () -> Unit,
    detailPlane: @Composable () -> Unit
) {
    val activity = LocalActivity.current as Activity
    val windowSize = calculateWindowSizeClass(activity)
    val scope = rememberCoroutineScope()
    when (windowSize.widthSizeClass) {
        WindowWidthSizeClass.Compact -> {
            SmallScreenExplorer(
                modifier = modifier,
                isSelected = isSelected,
                appBar = {
                    TopAppBar(
                        modifier = Modifier
//                    .border(2.dp, androidx.compose.ui.graphics.Color.Cyan)
                        ,

                        title = {
                            adaptativeAppBarState.title()
                        },
                        navigationIcon = {
                            IconButton(
                                onClick = {
                                    scope.launch { drawerState.open() }
                                },
                            ) {
                                Icon(Icons.Default.Menu, contentDescription = "Menu")
                            }
                        }
                    )
                },
                listPane = listPane,
                detailPlane = detailPlane,
                drawerState = drawerState
            )
        }

        WindowWidthSizeClass.Medium,
        WindowWidthSizeClass.Expanded -> {
            LargeScreenExplorer(
                modifier = modifier,
                appBar = {
                    TopAppBar(
                        modifier = Modifier
//                    .border(2.dp, androidx.compose.ui.graphics.Color.Cyan)
                        ,

                        title = {
                            adaptativeAppBarState.title()
                        },
                        navigationIcon = {

                        }
                    )
                },
                listPane = listPane,
                detailPlane = detailPlane
            )
        }
    }

}