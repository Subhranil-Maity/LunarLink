package com.subhranil.lunarexplorer.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.subhranil.lunarexplorer.R
import com.subhranil.lunarexplorer.data.api.Entry
import com.subhranil.lunarexplorer.data.api.EntryKind
import com.subhranil.lunarexplorer.util.FileCategory
import com.subhranil.lunarexplorer.util.fileCategory

@DrawableRes
fun entryIconRes(entry: Entry): Int = when {
    entry.kind == EntryKind.DIR -> R.drawable.folder_icon
    entry.isDirLike -> R.drawable.folder_symlink_icon
    else -> when (fileCategory(entry.name)) {
        FileCategory.IMAGE -> R.drawable.image_icon
        FileCategory.VIDEO -> R.drawable.video_icon
        FileCategory.AUDIO -> R.drawable.audio_icon
        FileCategory.DOCUMENT -> R.drawable.google_doc_icon
        FileCategory.PDF -> R.drawable.pdf_icon
        FileCategory.SPREADSHEET -> R.drawable.google_sheets_icon
        FileCategory.PRESENTATION -> R.drawable.google_slides_icon
        FileCategory.CAD -> R.drawable.dwg_icon
        else -> R.drawable.unknown_file_icon
    }
}

/** Multi-colored artwork keeps its own colors; everything else follows the theme. */
private fun keepsOwnColors(@DrawableRes res: Int) = res in setOf(
    R.drawable.google_doc_icon,
    R.drawable.google_sheets_icon,
    R.drawable.google_slides_icon,
    R.drawable.dwg_icon,
)

@Composable
fun EntryIcon(entry: Entry, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    val res = entryIconRes(entry)
    val tint = when {
        keepsOwnColors(res) -> Color.Unspecified
        entry.isDirLike -> MaterialTheme.colorScheme.primary
        res == R.drawable.pdf_icon -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Icon(painterResource(res), contentDescription = null, tint = tint, modifier = modifier.size(size))
}
