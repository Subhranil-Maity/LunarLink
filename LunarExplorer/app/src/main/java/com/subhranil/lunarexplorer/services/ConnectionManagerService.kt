package com.subhranil.lunarexplorer.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
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
import com.subhranil.lunarexplorer.lunarlink.LunarServerRepo
import com.subhranil.lunarexplorer.manager.DownloadManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.koin.core.context.GlobalContext.get

// ---------------- Compose helper ----------------
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

// ---------------- ConnectionManagerService ----------------
class ConnectionManagerService(
    val globalStateHandler: GlobalStateHandler = get().get(),
    val downloadManager: DownloadManager = get().get()
) : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    /** Update notification dynamically */
    fun updateNotificationToConnected(host: String) {
        updateNotification("Connected to $host", "Tap to use it")
    }
    fun updateNotification(title: String, content: String) {
        val notification = createNotification(title, content)
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, notification)
    }
    private fun isDownloadManagerAction(action: String): Boolean{
        return action.startsWith(DownloadManager.ACTION_PREFIX)
    }

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, createNotification())
        updateNotification(
            "Service Active",
            "No active LunarLink connection"
        )
        downloadManager.startPolling()
        // Service starts silently; notification will show when setLunarConnection is called
    }

    override fun onBind(intent: Intent?): IBinder = LocalBinder()

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    /** Handle Disconnect action */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (isDownloadManagerAction(intent?.action ?: "")) {
            downloadManager.handleAction(intent!!)
            return super.onStartCommand(intent, flags, startId)
        }
        if (intent?.action == ACTION_DISCONNECT) {
            globalStateHandler.disconnect()
            updateNotification("Not connected", "No active LunarLink connection")
        }
        return super.onStartCommand(intent, flags, startId)
    }

    /** Build notification with optional Disconnect button */
    private fun createNotification(
        title: String = "Service Active",
        content: String = "Initializing..."
    ): Notification {
        val channelId = "connection_service_channel"

        // Create channel (for Android O+)
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
        private const val NOTIFICATION_ID = 1001
        private const val ACTION_DISCONNECT = "com.example.app.ACTION_DISCONNECT"
    }
}
