import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinxSerialization)
}

/** In-app and Android `versionName`; reflected in generated [com.ikkoaudio.aiclient.AppVersion]. */
val appVersionName = "2.3"

val generateAppVersion by tasks.registering {
    val outDir = layout.buildDirectory.dir("generated/kotlin/appversion")
    val versionForSource = appVersionName.replace("\\", "\\\\").replace("\"", "\\\"")
    outputs.dir(outDir)
    doLast {
        val file = outDir.get().asFile.resolve("com/ikkoaudio/aiclient/AppVersion.kt")
        file.parentFile.mkdirs()
        file.writeText(
            """
            package com.ikkoaudio.aiclient

            /** Generated from composeApp/build.gradle.kts (appVersionName). Do not edit. */
            object AppVersion {
                const val VERSION_NAME: String = "$versionForSource"
            }
            """.trimIndent() + "\n"
        )
    }
}

afterEvaluate {
    tasks.matching { task ->
        task.name.matches(Regex("compile.*Kotlin.*", RegexOption.IGNORE_CASE))
    }.configureEach {
        dependsOn(generateAppVersion)
    }
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }
    
    js {
        browser()
        binaries.executable()
    }
    
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
        binaries.executable()
    }
    
    sourceSets {
        androidMain.dependencies {
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.datastore.preferences)
            implementation(libs.ktor.client.okhttp)
            implementation(libs.slf4j.android)
        }
        commonMain {
            kotlin.srcDir(layout.buildDirectory.dir("generated/kotlin/appversion"))
            dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.material.icons.extended)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.kermit)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.client.logging)
            implementation(libs.ktor.client.websockets)
            implementation(libs.ktor.serialization.kotlinx.json)
            }
        }
        jsMain.dependencies {
            implementation(libs.ktor.client.cio)
        }
        wasmJsMain.dependencies {
            implementation(libs.ktor.client.cio)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}

android {
    namespace = "com.ikkoaudio.aiclient"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.ikkoaudio.aiclient"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = appVersionName
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    debugImplementation(libs.compose.uiTooling)
}

