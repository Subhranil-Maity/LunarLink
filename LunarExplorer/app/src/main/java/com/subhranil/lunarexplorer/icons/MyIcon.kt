package com.subhranil.lunarexplorer.icons


import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.subhranil.lunarexplorer.models.EntryType
import com.subhranil.lunarexplorer.models.LunarMetaData


@Composable
fun getIcon(metaData: LunarMetaData): Pair<ImageVector, Color>  = getIcon(metaData.name, metaData.entryType)
@Composable
fun getIcon(name: String): Pair<ImageVector, Color>  = getIcon(name, EntryType.FILE)
@Composable
fun getIcon(name: String, entryType: EntryType): Pair<ImageVector, Color> {
    val cache = remember {
        mutableMapOf<String, Pair<ImageVector, Color>>()
    }
    return cache.getOrPut(name) {
        when(entryType){
            EntryType.DIRECTORY -> getDirectoryIcon(folderName = name)
            EntryType.FILE -> getFileIcon(fileName = name)
            EntryType.SYMLINK -> getSymlinkIcon()
            EntryType.OTHER -> getSymlinkIcon()
        }
    }
}
