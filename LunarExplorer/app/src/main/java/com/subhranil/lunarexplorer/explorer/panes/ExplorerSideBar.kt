package com.subhranil.lunarexplorer.explorer.panes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.subhranil.lunarexplorer.explorer.ExplorerActions
import com.subhranil.lunarexplorer.explorer.ExplorerState
import com.subhranil.lunarexplorer.explorer.components.DriveInfo
import com.subhranil.lunarexplorer.explorer.components.DirInfo
import com.subhranil.lunarexplorer.explorer.components.LinearDrive
import com.subhranil.lunarexplorer.explorer.components.LinearShotCut

@Composable
fun ExplorerSideBar(
    modifier: Modifier = Modifier,
    state: ExplorerState,
    onAction: (ExplorerActions) -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .padding(10.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text("Drives", style = MaterialTheme.typography.titleLarge)
            for (drive in state.mounts.drives) {
                LinearDrive(drive) {
                    onAction(ExplorerActions.GoToPath(drive.mountPoint))
                }
            }

            Text("Shortcuts", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(12.dp))
            for (data in state.mounts.mounts) {
                LinearShotCut(metaData = data) {
                    onAction(ExplorerActions.GoToPath(data.absolutePath))
                }
            }
        }

        Box(
            Modifier.fillMaxWidth()
        ) {
            state.currentFolderInfo?.let { info ->
                val drive = state.mounts.drives.find { it.mountPoint == info.absolutePath }
                if (drive != null) {
                    DriveInfo(metaData = info, drive = drive)
                } else {
                    DirInfo(metaData = info)
                }
            }
        }
    }
}




