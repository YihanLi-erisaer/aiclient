package com.ikkoaudio.aiclient.feature.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.KeyboardVoice
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material3.*
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
                    icon = { Icon(Icons.Outlined.Mic, contentDescription = null) },
                    label = { Text("Voice Chat") },
                    selected = state.selectedPage == AppPage.VOICECHAT,
                    onClick = {
                        viewModel.dispatch(ChatIntent.SelectPage(AppPage.VOICECHAT))
                    }
                )
                NavigationDrawerItem(
                    icon = { Icon(Icons.AutoMirrored.Outlined.Chat, contentDescription = null) },
                    label = { Text("LLM") },
                    selected = state.selectedPage == AppPage.LLM,
                    onClick = { viewModel.dispatch(ChatIntent.SelectPage(AppPage.LLM)) }
                )
                NavigationDrawerItem(
                    icon = { Icon(Icons.AutoMirrored.Outlined.VolumeUp, contentDescription = null) },
                    label = { Text("TTS") },
                    selected = state.selectedPage == AppPage.TTS,
                    onClick = { viewModel.dispatch(ChatIntent.SelectPage(AppPage.TTS)) }
                )
                NavigationDrawerItem(
                    icon = { Icon(Icons.Outlined.KeyboardVoice, contentDescription = null) },
                    label = { Text("ASR") },
                    selected = state.selectedPage == AppPage.ASR,
                    onClick = { viewModel.dispatch(ChatIntent.SelectPage(AppPage.ASR)) }
                )
                Spacer(modifier = Modifier.weight(1f))
                HorizontalDivider()
                SettingsDrawerSection(
                    memoryId = state.memoryId,
                    models = state.models,
                    selectedModel = state.selectedModel,
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
                            Icon(Icons.Filled.Menu, contentDescription = "Menu")
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
    memoryId: String?,
    models: List<com.ikkoaudio.aiclient.domain.model.LlmModel>,
    selectedModel: String?,
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
