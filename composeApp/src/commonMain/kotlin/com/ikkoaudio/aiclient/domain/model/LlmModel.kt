package com.ikkoaudio.aiclient.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class LlmModel(
    val name: String,
    val modifiedAt: String? = null
)
