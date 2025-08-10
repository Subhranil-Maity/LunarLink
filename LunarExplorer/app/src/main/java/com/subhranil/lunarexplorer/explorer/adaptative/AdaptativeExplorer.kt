package com.subhranil.lunarexplorer.explorer.adaptative

import android.app.Activity
import androidx.activity.compose.LocalActivity
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import com.subhranil.lunarexplorer.explorer.adaptative.small.SmallScreenExplorer

@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
fun AdaptativeExplorer(
    modifier: Modifier = Modifier,
    isSelected: Boolean = false,
    drawerState: DrawerState,
    appBar: @Composable (DrawerState) -> Unit,
    listPane: @Composable () -> Unit,
    detailPlane: @Composable () -> Unit
) {
    val activity = LocalActivity.current as Activity
    val windowSize = calculateWindowSizeClass(activity)
    val scope = rememberCoroutineScope()
    when(windowSize.widthSizeClass){
        WindowWidthSizeClass.Compact -> {
            SmallScreenExplorer(
                modifier = modifier,
                isSelected = isSelected,
                appBar = appBar,
                listPane = listPane,
                detailPlane = detailPlane,
                drawerState = drawerState
            )
        }
        WindowWidthSizeClass.Medium,
        WindowWidthSizeClass.Expanded -> {
            Text("NotYetImplemented")
        }
    }

}