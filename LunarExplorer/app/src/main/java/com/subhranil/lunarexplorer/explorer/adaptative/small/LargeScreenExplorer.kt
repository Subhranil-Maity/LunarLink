package com.subhranil.lunarexplorer.explorer.adaptative.small


import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DrawerState
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

@Composable
fun LargeScreenExplorer(
    modifier: Modifier = Modifier,
    appBar: @Composable () -> Unit,
    listPane: @Composable () -> Unit,
    detailPlane: @Composable () -> Unit,
) {
    Scaffold(
        topBar = {
            appBar()
        },
    ) { innerPadding ->
        val density = LocalDensity.current
        val top = with(density){
            (innerPadding.calculateTopPadding().toPx() - innerPadding.calculateBottomPadding().toPx() - 20).toDp()
        }
        Row(
            modifier = modifier.padding(
                start = innerPadding.calculateStartPadding(LayoutDirection.Ltr),
                end = innerPadding.calculateEndPadding(LayoutDirection.Ltr),
                bottom = innerPadding.calculateBottomPadding(),
                top = top
            )
        ) {
            Box(
                modifier = Modifier.weight(0.2f)
//                    .border(2.dp, androidx.compose.ui.graphics.Color.Yellow)
            ) {
                listPane()
            }
            Column(
                modifier = Modifier.weight(0.8f)
//                    .border(2.dp, androidx.compose.ui.graphics.Color.Red)
            ) {
                Box(Modifier) {
                    detailPlane()
                }
            }
        }
    }
}