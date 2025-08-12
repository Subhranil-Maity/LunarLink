package com.subhranil.lunarexplorer.explorer.adaptative.small

import android.content.Context
import android.widget.Toast
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.subhranil.lunarexplorer.explorer.ExplorerActions
import kotlinx.coroutines.launch
import org.koin.core.context.GlobalContext.get


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

                onAction(ExplorerActions.OnExtraOptionsDisMiss)
            },
            dragHandle = null
        ) {
            content()
        }
    }
}
