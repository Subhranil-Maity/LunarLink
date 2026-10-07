package com.subhranil.lunarexplorer.data.download

import com.subhranil.lunarexplorer.data.api.ErrorCode
import com.subhranil.lunarexplorer.data.api.LunarError
import kotlinx.serialization.Serializable

@Serializable
enum class DownloadState { QUEUED, RUNNING, PAUSED, FAILED, COMPLETED }

/** Why a download failed, stored so it survives restarts. [code] is an [ErrorCode] name or one of the local codes below. */
@Serializable
data class DownloadFailure(val code: String, val message: String = "") {
    fun toLunarError(): LunarError = when (code) {
        UNREACHABLE -> LunarError.Unreachable
        TIMEOUT -> LunarError.Timeout
        UNEXPECTED, STORAGE -> LunarError.Unexpected(message)
        else -> LunarError.Api(ErrorCode.parse(code), message, status = 0)
    }

    companion object {
        const val UNREACHABLE = "UNREACHABLE"
        const val TIMEOUT = "TIMEOUT"
        const val UNEXPECTED = "UNEXPECTED"
        const val STORAGE = "STORAGE"

        fun from(error: LunarError): DownloadFailure = when (error) {
            LunarError.Unreachable -> DownloadFailure(UNREACHABLE)
            LunarError.Timeout -> DownloadFailure(TIMEOUT)
            is LunarError.Unexpected -> DownloadFailure(UNEXPECTED, error.detail)
            is LunarError.Api -> DownloadFailure(error.code.name, error.message)
        }
    }
}

/** One file download. Saved in [DownloadStore], so it can resume after the app or the device restarts. */
@Serializable
data class DownloadRecord(
    val id: String,
    val serverId: String,
    val serverName: String,
    val remotePath: String,
    val name: String,
    /** Size and modification time of the remote file; a resume only continues if they haven't changed. */
    val size: Long,
    val remoteModified: Long? = null,
    val state: DownloadState = DownloadState.QUEUED,
    /** Bytes written to [contentUri]. Saved periodically while running, so it may lag behind the real count. */
    val bytesDone: Long = 0,
    /** The MediaStore item being written; `null` until the download first starts. */
    val contentUri: String? = null,
    val failure: DownloadFailure? = null,
    /** Consecutive network failures; the download fails for good after a few. */
    val networkRetries: Int = 0,
    val createdAt: Long,
    val finishedAt: Long? = null,
) {
    val isActive: Boolean get() = state == DownloadState.QUEUED || state == DownloadState.RUNNING
}
