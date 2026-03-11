package com.ikkoaudio.aiclient.feature.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import com.ikkoaudio.aiclient.core.permission.requestRecordPermissionIfNeeded
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(viewModel: ChatViewModel) {
    val state by viewModel.state.collectAsState()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.width(280.dp)
            ) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "AI Client",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(horizontal = 28.dp, vertical = 8.dp)
                )
                HorizontalDivider()
                NavigationDrawerItem(
                    // icon = { Text("🎤") },
                    label = { Text("Voice Chat") },
                    selected = state.selectedPage == AppPage.VOICECHAT,
                    onClick = {
                        viewModel.dispatch(ChatIntent.SelectPage(AppPage.VOICECHAT))
                    }
                )
                NavigationDrawerItem(
                    // icon = { Text("💬") },
                    label = { Text("LLM") },
                    selected = state.selectedPage == AppPage.LLM,
                    onClick = { viewModel.dispatch(ChatIntent.SelectPage(AppPage.LLM)) }
                )
                NavigationDrawerItem(
                    // icon = { Text("🔊") },
                    label = { Text("TTS") },
                    selected = state.selectedPage == AppPage.TTS,
                    onClick = { viewModel.dispatch(ChatIntent.SelectPage(AppPage.TTS)) }
                )
                NavigationDrawerItem(
                    // icon = { Text("📝") },
                    label = { Text("ASR") },
                    selected = state.selectedPage == AppPage.ASR,
                    onClick = { viewModel.dispatch(ChatIntent.SelectPage(AppPage.ASR)) }
                )
                Spacer(modifier = Modifier.weight(1f))
                HorizontalDivider()
                SettingsDrawerSection(
                    apiBaseUrl = state.apiBaseUrl,
                    memoryId = state.memoryId,
                    models = state.models,
                    selectedModel = state.selectedModel,
                    onApiUrlChange = { viewModel.dispatch(ChatIntent.SetApiBaseUrl(it)) },
                    onMemoryIdChange = { viewModel.dispatch(ChatIntent.SetMemoryId(it)) },
                    onModelSelect = { viewModel.dispatch(ChatIntent.SelectModel(it)) },
                    onLoadModels = { viewModel.dispatch(ChatIntent.LoadModels) }
                )
            }
        },
        gesturesEnabled = true
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            when (state.selectedPage) {
                                AppPage.VOICECHAT -> "Voice Chat"
                                AppPage.LLM -> "LLM"
                                AppPage.TTS -> "TTS"
                                AppPage.ASR -> "ASR"
                            }
                        )
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = {
                                if (state.drawerOpen) viewModel.dispatch(ChatIntent.CloseDrawer)
                                else viewModel.dispatch(ChatIntent.OpenDrawer)
                            }
                        ) {
                            Text("≡", style = MaterialTheme.typography.headlineMedium)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface
                    )
                )
            }
        ) { padding ->
            LaunchedEffect(state.drawerOpen) {
                if (state.drawerOpen) drawerState.open() else drawerState.close()
            }
            LaunchedEffect(drawerState) {
                snapshotFlow { drawerState.currentValue }
                    .collectLatest { value ->
                        viewModel.dispatch(
                            if (value == DrawerValue.Open) ChatIntent.OpenDrawer else ChatIntent.CloseDrawer
                        )
                    }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                when (state.selectedPage) {
                    AppPage.VOICECHAT -> VoiceChatPage(state, viewModel)
                    AppPage.LLM -> LlmPage(state, viewModel)
                    AppPage.TTS -> TtsPage(state, viewModel)
                    AppPage.ASR -> AsrPage(state, viewModel)
                }
            }
        }
    }
}

