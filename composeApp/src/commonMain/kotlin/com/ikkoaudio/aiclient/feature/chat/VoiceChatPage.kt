package com.ikkoaudio.aiclient.feature.chat

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ikkoaudio.aiclient.core.permission.requestRecordPermissionIfNeeded

@Composable
fun VoiceChatPage(state: ChatState, viewModel: ChatViewModel) {
    Column(modifier = Modifier.fillMaxSize()) {
        ErrorBanner(state.error) { viewModel.dispatch(ChatIntent.ClearError) }
        Spacer(modifier = Modifier.weight(1f))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (state.isLoading) {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(16.dp))
            }
            Text(
                "Press and hold to speak. Your voice will be sent to the AI and the response played aloud.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = 24.dp)
            )
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
                modifier = Modifier.size(120.dp),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (state.isRecording)
                        MaterialTheme.colorScheme.error
                    else
                        MaterialTheme.colorScheme.primary
                )
            ) {
                Text(if (state.isRecording) "Stop" else "Hold to talk", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}
