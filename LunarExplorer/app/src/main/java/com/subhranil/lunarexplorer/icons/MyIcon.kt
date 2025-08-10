package com.subhranil.lunarexplorer.icons


import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.subhranil.lunarexplorer.models.EntryType
import com.subhranil.lunarexplorer.models.LunarMetaData

@Composable
fun getIcon(metaData: LunarMetaData): Pair<ImageVector, Color> {
    val cache = remember {
        mutableMapOf<String, Pair<ImageVector, Color>>()
    }
    return cache.getOrPut(metaData.name) {
        when(metaData.entryType){
            EntryType.DIRECTORY -> getDirectoryIcon(folderName = metaData.name)
            EntryType.FILE -> getFileIcon(fileName = metaData.name)
            EntryType.SYMLINK -> getSymlinkIcon()
            EntryType.OTHER -> getSymlinkIcon()
        }
    }
}
