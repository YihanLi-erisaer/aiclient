package com.ikkoaudio.aiclient.feature.chat

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ikkoaudio.aiclient.core.permission.requestRecordPermissionIfNeeded

@Composable
fun AsrPage(state: ChatState, viewModel: ChatViewModel) {
    Column(modifier = Modifier.fillMaxSize()) {
        ErrorBanner(state.error) { viewModel.dispatch(ChatIntent.ClearError) }
        Spacer(modifier = Modifier.heightIn(min = 1.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            OutlinedTextField(
                value = state.inputText,
                onValueChange = { viewModel.dispatch(ChatIntent.UpdateInput(it)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp),
                placeholder = { Text("Transcribed text will appear here...") },
                maxLines = 6,
                readOnly = true
            )
            Spacer(modifier = Modifier.height(24.dp))
            if (state.isLoading) CircularProgressIndicator()
            Button(
                onClick = {
                    if (state.isRecording) {
                        viewModel.dispatch(ChatIntent.StopRecording)
                    } else {
                        requestRecordPermissionIfNeeded { granted ->
                            if (granted) viewModel.dispatch(ChatIntent.StartRecording)
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
                Text(if (state.isRecording) "Stop" else "Record", style = MaterialTheme.typography.labelMedium)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Tap to record. Audio will be transcribed to text.",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
