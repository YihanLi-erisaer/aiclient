# AI Client: A Unified Interface for Multi-LLM Applications

## Overview

AI Client is a full-stack application designed to provide a unified and extensible interface for interacting with multiple Large Language Models (LLMs). The project focuses on simplifying integration, improving usability, and enabling rapid prototyping of AI-powered applications.

Modern AI development faces a critical challenge: fragmented APIs across different model providers. This project addresses the problem by building a client-side system that abstracts model interactions and enables seamless switching between different LLM backends.

---

## Motivation

With the rapid evolution of LLM ecosystems (e.g., OpenAI, Claude, Gemini), developers often face:

- Inconsistent API formats and request structures
- High integration costs when switching models
- Limited flexibility in experimenting with multiple models

This project aims to:

- Provide a unified interaction layer for LLMs
- Reduce engineering overhead in AI application development
- Enable scalable and modular AI system design

---

## System Architecture

Frontend (Kotlin / Compose Multiplatform)

↓

State Management (MVI / ViewModel)

↓

API Layer (REST / HTTP Client)

↓

Backend / Proxy Layer (LLM Adapter / OpenAI-compatible API)

↓

Multiple LLM Providers (OpenAI / Claude / Gemini / etc.)

---

## Key Features

### 1. Unified LLM Interaction Interface
- Abstracts differences between model providers
- Enables seamless switching between LLMs
- Supports OpenAI-compatible API formats

### 2. Real-time AI Chat Experience
- Interactive chat interface with modern UI
- Efficient state updates using reactive architecture
- Designed for low latency and smooth UX

### 3. Modular System Design
- Clear separation between UI, state, and data layers
- Easily extendable for new models or features
- Suitable for scaling into production-level systems

### 4. Cross-platform Capability
- Built with Kotlin and Compose Multiplatform
- Potential deployment on Android / Desktop

---

## Tech Stack

### Frontend
- Kotlin
- Jetpack Compose / Compose Multiplatform

### Backend & Integration
- RESTful APIs
- OpenAI-compatible API interface
- LLM integration layer

### Architecture & Design
- MVI Architecture
- Reactive state management
- Client-server architecture

### Tools
- Git / GitHub
- Gradle

---

## Technical Highlights

- Designed a modular LLM client system with clear separation of concerns
- Implemented reactive UI updates using modern Android architecture
- Built a scalable interface for integrating multiple AI models
- Optimized interaction flow between frontend and AI backend

---

## Future Work

- Add Retrieval-Augmented Generation (RAG) support
- Introduce local model deployment (on-device inference)
- Implement caching and streaming optimization
- Enhance system scalability and performance monitoring

---

## Project Value

This project demonstrates the ability to:

- Design and implement full-stack AI applications
- Work with modern LLM ecosystems and APIs
- Build scalable and maintainable software systems
- Bridge frontend engineering with AI system integration



* [/composeApp](./composeApp/src) is for code that will be shared across your Compose Multiplatform applications.
  It contains several subfolders:
  - [commonMain](./composeApp/src/commonMain/kotlin) is for code that’s common for all targets.
  - Other folders are for Kotlin code that will be compiled for only the platform indicated in the folder name.
    For example, if you want to use Apple’s CoreCrypto for the iOS part of your Kotlin app,
    the [iosMain](./composeApp/src/iosMain/kotlin) folder would be the right place for such calls.
    Similarly, if you want to edit the Desktop (JVM) specific part, the [jvmMain](./composeApp/src/jvmMain/kotlin)
    folder is the appropriate location.

### Build and Run Android Application

To build and run the development version of the Android app, use the run configuration from the run widget
in your IDE’s toolbar or build it directly from the terminal:
- on macOS/Linux
  ```shell
  ./gradlew :composeApp:assembleDebug
  ```
- on Windows
  ```shell
  .\gradlew.bat :composeApp:assembleDebug
  ```
if you want to deploy asr model in frontend side, download the model from (https://k2-fsa.github.io/sherpa/onnx/pretrained_models/offline-transducer/zipformer-transducer-models.html)

### Build and Run Web Application

To build and run the development version of the web app, use the run configuration from the run widget
in your IDE's toolbar or run it directly from the terminal:
- for the Wasm target (faster, modern browsers):
  - on macOS/Linux
    ```shell
    ./gradlew :composeApp:wasmJsBrowserDevelopmentRun
    ```
  - on Windows
    ```shell
    .\gradlew.bat :composeApp:wasmJsBrowserDevelopmentRun
    ```
- for the JS target (slower, supports older browsers):
  - on macOS/Linux
    ```shell
    ./gradlew :composeApp:jsBrowserDevelopmentRun
    ```
  - on Windows
    ```shell
    .\gradlew.bat :composeApp:jsBrowserDevelopmentRun
    ```

---
**Original UI design link from penpot:** [Original UI design](https://design.penpot.app/#/view?file-id=1c48efe5-2f9f-81cd-8007-c2e878421e35&page-id=1c48efe5-2f9f-81cd-8007-c2e878421e36&section=interactions&index=0&share-id=9afc49c1-9c44-8036-8007-c2f0f444ab94)

Learn more about [Kotlin Multiplatform](https://www.jetbrains.com/help/kotlin-multiplatform-dev/get-started.html),

[Compose Multiplatform](https://github.com/JetBrains/compose-multiplatform/#compose-multiplatform),

[Kotlin/Wasm](https://kotl.in/wasm/)…

We would appreciate your feedback on Compose/Web and Kotlin/Wasm in the public Slack channel [#compose-web](https://slack-chats.kotlinlang.org/c/compose-web).
If you face any issues, please report them on [YouTrack](https://youtrack.jetbrains.com/newIssue?project=CMP).
---

## Author

Yihan Li  
Email: liyihan11unique@outlook.com  
GitHub: https://github.com/YihanLi-erisaer