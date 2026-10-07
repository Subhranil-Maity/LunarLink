package com.subhranil.lunarexplorer.data.download

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toUri
import androidx.work.ForegroundInfo
import com.subhranil.lunarexplorer.MainActivity
import com.subhranil.lunarexplorer.R
import com.subhranil.lunarexplorer.util.formatBytes

/**
 * Download notifications: one ongoing summary while a run is active (it is also the foreground-service
 * notification, so it disappears when the run ends), and one notification per finished or failed file.
 */
class DownloadNotifications(private val context: Context) : DownloadNotifier {
    private val manager = NotificationManagerCompat.from(context)

    init {
        manager.createNotificationChannelsCompat(
            listOf(
                NotificationChannelCompat.Builder(CHANNEL_PROGRESS, NotificationManager.IMPORTANCE_LOW)
                    .setName(context.getString(R.string.notification_channel_progress))
                    .build(),
                NotificationChannelCompat.Builder(CHANNEL_RESULTS, NotificationManager.IMPORTANCE_DEFAULT)
                    .setName(context.getString(R.string.notification_channel_results))
                    .build(),
            )
        )
    }

    fun foregroundInfo(summary: DownloadSummary? = null): ForegroundInfo =
        ForegroundInfo(PROGRESS_ID, progressNotification(summary), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)

    override fun progress(summary: DownloadSummary) = notify(PROGRESS_ID, progressNotification(summary))

    override fun completed(record: DownloadRecord) {
        val uri = record.contentUri ?: return
        val open = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri.toUri(), context.contentResolver.getType(uri.toUri()))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_RESULTS)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(record.name)
            .setContentText(context.getString(R.string.notification_download_complete))
            .setContentIntent(PendingIntent.getActivity(context, record.id.hashCode(), open, IMMUTABLE))
            .setAutoCancel(true)
            .setGroup(GROUP)
            .build()
        notify(record.id.hashCode(), notification)
    }

    override fun failed(record: DownloadRecord) {
        val notification = NotificationCompat.Builder(context, CHANNEL_RESULTS)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle(record.name)
            .setContentText(context.getString(R.string.notification_download_failed))
            .setContentIntent(openDownloads())
            .setAutoCancel(true)
            .setGroup(GROUP)
            .build()
        notify(record.id.hashCode(), notification)
    }

    override fun idle() = manager.cancel(PROGRESS_ID)

    private fun progressNotification(summary: DownloadSummary?): Notification {
        val builder = NotificationCompat.Builder(context, CHANNEL_PROGRESS)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(openDownloads())
            .addAction(0, context.getString(R.string.downloads_pause_all), pauseAll())
        if (summary == null) {
            return builder
                .setContentTitle(context.getString(R.string.notification_downloads_starting))
                .setProgress(0, 0, true)
                .build()
        }
        val title = if (summary.count == 1 && summary.firstName != null) {
            summary.firstName
        } else {
            context.resources.getQuantityString(R.plurals.notification_downloading, summary.count, summary.count)
        }
        val percent = if (summary.bytesTotal > 0) (summary.bytesDone * 100 / summary.bytesTotal).toInt() else 0
        return builder
            .setContentTitle(title)
            .setContentText(
                context.getString(
                    R.string.downloads_bytes_of,
                    formatBytes(summary.bytesDone),
                    formatBytes(summary.bytesTotal),
                )
            )
            .setProgress(100, percent.coerceIn(0, 100), summary.bytesTotal <= 0)
            .build()
    }

    private fun openDownloads(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_OPEN_DOWNLOADS, true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(context, 0, intent, IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun pauseAll(): PendingIntent {
        val intent = Intent(context, DownloadActionReceiver::class.java).setAction(DownloadActionReceiver.ACTION_PAUSE_ALL)
        return PendingIntent.getBroadcast(context, 0, intent, IMMUTABLE)
    }

    @SuppressLint("MissingPermission") // Checked by areNotificationsEnabled(); downloads work without notifications.
    private fun notify(id: Int, notification: Notification) {
        if (manager.areNotificationsEnabled()) manager.notify(id, notification)
    }

    companion object {
        const val PROGRESS_ID = 1001
        private const val CHANNEL_PROGRESS = "downloads"
        private const val CHANNEL_RESULTS = "download_results"
        private const val GROUP = "downloads"
        private const val IMMUTABLE = PendingIntent.FLAG_IMMUTABLE
    }
}
