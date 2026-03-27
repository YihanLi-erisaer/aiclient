package com.ikkoaudio.aiclient

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.text.font.FontFamily
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.preloadFont
import aiclient.composeapp.generated.resources.emojiFallbackFontResource

@OptIn(ExperimentalResourceApi::class)
@Composable
actual fun EmojiFontBootstrap(content: @Composable () -> Unit) {
    val emojiFont by preloadFont(emojiFallbackFontResource())
    val fontFamilyResolver = LocalFontFamilyResolver.current
    var ready by remember { mutableStateOf(false) }

    LaunchedEffect(emojiFont, fontFamilyResolver) {
        val loaded = emojiFont ?: return@LaunchedEffect
        fontFamilyResolver.preload(FontFamily(loaded))
        ready = true
    }

    if (!ready) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
    } else {
        content()
    }
}
