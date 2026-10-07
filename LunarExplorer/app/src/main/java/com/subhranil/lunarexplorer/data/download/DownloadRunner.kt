package com.subhranil.lunarexplorer.data.download

import com.subhranil.lunarexplorer.data.api.ApiResult
import com.subhranil.lunarexplorer.data.api.LunarError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/** Overall progress for the ongoing notification. */
data class DownloadSummary(val count: Int, val bytesDone: Long, val bytesTotal: Long, val firstName: String?)

/** Shows download status outside the app. */
interface DownloadNotifier {
    fun progress(summary: DownloadSummary)
    fun completed(record: DownloadRecord)
    fun failed(record: DownloadRecord)

    /** The run ended; remove the progress notification. */
    fun idle()
}

/**
 * Processes the download queue until it is empty, [maxParallel] files at a time. Each file runs in its
 * own job so pausing or removing one cancels just that transfer.
 */
class DownloadRunner(
    private val repository: DownloadRepository,
    private val engine: DownloadEngine,
    private val sessions: DownloadSessions,
    private val notifier: DownloadNotifier,
    private val now: () -> Long = { System.nanoTime() / 1_000_000 },
    private val maxParallel: Int = 2,
) {
    /**
     * Runs until nothing is left to do. Returns true when some downloads are waiting for the network,
     * so the caller should try again later.
     */
    suspend fun run(): Boolean {
        // Downloads that hit a network error are skipped for the rest of this run instead of retried in a loop.
        val deferred = ConcurrentHashMap.newKeySet<String>()
        val progress = ProgressReporter()
        repository.beginRun()
        try {
            do {
                coroutineScope {
                    repeat(maxParallel) {
                        launch {
                            while (true) {
                                val record = repository.claimNext(deferred) ?: break
                                process(record, deferred, progress)
                            }
                        }
                    }
                }
            } while (!repository.endRun(deferred))
        } catch (e: Throwable) {
            withContext(NonCancellable) { repository.abortRun() }
            throw e
        } finally {
            withContext(NonCancellable) { sessions.clear() }
            notifier.idle()
        }
        return deferred.isNotEmpty()
    }

    private suspend fun process(record: DownloadRecord, deferred: MutableSet<String>, progress: ProgressReporter) =
        coroutineScope {
            val job = launch { transfer(record, deferred, progress) }
            repository.attach(record.id, job)
            job.join()
            repository.detach(record.id, job)
        }

    private suspend fun transfer(record: DownloadRecord, deferred: MutableSet<String>, progress: ProgressReporter) {
        val tracker = Tracker(record.id, record.bytesDone, progress)
        try {
            val session = when (val r = sessions.get(record.serverId)) {
                is ApiResult.Success -> r.value
                is ApiResult.Failure -> {
                    val failure = DownloadFailure.from(r.error)
                    val outcome = DownloadOutcome.Failed(failure, retryable(r.error), record.bytesDone)
                    return finish(record, outcome, deferred)
                }
            }
            finish(record, engine.run(session, record, tracker), deferred)
        } catch (e: CancellationException) {
            withContext(NonCancellable) { repository.interrupted(record.id, tracker.bytes, tracker.contentUri) }
            throw e
        }
    }

    private suspend fun finish(record: DownloadRecord, outcome: DownloadOutcome, deferred: MutableSet<String>) {
        when (outcome) {
            is DownloadOutcome.Completed -> {
                repository.completed(record.id, outcome.size)
                notifier.completed(record.copy(contentUri = outcome.contentUri, state = DownloadState.COMPLETED))
            }
            is DownloadOutcome.Failed -> {
                if (outcome.retryable && record.networkRetries < MAX_NETWORK_RETRIES) {
                    repository.requeue(record.id, outcome.failure, outcome.bytesDone)
                    deferred += record.id
                } else {
                    val failed = repository.failed(record.id, outcome.failure, outcome.bytesDone)
                    notifier.failed(failed ?: record)
                }
            }
        }
    }

    private fun retryable(error: LunarError) = error == LunarError.Unreachable || error == LunarError.Timeout

    /** Rate-limits progress updates: UI a few times a second, the notification once a second, disk every few seconds. */
    private inner class Tracker(
        private val id: String,
        start: Long,
        private val progress: ProgressReporter,
    ) : DownloadListener {
        @Volatile
        var bytes: Long = start
            private set

        @Volatile
        var contentUri: String? = null
            private set
        private var savedBytes = start
        private var savedAt = now()
        private var reportedAt = 0L

        override suspend fun onStart(contentUri: String, size: Long, modified: Long?, offset: Long) {
            this.contentUri = contentUri
            bytes = offset
            savedBytes = offset
            savedAt = now()
            repository.started(id, contentUri, size, modified, offset)
            repository.reportBytes(id, offset)
        }

        override suspend fun onBytes(done: Long) {
            bytes = done
            val t = now()
            if (t - reportedAt >= UI_INTERVAL_MS) {
                reportedAt = t
                repository.reportBytes(id, done)
                progress.maybeNotify(t)
            }
            if (t - savedAt >= SAVE_INTERVAL_MS || done - savedBytes >= SAVE_BYTES) {
                savedAt = t
                savedBytes = done
                repository.saveProgress(id, done)
            }
        }
    }

    private inner class ProgressReporter {
        @Volatile
        private var notifiedAt = 0L
        fun maybeNotify(t: Long) {
            if (t - notifiedAt < NOTIFY_INTERVAL_MS) return
            notifiedAt = t
            val records = repository.records.value.orEmpty().filter { it.state == DownloadState.RUNNING }
            if (records.isEmpty()) return
            val live = repository.liveBytes.value
            notifier.progress(
                DownloadSummary(
                    count = records.size,
                    bytesDone = records.sumOf { live[it.id] ?: it.bytesDone },
                    bytesTotal = records.sumOf { it.size },
                    firstName = records.first().name,
                )
            )
        }
    }

    private companion object {
        const val MAX_NETWORK_RETRIES = 5
        const val UI_INTERVAL_MS = 250L
        const val NOTIFY_INTERVAL_MS = 1_000L
        const val SAVE_INTERVAL_MS = 5_000L
        const val SAVE_BYTES = 8L * 1024 * 1024
    }
}
