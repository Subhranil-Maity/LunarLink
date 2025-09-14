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
import com.subhranil.lunarexplorer.manager.task.DownloadTask.DownloadState
import com.subhranil.lunarexplorer.services.ConnectionManagerService
import java.io.File
import kotlin.math.floor


@SuppressLint("MissingPermission")

class DownloadManager(private val context: Context) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val activeTasks = mutableListOf<DownloadTask>()
    private val queuedTasks = mutableListOf<DownloadTask>()
    private val completedTasks = mutableListOf<DownloadTask>()
    private val notificationIdMap = mutableMapOf<DownloadTask, Int>()
    private val lastProgressMap = mutableMapOf<String, Float>()
    private val lastStateMap = mutableMapOf<String, DownloadState>()
    private var notificationCounter = 1
    private var maxDownloadsAtOnce = 3
    companion object{
        const val ACTION_PREFIX: String = "DOWNLOAD_MANAGER"
    }

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


    fun startPolling(intervalMs: Long = 0L) {
        scope.launch  {
            while (isActive) {
                if (activeTasks.size < maxDownloadsAtOnce) startNextDownload()
                if (activeTasks.isEmpty()) continue
                val iterator = activeTasks.iterator()
                while (iterator.hasNext()) {
                    val task = iterator.next()
                    val state = task.getState()
                    when (state) {
                        DownloadState.COMPLETED -> {
                            updateNotification(task)
                            completedTasks.add(task)
                            iterator.remove()
                        }
                        is DownloadState.FAILED -> {
                            updateNotification(task)
                        }
                        is DownloadState.IN_PROGRESS -> {
                            task.poll()
                            updateNotification(task)
                        }
                        DownloadState.PENDING -> {}
                        is DownloadState.PAUSED -> {
                            updateNotification(task)
                        }
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


    private fun updateNotification(task: DownloadTask) {
        val notificationId = notificationIdMap[task] ?: return
        val taskState = task.getState()
        // skip if no changes
        val lastState = lastStateMap[task.id]
        if (lastState == taskState) {
            if (taskState is DownloadTask.DownloadState.IN_PROGRESS) {
                val lastProgress = lastProgressMap[task.id] ?: -1f
                if (kotlin.math.abs(taskState.progress - lastProgress) < 0.01f) {
                    return
                }
                lastProgressMap[task.id] = taskState.progress
            } else {
                return
            }
        }
        lastStateMap[task.id] = taskState

        // ... build notification normally
        val builder = NotificationCompat.Builder(context, "downloads")
            .setSmallIcon(
                when (taskState) {
                    is DownloadState.COMPLETED -> android.R.drawable.stat_sys_download_done
                    else -> android.R.drawable.stat_sys_download
                }
            )
            .setContentTitle(
                when (taskState) {
                    is DownloadState.COMPLETED -> "Download ${task.fileName}"
                    is DownloadState.IN_PROGRESS -> "Downloading ${task.fileName}"
                    is DownloadState.PAUSED -> "Paused: ${task.fileName}"
                    is DownloadState.FAILED -> "Failed: ${task.fileName}"
                    is DownloadState.PENDING -> "Pending: ${task.fileName}"
                }
            )
            .setOngoing(taskState !is DownloadState.COMPLETED && taskState !is DownloadState.FAILED)
            .setAutoCancel(taskState is DownloadState.COMPLETED)

        // Content text and progress
        when (taskState) {
            is DownloadState.IN_PROGRESS -> {
                builder.setContentText("Progress: ${(taskState.progress * 100).toInt()}%")
                builder.setProgress(100, (taskState.progress * 100).toInt(), false)
            }
            is DownloadState.PAUSED -> {
                builder.setContentText("Paused at ${floor(taskState.pausedAt*100)}%")
                builder.setProgress(0, 0, false)
            }
            is DownloadState.COMPLETED -> {
                builder.setContentText("File downloaded successfully")
                builder.setProgress(0, 0, false)

                // FileProvider URI for system open
                val file = task.getFile()
                val uri = androidx.core.content.FileProvider.getUriForFile(
                    context,
                    context.packageName + ".provider",
                    file
                )

                val openIntent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, guessMimeType(file.name))
                    flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
                }

                val pendingIntent = PendingIntent.getActivity(
                    context,
                    notificationId,
                    Intent.createChooser(openIntent, "Open with"),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                builder.setContentIntent(pendingIntent)
                builder.setDefaults(NotificationCompat.DEFAULT_SOUND or NotificationCompat.DEFAULT_VIBRATE)
            }
            is DownloadState.FAILED -> {
                builder.setContentText("Download failed: ${taskState.error}")
                builder.setProgress(0, 0, false)
            }
            is DownloadState.PENDING -> {
                builder.setContentText("Waiting to start...")
                builder.setProgress(0, 0, false)
            }
        }


        // Add pause/resume button if applicable
        when (taskState) {
            is DownloadState.IN_PROGRESS -> {
                val intent = Intent(context, ConnectionManagerService::class.java).apply {
                    action = "${ACTION_PREFIX}_PAUSE"
                }
                val pauseIntent = PendingIntent.getService(
                    context,
                    notificationId,
                    intent.putExtra("taskId", task.id),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                builder.addAction(android.R.drawable.ic_media_pause, "Pause", pauseIntent)
            }
            is DownloadState.PAUSED -> {
                val intent = Intent(context, ConnectionManagerService::class.java).apply {
                    action = "${ACTION_PREFIX}_RESUME"
                }
                val resumeIntent = PendingIntent.getService(
                    context,
                    notificationId,
                    intent.putExtra("taskId", task.id),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                builder.addAction(android.R.drawable.ic_media_play, "Resume", resumeIntent)
            }
            else -> {
                // no action button
            }
        }

        notificationManager.notify(notificationId, builder.build())
    }

    fun handleAction(intent: Intent){
        val action = intent.action ?: return
        val taskId = intent.getStringExtra("taskId") ?: return
        val task = activeTasks.find { it.id == taskId } ?: return
        Log.d("DownloadManager", "Handling action $action for task ${task.fileName}")
        when(action){
            "${ACTION_PREFIX}_PAUSE" -> {
                task.pause()
                updateNotification(task)
            }
            "${ACTION_PREFIX}_RESUME" -> {
                task.resume()
                updateNotification(task)
            }
        }
    }
}


