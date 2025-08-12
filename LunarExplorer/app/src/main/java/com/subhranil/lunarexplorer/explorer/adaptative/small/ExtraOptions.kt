package com.subhranil.lunarexplorer.explorer.adaptative.small

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.subhranil.lunarexplorer.explorer.ExplorerActions


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExtraOptionsDrawer(
    modifier: Modifier = Modifier,
    shouldShow: Boolean,
    onAction: (ExplorerActions) -> Unit,
    content: @Composable () -> Unit = {}
) {
    val sheetState = rememberModalBottomSheetState()
//    val scope = rememberCoroutineScope()
//    val context = LocalContext.current
    if (shouldShow) {
        ModalBottomSheet(
            modifier = modifier,
            sheetState = sheetState,
            onDismissRequest = {
//                Toast.makeText(context, "Dismised By Dismiss", Toast.LENGTH_SHORT).show()

                onAction(ExplorerActions.OnOptionsDismiss)
            },
            dragHandle = null
        ) {
            content()
        }
    }
}
