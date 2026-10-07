package com.subhranil.lunarexplorer.data.download

import com.subhranil.lunarexplorer.data.api.Entry
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** Starts background processing of queued downloads (WorkManager in the app, a fake in tests). */
fun interface DownloadScheduler {
    /** [restart] replaces a run that is waiting (e.g. backing off); otherwise an existing one is kept. */
    fun schedule(restart: Boolean)
}

/**
 * The source of truth for downloads. The UI changes records here; [DownloadRunner] does the transfers.
 * Nothing runs unless a record is queued: [schedule] starts a run only when one isn't already going.
 */
class DownloadRepository(
    private val store: DownloadStore,
    private val files: DownloadFiles,
    private val scheduler: DownloadScheduler,
    scope: CoroutineScope,
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val newId: () -> String = { UUID.randomUUID().toString() },
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    val records: StateFlow<List<DownloadRecord>?> = store.records.stateIn(scope, SharingStarted.Eagerly, null)

    private val _liveBytes = MutableStateFlow<Map<String, Long>>(emptyMap())

    /** Bytes written so far for running downloads, fresher than [DownloadRecord.bytesDone]. */
    val liveBytes: StateFlow<Map<String, Long>> = _liveBytes.asStateFlow()

    private val jobs = ConcurrentHashMap<String, Job>()
    private val runMutex = Mutex()
    private var running = false

    /** Queues the files among [entries]; folders are ignored. Returns how many were queued. */
    suspend fun enqueue(serverId: String, serverName: String, entries: List<Entry>): Int {
        val now = clock()
        val added = entries.filterNot { it.isDirLike }.map {
            DownloadRecord(
                id = newId(),
                serverId = serverId,
                serverName = serverName,
                remotePath = it.path,
                name = it.name,
                size = it.size,
                remoteModified = it.modified,
                createdAt = now,
            )
        }
        if (added.isEmpty()) return 0
        store.update { it + added }
        schedule()
        return added.size
    }

    suspend fun pause(id: String) {
        store.update { list -> list.map { if (it.id == id && it.isActive) it.copy(state = DownloadState.PAUSED) else it } }
        stop(listOfNotNull(jobs[id]))
    }

    suspend fun pauseAll() {
        store.update { list -> list.map { if (it.isActive) it.copy(state = DownloadState.PAUSED) else it } }
        stop(jobs.values.toList())
    }

    /** Resumes a paused download or retries a failed one. */
    suspend fun resume(id: String) {
        store.update { list ->
            list.map {
                if (it.id == id && (it.state == DownloadState.PAUSED || it.state == DownloadState.FAILED)) {
                    it.copy(state = DownloadState.QUEUED, failure = null, networkRetries = 0)
                } else {
                    it
                }
            }
        }
        schedule()
    }

    /** Removes a download from the list. Unfinished downloads are stopped and their partial file deleted. */
    suspend fun remove(id: String) {
        // Remove the record before stopping the transfer: a stopped transfer that still finds its record
        // RUNNING puts it back in the queue, and the run would pick it up again.
        var removed: DownloadRecord? = null
        store.update { list ->
            removed = list.firstOrNull { it.id == id }
            list.filterNot { it.id == id }
        }
        stop(listOfNotNull(jobs[id]))
        // Only now is nothing writing to the file.
        removed?.takeIf { it.state != DownloadState.COMPLETED }?.contentUri?.let(files::delete)
        _liveBytes.update { it - id }
    }

    /** Removes completed downloads from the list; the files stay. */
    suspend fun clearFinished() {
        store.update { list -> list.filterNot { it.state == DownloadState.COMPLETED } }
    }

    /**
     * Called when the process starts. Downloads left running by a killed process go back to the queue, and
     * pending work is scheduled without disturbing a run WorkManager may already be starting.
     */
    suspend fun recoverInterrupted() {
        val pending = runMutex.withLock {
            // A run in this process claims records only after beginRun, which waits for this lock, so any
            // record still RUNNING here belongs to a dead process.
            if (running) return
            store.update { list ->
                list.map { if (it.state == DownloadState.RUNNING) it.copy(state = DownloadState.QUEUED) else it }
            }.any { it.state == DownloadState.QUEUED }
        }
        if (pending) scheduler.schedule(restart = false)
    }

    /**
     * Cancels transfers off the main thread: cancelling closes the HTTP stream, and the Android engine
     * reads the socket while closing, which StrictMode forbids on the main thread.
     */
    private suspend fun stop(transfers: List<Job>) {
        if (transfers.isNotEmpty()) withContext(io) { transfers.forEach { it.cancelAndJoin() } }
    }

    private suspend fun schedule() {
        val alreadyRunning = runMutex.withLock { running }
        // A run that is going will pick the new records up before it ends (see endRun).
        if (!alreadyRunning) scheduler.schedule(restart = true)
    }

    // ---------- used by DownloadRunner ----------

    internal suspend fun beginRun() = runMutex.withLock { running = true }

    /** Ends the run unless records it can take are still queued; returns whether it ended. */
    internal suspend fun endRun(skip: Set<String>): Boolean = runMutex.withLock {
        val more = store.current().any { it.state == DownloadState.QUEUED && it.id !in skip }
        if (!more) running = false
        !more
    }

    internal suspend fun abortRun() = runMutex.withLock { running = false }

    /** Marks the oldest queued record not in [skip] as running and returns it. */
    internal suspend fun claimNext(skip: Set<String>): DownloadRecord? {
        var claimed: DownloadRecord? = null
        store.update { list ->
            val next = list.filter { it.state == DownloadState.QUEUED && it.id !in skip }.minByOrNull { it.createdAt }
                ?: return@update list
            claimed = next.copy(state = DownloadState.RUNNING)
            list.map { if (it.id == next.id) claimed!! else it }
        }
        return claimed
    }

    internal fun attach(id: String, job: Job) {
        jobs[id] = job
    }

    internal fun detach(id: String, job: Job) {
        jobs.remove(id, job)
        _liveBytes.update { it - id }
    }

    internal fun reportBytes(id: String, bytes: Long) = _liveBytes.update { it + (id to bytes) }

    internal suspend fun started(id: String, contentUri: String, size: Long, modified: Long?, offset: Long) =
        edit(id) { it.copy(contentUri = contentUri, size = size, remoteModified = modified, bytesDone = offset) }

    internal suspend fun saveProgress(id: String, bytes: Long) = edit(id) { it.copy(bytesDone = bytes) }

    internal suspend fun completed(id: String, size: Long) = edit(id) {
        it.copy(state = DownloadState.COMPLETED, bytesDone = size, failure = null, networkRetries = 0, finishedAt = clock())
    }

    internal suspend fun failed(id: String, failure: DownloadFailure, bytes: Long) = edit(id) {
        it.copy(state = DownloadState.FAILED, failure = failure, bytesDone = bytes, finishedAt = clock())
    }

    /** A network problem: back to the queue for a later run. Progress resets the retry count. */
    internal suspend fun requeue(id: String, failure: DownloadFailure, bytes: Long) = edit(id) {
        if (it.state != DownloadState.RUNNING) return@edit it.copy(bytesDone = bytes)
        val retries = if (bytes > it.bytesDone) 1 else it.networkRetries + 1
        it.copy(state = DownloadState.QUEUED, failure = failure, bytesDone = bytes, networkRetries = retries)
    }

    /** The transfer was stopped (paused, removed, or the system stopped the run). */
    internal suspend fun interrupted(id: String, bytes: Long, contentUri: String?) {
        val record = edit(id) {
            val state = if (it.state == DownloadState.RUNNING) DownloadState.QUEUED else it.state
            it.copy(state = state, bytesDone = bytes)
        }
        // Removed while starting, before the file was recorded: nobody else knows about the file.
        if (record == null) contentUri?.let(files::delete)
    }

    private suspend fun edit(id: String, transform: (DownloadRecord) -> DownloadRecord): DownloadRecord? {
        var result: DownloadRecord? = null
        store.update { list ->
            list.map { if (it.id == id) transform(it).also { r -> result = r } else it }
        }
        return result
    }
}
