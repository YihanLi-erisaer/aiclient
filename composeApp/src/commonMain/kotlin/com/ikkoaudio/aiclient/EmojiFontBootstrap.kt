package com.ikkoaudio.aiclient

import androidx.compose.runtime.Composable

/**
 * On Web/Wasm: preloads the emoji font and registers it via [androidx.compose.ui.text.font.FontFamily.Resolver.preload]
 * so Skiko can render emoji (putting emoji as a 2nd font in [androidx.compose.ui.text.font.FontFamily] does not work on web).
 * On Android: no-op wrapper.
 */
@Composable
expect fun EmojiFontBootstrap(content: @Composable () -> Unit)
