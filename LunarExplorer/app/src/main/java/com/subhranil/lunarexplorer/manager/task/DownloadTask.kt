package com.subhranil.lunarexplorer.manager.task

import android.os.Environment
import android.util.Log
import kotlinx.coroutines.flow.update
import java.io.File
import java.io.FileOutputStream

class DownloadTask private constructor(
    val id: String,
    val fileName: String,
    val chunkSize: Long,
    val totalSize: Long,
    private val onStart: ((DownloadTask) -> Unit)?,
    private val onProgress: ((DownloadTask, Float) -> Unit)?,
    private val onComplete: (suspend (DownloadTask) -> Unit)?,
    private val onError: ((DownloadTask, Throwable) -> Unit)?,
    private val executor: suspend (DownloadTask, LongRange) -> Unit
) {
    @Volatile private var state: DownloadState = DownloadState.PENDING

    @Volatile private var file: File = createFile()
    private var outputStream: FileOutputStream = FileOutputStream(file, true)
    private val ranges = (0 until (totalSize + chunkSize - 1) / chunkSize).map { idx ->
        val start = idx * chunkSize
        val end = minOf(start + chunkSize - 1, totalSize - 1)
        start..end
    }.toMutableList()

    fun getFile(): File = file

    fun isCompleted() = state == DownloadState.COMPLETED
    fun getState() = state

    private var downloadedBytes: Long = 0


    fun fail(exception: Exception) {
        state = DownloadState.FAILED(exception)
        onError?.invoke(this, exception)
    }
    private fun createFile(): File {
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val lunarDir = File(downloadsDir, "Lunar")

        if (!lunarDir.exists()) {
            lunarDir.mkdirs()
        }
        // Use temp name while downloading
        var fl = File(lunarDir, ".$fileName.downloading")
        if (fl.exists()) {
            fl = File(lunarDir, ".$fileName.downloading.${System.currentTimeMillis()}")
        }
        return fl
    }

    fun appendData(data: ByteArray) {
        outputStream.write(data)
        outputStream.flush()
    }
    private fun getAcceptedFileName(): File {
        val parentDir = file.parentFile ?: return file
        val dotIndex = fileName.lastIndexOf('.')
        val baseName: String
        val extension: String

        if (dotIndex != -1) {
            baseName = fileName.substring(0, dotIndex)
            extension = fileName.substring(dotIndex) // includes the dot, e.g. ".mp4"
        } else {
            baseName = fileName
            extension = ""
        }

        var finalFile = File(parentDir, fileName)
        var i = 1
        while (finalFile.exists()) {
            finalFile = File(parentDir, "$baseName ($i)$extension")
            i++
        }
        return finalFile
    }


    fun finalizeDownload() {
        outputStream.close()
        val finalFile = getAcceptedFileName()
        if (file.renameTo(finalFile)) {
            file = finalFile
        }
    }

    fun updateProgress(bytesDownloaded: Long) {
        downloadedBytes += bytesDownloaded
        if (state is DownloadState.PAUSED) return
        state = DownloadState.IN_PROGRESS(downloadedBytes.toFloat() / totalSize)
    }

    suspend fun poll() {
        when (state) {
            is DownloadState.IN_PROGRESS -> {
                if (ranges.isEmpty()) {
                    state = DownloadState.COMPLETED
                    onComplete?.invoke(this)
                    return
                }
                val range = ranges.removeFirstOrNull()!!
                executor(this, range)
                updateProgress(range.last - range.first + 1)
                onProgress?.invoke(this, (state as DownloadState.IN_PROGRESS).progress)
            }
            else -> {}
        }
    }

    fun start() {
        if (state is DownloadState.PENDING) {
            state = DownloadState.IN_PROGRESS(0.0F)
            onStart?.invoke(this)
        }
    }
    fun pause(){
        val currentState = state
        if (currentState is DownloadState.IN_PROGRESS){
            state = DownloadState.PAUSED(currentState.progress)
        }
    }
    fun resume(){
        val currentState = state
        if (currentState is DownloadState.PAUSED){
            state = DownloadState.IN_PROGRESS(currentState.pausedAt)
        }
    }
    // --- State ---
    sealed class DownloadState {
        object PENDING : DownloadState()
        class IN_PROGRESS(val progress: Float) : DownloadState()
        object COMPLETED : DownloadState()
        class FAILED(val error: Throwable) : DownloadState()

        class PAUSED(val pausedAt: Float) : DownloadState()

    }

    // --- Builder pattern ---
    class Builder(
        private var id: String = "",
        private val url: String,
        private val outputFile: String
    ) {
        private var chunkSize: Long = 1024 * 1024 *10 // default 10MB
        private var totalSize: Long = -1
        private var onStart: ((DownloadTask) -> Unit)? = null
        private var onProgress: ((DownloadTask, Float) -> Unit)? = null
        private var onComplete: (suspend (DownloadTask) -> Unit)? = null
        private var onError: ((DownloadTask, Throwable) -> Unit)? = null
        private var executor: (suspend (DownloadTask, LongRange) -> Unit)? = null

        fun chunkSize(size: Long) = apply { this.chunkSize = size }
        fun totalSize(size: Long) = apply { this.totalSize = size }
        fun onStart(block: (DownloadTask) -> Unit) = apply { this.onStart = block }
        fun onProgress(block: (DownloadTask, Float) -> Unit) = apply { this.onProgress = block }
        fun onComplete(block: suspend (DownloadTask) -> Unit) = apply { this.onComplete = block }
        fun onError(block: (DownloadTask, Throwable) -> Unit) = apply { this.onError = block }
        fun executor(block: suspend (DownloadTask, LongRange) -> Unit) = apply { this.executor = block }

        fun build(): DownloadTask {
            if (totalSize <= 0) error("totalSize must be provided and > 0")
            val exec = executor ?: error("Executor must be provided!")
            if (id == ""){
                id = List(16) { ('a'..'z').random() }.joinToString("")
            }
            return DownloadTask(
                id,
                fileName = outputFile,
                chunkSize,
                totalSize,
                onStart,
                onProgress,
                onComplete,
                onError,
                exec
            )
        }
    }
}




