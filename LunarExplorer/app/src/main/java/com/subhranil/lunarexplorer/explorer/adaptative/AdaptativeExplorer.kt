package com.subhranil.lunarexplorer.explorer.adaptative

import android.app.Activity
import androidx.activity.compose.LocalActivity
import androidx.compose.material3.DrawerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.subhranil.lunarexplorer.explorer.ExplorerActions
import com.subhranil.lunarexplorer.explorer.adaptative.large.LargeScreenExplorer
import com.subhranil.lunarexplorer.explorer.adaptative.small.SmallScreenExplorer


//data class AdaptativeAppBarState(
//    val title: @Composable () -> Unit,
//)

@OptIn(ExperimentalMaterial3WindowSizeClassApi::class, ExperimentalMaterial3Api::class)
@Composable
fun AdaptativeExplorer(
    modifier: Modifier = Modifier,
    isSelected: Boolean = false,
    drawerState: DrawerState,
    topAppBar: @Composable (Boolean) -> Unit,
    listPane: @Composable () -> Unit,
    detailPlane: @Composable () -> Unit,
    extraOptionsPage: @Composable () -> Unit = {},
    onAction: (ExplorerActions) -> Unit,
    shouldShowExtraOptions: Boolean = false
) {
    val activity = LocalActivity.current as Activity
    val windowSize = calculateWindowSizeClass(activity)
    when (windowSize.widthSizeClass) {
        WindowWidthSizeClass.Compact -> {
            SmallScreenExplorer(
                modifier = modifier,
                isSelected = isSelected,
                appBar = {
                    topAppBar(true)
                },
                listPane = listPane,
                detailPlane = detailPlane,
                drawerState = drawerState,
                extraOptionsPage = extraOptionsPage,
                shouldShowExtraOptions = shouldShowExtraOptions,
                onAction = onAction
            )
        }

        WindowWidthSizeClass.Medium,
        WindowWidthSizeClass.Expanded -> {
            LargeScreenExplorer(
                modifier = modifier,
                appBar = {
                    topAppBar(false)
                },
                listPane = listPane,
                detailPlane = detailPlane,
                extraOptionsPage = extraOptionsPage,
                shouldShowExtraOptions = shouldShowExtraOptions
            )
        }
    }

}