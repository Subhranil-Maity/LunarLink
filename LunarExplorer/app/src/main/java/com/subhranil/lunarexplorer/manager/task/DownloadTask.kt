package com.subhranil.lunarexplorer.manager.task

import android.os.Environment
import java.io.File
import java.io.FileOutputStream

class DownloadTask private constructor(
    val id: String,
    val fileName: String,
    val chunkSize: Long,
    val totalSize: Long,
    private val onStart: ((DownloadTask) -> Unit)?,
    private val onProgress: ((DownloadTask, Double) -> Unit)?,
    private val onComplete: (suspend (DownloadTask) -> Unit)?,
    private val onError: ((DownloadTask, Throwable) -> Unit)?,
    private val executor: suspend (DownloadTask, LongRange) -> Unit
) {
    @Volatile private var started = false
    @Volatile private var completed = false
    @Volatile private var progressValue = 0.0

    @Volatile private var file: File = createFile()
    private var outputStream: FileOutputStream = FileOutputStream(file, true)
    private var error: Throwable? = null

    fun getFile(): File = file

    fun isCompleted() = completed
    fun getError(): Throwable? = error

    private var downloadedBytes: Long = 0

    val progress: Double
        get() = progressValue


    fun fail(exception: Exception) {
        this.error = exception
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
        var finalFile = File(file.parent, fileName)
        var i = 1
        while (finalFile.exists()){
            finalFile = File(file.parent, "$fileName ($i)")
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



    suspend fun start() {
        try {
            started = true
            onStart?.invoke(this)

            // Generate ranges internally
            val ranges = (0 until (totalSize + chunkSize - 1) / chunkSize).map { idx ->
                val start = idx * chunkSize
                val end = minOf(start + chunkSize - 1, totalSize - 1)
                start..end
            }

            for (range in ranges) {
                executor(this, range) // pass range to executor
            }

            completed = true
            onComplete?.invoke(this)
        } catch (e: Throwable) {
            onError?.invoke(this, e)
        }
    }

    fun updateProgress(bytesDownloaded: Long) {
        downloadedBytes += bytesDownloaded
        progressValue = downloadedBytes.toDouble() / totalSize
    }

    fun poll() {
        if (started && !completed) {
            onProgress?.invoke(this, progressValue)
        }
    }

    // --- Builder pattern ---
    class Builder(
        private var id: String = "",
        private val url: String,
        private val outputFile: String
    ) {
        private var chunkSize: Long = 1024 * 1024 // default 1MB
        private var totalSize: Long = -1
        private var onStart: ((DownloadTask) -> Unit)? = null
        private var onProgress: ((DownloadTask, Double) -> Unit)? = null
        private var onComplete: (suspend (DownloadTask) -> Unit)? = null
        private var onError: ((DownloadTask, Throwable) -> Unit)? = null
        private var executor: (suspend (DownloadTask, LongRange) -> Unit)? = null

        fun chunkSize(size: Long) = apply { this.chunkSize = size }
        fun totalSize(size: Long) = apply { this.totalSize = size }
        fun onStart(block: (DownloadTask) -> Unit) = apply { this.onStart = block }
        fun onProgress(block: (DownloadTask, Double) -> Unit) = apply { this.onProgress = block }
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




