package com.ikkoaudio.aiclient.feature.chat

import androidx.compose.material3.ButtonColors
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Desktop / tablet three-column layout from design spec */
object ChatLayoutTokens {
    val SidebarBackground = Color(0xFFC6D1DD)
    val CenterBackground = Color(0xFFE9EDD9)
    val NavActiveBackground = Color(0xFFBABF95)
    val ButtonPressed = Color.Red
    val FrontColor = Color.Black
    val NavInactiveBackground = Color(0xFFE9EDD9)
    val NavText = Color(0xFF1A1A1A)
    val CornerRadius = 12.dp
    /** Use three-column shell when width is at least this */
    val WideLayoutMinWidth = 720.dp
    val LeftWeight = 0.22f
    val CenterWeight = 0.56f
    val RightWeight = 0.22f
    /** Sliding drawer width on narrow screens (capped) */
    val NarrowDrawerWidthFraction = 0.88f
    val NarrowDrawerMaxWidth = 360.dp
    /** Collapsed strip with hamburger when narrow sidebars are closed */
    val NarrowPeekStripWidth = 52.dp
    /** Width reserved for inner-edge handle + content inset from that edge */
    val SidebarInnerHandleReserve = 44.dp
}
