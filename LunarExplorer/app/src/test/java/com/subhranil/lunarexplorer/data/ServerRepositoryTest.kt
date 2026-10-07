package com.subhranil.lunarexplorer.data

import androidx.datastore.preferences.core.stringPreferencesKey
import com.subhranil.lunarexplorer.data.servers.DuplicateServerException
import com.subhranil.lunarexplorer.data.servers.ServerDraft
import com.subhranil.lunarexplorer.data.servers.ServerRepository
import com.subhranil.lunarexplorer.testing.FakeCipher
import com.subhranil.lunarexplorer.testing.tempDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ServerRepositoryTest {

    private val store = tempDataStore()
    private val repo = ServerRepository(store, FakeCipher())

    private fun draft(url: String = "http://host:1234", user: String = "me", pass: String = "secret") =
        ServerDraft("Desk", url, user, pass, "linux")

    @Test
    fun addListAndReadCredentials() = runTest {
        val id = repo.add(draft())
        val saved = repo.servers.first().single()
        assertEquals(id, saved.id)
        assertEquals("Desk", saved.name)
        assertNull(saved.lastConnectedAt)
        assertEquals("secret", repo.credentials(id)!!.password)
    }

    @Test
    fun passwordIsNotStoredInPlainText() = runTest {
        repo.add(draft(pass = "hunter2"))
        val raw = store.data.first()[stringPreferencesKey("servers")]!!
        assertFalse(raw, "hunter2" in raw)
    }

    @Test
    fun rejectsDuplicates() = runTest {
        repo.add(draft())
        try {
            repo.add(draft(url = "HTTP://HOST:1234"))
            fail("expected duplicate")
        } catch (_: DuplicateServerException) {
        }
        // Same address, other user is fine.
        repo.add(draft(user = "other"))
        assertEquals(2, repo.servers.first().size)
    }

    @Test
    fun editDeleteAndMarkConnected() = runTest {
        val a = repo.add(draft())
        val b = repo.add(draft(url = "http://other"))
        try {
            repo.edit(b, draft())
            fail("editing into a duplicate must fail")
        } catch (_: DuplicateServerException) {
        }
        repo.edit(b, draft(url = "http://renamed", pass = "new"))
        assertEquals("http://renamed", repo.get(b)!!.baseUrl)
        assertEquals("new", repo.credentials(b)!!.password)

        repo.markConnected(a, 99, "Real name", "windows")
        val saved = repo.get(a)!!
        assertEquals(99L, saved.lastConnectedAt)
        assertEquals("Real name", saved.name)
        assertEquals("windows", saved.osType)

        repo.delete(a)
        assertNull(repo.get(a))
        repo.deleteAll()
        assertTrue(repo.servers.first().isEmpty())
    }

    @Test
    fun corruptDataIsTreatedAsEmpty() = runTest {
        store.updateData { it.toMutablePreferences().apply { set(stringPreferencesKey("servers"), "{broken") } }
        assertTrue(repo.servers.first().isEmpty())
    }
}
