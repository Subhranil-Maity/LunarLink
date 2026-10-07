package com.subhranil.lunarexplorer.data.download

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.core.qualifier.named
import java.util.concurrent.TimeUnit

/**
 * Runs the download queue. WorkManager only starts it while something is queued and a network is
 * available, and it returns as soon as the queue is empty, so idle downloads cost nothing.
 */
class DownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params), KoinComponent {
    private val runner: DownloadRunner by inject()
    private val notifications: DownloadNotifications by inject()

    override suspend fun doWork(): Result {
        try {
            setForeground(notifications.foregroundInfo())
        } catch (e: IllegalStateException) {
            // Started from the background (e.g. a retry) where a foreground service isn't allowed. Run as a
            // normal job; if the system stops it, progress is saved and the next run resumes.
        }
        val waitingForNetwork = runner.run()
        return if (waitingForNetwork) Result.retry() else Result.success()
    }

    override suspend fun getForegroundInfo() = notifications.foregroundInfo()
}

class WorkManagerDownloadScheduler(private val context: Context) : DownloadScheduler {
    override fun schedule(restart: Boolean) {
        val request = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        // REPLACE only ever replaces a run that is waiting (e.g. backing off after a network error), since
        // the repository doesn't restart while a run is active. The new run starts right away.
        val policy = if (restart) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP
        WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, policy, request)
    }

    private companion object {
        const val WORK_NAME = "downloads"
    }
}

/** Koin qualifier of the application-wide coroutine scope. */
const val APP_SCOPE = "app"

/** Handles notification actions. */
class DownloadActionReceiver : BroadcastReceiver(), KoinComponent {
    private val repository: DownloadRepository by inject()
    private val scope: CoroutineScope by inject(named(APP_SCOPE))

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_PAUSE_ALL) return
        val pending = goAsync()
        scope.launch {
            try {
                repository.pauseAll()
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_PAUSE_ALL = "com.subhranil.lunarexplorer.action.PAUSE_ALL_DOWNLOADS"
    }
}
