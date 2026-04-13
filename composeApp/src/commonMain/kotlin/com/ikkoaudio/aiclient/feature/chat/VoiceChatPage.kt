package com.ikkoaudio.aiclient.feature.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ikkoaudio.aiclient.core.permission.requestRecordPermissionIfNeeded

@Composable
fun VoiceChatPage(state: ChatState, viewModel: ChatViewModel) {
    VoiceChatBody(state, viewModel, Modifier.fillMaxSize())
}

@Composable
fun VoiceChatBody(state: ChatState, viewModel: ChatViewModel, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        ErrorBanner(state.error) { viewModel.dispatch(ChatIntent.ClearError) }
        Spacer(modifier = Modifier.weight(1f))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            OutlinedButton(
                onClick = { viewModel.dispatch(ChatIntent.CheckVoiceChatWebSocketHandshake) },
                enabled = state.voiceChatWebSocketHandshake !is VoiceChatWebSocketHandshakeState.Checking,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    when (state.voiceChatWebSocketHandshake) {
                        is VoiceChatWebSocketHandshakeState.Checking -> "Checking WebSocket…"
                        else -> "Check WebSocket handshake (HTTP 101)"
                    }
                )
            }
            when (val h = state.voiceChatWebSocketHandshake) {
                is VoiceChatWebSocketHandshakeState.Ok -> Text(
                    text = "Handshake OK: ${h.detail}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp)
                )
                is VoiceChatWebSocketHandshakeState.Failed -> Text(
                    text = "Handshake failed: ${h.message}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 8.dp)
                )
                else -> { }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Local ASR",
                    style = MaterialTheme.typography.bodyMedium
                )
                Switch(
                    checked = state.useLocalAsr,
                    onCheckedChange = { viewModel.dispatch(ChatIntent.ToggleLocalAsr) },
                    enabled = !state.isRecording,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
                Text(
                    if (state.localAsrAvailable) "Ready" else "Unavailable",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (state.localAsrAvailable)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.outline
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            if (state.isLoading) {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(16.dp))
            }
            Button(
                onClick = {
                    if (state.isRecording) {
                        viewModel.dispatch(ChatIntent.StopRecording)
                    } else {
                        requestRecordPermissionIfNeeded { granted ->
                            if (granted) viewModel.dispatch(ChatIntent.StartVoiceChat)
                            else viewModel.dispatch(ChatIntent.SetError("Microphone permission is required for recording"))
                        }
                    }
                },
                modifier = Modifier.size(180.dp, 56.dp),
                shape = RoundedCornerShape(ChatLayoutTokens.CornerRadius),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (state.isRecording)
                        MaterialTheme.colorScheme.error
                    else
                        ChatLayoutTokens.SidebarBackground,
                    contentColor = if (state.isRecording)
                        MaterialTheme.colorScheme.onError
                    else
                        ChatLayoutTokens.NavText
                )
            ) {
                Text(
                    if (state.isRecording) "Stop listening" else "Start listening",
                    style = MaterialTheme.typography.titleSmall
                )
            }
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                if (state.useLocalAsr)
                    "Keeps the microphone open. When VAD detects the end of a phrase, " +
                        "it is transcribed locally (sherpa-onnx), the text is sent to the LLM, " +
                        "and the reply is spoken via TTS. Tap Stop when you are done."
                else
                    "Keeps the microphone open. When VAD detects the end of a phrase, " +
                        "that audio clip is sent to the server for ASR→LLM→TTS and the reply is played. " +
                        "Tap Stop when you are done.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
        }
        Spacer(modifier = Modifier.weight(1f))
    }
}
