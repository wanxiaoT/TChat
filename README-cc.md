# TChat - AI 聊天应用

一个基于 Android 的 AI 聊天应用，采用模块化架构设计。

## 📦 项目结构

```
TChat/
├── app/                    # 主应用模块
├── core/                   # 核心模块（通用工具类）
├── data/                   # 数据层模块（数据模型、Repository）
├── network/                # 网络层模块（AI API 服务）
└── feature-chat/           # 聊天功能模块（UI + ViewModel）
```

## ✨ 功能特性

- ✅ 多会话管理
- ✅ 消息历史（内存存储）
- ✅ 流式响应（打字机效果）
- ✅ MVVM 架构
- ✅ 模块化设计
- ✅ Jetpack Compose UI

## 🛠️ 技术栈

- **语言**: Kotlin
- **UI**: Jetpack Compose
- **架构**: MVVM + Repository Pattern
- **网络**: OkHttp
- **异步**: Kotlin Coroutines & Flow
- **AI API**: OpenAI GPT

## 📋 环境要求

- JDK 17
- Android Studio Hedgehog (2023.1.1) 或更高版本
- Android SDK 26+
- Gradle 8.13

## 🚀 快速开始

### 1. 在 Android Studio 中打开项目

1. 启动 Android Studio
2. 选择 "Open" 打开项目
3. 选择 TChat 项目目录
4. 等待 Gradle 同步完成

### 2. 配置 API Key

打开 `app/src/main/java/com/tchat/wanxiaot/MainActivity.kt`，在第 24 行替换 API Key：

```kotlin
val apiKey = "YOUR_API_KEY_HERE" // 替换为你的 OpenAI API Key
```

### 3. 同步项目

点击 Android Studio 顶部的 "Sync Project with Gradle Files" 按钮。

### 4. 运行项目

1. 连接 Android 设备或启动模拟器
2. 点击运行按钮（绿色三角形）或按 `Shift + F10`
3. 等待应用安装并启动

## 📱 模块说明

### core 模块
提供通用工具类和基础封装：
- `Result`: 统一的结果封装类（Success/Error/Loading）

### data 模块
数据层，包含数据模型和 Repository：
- `Message`: 消息数据模型
- `Chat`: 会话数据模型
- `ChatRepository`: 数据仓库接口
- `ChatRepositoryImpl`: 数据仓库实现

### network 模块
网络层，处理 AI API 调用：
- `AIService`: AI 服务接口
- `OpenAIService`: OpenAI API 实现（支持流式响应）

### feature-chat 模块
聊天功能模块：
- `ChatViewModel`: 聊天视图模型
- `ChatScreen`: 聊天主界面
- `MessageList`: 消息列表组件
- `MessageItem`: 单条消息组件
- `MessageInput`: 消息输入框组件

## 🔧 常见问题

### Q: Gradle 同步失败
A: 确保你的 Android Studio 配置了 JDK 17，在 File → Project Structure → SDK Location 中检查。

### Q: 如何更换 AI 服务提供商？
A: 实现 `AIService` 接口，创建新的服务类（如 ClaudeService），然后在 MainActivity 中替换即可。

### Q: 如何添加持久化存储？
A: 可以集成 Room 数据库，在 data 模块中添加 DAO 和 Database 类。

## 📝 下一步开发计划

- [ ] 添加会话列表界面
- [ ] 集成 Room 数据库实现持久化
- [ ] 添加设置页面（API Key 配置、模型选择等）
- [ ] 支持多种 AI 服务（Claude、Gemini 等）
- [ ] 添加语音输入功能
- [ ] 优化 UI 设计
- [ ] 添加深色模式

## 📄 许可证

MIT License
