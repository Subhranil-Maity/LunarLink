package com.subhranil.lunarexplorer.data

import com.subhranil.lunarexplorer.data.api.ApiResult
import com.subhranil.lunarexplorer.data.api.ErrorCode
import com.subhranil.lunarexplorer.data.download.DownloadEngine
import com.subhranil.lunarexplorer.data.download.DownloadFailure
import com.subhranil.lunarexplorer.data.download.DownloadListener
import com.subhranil.lunarexplorer.data.download.DownloadOutcome
import com.subhranil.lunarexplorer.data.download.DownloadRecord
import com.subhranil.lunarexplorer.data.session.ServerSession
import com.subhranil.lunarexplorer.testing.FakeDownloadFiles
import com.subhranil.lunarexplorer.testing.FakeLunarServer
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadEngineTest {
    private val content = ByteArray(300_000) { (it % 251).toByte() }
    private val server = FakeLunarServer().file("/share/big.bin", content).dir("/share/docs")
    private val files = FakeDownloadFiles()
    private val engine = DownloadEngine(files)

    private class Listener : DownloadListener {
        var startOffset = -1L
        var lastBytes = -1L
        override suspend fun onStart(contentUri: String, size: Long, modified: Long?, offset: Long) {
            startOffset = offset
        }
        override suspend fun onBytes(done: Long) {
            lastBytes = done
        }
    }

    private suspend fun session() = (ServerSession.open("id", server.api(), "user", "pw") as ApiResult.Success).value

    private fun record(
        path: String = "/share/big.bin",
        bytesDone: Long = 0,
        contentUri: String? = null,
        modified: Long? = 1700000000,
    ) = DownloadRecord(
        id = "d1",
        serverId = "id",
        serverName = "Fake",
        remotePath = path,
        name = path.substringAfterLast('/'),
        size = content.size.toLong(),
        remoteModified = modified,
        bytesDone = bytesDone,
        contentUri = contentUri,
        createdAt = 0,
    )

    /** A partial file holding the first [bytes] bytes, as a paused download would leave it. */
    private fun partial(bytes: Int): String {
        val uri = files.create("big.bin")
        files.files[uri] = content.copyOf(bytes)
        return uri
    }

    @Test
    fun downloadsWholeFileAndPublishesIt() = runTest {
        val listener = Listener()
        val outcome = engine.run(session(), record(), listener) as DownloadOutcome.Completed
        assertArrayEquals(content, files.files.getValue(outcome.contentUri))
        assertTrue(outcome.contentUri in files.published)
        assertEquals(listOf<String?>(null), server.downloadRanges)
        assertEquals(content.size.toLong(), listener.lastBytes)
    }

    @Test
    fun resumesFromBytesAlreadyWritten() = runTest {
        val uri = partial(100_000)
        val listener = Listener()
        val outcome = engine.run(session(), record(bytesDone = 100_000, contentUri = uri), listener)
        assertEquals(DownloadOutcome.Completed(uri, content.size.toLong()), outcome)
        assertEquals(listOf<String?>("bytes=100000-"), server.downloadRanges)
        assertEquals(100_000L, listener.startOffset)
        assertArrayEquals(content, files.files.getValue(uri))
    }

    @Test
    fun restartsWhenRemoteFileChanged() = runTest {
        val uri = partial(100_000)
        val outcome = engine.run(session(), record(bytesDone = 100_000, contentUri = uri, modified = 1), Listener())
        assertTrue(outcome is DownloadOutcome.Completed)
        assertEquals(listOf<String?>(null), server.downloadRanges)
        assertArrayEquals(content, files.files.getValue(uri))
    }

    @Test
    fun restartsWhenServerIgnoresRange() = runTest {
        server.ignoreRange = true
        val uri = partial(100_000)
        val outcome = engine.run(session(), record(bytesDone = 100_000, contentUri = uri), Listener())
        assertTrue(outcome is DownloadOutcome.Completed)
        assertArrayEquals(content, files.files.getValue(uri))
    }

    @Test
    fun finishesWhenAllBytesWereAlreadyWritten() = runTest {
        val uri = partial(content.size)
        val outcome = engine.run(session(), record(bytesDone = content.size.toLong(), contentUri = uri), Listener())
        assertEquals(DownloadOutcome.Completed(uri, content.size.toLong()), outcome)
        assertTrue(uri in files.published)
    }

    @Test
    fun startsOverWhenPartialFileIsGone() = runTest {
        val outcome = engine.run(session(), record(bytesDone = 100_000, contentUri = "mem://gone"), Listener())
        outcome as DownloadOutcome.Completed
        assertArrayEquals(content, files.files.getValue(outcome.contentUri))
        assertEquals(listOf<String?>(null), server.downloadRanges)
    }

    @Test
    fun droppedConnectionIsRetryableAndKeepsProgress() = runTest {
        server.truncateNextDownloadAt = 50_000
        val outcome = engine.run(session(), record(), Listener()) as DownloadOutcome.Failed
        assertTrue(outcome.retryable)
        assertEquals(50_000L, outcome.bytesDone)
        assertEquals(DownloadFailure.UNREACHABLE, outcome.failure.code)
        assertTrue(files.published.isEmpty())

        // The next attempt continues from where the connection dropped.
        val uri = files.files.keys.single()
        val resumed = engine.run(session(), record(bytesDone = 50_000, contentUri = uri), Listener())
        assertTrue(resumed is DownloadOutcome.Completed)
        assertEquals("bytes=50000-", server.downloadRanges.last())
        assertArrayEquals(content, files.files.getValue(uri))
    }

    @Test
    fun missingFileFailsWithoutRetry() = runTest {
        val outcome = engine.run(session(), record(path = "/share/nope.bin"), Listener()) as DownloadOutcome.Failed
        assertFalse(outcome.retryable)
        assertEquals(ErrorCode.NOT_FOUND.name, outcome.failure.code)
    }

    @Test
    fun folderFailsWithoutRetry() = runTest {
        val outcome = engine.run(session(), record(path = "/share/docs"), Listener()) as DownloadOutcome.Failed
        assertFalse(outcome.retryable)
        assertEquals(ErrorCode.IS_A_DIRECTORY.name, outcome.failure.code)
    }

    @Test
    fun logsInAgainWhenTokenExpired() = runTest {
        val session = session()
        server.expireToken()
        val outcome = engine.run(session, record(), Listener())
        assertTrue(outcome is DownloadOutcome.Completed)
        assertEquals(2, server.loginCount)
    }
}
