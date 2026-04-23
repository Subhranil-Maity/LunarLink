package com.subhranil.lunarexplorer.services

import android.app.*
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.NotificationCompat
import com.subhranil.lunarexplorer.GlobalStateHandler
import com.subhranil.lunarexplorer.R
import com.subhranil.lunarexplorer.manager.DownloadManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.koin.android.ext.android.inject

@Composable
fun rememberConnectionService(): ConnectionManagerService? {
    val context = LocalContext.current
    var service by remember { mutableStateOf<ConnectionManagerService?>(null) }

    DisposableEffect(Unit) {
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                service = (binder as ConnectionManagerService.LocalBinder).getService()
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                service = null
            }
        }

        val intent = Intent(context, ConnectionManagerService::class.java)
        context.bindService(intent, connection, Context.BIND_AUTO_CREATE)

        onDispose {
            context.unbindService(connection)
        }
    }

    return service
}
class ConnectionManagerService : Service() {

    private val globalStateHandler: GlobalStateHandler by inject()
    private val downloadManager: DownloadManager by inject()

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val binder = LocalBinder()

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Start the service as a foreground service with a notification.
        startForeground(NOTIFICATION_ID, createNotification())

        // Handle incoming actions, including those from the DownloadManager
        val action = intent?.action
        if (isDownloadManagerAction(action)) {
            downloadManager.handleAction(intent!!)
        } else if (action == ACTION_DISCONNECT) {
            globalStateHandler.disconnect()
            updateNotification("Not connected", "No active LunarLink connection")
        }

        // Start polling only if there is pending/active work.
        if (downloadManager.hasWork()) {
            downloadManager.startPolling()
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        downloadManager.shutdown()
    }

    fun updateNotificationToConnected(host: String) {
        updateNotification("Connected to $host", "Tap to use it")
    }

    fun updateNotification(title: String, content: String) {
        val notification = createNotification(title, content)
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, notification)
    }

    // Your helper function to check for download manager actions
    private fun isDownloadManagerAction(action: String?): Boolean{
        return action?.startsWith(DownloadManager.ACTION_PREFIX) ?: false
    }

    // Your notification creation logic
    private fun createNotification(
        title: String = "Service Active",
        content: String = "Initializing..."
    ): Notification {
        val channelId = "connection_service_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Connection Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        val builder = NotificationCompat.Builder(this, channelId)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)

        // Only add Disconnect action if connected
        globalStateHandler.connection?.let {
            val disconnectIntent = Intent(this, ConnectionManagerService::class.java).apply {
                action = ACTION_DISCONNECT
            }
            val disconnectPendingIntent = PendingIntent.getService(
                this,
                0,
                disconnectIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(R.drawable.unknown_icon, "Disconnect", disconnectPendingIntent)
        }

        val notification = builder.build()
        notification.flags = notification.flags or Notification.FLAG_NO_CLEAR
        return notification
    }

    inner class LocalBinder : Binder() {
        fun getService(): ConnectionManagerService = this@ConnectionManagerService
    }

    companion object {
        const val NOTIFICATION_ID = 1001
        const val ACTION_DISCONNECT = "com.example.app.ACTION_DISCONNECT"
    }
}
