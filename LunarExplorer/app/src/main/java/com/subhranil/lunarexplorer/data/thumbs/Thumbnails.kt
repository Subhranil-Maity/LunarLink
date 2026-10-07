package com.subhranil.lunarexplorer.data.thumbs

import coil3.ImageLoader
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.key.Keyer
import coil3.request.Options
import com.subhranil.lunarexplorer.data.api.ApiResult
import com.subhranil.lunarexplorer.data.api.Entry
import com.subhranil.lunarexplorer.data.api.LunarError
import com.subhranil.lunarexplorer.data.session.SessionManager
import okio.Buffer

/** Coil request data for a server-side preview of an image file. */
data class Thumbnail(
    val serverId: String,
    val path: String,
    /** Part of the cache key, so a changed file gets a new preview. */
    val modified: Long?,
    val fileSize: Long,
    val sizePx: Int,
) {
    companion object {
        /** Image types the server can preview. Others aren't requested, so they don't cost a round trip. */
        private val SUPPORTED = setOf("image/jpeg", "image/png", "image/gif", "image/webp", "image/bmp")

        fun canPreview(entry: Entry): Boolean = !entry.isDirLike && entry.mimeType in SUPPORTED

        fun of(serverId: String, entry: Entry, sizePx: Int) =
            Thumbnail(serverId, entry.path, entry.modified, entry.size, sizePx)
    }
}

class ThumbnailException(val error: LunarError) : Exception(error.toString())

/** Loads [Thumbnail]s through the explorer's session, so login and token refresh are shared. */
class ThumbnailFetcher(
    private val data: Thumbnail,
    private val options: Options,
    private val sessions: SessionManager,
) : Fetcher {
    override suspend fun fetch(): FetchResult {
        val session = sessions.current(data.serverId)
            ?: throw IllegalStateException("Not connected to the server")
        val bytes = when (val result = session.thumbnail(data.path, data.sizePx)) {
            is ApiResult.Success -> result.value
            is ApiResult.Failure -> throw ThumbnailException(result.error)
        }
        return SourceFetchResult(
            source = ImageSource(Buffer().write(bytes), options.fileSystem),
            mimeType = null,
            dataSource = DataSource.NETWORK,
        )
    }

    class Factory(private val sessions: SessionManager) : Fetcher.Factory<Thumbnail> {
        override fun create(data: Thumbnail, options: Options, imageLoader: ImageLoader): Fetcher =
            ThumbnailFetcher(data, options, sessions)
    }
}

/** Memory-cache key. */
class ThumbnailKeyer : Keyer<Thumbnail> {
    override fun key(data: Thumbnail, options: Options): String =
        "thumb:${data.serverId}:${data.path}:${data.modified}:${data.fileSize}:${data.sizePx}"
}
