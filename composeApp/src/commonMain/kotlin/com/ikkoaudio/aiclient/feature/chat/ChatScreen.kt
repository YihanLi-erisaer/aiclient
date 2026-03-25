package com.ikkoaudio.aiclient.feature.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.outlined.KeyboardVoice
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.material3.DrawerValue
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(viewModel: ChatViewModel) {
    val state by viewModel.state.collectAsState()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val wide = maxWidth >= ChatLayoutTokens.WideLayoutMinWidth
        if (wide) {
            WideChatShell(state, viewModel)
        } else {
            CompactChatShell(
                state = state,
                viewModel = viewModel,
                drawerState = drawerState
            )
        }
    }
}

@Composable
private fun WideChatShell(state: ChatState, viewModel: ChatViewModel) {
    Row(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .weight(ChatLayoutTokens.LeftWeight)
                .fillMaxHeight()
                .background(ChatLayoutTokens.SidebarBackground)
        ) {
            LeftSidebarContent(state = state, viewModel = viewModel)
        }
        Box(
            modifier = Modifier
                .weight(ChatLayoutTokens.CenterWeight)
                .fillMaxHeight()
                .background(ChatLayoutTokens.CenterBackground)
        ) {
            WideCenterPage(state = state, viewModel = viewModel)
        }
        Box(
            modifier = Modifier
                .weight(ChatLayoutTokens.RightWeight)
                .fillMaxHeight()
                .background(ChatLayoutTokens.SidebarBackground)
        ) {
            RightSidebarNav(
                selectedPage = state.selectedPage,
                onSelectPage = { viewModel.dispatch(ChatIntent.SelectPage(it)) },
                memoryId = state.memoryId,
                models = state.models,
                selectedModel = state.selectedModel,
                onMemoryIdChange = { viewModel.dispatch(ChatIntent.SetMemoryId(it)) },
                onModelSelect = { viewModel.dispatch(ChatIntent.SelectModel(it)) },
                onLoadModels = { viewModel.dispatch(ChatIntent.LoadModels) }
            )
        }
    }
}

@Composable
private fun LeftSidebarContent(state: ChatState, viewModel: ChatViewModel) {
    when (state.selectedPage) {
        AppPage.LLM -> ChatHistorySidebar(messages = state.messages, isVoicePage = false)
        AppPage.VOICECHAT -> ChatHistorySidebar(messages = state.messages, isVoicePage = true)
        AppPage.ASR -> AsrLeftPanel(state)
        AppPage.TTS -> TtsLeftPanel()
    }
}

