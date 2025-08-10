package com.subhranil.lunarexplorer.icons

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import com.subhranil.lunarexplorer.R

@Composable
fun getDirectoryIcon(folderName: String): Pair<ImageVector, Color> {
    return Pair(
        ImageVector.vectorResource(R.drawable.folder_icon),
        MaterialTheme.colorScheme.primary
    )
}