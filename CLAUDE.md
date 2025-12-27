# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build Commands

```bash
# Build the project (use gradlew.bat on Windows)
./gradlew build

# Build debug APK
./gradlew assembleDebug

# Run unit tests
./gradlew test

# Run Android instrumented tests
./gradlew connectedAndroidTest

# Clean build
./gradlew clean build
```

## Project Architecture

TChat is a multi-module Android AI chat application built with Kotlin and Jetpack Compose.

### Module Structure

```
:app          → Main application module (UI, settings, navigation)
:core         → Shared utilities (Result wrapper)
:network      → AI provider implementations (OpenAI, Anthropic, Gemini)
:data         → Data models and repository layer
:feature-chat → Chat UI components and ViewModel
```

### Module Dependencies

```
app → feature-chat → data → network → core
         ↓           ↓
       network     core
```

### Key Architecture Patterns

**Navigation** (`app` module):
- Uses state-based navigation (not Jetpack Navigation)
- `showSettingsPage` state in `MainScreen` controls settings visibility
- `BackHandler` required for physical back button support
- Settings sub-pages managed via `SettingsSubPage` enum state

**AI Provider System** (`network` module):
- `AIProvider` interface defines streaming chat contract
- `AIProviderFactory` creates provider instances from config
- Three implementations: `OpenAIProvider`, `AnthropicProvider`, `GeminiProvider`
- All providers support streaming responses via Kotlin Flow

**Settings System** (`app` module):
- `SettingsManager` handles persistence via SharedPreferences with JSON serialization
- `AppSettings` contains provider configurations
- `ProviderConfig` stores per-provider: name, type, API key, endpoint, model list

**Chat Flow**:
- `ChatViewModel` in `feature-chat` manages UI state
- `ChatRepository` interface abstracts chat/message operations
- `ChatRepositoryImpl` connects to AI providers for streaming responses

### Tech Stack

- Kotlin with Coroutines & Flow for async operations
- Jetpack Compose with Material 3 for UI
- OkHttp for HTTP client (direct usage, no Retrofit)
- JDK 17, compileSdk 36, minSdk 26

### Material You Design Guidelines

**Dynamic Color**:
- Android 12+ (API 31+): Uses `dynamicLightColorScheme`/`dynamicDarkColorScheme` to extract colors from user wallpaper
- Fallback: Custom teal color palette defined in `Color.kt` for older devices

**Color Usage Principles**:
- Always use semantic colors from `MaterialTheme.colorScheme`, never hardcode colors
- `primary`/`primaryContainer`: Brand emphasis, key actions
- `secondary`/`secondaryContainer`: User-generated content (e.g., user chat bubbles)
- `surfaceContainer*` variants: Different elevation levels for layered surfaces
- `onXxx` colors: Text/icons on corresponding container (e.g., `onSecondaryContainer` on `secondaryContainer`)

**Component Styling**:
- Chat bubbles: User messages use `secondaryContainer`, AI messages use `surfaceContainerHigh`
- Cards: Use `ElevatedCard` or `OutlinedCard` with `surfaceContainerLow`
- Use `tonalElevation` for subtle depth instead of shadows
- Shapes: Use `MaterialTheme.shapes` (extraSmall to extraLarge: 4dp → 32dp rounded corners)

**Icon Coloring**:
- Vector icons: Use `Icon` component with `tint` parameter, not `Image`
- Apply `MaterialTheme.colorScheme.onSurface` or `onSurfaceVariant` for proper theme adaptation

### Repository Configuration

Uses Aliyun Maven mirrors (configured in `settings.gradle.kts`) for faster dependency resolution in China.
