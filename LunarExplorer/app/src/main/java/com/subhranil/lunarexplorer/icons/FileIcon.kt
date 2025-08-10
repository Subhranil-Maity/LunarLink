package com.subhranil.lunarexplorer.icons

import android.util.Log
import androidx.annotation.DrawableRes
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import com.subhranil.lunarexplorer.R


// Precompute a mapping of extensions to FileEntryType
private val extensionToTypeMap: Map<String, FileEntryType> = mapOf(
    *IMAGE_EXTENSIONS.map { it to FileEntryType.IMAGE }.toTypedArray(),
    *VIDEO_EXTENSIONS.map { it to FileEntryType.VIDEO }.toTypedArray(),
    *AUDIO_EXTENSIONS.map { it to FileEntryType.AUDIO }.toTypedArray(),
    *DOCUMENT_EXTENSIONS.map { it to FileEntryType.DOCUMENT }.toTypedArray(),
    *PDF_EXTENSIONS.map { it to FileEntryType.PDF }.toTypedArray(),
    *SPREADSHEET_EXTENSIONS.map { it to FileEntryType.SPREADSHEET }.toTypedArray(),
    *PRESENTATION_EXTENSIONS.map { it to FileEntryType.PRESENTATION }.toTypedArray(),
    *ARCHIVE_EXTENSIONS.map { it to FileEntryType.ARCHIVE }.toTypedArray(),
    *EXECUTABLE_EXTENSIONS.map { it to FileEntryType.EXECUTABLE }.toTypedArray(),
    *TEXT_EXTENSIONS.map { it to FileEntryType.TEXT }.toTypedArray(),
    *SYSTEM_EXTENSIONS.map { it to FileEntryType.SYSTEM }.toTypedArray(),
    *DWG.map { it to FileEntryType.DWG }.toTypedArray()
)

@Composable
fun getFileIcon(fileName: String): Pair<ImageVector, Color> {
     return fromFileName(fileName).getIcon()
}


@Composable
fun fromFileName(fileName: String): FileEntryType {
    Log.d("FileIcon", "Getting file type for $fileName")
    val extension = fileName.substringAfterLast('.', "").lowercase()

    val type = extensionToTypeMap[extension] ?: FileEntryType.UNKNOWN
    Log.d("FileIcon", "File type for $fileName: $type")
    return type
}

enum class FileEntryType(
    @DrawableRes
    private val icon: Int? = null,
    private val color: Color? = null
) {
    UNKNOWN,
    IMAGE(R.drawable.image_icon),
    VIDEO(R.drawable.video_icon),
    AUDIO(R.drawable.audio_icon),
    DOCUMENT(R.drawable.google_doc_icon, Color.Unspecified),
    PDF(R.drawable.pdf_icon, Color.Red.copy(alpha = 0.8f)),
    SPREADSHEET(R.drawable.google_sheets_icon, Color.Unspecified),
    DWG(R.drawable.dwg_icon, Color.Unspecified),
    PRESENTATION(R.drawable.google_slides_icon, Color.Unspecified),
    ARCHIVE,
    EXECUTABLE,
    TEXT,
    SYSTEM;

    @Composable
    fun getIcon(): Pair<ImageVector, Color> {
        return Pair(
            ImageVector.vectorResource(icon ?: R.drawable.unknown_file_icon),
            color ?: MaterialTheme.colorScheme.primary
        )
    }
}