package com.ikkoaudio.aiclient.di

/**
 * Platform-specific application context.
 * On Android: Context
 * On JS: Unit (not applicable)
 */
expect fun getAppContext(): Any?
