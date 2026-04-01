package com.ikkoaudio.aiclient.feature.chat

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun TtsLeftPanel(modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxSize().padding(32.dp)) {
        Text(
            text = "TTS",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Type or paste text in the center panel, then tap Speak to hear it aloud.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun TtsPage(state: ChatState, viewModel: ChatViewModel) {
    TtsCenterBody(state, viewModel, Modifier.fillMaxSize())
}

@Composable
fun TtsCenterBody(state: ChatState, viewModel: ChatViewModel, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        ErrorBanner(state.error) { viewModel.dispatch(ChatIntent.ClearError) }
        Spacer(modifier = Modifier.weight(1f))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            OutlinedTextField(
                value = state.inputText,
                onValueChange = { viewModel.dispatch(ChatIntent.UpdateInput(it)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp),
                placeholder = { Text("Enter text to convert to speech...") },
                shape = RoundedCornerShape(ChatLayoutTokens.CornerRadius),
                maxLines = 6
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = { viewModel.dispatch(ChatIntent.TextToSpeech) },
                enabled = state.inputText.isNotBlank() && !state.isLoading,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(ChatLayoutTokens.CornerRadius),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ChatLayoutTokens.SidebarBackground,
                    contentColor = ChatLayoutTokens.NavText
                )
            ) {
                if (state.isLoading) CircularProgressIndicator(modifier = Modifier.size(24.dp))
                else Text("Speak")
            }
        }
        Spacer(modifier = Modifier.weight(1f))
    }
}
