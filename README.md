# AI Client (aiclient)

Unified **Kotlin / Compose Multiplatform** client for **ASR**, **LLM chat**, **TTS**, and **end-to-end voice chat** (ASR → LLM → TTS) against a compatible backend.

---

## Overview

This app provides a single UI to:

- Talk to an LLM with streaming replies (chat page).
- Run **text-to-speech** and **speech-to-text** (ASR).
- Use **continuous voice chat**: microphone stays open, utterances are segmented with **VAD**, then sent to the server (audio or on-device text) and the spoken reply is played back.

The client targets **Android** (full feature set, including on-device ASR) and **Web** (JS / Wasm; voice chat VAD on JS; Wasm mic path is limited).

---

## Key Features

| Area | What it does |
|------|----------------|
| **LLM** | Streamed chat, optional memory / conversation id, image upload where supported. |
| **TTS** | Sends text to backend TTS; plays returned audio. |
| **ASR** | Continuous listen: **WebRTC VAD** (Android) segments phrases → transcribe (local sherpa-onnx when available, else HTTP) → append to transcript. |
| **Voice chat** | Continuous recording + VAD; each utterance triggers **WebSocket** `/ws/chat`-style pipeline: server ASR+LLM+TTS **or** **local ASR** then **text over the same WebSocket** with an `END` marker; reply audio is WAV (or compatible) over the socket. |
| **Local ASR (Android)** | [sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx) under `assets/models/sherpa-asr/`. **Offline** models work for phrase-at-a-time recognition; **streaming / online** models expose live partial text when metadata supports it. |
| **Server “thinking” hint** | Plain text, JSON (`message` / `text` / `content`, etc.), or small **Binary UTF-8** status frames can show an interim line (e.g. “努力思考中，请等一等……”) until final TTS audio arrives. |
| **Settings** | API base URL, WebSocket URL, memory id, and related defaults (see in-app settings). |

Default endpoints in code (override in app settings): HTTP `ChatState.Defaults.API_BASE_URL`, WebSocket `ChatState.Defaults.VOICE_CHAT_WS_URL`.

---

## Architecture (high level)

```
Compose UI  →  ChatViewModel  →  AiRepository  →  Ktor (HTTP + WebSocket)
                              ↘  LocalAsrEngine (Android / sherpa-onnx)
```

- **HTTP**: transcribe, chat, chat stream, TTS, combined ASR+LLM+TTS, etc.
- **WebSocket voice chat**: client sends **binary chunks + Text `END`** for audio, or **Text user message + Text `END`** when using local ASR; server may send **binary audio**, **Text** status / `[DONE]`, and optional JSON `audio_meta` prefixes (see implementation in `KtorAiApi`).

---

## Tech Stack

- Kotlin, **Compose Multiplatform**, **Ktor** client (HTTP, WebSocket)
- Android: **AudioRecord**, **WebRTC VAD** (android-vad), **sherpa-onnx** JNI where bundled
- Coroutines, StateFlow, DataStore (settings)

---

## How to use

### 1. Run the backend

Point the app at your ASR / LLM / TTS / WebSocket server. Implement (or proxy) routes consistent with `AiApi` / `KtorAiApi` (e.g. `/api/asr/transcribe`, `/api/llm/chat`, `/api/tts/speak`, WebSocket voice chat).

### 2. Configure the app

Open **Settings** in the app and set:

- **API base URL** (HTTP), e.g. `http://YOUR_HOST:8080`
- **Voice chat WebSocket URL**, e.g. `ws://YOUR_HOST:8080/ws/chat`
- **Memory / conversation id** if your backend expects it (query or body per your API).

You can also probe **WebSocket handshake** from the Voice chat screen.

### 3. Voice chat tab

1. Grant **microphone** permission.
2. Optional: enable **Local ASR** if models are installed (Android).  
   - **Offline** model: each VAD segment is transcribed on-device, then **text** is sent on the WebSocket (with `END`), same socket as audio mode.  
   - **Streaming** model (if supported): partial text may appear while speaking.
