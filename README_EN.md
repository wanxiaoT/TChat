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

[Voluntary Donation](https://tchat.153595.xyz/Donate/)

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

# v1.1

### New Features:
  - Streaming message output
  - Output content token upload/download/TPS (tokens per second)/first token latency display
  - Persistent data storage (now supports both API provider persistent storage and local conversation persistent storage)
  - Multi-conversation data reception optimization
  - Optimized conversation page display, supports model selection on conversation page instead of provider settings page
  - Provider multi-model support

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
