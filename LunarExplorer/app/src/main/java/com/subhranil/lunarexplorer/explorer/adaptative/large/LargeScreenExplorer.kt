package com.subhranil.lunarexplorer.explorer.adaptative.large


import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

@Composable
fun LargeScreenExplorer(
    modifier: Modifier = Modifier,
    appBar: @Composable () -> Unit,
    listPane: @Composable () -> Unit,
    detailPlane: @Composable () -> Unit,
    extraOptionsPage: @Composable () -> Unit = {},
    shouldShowExtraOptions: Boolean = false
) {
    val showLargeExtraOptionsInSideBar = true
    Scaffold(
        topBar = { appBar() }
    ) { innerPadding ->
        val density = LocalDensity.current
        val top = with(density) {
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
            val col = MaterialTheme.colorScheme.secondary
            Box(
                modifier = Modifier
                    .weight(0.2f)
                    .fillMaxHeight()
                    .drawBehind {
                        val strokeWidth = 0.2.dp.toPx()
                        val color = col
                        drawLine(
                            color = color,
                            start = Offset(size.width, 0f),
                            end = Offset(size.width, size.height),
                            strokeWidth = strokeWidth
                        )
                    }
            ) {
                listPane()
            }
            val detailWeight by animateFloatAsState(
                targetValue = if (showLargeExtraOptionsInSideBar && shouldShowExtraOptions) 0.6f else 0.8f,
                animationSpec = tween(300)
            )

            val extraWeight by animateFloatAsState(
                targetValue = if (showLargeExtraOptionsInSideBar && shouldShowExtraOptions) 0.4f else 0f,
                animationSpec = tween(300)
            )
            // Animate weights for detail and extra pane
            Row(
                modifier = Modifier.weight(0.8f)
            ) {
                Box(
                    modifier = Modifier.weight(detailWeight)
                ) {
                    detailPlane()
                }

                // Only show extra pane when weight > 0 to avoid layout issues
                if (extraWeight > 0f) {
                    AnimatedVisibility(
                        visible = shouldShowExtraOptions,
                        enter = slideInHorizontally(
                            initialOffsetX = { fullWidth -> fullWidth },
                            animationSpec = tween(300)
                        ),
                        exit = slideOutHorizontally(
                            targetOffsetX = { fullWidth -> fullWidth },
                            animationSpec = tween(300)
                        ),
                        modifier = Modifier
                            .weight(extraWeight)
                            .fillMaxHeight()
                    ) {
                        Box(Modifier.fillMaxSize()) {
                            extraOptionsPage()
                        }
                    }
                }
            }
        }
    }
}
