package com.subhranil.lunarexplorer.data.download

import com.subhranil.lunarexplorer.data.api.ApiResult
import com.subhranil.lunarexplorer.data.api.DownloadResponse
import com.subhranil.lunarexplorer.data.api.ErrorCode
import com.subhranil.lunarexplorer.data.api.LocalWriteException
import com.subhranil.lunarexplorer.data.api.LunarError
import com.subhranil.lunarexplorer.data.session.ServerSession
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

sealed interface DownloadOutcome {
    data class Completed(val contentUri: String, val size: Long) : DownloadOutcome

    /** [retryable] failures are network problems; the download can continue later from [bytesDone]. */
    data class Failed(val failure: DownloadFailure, val retryable: Boolean, val bytesDone: Long) : DownloadOutcome
}

interface DownloadListener {
    /** The target file and the remote file's current details are known; [offset] is where writing starts. */
    suspend fun onStart(contentUri: String, size: Long, modified: Long?, offset: Long)

    /** Called for every chunk written. Implementations must be cheap most of the time. */
    suspend fun onBytes(done: Long)
}

/**
 * Downloads one file in a single streaming request, resuming from the bytes already written when the
 * remote file hasn't changed. Suspends on network reads only, so a paused or idle download costs nothing.
 */
class DownloadEngine(
    private val files: DownloadFiles,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    suspend fun run(session: ServerSession, record: DownloadRecord, listener: DownloadListener): DownloadOutcome =
        withContext(io) {
            try {
                download(session, record, listener)
            } catch (e: CancellationException) {
                throw e
            } catch (e: LocalWriteException) {
                DownloadOutcome.Failed(storage(e.cause ?: e), retryable = false, bytesDone = 0)
            } catch (e: Exception) {
                DownloadOutcome.Failed(storage(e), retryable = false, bytesDone = 0)
            }
        }

    private suspend fun download(session: ServerSession, record: DownloadRecord, listener: DownloadListener): DownloadOutcome {
        val remote = when (val r = session.stat(record.remotePath, follow = true)) {
            is ApiResult.Failure -> return failed(r.error, record.bytesDone)
            is ApiResult.Success -> r.value
        }
        if (remote.isDirLike) {
            return DownloadOutcome.Failed(DownloadFailure(ErrorCode.IS_A_DIRECTORY.name), retryable = false, bytesDone = 0)
        }
        val size = remote.size
        val unchanged = size == record.size && remote.modified == record.remoteModified
        val existing = record.contentUri?.takeIf { files.exists(it) }
        val uri = existing ?: files.create(record.name)
        var offset = if (existing != null && unchanged) record.bytesDone.coerceIn(0, size) else 0L
        listener.onStart(uri, size, remote.modified, offset)

        // A second pass is only needed when the server ignored the range or the file changed underneath us.
        repeat(MAX_PASSES) {
            var done = offset
            var restart = false
            val result = session.download(record.remotePath, offset) { response ->
                when (response.status) {
                    RANGE_NOT_SATISFIABLE -> restart = offset != size
                    else -> {
                        done = if (response.status == PARTIAL_CONTENT) offset else 0L
                        copy(response, uri, done) { written ->
                            done = written
                            listener.onBytes(written)
                        }
                    }
                }
            }
            when {
                result is ApiResult.Failure -> return failed(result.error, done)
                restart || done > size -> offset = 0
                done < size -> return failed(LunarError.Unreachable, done) // Connection closed early.
                else -> {
                    files.publish(uri)
                    return DownloadOutcome.Completed(uri, size)
                }
            }
        }
        return DownloadOutcome.Failed(
            DownloadFailure(DownloadFailure.UNEXPECTED, "The file changed while downloading"),
            retryable = false,
            bytesDone = 0,
        )
    }

    /** Writes the body to [uri] starting at [start], reporting the byte count after every chunk. */
    private suspend inline fun copy(response: DownloadResponse, uri: String, start: Long, onWritten: (Long) -> Unit) {
        var done = start
        val writer = local { files.open(uri, start) }
        writer.use {
            val buffer = ByteArray(BUFFER_SIZE)
            while (true) {
                // readAvailable doesn't suspend while data is buffered, so check for pause/cancel explicitly.
                currentCoroutineContext().ensureActive()
                val read = response.body.readAvailable(buffer, 0, buffer.size)
                if (read < 0) break
                if (read == 0) continue
                local { writer.write(buffer, read) }
                done += read
                onWritten(done)
            }
        }
    }

    private inline fun <T> local(block: () -> T): T = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        throw LocalWriteException(e)
    }

    private fun failed(error: LunarError, bytesDone: Long) = DownloadOutcome.Failed(
        failure = DownloadFailure.from(error),
        retryable = error == LunarError.Unreachable || error == LunarError.Timeout,
        bytesDone = bytesDone,
    )

    private fun storage(e: Throwable) =
        DownloadFailure(DownloadFailure.STORAGE, e.message ?: e::class.simpleName.orEmpty())

    private companion object {
        const val BUFFER_SIZE = 128 * 1024
        const val MAX_PASSES = 2
        const val PARTIAL_CONTENT = 206
        const val RANGE_NOT_SATISFIABLE = 416
    }
}
