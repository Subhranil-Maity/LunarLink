package com.subhranil.lunarexplorer.manager

import android.annotation.SuppressLint
import com.subhranil.lunarexplorer.manager.task.DownloadTask
import kotlinx.coroutines.*

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.FileProvider
import com.subhranil.lunarexplorer.io.guessMimeType
import java.io.File

@SuppressLint("MissingPermission")

class DownloadManager(private val context: Context) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val activeTasks = mutableListOf<DownloadTask>()
    private val queuedTasks = mutableListOf<DownloadTask>()
    private val completedTasks = mutableListOf<DownloadTask>()
    private val notificationIdMap = mutableMapOf<DownloadTask, Int>()
    private var notificationCounter = 1
    private var maxDownloadsAtOnce = 3

    private val notificationManager = NotificationManagerCompat.from(context)

    init {
        val channel = NotificationChannel(
            "downloads",
            "Downloads",
            NotificationManager.IMPORTANCE_LOW
        )
        notificationManager.createNotificationChannel(channel)
    }

    fun addToDownloadQueue(task: DownloadTask) {
        queuedTasks.add(task)
    }

    fun startNextDownload() {
        val nextTask = queuedTasks.firstOrNull() ?: return
        queuedTasks.removeAt(0)
        activeTasks.add(nextTask)
        notificationIdMap[nextTask] = notificationCounter++
        showNotification(nextTask, 0f)
        scope.launch {
            nextTask.start()
        }
    }


    fun startPolling(intervalMs: Long = 0) {
        scope.launch  {
            while (isActive) {
                if (activeTasks.size < maxDownloadsAtOnce) startNextDownload()
                if (activeTasks.isEmpty()) continue
                val iterator = activeTasks.iterator()
                while (iterator.hasNext()) {
                    val task = iterator.next()
                    val state = task.getState()
                    when (state) {
                        DownloadTask.DownloadState.COMPLETED -> {
                            updateNotification(task, 1f, true)
                            completedTasks.add(task)
                            iterator.remove()
                        }
                        is DownloadTask.DownloadState.FAILED -> {}
                        is DownloadTask.DownloadState.IN_PROGRESS -> {
                            task.poll()
                            updateNotification(task, state.progress, false)
                        }
                        DownloadTask.DownloadState.PENDING -> {}
                    }
                }
                delay(intervalMs)
            }
        }
    }

    fun shutdown() {
        scope.cancel()
    }

    private fun showNotification(task: DownloadTask, progress: Float) {
        val notificationId = notificationIdMap[task] ?: return
        val builder = NotificationCompat.Builder(context, "downloads")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Downloading...")
            .setContentText("Progress: ${(progress * 100).toInt()}%")
            .setOngoing(true)
            .setProgress(100, (progress * 100).toInt(), false)

        notificationManager.notify(notificationId, builder.build())
    }


    private fun updateNotification(task: DownloadTask, progress: Float, completed: Boolean) {
        val notificationId = notificationIdMap[task] ?: return
        val builder = NotificationCompat.Builder(context, "downloads")
            .setSmallIcon(
                if (completed) android.R.drawable.stat_sys_download_done
                else android.R.drawable.stat_sys_download
            )
            .setContentTitle(
                if (completed) "Download ${task.fileName}"
                else "Downloading ${task.fileName}"
            )
            .setContentText(
                if (completed) "File downloaded successfully"
                else "Progress: ${(progress * 100).toInt()}%"
            )
            .setOngoing(!completed)
            .setAutoCancel(completed)
            .setProgress(if (completed) 0 else 100, (progress * 100).toInt(), false)

        if (completed) {
            val file = task.getFile()

            // FileProvider URI (for API 24+)
            val uri = androidx.core.content.FileProvider.getUriForFile(
                context,
                context.packageName + ".provider", // make sure this matches your manifest
                file
            )

            // Intent to open file with system chooser
            val openIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, guessMimeType(file.name))
                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
            }

            val pendingIntent = PendingIntent.getActivity(
                context,
                notificationId, // unique per task
                Intent.createChooser(openIntent, "Open with"),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            builder.setContentIntent(pendingIntent)
            // Play sound + vibration on completion
            builder.setDefaults(NotificationCompat.DEFAULT_SOUND or NotificationCompat.DEFAULT_VIBRATE)
        }

        notificationManager.notify(notificationId, builder.build())
    }


}


