package com.subhranil.lunarexplorer.data.api

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/** One JSON configuration for the whole app. Unknown fields are ignored so new server fields don't break the app. */
val LunarJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
}

// OkHttp rather than the HttpURLConnection-based Android engine: cancelling a request while its body is
// being read (pausing a download, leaving a folder) crashes the Android engine but is safe with OkHttp.
fun createHttpClient(engine: HttpClientEngine = OkHttp.create()): HttpClient = HttpClient(engine) {
    expectSuccess = false
    install(ContentNegotiation) { json(LunarJson) }
    install(HttpTimeout) {
        connectTimeoutMillis = 5_000
        socketTimeoutMillis = 30_000
        requestTimeoutMillis = 30_000
    }
}
