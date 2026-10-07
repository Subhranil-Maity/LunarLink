package com.subhranil.lunarexplorer.data.servers

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.subhranil.lunarexplorer.data.api.LunarJson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import java.util.UUID

/** A saved server as shown in the UI. The password is only available through [ServerRepository.credentials]. */
data class SavedServer(
    val id: String,
    val name: String,
    val baseUrl: String,
    val username: String,
    val osType: String?,
    val lastConnectedAt: Long?,
)

data class Credentials(val baseUrl: String, val username: String, val password: String)

data class ServerDraft(
    val name: String,
    val baseUrl: String,
    val username: String,
    val password: String,
    val osType: String?,
)

class DuplicateServerException : Exception("A server with this address and username is already saved")

@Serializable
private data class StoredServer(
    val id: String,
    val name: String,
    val baseUrl: String,
    val username: String,
    val encryptedPassword: String,
    val osType: String? = null,
    val lastConnectedAt: Long? = null,
)

class ServerRepository(
    private val store: DataStore<Preferences>,
    private val cipher: PasswordCipher,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    val servers: Flow<List<SavedServer>> = store.data.map { prefs -> decode(prefs).map { it.toSaved() } }

    suspend fun get(id: String): SavedServer? = servers.first().firstOrNull { it.id == id }

    suspend fun credentials(id: String): Credentials? {
        val stored = decode(store.data.first()).firstOrNull { it.id == id } ?: return null
        return Credentials(stored.baseUrl, stored.username, cipher.decrypt(stored.encryptedPassword))
    }

    /** Saves a new server and returns its id. Throws [DuplicateServerException]. */
    suspend fun add(draft: ServerDraft): String {
        val id = newId()
        update { list ->
            if (list.any { it.matches(draft) }) throw DuplicateServerException()
            list + StoredServer(
                id = id,
                name = draft.name,
                baseUrl = draft.baseUrl,
                username = draft.username,
                encryptedPassword = cipher.encrypt(draft.password),
                osType = draft.osType,
            )
        }
        return id
    }

    /** Replaces a server's details. Throws [DuplicateServerException]. */
    suspend fun edit(id: String, draft: ServerDraft) = update { list ->
        if (list.any { it.id != id && it.matches(draft) }) throw DuplicateServerException()
        list.map {
            if (it.id != id) it else it.copy(
                name = draft.name,
                baseUrl = draft.baseUrl,
                username = draft.username,
                encryptedPassword = cipher.encrypt(draft.password),
                osType = draft.osType,
            )
        }
    }

    suspend fun delete(id: String) = update { list -> list.filterNot { it.id == id } }

    suspend fun deleteAll() = update { emptyList() }

    suspend fun markConnected(id: String, at: Long, name: String, osType: String) = update { list ->
        list.map { if (it.id == id) it.copy(lastConnectedAt = at, name = name, osType = osType) else it }
    }

    private suspend fun update(transform: (List<StoredServer>) -> List<StoredServer>) {
        store.edit { prefs -> prefs[KEY] = LunarJson.encodeToString(transform(decode(prefs))) }
    }

    private fun decode(prefs: Preferences): List<StoredServer> =
        prefs[KEY]?.let { runCatching { LunarJson.decodeFromString<List<StoredServer>>(it) }.getOrNull() }
            ?: emptyList()

    private fun StoredServer.matches(draft: ServerDraft) =
        baseUrl.equals(draft.baseUrl, ignoreCase = true) && username == draft.username

    private fun StoredServer.toSaved() = SavedServer(id, name, baseUrl, username, osType, lastConnectedAt)

    private companion object {
        val KEY = stringPreferencesKey("servers")
    }
}