@Composable
private fun SettingsDrawerSection(
    apiBaseUrl: String,
    memoryId: String?,
    models: List<com.ikkoaudio.aiclient.domain.model.LlmModel>,
    selectedModel: String?,
    onApiUrlChange: (String) -> Unit,
    onMemoryIdChange: (String?) -> Unit,
    onModelSelect: (String) -> Unit,
    onLoadModels: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text("Settings", style = MaterialTheme.typography.titleSmall)
        Text(if (expanded) "−" else "+", style = MaterialTheme.typography.titleMedium)
    }
    if (expanded) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            OutlinedTextField(
                value = apiBaseUrl,
                onValueChange = onApiUrlChange,
                label = { Text("API URL") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = memoryId ?: "",
                onValueChange = { onMemoryIdChange(it.ifEmpty { null }) },
                label = { Text("Memory ID") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = onLoadModels, modifier = Modifier.fillMaxWidth()) {
                Text("Load Models")
            }
            if (models.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                models.forEach { model ->
                    FilterChip(
                        selected = model.name == selectedModel,
                        onClick = { onModelSelect(model.name) },
                        label = { Text(model.name) },
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun VoiceChatPage(state: ChatState, viewModel: ChatViewModel) {
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
                shape = androidx.compose.foundation.shape.CircleShape,
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

@Composable
private fun LlmPage(state: ChatState, viewModel: ChatViewModel) {
    val listState = rememberLazyListState()
    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) listState.animateScrollToItem(state.messages.size - 1)
    }
    Column(modifier = Modifier.fillMaxSize()) {
        ErrorBanner(state.error) { viewModel.dispatch(ChatIntent.ClearError) }
        LazyColumn(
            modifier = Modifier.weight(1f),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(16.dp)
        ) {
            items(state.messages) { msg ->
                MessageBubble(role = msg.role, content = msg.content, isStreaming = msg.isStreaming)
            }
            if (state.isLoading && state.messages.none { it.isStreaming }) {
                item { CircularProgressIndicator(modifier = Modifier.padding(16.dp)) }
            }
        }
        LlmInputSection(
            inputText = state.inputText,
            isLoading = state.isLoading,
            onTextChange = { viewModel.dispatch(ChatIntent.UpdateInput(it)) },
            onSend = { viewModel.dispatch(ChatIntent.SendMessage(state.inputText)) }
        )
    }
}

@Composable
private fun TtsPage(state: ChatState, viewModel: ChatViewModel) {
    Column(modifier = Modifier.fillMaxSize()) {
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
                maxLines = 6
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = { viewModel.dispatch(ChatIntent.TextToSpeech) },
                enabled = state.inputText.isNotBlank() && !state.isLoading,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (state.isLoading) CircularProgressIndicator(modifier = Modifier.size(24.dp))
                else Text("Speak")
            }
        }
    }
}

@Composable
private fun AsrPage(state: ChatState, viewModel: ChatViewModel) {
    Column(modifier = Modifier.fillMaxSize()) {
        ErrorBanner(state.error) { viewModel.dispatch(ChatIntent.ClearError) }
        Spacer(modifier = Modifier.weight(1f))
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
                shape = androidx.compose.foundation.shape.CircleShape,
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

@Composable
private fun ErrorBanner(error: String?, onDismiss: () -> Unit) {
    error?.let { msg ->
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = CardDefaults.cardColors(MaterialTheme.colorScheme.errorContainer),
            onClick = onDismiss
        ) {
            Text(msg, modifier = Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onErrorContainer)
        }
    }
}

@Composable
private fun MessageBubble(role: String, content: String, isStreaming: Boolean) {
    val isUser = role == "user"
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 12.dp, topEnd = 12.dp,
                bottomStart = if (isUser) 12.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 12.dp
            ),
            color = if (isUser) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceVariant
        ) {
            Text(
                text = content.ifEmpty { if (isStreaming) "..." else "" },
                modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}

@Composable
private fun LlmInputSection(
    inputText: String,
    isLoading: Boolean,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit
) {
    Surface(tonalElevation = 3.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            OutlinedTextField(
                value = inputText,
                onValueChange = onTextChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("Message...") },
                maxLines = 4
            )
            Spacer(modifier = Modifier.width(8.dp))
            FilledTonalButton(
                onClick = onSend,
                enabled = inputText.isNotBlank() && !isLoading
            ) {
                Text("Send")
            }
        }
    }
}
