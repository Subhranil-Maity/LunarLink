package com.subhranil.lunarexplorer.data.download

import android.content.ContentValues
import android.content.Context
import android.os.Bundle
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import androidx.core.net.toUri
import java.io.Closeable
import java.io.FileOutputStream
import java.io.IOException

/** Writes bytes to one local file. */
interface DownloadWriter : Closeable {
    fun write(buffer: ByteArray, length: Int)
}

/** Where downloaded files are stored. Uris are strings so records stay serializable and tests need no Android. */
interface DownloadFiles {
    /** Creates a new, hidden file for [name] and returns its uri. */
    fun create(name: String): String

    fun exists(uri: String): Boolean

    /** Opens [uri] for writing at [offset]; anything after [offset] is discarded. */
    fun open(uri: String, offset: Long): DownloadWriter

    /** Makes a finished file visible to other apps. */
    fun publish(uri: String)

    fun delete(uri: String)
}

/**
 * Saves into the shared `Download/Lunar` folder through MediaStore, which needs no storage permission.
 * Files stay pending (hidden from other apps) until [publish]. MediaStore picks a unique name when one is taken.
 */
class MediaStoreDownloadFiles(context: Context) : DownloadFiles {
    private val resolver = context.contentResolver

    override fun create(name: String): String {
        // MediaStore may change the extension when the type doesn't match it, so derive the type from the name.
        val ext = name.substringAfterLast('.', "").lowercase()
        val type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, name)
            put(MediaStore.Downloads.MIME_TYPE, type)
            put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/$FOLDER")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: throw IOException("Couldn't create $name in Downloads")
        return uri.toString()
    }

    override fun exists(uri: String): Boolean = runCatching {
        // Pending items are left out of queries unless asked for.
        val args = Bundle().apply { putInt(MediaStore.QUERY_ARG_MATCH_PENDING, MediaStore.MATCH_INCLUDE) }
        resolver.query(uri.toUri(), arrayOf(MediaStore.Downloads._ID), args, null)?.use { it.count > 0 } ?: false
    }.getOrDefault(false)

    override fun open(uri: String, offset: Long): DownloadWriter {
        val pfd = resolver.openFileDescriptor(uri.toUri(), "rw") ?: throw IOException("Couldn't open $uri")
        return try {
            FileWriter(pfd, offset)
        } catch (e: Exception) {
            pfd.close()
            throw e
        }
    }

    override fun publish(uri: String) {
        val values = ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }
        resolver.update(uri.toUri(), values, null, null)
    }

    override fun delete(uri: String) {
        runCatching { resolver.delete(uri.toUri(), null, null) }
    }

    private class FileWriter(private val pfd: ParcelFileDescriptor, offset: Long) : DownloadWriter {
        private val stream = FileOutputStream(pfd.fileDescriptor)
        private val channel = stream.channel.apply {
            truncate(offset)
            position(offset)
        }

        override fun write(buffer: ByteArray, length: Int) = stream.write(buffer, 0, length)

        override fun close() {
            try {
                channel.force(false)
                stream.close()
            } finally {
                pfd.close()
            }
        }
    }

    companion object {
        const val FOLDER = "Lunar"
    }
}
