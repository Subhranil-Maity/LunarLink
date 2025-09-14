package com.subhranil.lunarexplorer.manager

import android.annotation.SuppressLint
import com.subhranil.lunarexplorer.manager.task.DownloadTask
import kotlinx.coroutines.*

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

@SuppressLint("MissingPermission")

class DownloadManager(private val context: Context) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val activeTasks = mutableListOf<DownloadTask>()
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

    fun addTask(task: DownloadTask) {
        activeTasks.add(task)
        notificationIdMap[task] = notificationCounter++
        showNotification(task, 0f)
    }
    fun startPolling(intervalMs: Long = 100) {
        scope.launch  {
            while (isActive) {
                val iterator = activeTasks.iterator()
                while (iterator.hasNext()) {
                    val task = iterator.next()
                    if (task.isCompleted()) {
                        updateNotification(task, 1f, true)
                        iterator.remove()
                    } else {
                        task.poll()
                        updateNotification(task, task.progress.toFloat(), false)
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
            .setContentTitle(if (completed) "Download ${task.fileName}" else "Downloading ${task.fileName}")
            .setContentText(
                if (completed) "File downloaded successfully"
                else "Progress: ${(progress * 100).toInt()}%"
            )
            .setOngoing(!completed)
            .setAutoCancel(completed)
            .setProgress(if (completed) 0 else 100, (progress * 100).toInt(), false)

        notificationManager.notify(notificationId, builder.build())
    }
}


