package com.subhranil.lunarexplorer.io
import android.content.ContentValues
import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.URLConnection

/**
 * Guess MIME type from filename (extension). Returns "application/octet-stream" if unknown.
 */
fun guessMimeType(filename: String): String {
    // try MimeTypeMap first
    val ext = MimeTypeMap.getFileExtensionFromUrl(filename)?.lowercase()?.takeIf { it.isNotBlank() }
    if (ext != null) {
        MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)?.let { return it }
    }

    // fallback to URLConnection
    URLConnection.guessContentTypeFromName(filename)?.let { return it }

    // final fallback
    return "application/octet-stream"
}

/**
 * Save bytes to Downloads with guessed MIME type from filename.
 * Returns the content Uri of the saved file on success.
 *
 * Call from a coroutine (e.g., viewModelScope.launch { ... }).
 */
suspend fun saveBytesToDownloadsWithGuessedMime(
    context: Context,
    data: ByteArray,
    filename: String
): android.net.Uri = withContext(Dispatchers.IO) {
    val mimeType = guessMimeType(filename)

    val resolver = context.contentResolver
    val values = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
        put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
        // place in primary Downloads folder
        put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
        // mark pending while we write
        put(MediaStore.Downloads.IS_PENDING, 1)
    }

    val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
    val uri = resolver.insert(collection, values)
        ?: throw IOException("Failed to create MediaStore entry for $filename")

    try {
        resolver.openOutputStream(uri).use { out ->
            if (out == null) throw IOException("Failed to open output stream for $uri")
            out.write(data)
            out.flush()
        }

        // mark as complete/visible
        values.clear()
        values.put(MediaStore.Downloads.IS_PENDING, 0)
        resolver.update(uri, values, null, null)

        return@withContext uri
    } catch (e: Exception) {
        // cleanup partial entry
        resolver.delete(uri, null, null)
        throw e
    }
}
