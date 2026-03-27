package com.ikkoaudio.aiclient.di

import io.ktor.client.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.engine.js.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

actual fun createHttpClient(): HttpClient = HttpClient(Js) {
    install(ContentNegotiation) {
        json(Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
        })
    }
    install(Logging) {
        level = LogLevel.INFO
    }
    installAppWebSockets()
}
