package com.ikkoaudio.aiclient.di

import io.ktor.client.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.logging.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import java.util.concurrent.TimeUnit

actual fun createHttpClient(): HttpClient = HttpClient(io.ktor.client.engine.okhttp.OkHttp) {
    engine {
        config {
            connectTimeout(15, TimeUnit.SECONDS)
            readTimeout(90, TimeUnit.SECONDS)
            writeTimeout(90, TimeUnit.SECONDS)
            callTimeout(90, TimeUnit.SECONDS)
        }
    }
    install(ContentNegotiation) {
        json(Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
        })
    }
    install(HttpTimeout) {
        requestTimeoutMillis = 90_000
        connectTimeoutMillis = 15_000
        socketTimeoutMillis = 90_000
    }
    install(Logging) {
        level = LogLevel.INFO
    }
}