@Composable
private fun ChatHistorySidebar(messages: List<ChatMessageUi>, isVoicePage: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Chat History",
            style = MaterialTheme.typography.titleMedium,
            color = ChatLayoutTokens.NavText
        )
        Spacer(modifier = Modifier.height(12.dp))
        if (messages.isEmpty()) {
            Text(
                text = if (isVoicePage) {
                    "Use Hold to Chat in the center. Text conversations from the LLM tab will show up here."
                } else {
                    "No messages yet. Enter your question below to start."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(messages, key = { it.id }) { msg ->
                    val label = if (msg.role == "user") "You" else "Assistant"
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = ChatLayoutTokens.NavInactiveBackground,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(10.dp)) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = msg.content.ifBlank { "…" },
                                style = MaterialTheme.typography.bodySmall,
                                color = ChatLayoutTokens.NavText,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RightSidebarNav(
    selectedPage: AppPage,
    onSelectPage: (AppPage) -> Unit,
    memoryId: String?,
    models: List<com.ikkoaudio.aiclient.domain.model.LlmModel>,
    selectedModel: String?,
    onMemoryIdChange: (String?) -> Unit,
    onModelSelect: (String) -> Unit,
    onLoadModels: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(40.dp, Alignment.CenterVertically)
    ) {
        Spacer(modifier = Modifier.weight(0.2f))
        ModeNavButton(
            label = "Voice Chat",
            selected = selectedPage == AppPage.VOICECHAT,
            onClick = { onSelectPage(AppPage.VOICECHAT) }
        )
        ModeNavButton(
            label = "LLM",
            selected = selectedPage == AppPage.LLM,
            onClick = { onSelectPage(AppPage.LLM) }
        )
        ModeNavButton(
            label = "ASR",
            selected = selectedPage == AppPage.ASR,
            onClick = { onSelectPage(AppPage.ASR) }
        )
        ModeNavButton(
            label = "TTS",
            selected = selectedPage == AppPage.TTS,
            onClick = { onSelectPage(AppPage.TTS) }
        )
        Spacer(modifier = Modifier.weight(1f))
        SettingsSidebarSection(
            memoryId = memoryId,
            models = models,
            selectedModel = selectedModel,
            onMemoryIdChange = onMemoryIdChange,
            onModelSelect = onModelSelect,
            onLoadModels = onLoadModels
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModeNavButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(ChatLayoutTokens.CornerRadius),
        color = if (selected) ChatLayoutTokens.NavActiveBackground else ChatLayoutTokens.NavInactiveBackground,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(vertical = 14.dp, horizontal = 12.dp),
            style = MaterialTheme.typography.labelLarge,
            color = ChatLayoutTokens.NavText
        )
    }
}

@Composable
private fun SettingsSidebarSection(
    memoryId: String?,
    models: List<com.ikkoaudio.aiclient.domain.model.LlmModel>,
    selectedModel: String?,
    onMemoryIdChange: (String?) -> Unit,
    onModelSelect: (String) -> Unit,
    onLoadModels: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { expanded = !expanded }
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Settings", style = MaterialTheme.typography.titleSmall, color = ChatLayoutTokens.NavText)
        Text(if (expanded) "−" else "+", style = MaterialTheme.typography.titleMedium)
    }
    if (expanded) {
        Column(
            modifier = Modifier.padding(bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = memoryId ?: "",
                onValueChange = { onMemoryIdChange(it.ifEmpty { null }) },
                label = { Text("Memory ID") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Button(onClick = onLoadModels, modifier = Modifier.fillMaxWidth()) {
                Text("Load Models")
            }
            models.forEach { model ->
                FilterChip(
                    selected = model.name == selectedModel,
                    onClick = { onModelSelect(model.name) },
                    label = { Text(model.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun WideCenterPage(state: ChatState, viewModel: ChatViewModel) {
    when (state.selectedPage) {
        AppPage.VOICECHAT -> VoiceChatBody(state, viewModel, Modifier.fillMaxSize())
        AppPage.LLM -> LlmChatBody(state, viewModel, Modifier.fillMaxSize())
        AppPage.ASR -> AsrCenterPanel(state, viewModel, Modifier.fillMaxSize())
        AppPage.TTS -> TtsCenterBody(state, viewModel, Modifier.fillMaxSize())
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CompactChatShell(
    state: ChatState,
    viewModel: ChatViewModel,
    drawerState: androidx.compose.material3.DrawerState
) {
    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(modifier = Modifier.width(280.dp)) {
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
                    onClick = { viewModel.dispatch(ChatIntent.SelectPage(AppPage.VOICECHAT)) }
                )
                NavigationDrawerItem(
                    icon = { Icon(Icons.AutoMirrored.Outlined.Chat, contentDescription = null) },
                    label = { Text("LLM") },
                    selected = state.selectedPage == AppPage.LLM,
                    onClick = { viewModel.dispatch(ChatIntent.SelectPage(AppPage.LLM)) }
                )
                NavigationDrawerItem(
                    icon = { Icon(Icons.Outlined.KeyboardVoice, contentDescription = null) },
                    label = { Text("ASR") },
                    selected = state.selectedPage == AppPage.ASR,
                    onClick = { viewModel.dispatch(ChatIntent.SelectPage(AppPage.ASR)) }
                )
                NavigationDrawerItem(
                    icon = { Icon(Icons.AutoMirrored.Outlined.VolumeUp, contentDescription = null) },
                    label = { Text("TTS") },
                    selected = state.selectedPage == AppPage.TTS,
                    onClick = { viewModel.dispatch(ChatIntent.SelectPage(AppPage.TTS)) }
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
