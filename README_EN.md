[简体中文](README.md) | [繁體中文（中國台灣）](README_ZH_TW.md) | [繁體中文（中國香港）](README_HK.md) | [繁體中文（中國澳門）](README_ZH_MO.MD) | English


<img src="zDocumentsAssets/TChat.jpg" width="400">

# TChat
## Open Source Android AI Chat App


The author of TChat is from mainland China. It's best to communicate in Simplified Chinese. If you can't, please use a translator. Thank you.

[TChat](https://github.com/wanxiaoT/TChat) By [wanxiaoT](https://github.com/wanxiaoT)

[QQ Group: 819083916](https://qm.qq.com/cgi-bin/qm/qr?k=MhzseFKAGyXOC18WbtNtz3Dh1kl-5uj-&jump_from=webapi&authKey=2Oz35S0JdSNfRwutsIyQZ8Y5k/3NG9iKfpJDUPVvjoxuu4NVYYh5WuIrKSyoFXhB)

## Features

- Multi-provider support (OpenAI, Anthropic Claude, Google Gemini)
- Custom API Key and endpoint configuration
- Model selection and fetching from API
- Manual model input support
- Chat history with sidebar navigation
- Streaming chat responses
- Material 3 UI with Jetpack Compose
- Knowledge Base (RAG) feature
- MCP (Model Context Protocol) tool server support

[Voluntary Donation](https://tchat.wanxiaot.com/donate.html)

# v1.5

### New Features

- **Assistant Parameter Enhancement**
  - New Top-p parameter control with toggle and slider (0.0~1.0)
  - Context message count changed to RadioButton selection:
    - Unlimited (default): Keep all history messages
    - Limited count: Quick slider adjustment (1~200) + manual input for any value

- **UI/UX Improvements**
  - RadioButton options support clicking entire row text, using Material You `selectable` interaction
  - Message count input changed from outlined style to underline style (Material You TextField)
  - Input text centered for better visual harmony
  - Fixed settings page multi-level navigation animation direction: correctly use back animation (slide in from left) when returning from detail page to list page

---

# v1.4

### New Features

- **Chat Interface Optimization**
  - Disabled Markdown link auto-parsing, `[name](link)` format displays as plain text
  - Top title bar shows current assistant name, subtitle shows `Provider > Model`
  - AI avatar moved above message, model name displayed next to avatar
  - Tool selector popup optimization: hide permission status when authorized, only show when unauthorized

- **Usage Statistics**
  - New "Usage Statistics" entry in settings page
  - Display total upstream Token (input)
  - Display total downstream Token (output)
  - Display total API call count
  - Display call count by model

- **MCP (Model Context Protocol) Tool Server Support**
  - Support connecting to external MCP servers to extend AI tool capabilities
  - Support SSE and Streamable HTTP transport protocols
  - Server management: add/edit/delete/enable/disable
  - Connection test feature, shows available tool count
  - Custom request headers and timeout configuration

- **MCP Server Management Page**
  - New "MCP Servers" entry in settings page
  - Card-style server list showing name, description, URL
  - One-click connection status test
  - Support batch management of multiple MCP servers

- **Assistant MCP Tool Configuration**
  - New "MCP Tools" tab in assistant detail page
  - Independently select enabled MCP servers for each assistant
  - MCP tools work together with local tools and knowledge base tools

- **Chat Integration**
  - AI can automatically call enabled MCP server tools
  - Tool call results displayed in real-time
  - Support multi-round tool calls

---

### Technical Improvements

#### 1. Chat Interface Optimization

**Feature**: Improved chat message display layout

**Implementation**:
- Removed `LinkifyPlugin`, disabled automatic link parsing
- TopAppBar added subtitle showing `Provider > Model`
- AI message layout changed to vertical structure: avatar+model name on top, content below

---

#### 2. Usage Statistics Feature

**Feature**: Statistics for Token usage and model call counts

**Implementation**:
- `MessageEntity` added `modelName` field
- `MessageDao` added statistics query methods
- `UsageStatsScreen` usage statistics page
- Database version 9 → 10 migration

---

#### 3. MCP Client Implementation

**Feature**: SSE client supporting MCP protocol

**Implementation**:
- `McpClient` interface defines connection, tool list, tool call operations
- `McpSseClient` implements SSE transport protocol
- JSON-RPC 2.0 message format
- Session management support

---

#### 4. MCP Data Layer

**Feature**: MCP server configuration persistence

**Implementation**:
- `McpServerEntity` database entity
- `McpServerDao` data access object
- `McpServerRepository` repository interface and implementation
- Database version 8 → 9 migration

---

#### 5. MCP Tool Service

**Feature**: Convert MCP tools to local Tool objects

**Implementation**:
- `McpToolService` tool conversion service
- Tool caching mechanism to avoid repeated requests
- Automatic handling of tool calls and result returns

---

#### 6. Assistant Model Extension

**Changes**:
- `Assistant` added `mcpServerIds` field
- `AssistantEntity` added `mcpServerIds` field
- Support configuring different MCP servers for each assistant

---

### Files Involved

| Module | File | Changes |
|------|------|------|
| feature-chat | MarkdownText.kt | Removed LinkifyPlugin |
| feature-chat | MessageItem.kt | AI avatar moved above, display model name |
| feature-chat | MessageList.kt | Pass modelName parameter |
| feature-chat | ChatScreen.kt | Pass modelName to ViewModel |
| feature-chat | ChatViewModel.kt | setTools supports modelName |
| data | MessageEntity.kt | Added modelName field |
| data | MessageDao.kt | Added statistics query methods |
| data | Message.kt | Added modelName field |
| data | ChatRepository.kt | ChatConfig added modelName |
| data | ChatRepositoryImpl.kt | Record model name when saving messages |
| data | AppDatabase.kt | Version 10, modelName migration |
| data | McpServer.kt | MCP server model definition |
| data | McpServerEntity.kt | MCP server database entity |
| data | McpServerDao.kt | MCP server DAO |
| data | McpClient.kt | MCP client interface |
| data | McpSseClient.kt | SSE client implementation |
| data | McpClientFactory.kt | Client factory |
| data | McpServerRepository.kt | Repository interface |
| data | McpServerRepositoryImpl.kt | Repository implementation |
| data | McpToolService.kt | MCP tool service |
| data | Assistant.kt | Added mcpServerIds field |
| data | AssistantEntity.kt | Added mcpServerIds field |
| data | AssistantRepositoryImpl.kt | Update conversion logic |
| data | AppDatabase.kt | Version 9, MCP table migration |
| data | build.gradle.kts | Added OkHttp SSE dependency |
| app | McpViewModel.kt | MCP management ViewModel |
| app | McpScreen.kt | MCP server management page |
| app | SettingsScreen.kt | Added MCP settings entry |
| app | AssistantDetailScreen.kt | Added MCP tools tab |
| app | AssistantDetailViewModel.kt | Added MCP server support |
| app | MainActivity.kt | Integrate MCP tools into chat, added subtitle |
| app | UsageStatsScreen.kt | Usage statistics page (new) |
| app | SettingsScreen.kt | Added usage statistics entry |
| feature-chat | ToolSelectorSheet.kt | Hide permission status when authorized |

---

<img width="544" height="945" alt="image" src="https://github.com/user-attachments/assets/f45d79d0-07fd-4a1e-91cf-5620cfa9136f" />


# v1.3

### New Features

- **Knowledge Base (RAG) Feature**
  - Support creating and managing multiple knowledge bases
  - Content import support: text notes, URL web scraping, file upload (TXT/MD)
  - Vector embedding generation and similarity retrieval
  - Support OpenAI and Gemini Embedding API

- **Knowledge Base Management**
  - Create/edit/delete knowledge bases
  - Select Embedding provider and model
  - Batch process pending entries
  - Processing status display (pending/processing/completed/failed)

- **Knowledge Entry Management**
  - Tab switching view (All/Files/Notes/URL)
  - Add/edit/delete entries
  - Individual or batch processing
  - Semantic search functionality

- **Settings Entry**
  - New "Knowledge Base" entry in settings page
  - Located under "General" group

- **Tool Call Parameter Storage**
  - Save complete tool call parameters (JSON format)
  - Record tool execution time (millisecond level)
  - Persistent storage to database, view historical calls when reloading conversation

- **Tool Call UI Improvements**
  - New tool call card design
  - Display tool name, parameter summary, execution time
  - Click to expand and view full input parameters and execution results
  - Formatted JSON display for better readability
  - Success/failure status icon differentiation

- **Error Handling Optimization**
  - Safely handle parameterless tool calls
  - Compatible with old version corrupted data, provide friendly prompts
  - Display friendly error message when JSON parsing fails

---

### Technical Improvements

#### 1. Embedding API Support

**Feature**: Support for OpenAI and Gemini vector embedding APIs

**Implementation**:
- `EmbeddingProvider` interface defines embedding operations
- `OpenAIEmbeddingProvider` calls `/embeddings` endpoint
- `GeminiEmbeddingProvider` calls `embedContent` endpoint
- Support batch embedding processing

---

#### 2. Knowledge Base Data Layer

**Feature**: Complete knowledge base data management

**Implementation**:
- `KnowledgeRepository` interface and implementation
- `KnowledgeService` handles content loading, chunking, vectorization
- Document loaders: `TextLoader`, `UrlLoader`, `FileLoader`
- Database version 6 → 7 migration, added status/errorMessage fields

---

#### 3. Vector Retrieval

**Feature**: Semantic search based on cosine similarity

**Implementation**:
- Text chunking (by paragraph, with overlap support)
- Vectors stored as JSON format
- Cosine similarity calculation
- Top-K results return with threshold filtering support

---

#### 4. ToolResultData Model Extension

**Changes**:
- Added `arguments` field to store tool call parameters
- Added `executionTimeMs` field to record execution time
- JSON serialization/deserialization supports new fields

---

#### 5. Parameterless Tool Call Fix

**Issue**: When APIs like Gemini return parameterless tool calls, empty arguments string causes parsing failure

**Fix**:
- Check `toolCall.arguments.ifBlank { "{}" }` before execution
- Ensure empty parameters are converted to valid empty JSON object

---

#### 6. Legacy Data Compatibility Handling

**Feature**: Detect and handle previously saved corrupted data

**Implementation**:
- Detect "End of input at character 0" error on load
- Display friendly prompt for corrupted data
- Auto-correct empty parameter fields

---

### Files Involved

| Module | File | Changes |
|------|------|------|
| network | EmbeddingProvider.kt | Embedding interface definition |
| network | OpenAIEmbeddingProvider.kt | OpenAI Embedding implementation |
| network | GeminiEmbeddingProvider.kt | Gemini Embedding implementation |
| network | EmbeddingProviderFactory.kt | Embedding factory class |
| data | KnowledgeItemEntity.kt | Added status/errorMessage fields |
| data | KnowledgeRepository.kt | Knowledge base Repository interface |
| data | KnowledgeRepositoryImpl.kt | Repository implementation |
| data | KnowledgeService.kt | Knowledge base core service |
| data | DocumentLoader.kt | Document loader interface |
| data | TextLoader.kt | Text loader |
| data | UrlLoader.kt | URL web page loader |
| data | FileLoader.kt | File loader |
| data | AppDatabase.kt | Version 7, status field migration |
| data | Message.kt | ToolResultData added arguments, executionTimeMs fields |
| data | ChatRepositoryImpl.kt | Tool execution records parameters and time, legacy data compatibility |
| app | KnowledgeViewModel.kt | Knowledge base ViewModel |
| app | KnowledgeScreen.kt | Knowledge base list page |
| app | KnowledgeDetailScreen.kt | Knowledge base detail page |
| app | SettingsScreen.kt | Added knowledge base entry |
| feature-chat | MessageItem.kt | New tool card UI, safe JSON parsing |

---

# v1.2

### New Features

- **Provider Multi-model Management**
  - Provider configuration supports fetching model list from API
  - Pop-up to select models to save after fetching (supports multi-select)
  - Saved models can be deleted individually
  - Supports manually adding custom models

- **Chat Page Model Selection**
  - Select models from toolbar above chat input
  - Uses **Lucide Icon** to display model type icons
    - OpenAI → ✨ Sparkles
    - Claude → 🤖 Bot
    - Gemini → 🧠 BrainCircuit

- **QR Code Sharing Optimization**
  - Uses ModalBottomSheet style instead of dialog
  - Supports choosing whether to include model list
  - Shows estimated data size
  - QR code card displays provider name and endpoint

- **Material You UI Redesign**
  - Provider list page uses ElevatedCard design
  - Provider edit page uses card group layout
  - Uses FAB floating button instead of fixed bottom button
  - Removed "Set as Current" button (simplified operation)

- **Local Tool Calling Feature**
  - Supports AI calling local tools to complete tasks
  - **All three providers supported**: OpenAI, Anthropic Claude, Google Gemini can all use tools
  - Tool toggle button in chat toolbar
  - Tool execution results expandable to view details
  - Supported tools:
    - `read_file` - Read file content
    - `write_file` - Write file content
    - `list_directory` - List directory files
    - `delete_file` - Delete file
    - `create_directory` - Create directory
    - `web_fetch` - Web page content fetching
    - `get_system_info` - Get device information

---

### Technical Improvements

#### 1. Tool Call Loop Mechanism

**Feature**: AI can continuously call multiple tools to complete complex tasks

**Implementation**:
- Send message with tool definitions to AI
- Auto-execute when AI returns tool call request
- Send execution result back to AI to continue conversation
- Maximum 10 rounds of tool calls to avoid infinite loops

---

#### 2. Tool Result Visualization

**Feature**: Display tool execution details in chat interface

**Implementation**:
- Each tool call displayed as independent clickable card
- Shows "Call xxx", click to expand and view detailed execution results
- Success uses theme color, failure uses error color

---

#### 3. Database Tool Data Support

**Changes**:
- MessageEntity added `toolCallId`, `toolName`, `toolCallsJson`, `toolResultsJson` fields
- Database version 5 → 6 migration

---

#### 4. Parameterless Tool Compatibility Fix

**Issue**: `get_system_info` and other parameterless tools cannot be called in some APIs (like Anthropic)

**Cause**: Parameterless tool's `parameters` returns `null`, but Anthropic and other APIs require a valid `input_schema`

**Fix**: Parameterless tools now return empty object `InputSchema.Obj(emptyMap(), emptyList())`

---

### Files Involved

| Module | File | Changes |
|------|------|------|
| network | AIProvider.kt | Added tool call related data classes |
| network | OpenAIProvider.kt | Support Function Calling |
| network | AnthropicProvider.kt | Support Tool Use |
| network | GeminiProvider.kt | Support Function Calling |
| data | Tool.kt | Tool definition and execution interface |
| data | LocalTools.kt | Local tool implementation |
| data | Message.kt | Added ToolCallData, ToolResultData |
| data | MessageEntity.kt | Added tool related fields |
| data | ChatRepository.kt | Added ChatConfig configuration |
| data | ChatRepositoryImpl.kt | Tool call loop implementation |
| data | AppDatabase.kt | Version 6, tool field migration |
| feature-chat | ChatScreen.kt | Tool toggle button |
| feature-chat | MessageItem.kt | Tool result display UI |
| app | MainActivity.kt | LocalTools integration |

---

# v1.1

### New Features

- Streaming message output
- Output content Token upstream/downstream/TPS (tokens per second)/first token latency display
- Persistent data storage (supports both API provider configuration and local conversation persistence)
- Multi-conversation data reception optimization
- Optimized conversation page display, supports model selection directly on conversation page
- Support single provider with multiple models

---

### Technical Improvements

#### 1. Continue Receiving AI Messages When Switching Chats

**Issue**: Previously, switching chats would cancel the ongoing AI streaming response

**Solution**: Application-level Scope + MessageSender singleton
- MessageSender singleton manages send tasks for all chats
- Uses `Map<chatId, Job>` to independently manage each chat
- Switching chats only cancels database subscription, not send tasks

---

#### 2. Token Statistics Persistence

**Issue**: Database did not store Token statistics information

**Fix**:
- MessageEntity added `inputTokens`, `outputTokens`, `tokensPerSecond`, `firstTokenLatency`
- Database version 1 → 2 migration

---

#### 3. AI Reply Regeneration Feature

**Feature**: Users can have AI regenerate replies, new and old replies coexist as variants

**Implementation**:
- 🔄 Refresh button displayed below user messages
- AI regenerates reply after click
- New reply added as variant, doesn't overwrite old reply

---

#### 4. Multi-variant Switching Feature

**Feature**: When AI message has multiple variants, can switch between them

**Implementation**:
- AI message shows `< 1/3 >` variant selector below
- Click `<` `>` to cycle through different versions
- Variants stored in JSON format in database

---

#### 5. OpenAI Streaming Response Fix

**Issue**: Some APIs return usage early, causing exit with empty content

**Fix**:
- Process content (choices) first, then save usage
- Wait for `[DONE]` marker before sending Done
- Avoid premature exit caused by usage

---

### Files Involved

| Module | File | Changes |
|------|------|------|
| data | Message.kt | Added MessageVariant, variant fields |
| data | MessageEntity.kt | Added statistics and variant fields |
| data | AppDatabase.kt | Version 3, two migrations |
| data | MessageDao.kt | Variant update methods |
| data | ChatRepository.kt | regenerateMessage, selectVariant interfaces |
| data | ChatRepositoryImpl.kt | Regeneration, variant selection implementation |
| data | MessageSender.kt | Application Scope singleton |
| feature-chat | ChatViewModel.kt | Regeneration, variant selection methods |
| feature-chat | MessageItem.kt | Refresh button, variant selector UI |
| feature-chat | MessageList.kt | Pass callbacks |
| feature-chat | ChatScreen.kt | Connect callbacks |
| network | OpenAIProvider.kt | Fix streaming response |
| app | MainActivity.kt | MessageSender initialization |

---

# v1.0

### First Official Release!
### Core Features
- **Multi-provider Support**
  - OpenAI
  - Anthropic Claude
  - Gemini
  **Flexible Provider Configuration**
  - Custom API Key
  - Custom API endpoint (supports third-party proxies)
  - Auto-fetch available model list from API
  - Manual input for custom model names
  - Multi-provider configuration management, one-click switch
- **Chat Features**
  - Non-streaming responses, real-time AI response display
  - Multi-session management
  - Sidebar chat history navigation
  - Create/Delete/Switch conversations

### Features Not Yet Implemented:
  - Streaming message output
  - Output content TPS/token speed/tokens count display
  - Persistent data storage (currently only supports persistent storage for API provider services)



## License

[License](LICENSE)
