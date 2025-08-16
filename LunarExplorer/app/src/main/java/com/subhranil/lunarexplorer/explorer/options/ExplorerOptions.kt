package com.subhranil.lunarexplorer.explorer.options

import androidx.compose.material.icons.Icons
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import com.subhranil.lunarexplorer.R
import com.subhranil.lunarexplorer.explorer.AppBarActionEnum

data class ExplorerOptions(
    val label: String,
    val icon: ImageVector,
    val type: ExplorerOptionType,
)

enum class ExplorerOptionType {
    MOVE,
    COPY,
    DELETE,
    RENAME,
    DOWNLOAD
}

@Composable
fun getExplorerOptions(): List<ExplorerOptions>{

    val copy = ExplorerOptions(
        label = "Copy",
        icon = ImageVector.vectorResource(R.drawable.copy_icon),
        type = ExplorerOptionType.COPY
    )
    val rename = ExplorerOptions(
        label = "Rename",
        icon = ImageVector.vectorResource(R.drawable.rename_icon),
        type = ExplorerOptionType.RENAME
    )
    val move = ExplorerOptions(
        label = "Move",
        icon = ImageVector.vectorResource(R.drawable.move_folder_icon),
        type = ExplorerOptionType.MOVE
    )
    val delete = ExplorerOptions(
        label = "Delete",
        icon = ImageVector.vectorResource(R.drawable.delete_icon),
        type = ExplorerOptionType.DELETE
    )

    return listOf(
        copy,
        move,
        rename,
        delete
    )
}