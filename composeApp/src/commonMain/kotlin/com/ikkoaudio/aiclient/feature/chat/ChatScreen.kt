package com.ikkoaudio.aiclient.feature.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.Dp
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

        val collapsedStrip = ChatLayoutTokens.NarrowPeekStripWidth
        val handleReserve = ChatLayoutTokens.SidebarInnerHandleReserve
        val leftTargetWide =
            if (leftOpen) maxWidth * ChatLayoutTokens.LeftWeight else collapsedStrip
        val rightTargetWide =
            if (rightOpen) maxWidth * ChatLayoutTokens.RightWeight else collapsedStrip
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
                        Box(modifier = Modifier.fillMaxSize()) {
                            LeftSidebarChrome(
                                state = state,
                                expanded = leftOpen,
                                onToggle = {
                                    if (leftOpen) leftOpen = false
                                    else {
                                        rightOpen = false
                                        leftOpen = true
                                    }
                                },
                                contentPaddingEnd = handleReserve
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
                    }
                    Box(
                        modifier = Modifier
                            .width(rightWidthWide)
                            .fillMaxHeight()
                            .clip(RectangleShape)
                            .background(ChatLayoutTokens.SidebarBackground)
                    ) {
                        RightSidebarChrome(
                            expanded = rightOpen,
                            onToggle = {
                                if (rightOpen) rightOpen = false
                                else {
                                    leftOpen = false
                                    rightOpen = true
                                }
                            },
                            contentPaddingStart = handleReserve,
                            selectedPage = state.selectedPage,
                            onSelectPage = { viewModel.dispatch(ChatIntent.SelectPage(it)) },
                            settingsScreenVisible = state.settingsScreenVisible,
                            onOpenSettings = { viewModel.dispatch(ChatIntent.OpenSettingsScreen) }
                        )
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
                        Box(modifier = Modifier.fillMaxSize()) {
                            LeftSidebarChrome(
                                state = state,
                                expanded = leftOpen,
                                onToggle = {
                                    if (leftOpen) leftOpen = false
                                    else {
                                        rightOpen = false
                                        leftOpen = true
                                    }
                                },
                                contentPaddingEnd = handleReserve
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
                    }
                    Box(
                        modifier = Modifier
                            .width(rightColNarrow)
                            .fillMaxHeight()
                            .clip(RectangleShape)
                            .background(ChatLayoutTokens.SidebarBackground)
                    ) {
                        RightSidebarChrome(
                            expanded = rightOpen,
                            onToggle = {
                                if (rightOpen) rightOpen = false
                                else {
                                    leftOpen = false
                                    rightOpen = true
                                }
                            },
                            contentPaddingStart = handleReserve,
                            selectedPage = state.selectedPage,
                            onSelectPage = { viewModel.dispatch(ChatIntent.SelectPage(it)) },
                            settingsScreenVisible = state.settingsScreenVisible,
                            onOpenSettings = { viewModel.dispatch(ChatIntent.OpenSettingsScreen) }
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = state.settingsScreenVisible,
                enter = slideInHorizontally(animationSpec = tween(320)) { it },
                exit = slideOutHorizontally(animationSpec = tween(320)) { it },
                modifier = Modifier.fillMaxSize()
            ) {
                SettingsFullScreen(
                    state = state,
                    viewModel = viewModel,
                    onClose = { viewModel.dispatch(ChatIntent.CloseSettingsScreen) }
                )
            }
        }
    }
}

@Composable
private fun LeftSidebarChrome(
    state: ChatState,
    expanded: Boolean,
    onToggle: () -> Unit,
    contentPaddingEnd: Dp
) {
    val reserve = ChatLayoutTokens.SidebarInnerHandleReserve
    Box(modifier = Modifier.fillMaxSize()) {
        if (expanded) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(end = contentPaddingEnd)
            ) {
                LeftSidebarContent(state = state)
            }
        }
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(reserve),
            contentAlignment = Alignment.Center
        ) {
            InnerEdgeMenuIconButton(
                onClick = onToggle,
                contentDescription = if (expanded) "Close left sidebar" else "Open left sidebar"
            )
        }
    }
}

@Composable
private fun RightSidebarChrome(
    expanded: Boolean,
    onToggle: () -> Unit,
    contentPaddingStart: Dp,
    selectedPage: AppPage,
    onSelectPage: (AppPage) -> Unit,
    settingsScreenVisible: Boolean,
    onOpenSettings: () -> Unit
) {
    val reserve = ChatLayoutTokens.SidebarInnerHandleReserve
    Box(modifier = Modifier.fillMaxSize()) {
        if (expanded) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = contentPaddingStart)
            ) {
                RightSidebarNav(
                    selectedPage = selectedPage,
                    onSelectPage = onSelectPage,
                    settingsScreenVisible = settingsScreenVisible,
                    onOpenSettings = onOpenSettings
                )
            }
        }
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .fillMaxHeight()
                .width(reserve),
            contentAlignment = Alignment.Center
        ) {
            InnerEdgeMenuIconButton(
                onClick = onToggle,
                contentDescription = if (expanded) "Close right sidebar" else "Open right sidebar"
            )
        }
    }
}

@Composable
private fun InnerEdgeMenuIconButton(
    onClick: () -> Unit,
    contentDescription: String
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(48.dp),
        colors = IconButtonDefaults.iconButtonColors(
            containerColor = Color.Transparent,
            contentColor = ChatLayoutTokens.FrontColor
        )
    ) {
        Icon(
            imageVector = Icons.Filled.Menu,
            contentDescription = contentDescription,
            modifier = Modifier.size(24.dp),
            tint = ChatLayoutTokens.FrontColor
        )
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
    settingsScreenVisible: Boolean,
    onOpenSettings: () -> Unit
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
            selected = selectedPage == AppPage.VOICECHAT && !settingsScreenVisible,
            onClick = { onSelectPage(AppPage.VOICECHAT) }
        )
        ModeNavButton(
            label = "LLM",
            selected = selectedPage == AppPage.LLM && !settingsScreenVisible,
            onClick = { onSelectPage(AppPage.LLM) }
        )
        ModeNavButton(
            label = "ASR",
            selected = selectedPage == AppPage.ASR && !settingsScreenVisible,
            onClick = { onSelectPage(AppPage.ASR) }
        )
        ModeNavButton(
            label = "TTS",
            selected = selectedPage == AppPage.TTS && !settingsScreenVisible,
            onClick = { onSelectPage(AppPage.TTS) }
        )
        ModeNavButton(
            label = "Settings",
            selected = settingsScreenVisible,
            onClick = onOpenSettings
        )
        Spacer(modifier = Modifier.weight(1f))
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
private fun WideCenterPage(state: ChatState, viewModel: ChatViewModel) {
    when (state.selectedPage) {
        AppPage.VOICECHAT -> VoiceChatBody(state, viewModel, Modifier.fillMaxSize())
        AppPage.LLM -> LlmChatBody(state, viewModel, Modifier.fillMaxSize())
        AppPage.ASR -> AsrCenterPanel(state, viewModel, Modifier.fillMaxSize())
        AppPage.TTS -> TtsCenterBody(state, viewModel, Modifier.fillMaxSize())
    }
}