3. Tap **Start listening**. Speak in phrases; short pauses trigger end-of-utterance (VAD tunable in `VoiceChatRecorder.android.kt`).
4. Wait for server processing: an interim status line may appear; **TTS audio** plays when the full reply arrives. Recording can **pause during playback** to reduce echo (see `ChatViewModel` / recorder pause-resume).
5. Tap **Stop** when finished.

### 4. ASR tab

1. Tap **Record** to start **continuous** VAD-based listening (same segmentation idea as voice chat, without LLM/TTS).
2. Transcripts **append** to the text area (newline between utterances).
3. Tap **Stop** to end.

### 5. LLM / TTS tabs

- **LLM**: type a message, send, read streamed assistant reply.
- **TTS**: enter text, play synthesized speech from the server.

### 6. Local ASR models (Android only)

1. Download a compatible **sherpa-onnx** model (e.g. [offline Zipformer transducer](https://k2-fsa.github.io/sherpa/onnx/pretrained_models/offline-transducer/zipformer-transducer-models.html) or an **online / streaming** variant if you want streaming ASR).
2. Place files under **`composeApp/src/androidMain/assets/models/sherpa-asr/`** (or the path in `AndroidSherpaOnnxLocalAsrEngine.DEFAULT_MODEL_DIR`):  
   - **Transducer**: `encoder*.onnx`, `decoder*.onnx`, `joiner*.onnx`, `tokens.txt`  
   - **Zipformer CTC**: `model.onnx` or `model.int8.onnx`, `tokens.txt`
3. Rebuild the app. The UI shows whether local ASR is **Unavailable / Offline / Streaming**.

**Note:** Initializing an **online** recognizer with an **offline-only** encoder can crash native code; the app skips online init when encoder metadata is incompatible (see `AndroidSherpaOnnxLocalAsrEngine`).

---

## Build & run

### Android

```shell
# macOS / Linux
./gradlew :composeApp:assembleDebug

# Windows
.\gradlew.bat :composeApp:assembleDebug
```

Install the debug APK from `composeApp/build/outputs/apk/debug/` or run from Android Studio.

### Web (Wasm — preferred for modern browsers)

```shell
# macOS / Linux
./gradlew :composeApp:wasmJsBrowserDevelopmentRun

# Windows
.\gradlew.bat :composeApp:wasmJsBrowserDevelopmentRun
```

### Web (JS)

```shell
# macOS / Linux
./gradlew :composeApp:jsBrowserDevelopmentRun

# Windows
.\gradlew.bat :composeApp:jsBrowserDevelopmentRun
```

---

## Repository layout

- [`composeApp/src/commonMain`](./composeApp/src/commonMain/kotlin) — shared UI, ViewModel, API interfaces, Ktor implementation
- [`composeApp/src/androidMain`](./composeApp/src/androidMain/kotlin) — Android audio, permissions, sherpa-onnx engine
- [`composeApp/src/jsMain`](./composeApp/src/jsMain/kotlin) / [`wasmJsMain`](./composeApp/src/wasmJsMain/kotlin) — web audio / stubs

---

## Roadmap ideas

- RAG and document-grounded chat  
- Richer on-device models on more targets  
- Connection quality and retry UX for WebSocket voice chat  

---

## Design reference

**Original UI (Penpot):** [Penpot design](https://design.penpot.app/#/view?file-id=1c48efe5-2f9f-81cd-8007-c2e878421e35&page-id=1c48efe5-2f9f-81cd-8007-c2e878421e36&section=interactions&index=0&share-id=9afc49c1-9c44-8036-8007-c2f0f444ab94)

---

## Learn more

- [Kotlin Multiplatform](https://www.jetbrains.com/help/kotlin-multiplatform-dev/get-started.html)  
- [Compose Multiplatform](https://github.com/JetBrains/compose-multiplatform)  
- [Kotlin/Wasm](https://kotl.in/wasm/)  

Feedback: [#compose-web Slack](https://slack-chats.kotlinlang.org/c/compose-web) · Issues: [JetBrains YouTrack (CMP)](https://youtrack.jetbrains.com/newIssue?project=CMP)

---

## Author

**Yihan Li**  
Email: liyihan11unique@outlook.com  
GitHub: https://github.com/YihanLi-erisaer
