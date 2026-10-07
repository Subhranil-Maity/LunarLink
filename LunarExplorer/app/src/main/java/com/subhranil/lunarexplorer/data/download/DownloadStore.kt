package com.subhranil.lunarexplorer.data.download

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.subhranil.lunarexplorer.data.api.LunarJson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** Persists download records as one JSON list. Edits are serialized by DataStore, so [update] is atomic. */
class DownloadStore(private val store: DataStore<Preferences>) {

    val records: Flow<List<DownloadRecord>> = store.data.map(::decode)

    suspend fun current(): List<DownloadRecord> = records.first()

    /** Applies [transform] atomically and returns the new list. */
    suspend fun update(transform: (List<DownloadRecord>) -> List<DownloadRecord>): List<DownloadRecord> {
        var result = emptyList<DownloadRecord>()
        store.edit { prefs ->
            result = transform(decode(prefs))
            prefs[KEY] = LunarJson.encodeToString(result)
        }
        return result
    }

    private fun decode(prefs: Preferences): List<DownloadRecord> =
        prefs[KEY]?.let { runCatching { LunarJson.decodeFromString<List<DownloadRecord>>(it) }.getOrNull() }
            ?: emptyList()

    private companion object {
        val KEY = stringPreferencesKey("downloads")
    }
}
