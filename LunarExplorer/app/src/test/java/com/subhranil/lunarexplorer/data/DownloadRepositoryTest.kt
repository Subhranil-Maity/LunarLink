package com.subhranil.lunarexplorer.data

import com.subhranil.lunarexplorer.data.api.Entry
import com.subhranil.lunarexplorer.data.api.EntryKind
import com.subhranil.lunarexplorer.data.download.DownloadEngine
import com.subhranil.lunarexplorer.data.download.DownloadFailure
import com.subhranil.lunarexplorer.data.download.DownloadRecord
import com.subhranil.lunarexplorer.data.download.DownloadRepository
import com.subhranil.lunarexplorer.data.download.DownloadRunner
import com.subhranil.lunarexplorer.data.download.DownloadSessions
import com.subhranil.lunarexplorer.data.download.DownloadState
import com.subhranil.lunarexplorer.data.download.DownloadStore
import com.subhranil.lunarexplorer.data.servers.ServerDraft
import com.subhranil.lunarexplorer.data.servers.ServerRepository
import com.subhranil.lunarexplorer.testing.FakeCipher
import com.subhranil.lunarexplorer.testing.FakeDownloadFiles
import com.subhranil.lunarexplorer.testing.FakeLunarServer
import com.subhranil.lunarexplorer.testing.FakeScheduler
import com.subhranil.lunarexplorer.testing.RecordingNotifier
import com.subhranil.lunarexplorer.testing.tempDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadRepositoryTest {
    private val server = FakeLunarServer()
        .file("/share/a.txt", "hello".toByteArray())
        .file("/share/b.txt", "world!".toByteArray())
    private val store = DownloadStore(tempDataStore())
    private val files = FakeDownloadFiles()
    private val scheduler = FakeScheduler()
    private val notifier = RecordingNotifier()
    private var ids = 0

    private fun repository(scope: CoroutineScope) =
        DownloadRepository(store, files, scheduler, scope, clock = { 1000L + ids }, newId = { "d${ids++}" })

    private suspend fun runner(repo: DownloadRepository): DownloadRunner {
        val servers = ServerRepository(tempDataStore(), FakeCipher(), newId = { "server" })
        servers.add(ServerDraft("Fake", "http://fake", "user", "pw", "linux"))
        return DownloadRunner(repo, DownloadEngine(files), DownloadSessions(servers) { server.api() }, notifier)
    }

    private fun entry(name: String, kind: EntryKind = EntryKind.FILE, size: Long = 5) =
        Entry(name, "/share/$name", kind, size = size, modified = 1700000000)

    private suspend fun record(id: String): DownloadRecord = store.current().single { it.id == id }

    @Test
    fun enqueueSkipsFoldersAndSchedulesOnce() = runTest {
        val repo = repository(backgroundScope)
        val queued = repo.enqueue("server", "Fake", listOf(entry("a.txt"), entry("docs", EntryKind.DIR)))
        assertEquals(1, queued)
        assertEquals(listOf("a.txt"), store.current().map { it.name })
        assertEquals(DownloadState.QUEUED, store.current().single().state)
        assertEquals(1, scheduler.calls)

        assertEquals(0, repo.enqueue("server", "Fake", listOf(entry("docs", EntryKind.DIR))))
        assertEquals(1, scheduler.calls)
    }

    @Test
    fun runnerDownloadsQueueAndStops() = runTest {
        val repo = repository(backgroundScope)
        repo.enqueue("server", "Fake", listOf(entry("a.txt", size = 5), entry("b.txt", size = 6)))

        val waiting = runner(repo).run()

        assertFalse(waiting)
        assertTrue(store.current().all { it.state == DownloadState.COMPLETED })
        assertEquals(setOf("a.txt", "b.txt"), notifier.completed.toSet())
        val a = record("d0")
        assertArrayEquals("hello".toByteArray(), files.files.getValue(a.contentUri!!))
        assertEquals(5L, a.bytesDone)
        assertTrue(repo.liveBytes.value.isEmpty())
    }

    @Test
    fun enqueueDuringRunDoesNotScheduleAgain() = runTest {
        val repo = repository(backgroundScope)
        repo.beginRun()
        repo.enqueue("server", "Fake", listOf(entry("a.txt")))
        assertEquals(0, scheduler.calls)
        // The run sees the new record before it ends.
        assertFalse(repo.endRun(emptySet()))
    }

    @Test
    fun networkFailureRequeuesUntilRetriesRunOut() = runTest {
        val repo = repository(backgroundScope)
        repo.enqueue("server", "Fake", listOf(entry("a.txt")))
        val runner = runner(repo)

        repeat(5) { attempt ->
            server.truncateNextDownloadAt = 0
            assertTrue("attempt $attempt should wait for the network", runner.run())
            val r = record("d0")
            assertEquals(DownloadState.QUEUED, r.state)
            assertEquals(DownloadFailure.UNREACHABLE, r.failure?.code)
        }
        server.truncateNextDownloadAt = 0
        assertFalse(runner.run())
        assertEquals(DownloadState.FAILED, record("d0").state)
        assertEquals(listOf("a.txt"), notifier.failed)
    }

    @Test
    fun permanentFailureIsNotRetried() = runTest {
        val repo = repository(backgroundScope)
        repo.enqueue("server", "Fake", listOf(entry("missing.txt")))
        assertFalse(runner(repo).run())
        val r = record("d0")
        assertEquals(DownloadState.FAILED, r.state)
        assertEquals("NOT_FOUND", r.failure?.code)
    }

    @Test
    fun pauseResumeAndRetry() = runTest {
        val repo = repository(backgroundScope)
        repo.enqueue("server", "Fake", listOf(entry("a.txt")))
        repo.pause("d0")
        assertEquals(DownloadState.PAUSED, record("d0").state)

        // A paused download isn't picked up.
        assertFalse(runner(repo).run())
        assertEquals(DownloadState.PAUSED, record("d0").state)

        repo.resume("d0")
        assertEquals(DownloadState.QUEUED, record("d0").state)
        assertEquals(2, scheduler.calls)
        runner(repo).run()
        assertEquals(DownloadState.COMPLETED, record("d0").state)
    }

    @Test
    fun removeDeletesPartialFileButClearKeepsFinishedFiles() = runTest {
        val repo = repository(backgroundScope)
        repo.enqueue("server", "Fake", listOf(entry("a.txt"), entry("b.txt", size = 6)))
        runner(repo).run()
        val uri = record("d0").contentUri!!

        repo.enqueue("server", "Fake", listOf(entry("a.txt")))
        server.truncateNextDownloadAt = 2
        runner(repo).run()
        val partialUri = record("d2").contentUri!!
        assertEquals(2, files.files.getValue(partialUri).size)

        repo.remove("d2")
        assertFalse(files.exists(partialUri))
        assertNull(store.current().firstOrNull { it.id == "d2" })

        repo.clearFinished()
        assertTrue(store.current().isEmpty())
        assertTrue(files.exists(uri))
    }

    @Test
    fun recoversDownloadsInterruptedByProcessDeath() = runTest {
        val repo = repository(backgroundScope)
        repo.enqueue("server", "Fake", listOf(entry("a.txt")))
        repo.claimNext(emptySet())
        assertEquals(DownloadState.RUNNING, record("d0").state)

        repo.recoverInterrupted()
        assertEquals(DownloadState.QUEUED, record("d0").state)
        assertEquals(2, scheduler.calls)
        assertEquals(false, scheduler.lastRestart)
    }

    @Test
    fun recoveryLeavesTheCurrentRunAlone() = runTest {
        val repo = repository(backgroundScope)
        repo.enqueue("server", "Fake", listOf(entry("a.txt")))
        repo.beginRun()
        repo.claimNext(emptySet())

        repo.recoverInterrupted()
        assertEquals(DownloadState.RUNNING, record("d0").state)
        assertEquals(1, scheduler.calls)
    }

    @Test
    fun removeStopsARunningTransfer() = runTest {
        val repo = repository(backgroundScope)
        server.file("/share/slow.bin", ByteArray(200_000) { 1 })
        server.slowDownloads = true
        repo.enqueue("server", "Fake", listOf(entry("slow.bin", size = 200_000)))
        val runner = runner(repo)

        val run = async(Dispatchers.Default) { runner.run() }
        withContext(Dispatchers.Default) {
            withTimeout(5_000) { repo.liveBytes.first { (it["d0"] ?: 0) > 0 } }
        }
        repo.remove("d0")

        withContext(Dispatchers.Default) { withTimeout(5_000) { run.await() } }
        assertTrue(store.current().isEmpty())
        assertTrue(notifier.failed.isEmpty())
        assertTrue(files.files.isEmpty())
        // The stopped transfer must not be queued again and restarted.
        assertEquals(1, server.downloadRanges.size)
    }

    @Test
    fun pauseStopsARunningTransfer() = runTest {
        val repo = repository(backgroundScope)
        server.file("/share/slow.bin", ByteArray(200_000) { 1 })
        server.slowDownloads = true
        repo.enqueue("server", "Fake", listOf(entry("slow.bin", size = 200_000)))
        val runner = runner(repo)

        val run = async(Dispatchers.Default) { runner.run() }
        withContext(Dispatchers.Default) {
            withTimeout(5_000) { repo.liveBytes.first { (it["d0"] ?: 0) > 0 } }
        }
        repo.pause("d0")

        withContext(Dispatchers.Default) { withTimeout(5_000) { run.await() } }
        val r = record("d0")
        assertEquals(DownloadState.PAUSED, r.state)
        assertTrue(r.bytesDone in 1 until 200_000)
        assertTrue(notifier.failed.isEmpty())
    }

    @Test
    fun removeStopsATransferWhoseDataIsAlreadyBuffered() = runTest {
        // The whole body is available at once, so reads never suspend; only slow disk writes pace the loop.
        val slowFiles = FakeDownloadFiles(writeDelayMs = 20)
        val repo = DownloadRepository(store, slowFiles, scheduler, backgroundScope, newId = { "d${ids++}" })
        server.file("/share/big.bin", ByteArray(4_000_000) { 1 })
        repo.enqueue("server", "Fake", listOf(entry("big.bin", size = 4_000_000)))
        val servers = ServerRepository(tempDataStore(), FakeCipher(), newId = { "server" })
        servers.add(ServerDraft("Fake", "http://fake", "user", "pw", "linux"))
        val runner = DownloadRunner(repo, DownloadEngine(slowFiles), DownloadSessions(servers) { server.api() }, notifier)

        val run = async(Dispatchers.Default) { runner.run() }
        withContext(Dispatchers.Default) {
            withTimeout(5_000) { repo.liveBytes.first { (it["d0"] ?: 0) > 0 } }
        }
        withContext(Dispatchers.Default) { withTimeout(2_000) { repo.remove("d0") } }

        withContext(Dispatchers.Default) { withTimeout(2_000) { run.await() } }
        assertTrue(store.current().isEmpty())
        assertTrue(notifier.failed.isEmpty())
        assertTrue(notifier.completed.isEmpty())
        assertTrue(slowFiles.files.isEmpty())
    }
}
