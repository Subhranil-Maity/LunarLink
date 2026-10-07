package com.subhranil.lunarexplorer.testing

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.subhranil.lunarexplorer.data.download.DownloadFiles
import com.subhranil.lunarexplorer.data.download.DownloadNotifier
import com.subhranil.lunarexplorer.data.download.DownloadRecord
import com.subhranil.lunarexplorer.data.download.DownloadScheduler
import com.subhranil.lunarexplorer.data.download.DownloadSummary
import com.subhranil.lunarexplorer.data.download.DownloadWriter
import com.subhranil.lunarexplorer.data.servers.PasswordCipher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files

/** Reversible "encryption" so tests can check that passwords aren't stored as-is. */
class FakeCipher : PasswordCipher {
    override fun encrypt(plain: String) = "enc:" + plain.reversed()
    override fun decrypt(encoded: String) = encoded.removePrefix("enc:").reversed()
}

fun tempDataStore(scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())): DataStore<Preferences> {
    val dir = Files.createTempDirectory("lunar-test").toFile()
    return PreferenceDataStoreFactory.create(scope = scope) { File(dir, "test.preferences_pb") }
}

@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(val dispatcher: TestDispatcher = UnconfinedTestDispatcher()) : TestWatcher() {
    override fun starting(description: Description) = Dispatchers.setMain(dispatcher)
    override fun finished(description: Description) = Dispatchers.resetMain()
}

/** In-memory [DownloadFiles]; uris are "mem://<n>". */
class FakeDownloadFiles(private val writeDelayMs: Long = 0) : DownloadFiles {
    // Downloads run in parallel on IO threads, so everything here must be thread-safe.
    val files = java.util.concurrent.ConcurrentHashMap<String, ByteArray>()
    val names = java.util.concurrent.ConcurrentHashMap<String, String>()
    val published: MutableSet<String> = java.util.concurrent.ConcurrentHashMap.newKeySet()
    private val next = java.util.concurrent.atomic.AtomicInteger()

    override fun create(name: String): String {
        val uri = "mem://${next.getAndIncrement()}"
        files[uri] = ByteArray(0)
        names[uri] = name
        return uri
    }

    override fun exists(uri: String) = files.containsKey(uri)

    override fun open(uri: String, offset: Long): DownloadWriter {
        val start = files.getValue(uri).copyOf(offset.toInt())
        val out = ByteArrayOutputStream().apply { write(start) }
        return object : DownloadWriter {
            override fun write(buffer: ByteArray, length: Int) {
                if (writeDelayMs > 0) Thread.sleep(writeDelayMs)
                out.write(buffer, 0, length)
                files[uri] = out.toByteArray()
            }

            override fun close() {
                files[uri] = out.toByteArray()
            }
        }
    }

    override fun publish(uri: String) {
        published += uri
    }

    override fun delete(uri: String) {
        files.remove(uri)
        published.remove(uri)
    }
}

class FakeScheduler : DownloadScheduler {
    var calls = 0
        private set

    var lastRestart: Boolean? = null
        private set

    override fun schedule(restart: Boolean) {
        calls++
        lastRestart = restart
    }
}

class RecordingNotifier : DownloadNotifier {
    val completed = mutableListOf<String>()
    val failed = mutableListOf<String>()
    override fun progress(summary: DownloadSummary) = Unit
    override fun completed(record: DownloadRecord) {
        completed += record.name
    }
    override fun failed(record: DownloadRecord) {
        failed += record.name
    }
    override fun idle() = Unit
}
