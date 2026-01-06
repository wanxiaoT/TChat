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
- **Tool Calling Loop**: Supports up to 10 iterations of tool calls (execute → send result → continue)
- **Message Parts**: Messages use `MessagePart` architecture (TextPart, ToolCallPart, ToolResultPart) stored in `partsJson`
- **MessageSender**: Application-scoped singleton manages concurrent chat sessions, prevents race conditions when switching chats

### Tech Stack

- Kotlin with Coroutines & Flow for async operations
- Jetpack Compose with Material 3 for UI
- Room Database for data persistence
- OkHttp for HTTP client (direct usage, no Retrofit)
- JDK 17, compileSdk 36, minSdk 26

**IMPORTANT - Database Migrations**:
When modifying database entities (adding/removing/changing columns), you MUST:
1. Increment the database version in `AppDatabase.kt`
2. Create a new migration (e.g., `MIGRATION_X_Y`) following existing patterns
3. Add the migration to `.addMigrations()` in `getInstance()`
4. Use `ALTER TABLE` for column additions with `DEFAULT` values to preserve existing data
5. For complex schema changes, create new table → copy data → drop old → rename new
6. Test migration paths to ensure no data loss

### Advanced Features

**Tool System**:
- **Local Tools** (`LocalTools.kt` in `data` module): File operations, web fetch, system info
- **Knowledge Tools**: RAG-based document search using vector embeddings (OpenAI/Gemini embedding APIs)
- **MCP Tools** (`McpClient.kt`): External Model Context Protocol servers with SSE/HTTP transport
- Tool execution is handled in `ChatRepositoryImpl` with automatic retry loop (max 10 iterations)
- All three providers (OpenAI, Anthropic, Gemini) support tool calling

**Message Variants**:
- Regenerate AI responses with alternative outputs (stored in `variantsJson`)
- Switch between variants without re-sending the message
- UI shows variant selector (e.g., "< 1/3 >") when multiple variants exist

**Regex Stream Processing**:
- Real-time regex replacement during AI streaming (`RegexStreamProcessor`)
- Applied per-assistant with ordered rules from `enabledRegexRuleIds`
- Buffer management handles patterns that span chunk boundaries

**Deep Research**:
- Multi-step web research with iterative AI synthesis (`DeepResearchService.kt`)
- Configurable breadth (queries per layer) and depth (recursion levels)
- Uses Tavily or Firecrawl search APIs
- Independent AI configuration separate from main chat settings

### Material You Design Guidelines

**Dynamic Color**:
- Android 12+ (API 31+): Uses `dynamicLightColorScheme`/`dynamicDarkColorScheme` to extract colors from user wallpaper
- Fallback: Custom teal color palette defined in `Color.kt` for older devices

**Color Usage Principles**:
- Always use semantic colors from `MaterialTheme.colorScheme`, never hardcode colors
- `primary`/`primaryContainer`: Brand emphasis, key actions (buttons, FABs, progress indicators)
- `secondary`/`secondaryContainer`: User-generated content (e.g., user chat bubbles)
- `tertiary`/`tertiaryContainer`: Complementary accents, tags, badges
- `surface`, `surfaceVariant`: Background surfaces for content
- `surfaceContainer*` variants: Different elevation levels for layered surfaces
  - `surfaceContainerLowest`: Lowest elevation (0dp)
  - `surfaceContainerLow`: Low elevation (1dp)
  - `surfaceContainer`: Default elevation (3dp)
  - `surfaceContainerHigh`: High elevation (6dp) - AI messages
  - `surfaceContainerHighest`: Highest elevation (12dp)
- `onXxx` colors: Text/icons on corresponding container (e.g., `onSecondaryContainer` on `secondaryContainer`)
- `outline`, `outlineVariant`: Borders and dividers
- `error`/`errorContainer`: Error states and destructive actions

**Component Styling**:
- **Chat bubbles**: User messages use `secondaryContainer`, AI messages use `surfaceContainerHigh`
- **Cards**: Use `ElevatedCard` or `OutlinedCard` with appropriate surface colors
  - Elevated: Use `containerColor = MaterialTheme.colorScheme.surfaceContainerLow`
  - Outlined: Use `colors = CardDefaults.outlinedCardColors()`
