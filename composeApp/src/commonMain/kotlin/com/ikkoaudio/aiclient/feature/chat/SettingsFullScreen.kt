package com.ikkoaudio.aiclient.feature.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ikkoaudio.aiclient.AppVersion

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsFullScreen(
    state: ChatState,
    viewModel: ChatViewModel,
    onClose: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = ChatLayoutTokens.CenterBackground
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Close settings"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = ChatLayoutTokens.NavInactiveBackground,
                    titleContentColor = ChatLayoutTokens.NavText
                )
            )
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedTextField(
                    value = state.memoryId ?: "",
                    onValueChange = { viewModel.dispatch(ChatIntent.SetMemoryId(it.ifEmpty { null })) },
                    label = { Text("Memory ID") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Button(
                    onClick = { viewModel.dispatch(ChatIntent.LoadModels) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Load Models")
                }
                state.models.forEach { model ->
                    FilterChip(
                        selected = model.name == state.selectedModel,
                        onClick = { viewModel.dispatch(ChatIntent.SelectModel(model.name)) },
                        label = {
                            Text(
                                model.name,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "App version",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = AppVersion.VERSION_NAME,
                    style = MaterialTheme.typography.titleMedium,
                    color = ChatLayoutTokens.NavText
                )
            }
        }
    }
}
