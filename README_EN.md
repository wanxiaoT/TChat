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
