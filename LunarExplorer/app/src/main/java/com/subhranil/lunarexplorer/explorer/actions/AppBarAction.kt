package com.subhranil.lunarexplorer.explorer.actions

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import com.subhranil.lunarexplorer.R
import com.subhranil.lunarexplorer.explorer.AppBarActionEnum

data class AppBarAction(
    val label: String,
    val icon: ImageVector,
    val type: AppBarActionEnum,
    val subActions: List<AppBarAction> = emptyList()
)

@Composable
fun getActionButtons(modifier: Modifier = Modifier): List<AppBarAction>{
    val settingAction = AppBarAction(
        label = "Setting",
        icon = Icons.Default.Settings,
        type = AppBarActionEnum.SEARCH

    )
    val createFile = AppBarAction(
        label = "File",
        icon = ImageVector.vectorResource(R.drawable.unknown_file_icon),
        type = AppBarActionEnum.CREATE_FILE
    )
    val createFolder = AppBarAction(
        label = "Folder",
        icon = ImageVector.vectorResource(R.drawable.folder_icon),
        type = AppBarActionEnum.CREATE_FOLDER
    )
    val create = AppBarAction(
        label = "Create",
        icon = Icons.Default.AddCircle,
        type = AppBarActionEnum.CREATE,
        subActions = listOf(
            createFile,
            createFolder
        )
    )

    return listOf(
        settingAction,
        create
    )
}


