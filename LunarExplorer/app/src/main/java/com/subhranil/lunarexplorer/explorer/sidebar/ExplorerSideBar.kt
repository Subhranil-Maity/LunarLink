package com.subhranil.lunarexplorer.explorer.sidebar

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.subhranil.lunarexplorer.explorer.ExplorerActions
import com.subhranil.lunarexplorer.explorer.components.LinearDrive
import com.subhranil.lunarexplorer.explorer.components.LinearEntry
import com.subhranil.lunarexplorer.models.LunarMetaData
import com.subhranil.lunarexplorer.models.Mounts

@Composable
fun ExplorerSideBar(
    modifier: Modifier = Modifier,
    mounts: Mounts,
    onAction: (ExplorerActions) -> Unit
) {
    Column(
        modifier = modifier
            .padding(10.dp)
    ) {
        Text("Drives", style = MaterialTheme.typography.titleLarge)
        for (drive in mounts.drives) {
            LinearDrive(drive){
                onAction(ExplorerActions.GoToPath(drive.mountPoint))
            }
        }
        Text("Shortcuts", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(12.dp))
        for (data in mounts.mounts){
            LinearEntry(metaData = data){
                onAction(ExplorerActions.GoToPath(data.absolutePath))
            }
        }
    }
}



