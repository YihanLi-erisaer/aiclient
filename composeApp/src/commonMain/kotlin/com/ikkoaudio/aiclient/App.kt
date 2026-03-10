package com.ikkoaudio.aiclient

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import com.ikkoaudio.aiclient.di.AppModule
import com.ikkoaudio.aiclient.feature.chat.ChatScreen
import com.ikkoaudio.aiclient.feature.chat.ChatViewModel

@Composable
fun App() {
    val scope = rememberCoroutineScope()
    MaterialTheme {
        val viewModel: ChatViewModel = remember(scope) {
            AppModule.createChatViewModel(scope)
        }
        ChatScreen(viewModel = viewModel)
    }
}

@Composable
fun AppPreview() {
    MaterialTheme {
        val scope = rememberCoroutineScope()
        val viewModel = remember(scope) {
            AppModule.createChatViewModel(scope)
        }
        ChatScreen(viewModel = viewModel)
    }
}
