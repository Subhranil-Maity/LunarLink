package com.subhranil.lunarexplorer.ui.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.subhranil.lunarexplorer.data.download.DownloadRecord
import com.subhranil.lunarexplorer.data.download.DownloadRepository
import com.subhranil.lunarexplorer.data.download.DownloadState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DownloadItem(
    val record: DownloadRecord,
    val bytesDone: Long,
    /** Recent transfer speed; `null` until there are enough samples. */
    val bytesPerSecond: Long? = null,
) {
    val id: String get() = record.id

    /** Queued again after a network error, waiting for the next attempt. */
    val waitingForNetwork: Boolean get() = record.state == DownloadState.QUEUED && record.failure != null

    val progress: Float get() = if (record.size > 0) (bytesDone.toFloat() / record.size).coerceIn(0f, 1f) else 0f
}

data class DownloadsUiState(
    val isLoading: Boolean = true,
    val active: List<DownloadItem> = emptyList(),
    val paused: List<DownloadItem> = emptyList(),
    val failed: List<DownloadItem> = emptyList(),
    val completed: List<DownloadItem> = emptyList(),
) {
    val isEmpty: Boolean get() = active.isEmpty() && paused.isEmpty() && failed.isEmpty() && completed.isEmpty()
}

class DownloadsViewModel(
    private val downloads: DownloadRepository,
    now: () -> Long = { System.nanoTime() / 1_000_000 },
) : ViewModel() {
    private val speeds = SpeedTracker(now)

    val state: StateFlow<DownloadsUiState> = combine(downloads.records, downloads.liveBytes) { records, live ->
        if (records == null) return@combine DownloadsUiState()
        val items = records.map { record ->
            val bytes = live[record.id] ?: record.bytesDone
            val speed = if (record.state == DownloadState.RUNNING) speeds.sample(record.id, bytes) else null
            DownloadItem(record, bytes, speed)
        }
        speeds.retain(items.filter { it.record.state == DownloadState.RUNNING }.map { it.id }.toSet())
        DownloadsUiState(
            isLoading = false,
            active = items.filter { it.record.isActive }
                .sortedWith(compareBy({ it.record.state != DownloadState.RUNNING }, { it.record.createdAt })),
            paused = items.filter { it.record.state == DownloadState.PAUSED }.sortedBy { it.record.createdAt },
            failed = items.filter { it.record.state == DownloadState.FAILED }.sortedByDescending { it.record.finishedAt },
            completed = items.filter { it.record.state == DownloadState.COMPLETED }
                .sortedByDescending { it.record.finishedAt },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DownloadsUiState())

    fun pause(id: String) = launch { downloads.pause(id) }

    fun pauseAll() = launch { downloads.pauseAll() }

    fun resume(id: String) = launch { downloads.resume(id) }

    fun remove(id: String) = launch { downloads.remove(id) }

    fun clearCompleted() = launch { downloads.clearFinished() }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}

/** Smoothed bytes-per-second per download, from the progress samples the UI already receives. */
private class SpeedTracker(private val now: () -> Long) {
    private class Sample(var timeMs: Long, var bytes: Long, var speed: Double?)

    private val samples = mutableMapOf<String, Sample>()

    fun sample(id: String, bytes: Long): Long? {
        val t = now()
        val last = samples[id] ?: return null.also { samples[id] = Sample(t, bytes, null) }
        val elapsed = t - last.timeMs
        if (elapsed < MIN_INTERVAL_MS) return last.speed?.toLong()
        val instant = (bytes - last.bytes).coerceAtLeast(0) * 1000.0 / elapsed
        last.speed = last.speed?.let { it + SMOOTHING * (instant - it) } ?: instant
        last.timeMs = t
        last.bytes = bytes
        return last.speed?.toLong()
    }

    fun retain(ids: Set<String>) {
        samples.keys.retainAll(ids)
    }

    private companion object {
        const val MIN_INTERVAL_MS = 500L
        const val SMOOTHING = 0.3
    }
}