- **Lists**: Use `surface` for list backgrounds, `surfaceVariant` for alternating items
- **Dialogs**: Use `surface` with `tonalElevation = 6.dp`
- **Bottom Sheets**: Use `surfaceContainerLow` with rounded top corners
- Use `tonalElevation` for subtle depth instead of hard shadows
- Shapes: Use `MaterialTheme.shapes` (extraSmall to extraLarge: 4dp → 32dp rounded corners)

**Typography**:
- Always use `MaterialTheme.typography` scale
- `displayLarge/Medium/Small`: Large titles, hero text
- `headlineLarge/Medium/Small`: Section headers
- `titleLarge/Medium/Small`: List item titles, card headers
- `bodyLarge/Medium/Small`: Body text, descriptions
- `labelLarge/Medium/Small`: Buttons, tabs, chips
- Avoid custom font sizes; use typography scale variants

**Spacing & Layout**:
- Use consistent spacing increments: 4dp, 8dp, 12dp, 16dp, 24dp, 32dp
- Padding hierarchy:
  - Screen edges: 16dp
  - Card internal padding: 16dp horizontal, 12dp vertical
  - List item padding: 16dp horizontal, 8dp vertical
  - Icon-text spacing: 8dp
  - Section spacing: 24dp
- Minimum touch target: 48dp × 48dp for interactive elements

**Icon Coloring**:
- Vector icons: Use `Icon` component with `tint` parameter, not `Image`
- Apply `MaterialTheme.colorScheme.onSurface` or `onSurfaceVariant` for proper theme adaptation
- Use `onSurfaceVariant` for secondary/decorative icons
- Use `primary` for interactive/branded icons

**Interactive States**:
- Use `Modifier.clickable` with `indication` for ripple effects
- Apply `indication = ripple()` for bounded ripples
- Use `rememberRipple()` for custom ripple colors
- Hover/focus states automatically handled by Material components

**Common Patterns**:
- **Settings screens**: Use `Scaffold` with `TopAppBar`, list items with `ListItem` or custom composables
- **Form inputs**: Use `OutlinedTextField` with proper label, supportingText, and error states
- **Buttons**:
  - Primary action: `Button` (filled)
  - Secondary action: `OutlinedButton` or `TextButton`
  - Destructive action: `Button` with `colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)`
- **Loading states**: `CircularProgressIndicator` with `primary` color, 16-24dp size
- **Empty states**: Centered icon + text with `onSurfaceVariant` color

**Accessibility**:
- Ensure color contrast ratios meet WCAG AA standards (handled by Material You color system)
- Provide content descriptions for icons: `contentDescription` parameter
- Use semantic components when available (e.g., `Switch`, `Checkbox` instead of custom implementations)

**Settings Page Design Requirements**:
- Use `Scaffold` with `TopAppBar` for consistent navigation
- Group related settings with section headers using `titleMedium` typography
- Use `OutlinedCard` or subtle dividers to separate setting groups
- Setting items should follow Material 3 list item patterns:
  - Icon (optional, 24dp) + Title + Supporting text (optional) + Trailing element (switch/chevron/etc.)
  - Padding: 16dp horizontal, 12dp vertical
  - Minimum height: 56dp
- Interactive elements (switches, radio buttons) should use Material 3 components
- Use `IconButton` for navigation and actions
- Apply proper surface elevation for layered content (dialogs, bottom sheets)

**Chat Screen Design Requirements**:
- Message bubbles should have asymmetric alignment (user: right, AI: left)
- User messages: Use `secondaryContainer` background with `large` shape (24dp corners)
- AI messages: No background, full-width layout with markdown support
- Message spacing: 4dp vertical between messages, 8dp between user/AI turns
- Input area: Use `OutlinedTextField` with send button, stick to bottom with `Scaffold` bottomBar
- Loading indicator: Small `CircularProgressIndicator` (16dp) below streaming message
- Statistics info: Use `bodySmall` typography with `onSurfaceVariant` color, shown after message completion

**Token Statistics Display**:
- Position: Below AI message content, after streaming completes
- Layout: Horizontal row with 12dp spacing between items
- Typography: `bodySmall` (12sp)
- Color: `onSurfaceVariant` (low emphasis)
- Format:
  - Token count: "150 tokens"
  - TPS: "25.3 TPS" (1 decimal place)
  - Latency: "450ms"
- Only show if tokenCount > 0
- Hide during streaming (show loading indicator instead)

### Repository Configuration

Uses Aliyun Maven mirrors (configured in `settings.gradle.kts`) for faster dependency resolution in China.
