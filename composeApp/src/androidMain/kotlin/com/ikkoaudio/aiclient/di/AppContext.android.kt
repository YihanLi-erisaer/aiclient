package com.ikkoaudio.aiclient.di

import android.app.Application
import android.content.Context

private var appContext: Context? = null

fun initAppContext(context: Context) {
    appContext = context.applicationContext
}

actual fun getAppContext(): Any? = appContext
