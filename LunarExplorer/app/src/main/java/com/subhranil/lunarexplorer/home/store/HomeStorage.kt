package com.subhranil.lunarexplorer.home.store

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.subhranil.lunarexplorer.home.models.LunarHostConnectionConfig
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json

class HomeStorage(
    val context: Context
) {
    private companion object{
        const val LUNAR_HOST: String = "LUNAR_HOST"
    }
    private val Context.homeDataStore by preferencesDataStore("home_storage")

    suspend fun saveString(key: String, value: String){
        context.homeDataStore.edit {
            it[stringPreferencesKey(key)] = value

        }
    }
    suspend fun getString(key: String): String? {
        return context.homeDataStore.data.first()[stringPreferencesKey(key)]
    }

    suspend fun getSavedLunarHosts(): List<LunarHostConnectionConfig> {
        val string = getString(LUNAR_HOST)?: return emptyList()
        return try {
            Json.decodeFromString<List<LunarHostConnectionConfig>>(string)
        }catch (e: Exception){
            currentCoroutineContext().ensureActive()
            emptyList()
        }
    }
    suspend fun saveLunarHosts(hosts: List<LunarHostConnectionConfig>){
        saveString(LUNAR_HOST, Json.encodeToString(hosts))
    }
}

