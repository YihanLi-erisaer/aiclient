package com.ikkoaudio.aiclient

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.*
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.ikkoaudio.aiclient.di.AppModule
import com.ikkoaudio.aiclient.feature.chat.ChatScreen
import com.ikkoaudio.aiclient.feature.chat.ChatViewModel
import org.jetbrains.compose.resources.Font
import aiclient.composeapp.generated.resources.Res
import aiclient.composeapp.generated.resources.*

@Composable
fun App() {
    // Noto Sans SC includes Latin + Simplified Chinese; use as primary for CJK support
    val appFont = Font(Res.font.NotoSansSC_Regular, FontWeight.Normal)
    val appFontFamily = remember(appFont) { FontFamily(appFont) }
    val defaultTypography = Typography()
    val appTypography = remember(appFontFamily) {
        Typography(
            displayLarge = defaultTypography.displayLarge.copy(fontFamily = appFontFamily),
            displayMedium = defaultTypography.displayMedium.copy(fontFamily = appFontFamily),
            displaySmall = defaultTypography.displaySmall.copy(fontFamily = appFontFamily),
            headlineLarge = defaultTypography.headlineLarge.copy(fontFamily = appFontFamily),
            headlineMedium = defaultTypography.headlineMedium.copy(fontFamily = appFontFamily),
            headlineSmall = defaultTypography.headlineSmall.copy(fontFamily = appFontFamily),
            titleLarge = defaultTypography.titleLarge.copy(fontFamily = appFontFamily),
            titleMedium = defaultTypography.titleMedium.copy(fontFamily = appFontFamily),
            titleSmall = defaultTypography.titleSmall.copy(fontFamily = appFontFamily),
            bodyLarge = defaultTypography.bodyLarge.copy(fontFamily = appFontFamily),
            bodyMedium = defaultTypography.bodyMedium.copy(fontFamily = appFontFamily),
            bodySmall = defaultTypography.bodySmall.copy(fontFamily = appFontFamily),
            labelLarge = defaultTypography.labelLarge.copy(fontFamily = appFontFamily),
            labelMedium = defaultTypography.labelMedium.copy(fontFamily = appFontFamily),
            labelSmall = defaultTypography.labelSmall.copy(fontFamily = appFontFamily),
        )
    }
    val scope = rememberCoroutineScope()
    MaterialTheme(typography = appTypography) {
        val viewModel: ChatViewModel = remember(scope) {
            AppModule.createChatViewModel(scope)
        }
        ChatScreen(viewModel = viewModel)
    }
}

@Composable
fun AppPreview() {
    MaterialTheme {
        val scope = rememberCoroutineScope()
        val viewModel = remember(scope) {
            AppModule.createChatViewModel(scope)
        }
        ChatScreen(viewModel = viewModel)
    }
}
