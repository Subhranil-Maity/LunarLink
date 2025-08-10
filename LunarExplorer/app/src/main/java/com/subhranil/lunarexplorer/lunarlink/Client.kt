package com.subhranil.lunarexplorer.lunarlink

import android.util.Log
import io.ktor.client.*
import io.ktor.client.engine.android.*
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json


fun provideHttpClient(): HttpClient {
    return HttpClient(Android) {
        install(ContentNegotiation) {
            json(Json {
                prettyPrint = true
                isLenient = true
                ignoreUnknownKeys = true
            })

        }
//        install(Logging) {
//            logger = object : Logger {
//                override fun log(message: String) {
//                    Log.d("HttpLogging:", message)
//                }
//
//            }
//        }
    }
}