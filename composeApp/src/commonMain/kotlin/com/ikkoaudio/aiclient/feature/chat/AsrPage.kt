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
import androidx.compose.foundation.shape.RoundedCornerShape
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

/** Left column in wide layout: transcription output */
@Composable
fun AsrLeftPanel(state: ChatState, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxSize().padding(32.dp)) {
        Text(
            text = "Transcription",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = state.outputText,
            onValueChange = { },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .heightIn(min = 120.dp),
            placeholder = { Text("Transcribed text will appear here...") },
            maxLines = 12,
            readOnly = true
        )
    }
}

/** Center column in wide layout: record control */
@Composable
fun AsrCenterPanel(state: ChatState, viewModel: ChatViewModel, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxSize()) {
        ErrorBanner(state.error) { viewModel.dispatch(ChatIntent.ClearError) }
        Spacer(modifier = Modifier.weight(1f))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (state.isLoading) CircularProgressIndicator()
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = {
                    if (state.isRecording) {
                        viewModel.dispatch(ChatIntent.StopRecording)
                    } else {
                        requestRecordPermissionIfNeeded { granted ->
                            if (granted) viewModel.dispatch(ChatIntent.StartAsrListening)
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
                Text(if (state.isRecording) "Stop" else "Record", style = MaterialTheme.typography.titleSmall)
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "Keeps the microphone open. When VAD detects the end of a phrase, " +
                    "it is transcribed and appended to the output. Tap Stop when done.",
                style = MaterialTheme.typography.bodyMedium
            )
        }
        Spacer(modifier = Modifier.weight(1f))
    }
}

/** Narrow layout: single column (transcription + record), same as before */
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
                value = state.outputText,
                onValueChange = { },
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
                            if (granted) viewModel.dispatch(ChatIntent.StartAsrListening)
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
                Text(if (state.isRecording) "Stop" else "Record", style = MaterialTheme.typography.titleSmall)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Keeps the microphone open. When VAD detects the end of a phrase, " +
                    "it is transcribed and appended to the output. Tap Stop when done.",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
