package com.ikkoaudio.aiclient.feature.chat

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun ChatScreen(viewModel: ChatViewModel) {
    val state by viewModel.state.collectAsState()
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val narrow = maxWidth < ChatLayoutTokens.WideLayoutMinWidth
        var leftOpen by remember { mutableStateOf(!narrow) }
        var rightOpen by remember { mutableStateOf(!narrow) }

        LaunchedEffect(narrow) {
            if (narrow) {
                leftOpen = false
                rightOpen = false
            } else {
                leftOpen = true
                rightOpen = true
            }
        }

        val leftTargetWide =
            if (leftOpen) maxWidth * ChatLayoutTokens.LeftWeight else 0.dp
        val rightTargetWide =
            if (rightOpen) maxWidth * ChatLayoutTokens.RightWeight else 0.dp
        val leftWidthWide by animateDpAsState(
            targetValue = leftTargetWide,
            animationSpec = tween(320),
            label = "leftWide"
        )
        val rightWidthWide by animateDpAsState(
            targetValue = rightTargetWide,
            animationSpec = tween(320),
            label = "rightWide"
        )

        val drawerWidth =
            (maxWidth * ChatLayoutTokens.NarrowDrawerWidthFraction)
                .coerceAtMost(ChatLayoutTokens.NarrowDrawerMaxWidth)
        val peekStripWidth = ChatLayoutTokens.NarrowPeekStripWidth
        val leftColNarrow by animateDpAsState(
            targetValue = if (leftOpen) drawerWidth else peekStripWidth,
            animationSpec = tween(320),
            label = "leftNarrowCol"
        )
        val rightColNarrow by animateDpAsState(
            targetValue = if (rightOpen) drawerWidth else peekStripWidth,
            animationSpec = tween(320),
            label = "rightNarrowCol"
        )

        Box(modifier = Modifier.fillMaxSize()) {
            if (!narrow) {
                Row(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .width(leftWidthWide)
                            .fillMaxHeight()
                            .clip(RectangleShape)
                            .background(ChatLayoutTokens.SidebarBackground)
                    ) {
                        if (leftWidthWide > 1.dp) {
                            LeftSidebarWithMenu(
                                state = state,
                                onMenuClick = { leftOpen = false }
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .background(ChatLayoutTokens.CenterBackground)
                    ) {
                        WideCenterPage(state = state, viewModel = viewModel)
                        if (!leftOpen) {
                            IconButton(
                                onClick = { leftOpen = true },
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Menu,
                                    contentDescription = "Open left sidebar"
                                )
                            }
                        }
                        if (!rightOpen) {
                            IconButton(
                                onClick = { rightOpen = true },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Menu,
                                    contentDescription = "Open right sidebar"
                                )
                            }
                        }
                    }
                    Box(
                        modifier = Modifier
                            .width(rightWidthWide)
                            .fillMaxHeight()
                            .clip(RectangleShape)
                            .background(ChatLayoutTokens.SidebarBackground)
                    ) {
                        if (rightWidthWide > 1.dp) {
                            RightSidebarWithMenu(
                                selectedPage = state.selectedPage,
                                onSelectPage = { viewModel.dispatch(ChatIntent.SelectPage(it)) },
                                memoryId = state.memoryId,
                                models = state.models,
                                selectedModel = state.selectedModel,
                                onMemoryIdChange = { viewModel.dispatch(ChatIntent.SetMemoryId(it)) },
                                onModelSelect = { viewModel.dispatch(ChatIntent.SelectModel(it)) },
                                onLoadModels = { viewModel.dispatch(ChatIntent.LoadModels) },
                                onMenuClick = { rightOpen = false }
                            )
                        }
                    }
                }
            } else {
                Row(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .width(leftColNarrow)
                            .fillMaxHeight()
                            .clip(RectangleShape)
                            .background(ChatLayoutTokens.SidebarBackground)
                    ) {
                        if (leftOpen) {
                            LeftSidebarWithMenu(
                                state = state,
                                onMenuClick = { leftOpen = false }
                            )
                        } else {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                IconButton(
                                    onClick = {
                                        rightOpen = false
                                        leftOpen = true
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Menu,
                                        contentDescription = "Open chat history"
                                    )
                                }
                            }
                        }
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .background(ChatLayoutTokens.CenterBackground)
                    ) {
                        WideCenterPage(state = state, viewModel = viewModel)
                    }
                    Box(
                        modifier = Modifier
                            .width(rightColNarrow)
                            .fillMaxHeight()
                            .clip(RectangleShape)
                            .background(ChatLayoutTokens.SidebarBackground)
                    ) {
                        if (rightOpen) {
                            RightSidebarWithMenu(
                                selectedPage = state.selectedPage,
                                onSelectPage = { viewModel.dispatch(ChatIntent.SelectPage(it)) },
                                memoryId = state.memoryId,
                                models = state.models,
                                selectedModel = state.selectedModel,
                                onMemoryIdChange = { viewModel.dispatch(ChatIntent.SetMemoryId(it)) },
                                onModelSelect = { viewModel.dispatch(ChatIntent.SelectModel(it)) },
                                onLoadModels = { viewModel.dispatch(ChatIntent.LoadModels) },
                                onMenuClick = { rightOpen = false }
                            )
                        } else {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                IconButton(
                                    onClick = {
                                        leftOpen = false
                                        rightOpen = true
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Menu,
                                        contentDescription = "Open navigation"
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LeftSidebarWithMenu(
    state: ChatState,
    onMenuClick: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onMenuClick) {
                Icon(
                    imageVector = Icons.Filled.Menu,
                    contentDescription = "Close sidebar"
                )
            }
        }
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            LeftSidebarContent(state = state)
        }
    }
}

@Composable
private fun RightSidebarWithMenu(
    selectedPage: AppPage,
    onSelectPage: (AppPage) -> Unit,
    memoryId: String?,
    models: List<com.ikkoaudio.aiclient.domain.model.LlmModel>,
    selectedModel: String?,
    onMemoryIdChange: (String?) -> Unit,
    onModelSelect: (String) -> Unit,
    onLoadModels: () -> Unit,
    onMenuClick: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(end = 4.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onMenuClick) {
                Icon(
                    imageVector = Icons.Filled.Menu,
                    contentDescription = "Close sidebar"
                )
            }
        }
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            RightSidebarNav(
                selectedPage = selectedPage,
                onSelectPage = onSelectPage,
                memoryId = memoryId,
                models = models,
                selectedModel = selectedModel,
                onMemoryIdChange = onMemoryIdChange,
                onModelSelect = onModelSelect,
                onLoadModels = onLoadModels
            )
        }
    }
}

@Composable
private fun LeftSidebarContent(state: ChatState) {
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
