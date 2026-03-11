package com.ikkoaudio.aiclient.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class TranscribeResponse(
    val text: String? = null
)

@Serializable
data class ChatResponse(
    val message: String? = null,
    val content: String? = null,
    val response: String? = null,
    val text: String? = null
)

@Serializable
data class ModelsResponse(
    val models: List<ModelItem>? = null
)

@Serializable
data class ModelItem(
    val name: String,
    val modified_at: String? = null
)

@Serializable
data class TtsResponse(
    val url: String? = null,
    val audioUrl: String? = null
)
