简体中文 | [繁體中文（中國台灣）](README_ZH_TW.md) | [繁體中文（中國香港）](README_HK.md) | [繁體中文（中國澳門）](README_ZH_MO.MD) | [English](README_EN.md)


<img src="zDocumentsAssets/TChat.jpg" width="400">

# TChat
## 开源的安卓端AI聊天软件



TChat的作者是大陆人，交流最好用简体中文，如果不会使用那就用翻译，谢谢

[TChat](https://github.com/wanxiaoT/TChat) By [wanxiaoT](https://github.com/wanxiaoT)

[QQ Group：819083916](https://qm.qq.com/cgi-bin/qm/qr?k=MhzseFKAGyXOC18WbtNtz3Dh1kl-5uj-&jump_from=webapi&authKey=2Oz35S0JdSNfRwutsIyQZ8Y5k/3NG9iKfpJDUPVvjoxuu4NVYYh5WuIrKSyoFXhB)

## 功能特色

- 多服务商支持（OpenAI、Anthropic Claude、Google Gemini）
- 自定义 API Key 和端点配置
- 模型选择和从 API 拉取模型列表
- 手动输入模型支持
- 侧边栏聊天记录导航
- 流式聊天回复
- Material 3 UI 搭配 Jetpack Compose
- 知识库（RAG）功能
- MCP（Model Context Protocol）工具服务器支持

[自愿赞助](https://tchat.153595.xyz/Donate/)


# v1.4

### 新增功能

- **聊天界面优化**
  - 禁用 Markdown 链接自动解析，`[名字](链接)` 格式显示为纯文本
  - 顶部标题栏显示当前助手名称，副标题显示 `服务商 > 模型`
  - AI 头像移至消息上方，头像旁显示模型名称
  - 工具选择弹窗优化：已授权时隐藏权限状态，只在未授权时显示

- **使用统计功能**
  - 设置页面新增"使用统计"入口
  - 显示上行 Token（输入）总计
  - 显示下行 Token（输出）总计
  - 显示总调用次数
  - 按模型分类显示调用次数

- **MCP（Model Context Protocol）工具服务器支持**
  - 支持连接外部 MCP 服务器，扩展 AI 工具能力
  - 支持 SSE 和 Streamable HTTP 两种传输协议
  - 服务器管理：添加/编辑/删除/启用/禁用
  - 连接测试功能，显示可用工具数量
  - 自定义请求头和超时配置

- **MCP 服务器管理页面**
  - 设置页面新增"MCP 服务器"入口
  - 卡片式服务器列表，显示名称、描述、URL
  - 一键测试连接状态
  - 支持批量管理多个 MCP 服务器

- **助手 MCP 工具配置**
  - 助手详情页新增"MCP工具"标签页
  - 为每个助手独立选择启用的 MCP 服务器
  - MCP 工具与本地工具、知识库工具协同工作

- **聊天集成**
  - AI 可自动调用已启用的 MCP 服务器工具
  - 工具调用结果实时显示
  - 支持多轮工具调用

---

### 技术改进详情

#### 1. 聊天界面优化

**功能**：改进聊天消息显示布局

**实现**：
- 移除 `LinkifyPlugin`，禁用链接自动解析
- TopAppBar 添加副标题显示 `服务商 > 模型`
- AI 消息布局改为垂直结构：头像+模型名称在上，内容在下

---

#### 2. 使用统计功能

**功能**：统计 Token 使用量和模型调用次数

**实现**：
- `MessageEntity` 添加 `modelName` 字段
- `MessageDao` 添加统计查询方法
- `UsageStatsScreen` 使用统计页面
- 数据库版本 9 → 10 迁移

---

#### 3. MCP 客户端实现

**功能**：支持 MCP 协议的 SSE 客户端

**实现**：
- `McpClient` 接口定义连接、工具列表、工具调用操作
- `McpSseClient` 实现 SSE 传输协议
- JSON-RPC 2.0 消息格式
- 支持 session 管理

---

#### 4. MCP 数据层

**功能**：MCP 服务器配置持久化

**实现**：
- `McpServerEntity` 数据库实体
- `McpServerDao` 数据访问对象
- `McpServerRepository` 仓库接口和实现
- 数据库版本 8 → 9 迁移

---

#### 5. MCP 工具服务

**功能**：将 MCP 工具转换为本地 Tool 对象

**实现**：
- `McpToolService` 工具转换服务
- 工具缓存机制，避免重复请求
- 自动处理工具调用和结果返回

---

#### 6. 助手模型扩展

**修改**：
- `Assistant` 添加 `mcpServerIds` 字段
- `AssistantEntity` 添加 `mcpServerIds` 字段
- 支持为每个助手配置不同的 MCP 服务器

---

### 涉及文件

| 模块 | 文件 | 修改 |
|------|------|------|
| feature-chat | MarkdownText.kt | 移除 LinkifyPlugin |
| feature-chat | MessageItem.kt | AI 头像移至上方，显示模型名称 |
| feature-chat | MessageList.kt | 传递 modelName 参数 |
| feature-chat | ChatScreen.kt | 传递 modelName 到 ViewModel |
| feature-chat | ChatViewModel.kt | setTools 支持 modelName |
| data | MessageEntity.kt | 添加 modelName 字段 |
| data | MessageDao.kt | 添加统计查询方法 |
| data | Message.kt | 添加 modelName 字段 |
| data | ChatRepository.kt | ChatConfig 添加 modelName |
| data | ChatRepositoryImpl.kt | 保存消息时记录模型名称 |
| data | AppDatabase.kt | 版本 10，modelName 迁移 |
| data | McpServer.kt | MCP 服务器模型定义 |
| data | McpServerEntity.kt | MCP 服务器数据库实体 |
| data | McpServerDao.kt | MCP 服务器 DAO |
| data | McpClient.kt | MCP 客户端接口 |
| data | McpSseClient.kt | SSE 客户端实现 |
| data | McpClientFactory.kt | 客户端工厂 |
| data | McpServerRepository.kt | Repository 接口 |
| data | McpServerRepositoryImpl.kt | Repository 实现 |
| data | McpToolService.kt | MCP 工具服务 |
| data | Assistant.kt | 添加 mcpServerIds 字段 |
| data | AssistantEntity.kt | 添加 mcpServerIds 字段 |
| data | AssistantRepositoryImpl.kt | 更新转换逻辑 |
| data | AppDatabase.kt | 版本 9，MCP 表迁移 |
| data | build.gradle.kts | 添加 OkHttp SSE 依赖 |
| app | McpViewModel.kt | MCP 管理 ViewModel |
| app | McpScreen.kt | MCP 服务器管理页面 |
| app | SettingsScreen.kt | 添加 MCP 设置入口 |
| app | AssistantDetailScreen.kt | 添加 MCP 工具标签页 |
| app | AssistantDetailViewModel.kt | 添加 MCP 服务器支持 |
| app | MainActivity.kt | 集成 MCP 工具到聊天，添加副标题 |
| app | UsageStatsScreen.kt | 使用统计页面（新增） |
| app | SettingsScreen.kt | 添加使用统计入口 |
| feature-chat | ToolSelectorSheet.kt | 已授权时隐藏权限状态显示 |

---



# v1.3

### 新增功能

- **知识库（RAG）功能**
  - 支持创建和管理多个知识库
  - 内容导入支持：文本笔记、URL网页抓取、文件上传（TXT/MD）
  - 向量嵌入生成与相似度检索
  - 支持 OpenAI 和 Gemini 的 Embedding API

- **知识库管理**
  - 创建/编辑/删除知识库
  - 选择 Embedding 服务商和模型
  - 批量处理待处理条目
  - 处理状态显示（待处理/处理中/已完成/失败）

- **知识条目管理**
  - Tab 切换查看（全部/文件/笔记/URL）
  - 添加/编辑/删除条目
  - 单独处理或批量处理
  - 语义搜索功能

- **设置入口**
  - 设置页面新增"知识库"入口
  - 位于"通用"分组下

- **工具调用参数保存**
  - 保存完整的工具调用参数（JSON 格式）
  - 记录工具执行耗时（毫秒级）
  - 持久化存储到数据库，重载对话可查看历史调用

- **工具调用 UI 改进**
  - 全新的工具调用卡片设计
  - 显示工具名称、参数摘要、执行时间
  - 点击展开查看完整的输入参数和执行结果
  - 格式化 JSON 显示，更易读
  - 成功/失败状态图标区分

- **错误处理优化**
  - 安全处理无参数工具调用
  - 兼容旧版本损坏数据，提供友好提示
  - JSON 解析失败时显示友好错误信息

---

### 技术改进详情

#### 1. Embedding API 支持

**功能**：支持 OpenAI 和 Gemini 的向量嵌入 API

**实现**：
- `EmbeddingProvider` 接口定义嵌入操作
- `OpenAIEmbeddingProvider` 调用 `/embeddings` 端点
- `GeminiEmbeddingProvider` 调用 `embedContent` 端点
- 支持批量嵌入处理

---

#### 2. 知识库数据层

**功能**：完整的知识库数据管理

**实现**：
- `KnowledgeRepository` 接口和实现
- `KnowledgeService` 处理内容加载、分块、向量化
- 文档加载器：`TextLoader`、`UrlLoader`、`FileLoader`
- 数据库版本 6 → 7 迁移，添加 status/errorMessage 字段

---

#### 3. 向量检索

**功能**：基于余弦相似度的语义搜索

**实现**：
- 文本分块（按段落，支持重叠）
- 向量存储为 JSON 格式
- 余弦相似度计算
- Top-K 结果返回，支持阈值过滤

---

#### 4. ToolResultData 模型扩展

**修改**：
- 新增 `arguments` 字段存储工具调用参数
- 新增 `executionTimeMs` 字段记录执行耗时
- JSON 序列化/反序列化支持新字段

---

#### 5. 无参数工具调用修复

**问题**：Gemini 等 API 返回无参数工具调用时，arguments 为空字符串导致解析失败

**修复**：
- 执行前检查 `toolCall.arguments.ifBlank { "{}" }`
- 确保空参数被转换为有效的空 JSON 对象

---

#### 6. 旧数据兼容处理

**功能**：检测并处理之前保存的损坏数据

**实现**：
- 加载时检测 "End of input at character 0" 错误
- 对损坏数据显示友好提示
- 自动修正空参数字段

---

### 涉及文件

| 模块 | 文件 | 修改 |
|------|------|------|
| network | EmbeddingProvider.kt | Embedding 接口定义 |
| network | OpenAIEmbeddingProvider.kt | OpenAI Embedding 实现 |
| network | GeminiEmbeddingProvider.kt | Gemini Embedding 实现 |
| network | EmbeddingProviderFactory.kt | Embedding 工厂类 |
| data | KnowledgeItemEntity.kt | 添加 status/errorMessage 字段 |
| data | KnowledgeRepository.kt | 知识库 Repository 接口 |
| data | KnowledgeRepositoryImpl.kt | Repository 实现 |
| data | KnowledgeService.kt | 知识库核心服务 |
| data | DocumentLoader.kt | 文档加载器接口 |
| data | TextLoader.kt | 文本加载器 |
| data | UrlLoader.kt | URL 网页加载器 |
| data | FileLoader.kt | 文件加载器 |
| data | AppDatabase.kt | 版本 7，status 字段迁移 |
| data | Message.kt | ToolResultData 添加 arguments、executionTimeMs 字段 |
| data | ChatRepositoryImpl.kt | 工具执行记录参数和耗时，旧数据兼容处理 |
| app | KnowledgeViewModel.kt | 知识库 ViewModel |
| app | KnowledgeScreen.kt | 知识库列表页面 |
| app | KnowledgeDetailScreen.kt | 知识库详情页面 |
| app | SettingsScreen.kt | 添加知识库入口 |
| feature-chat | MessageItem.kt | 新工具卡片 UI，安全 JSON 解析 |

---



# v1.2

### 新增功能

- **服务商多模型管理**
  - 服务商配置支持从 API 拉取模型列表
  - 拉取后弹窗选择要保存的模型（支持多选）
  - 已保存模型可单独删除
  - 支持手动添加自定义模型

- **聊天页面模型选择**
  - 在聊天页面输入框上方工具栏选择模型
  - 使用 **Lucide Icon** 显示模型类型图标
    - OpenAI → ✨ Sparkles
    - Claude → 🤖 Bot
    - Gemini → 🧠 BrainCircuit

- **二维码分享优化**
  - 使用 ModalBottomSheet 样式替代弹窗
  - 支持选择是否包含模型列表
  - 显示预计数据大小
  - 二维码卡片显示服务商名称和端点

- **Material You 界面重构**
  - 服务商列表页使用 ElevatedCard 卡片设计
  - 服务商编辑页使用卡片分组布局
  - 使用 FAB 浮动按钮替代底部固定按钮
  - 删除"设为当前使用"按钮（简化操作）

- **本地工具调用功能**
  - 支持 AI 调用本地工具完成任务
  - **三大提供商全支持**：OpenAI、Anthropic Claude、Google Gemini 均可使用工具
  - 聊天工具栏添加工具开关按钮
  - 工具执行结果可展开查看详情
  - 支持的工具：
    - `read_file` - 读取文件内容
    - `write_file` - 写入文件内容
    - `list_directory` - 列出目录文件
    - `delete_file` - 删除文件
    - `create_directory` - 创建目录
    - `web_fetch` - 网页内容抓取
    - `get_system_info` - 获取设备信息

---

### 技术改进详情

#### 1. 工具调用循环机制

**功能**：AI 可以连续调用多个工具完成复杂任务

**实现**：
- 发送消息时携带工具定义给 AI
- AI 返回工具调用请求时自动执行
- 执行结果发送回 AI 继续对话
- 最多支持 10 轮工具调用，避免无限循环

---

#### 2. 工具结果可视化

**功能**：在聊天界面显示工具执行详情

**实现**：
- 每个工具调用显示为独立的可点击卡片
- 显示"调用 xxx"，点击可展开查看详细执行结果
- 成功使用主题色，失败使用错误色

---

#### 3. 数据库支持工具数据

**修改**：
- MessageEntity 添加 `toolCallId`、`toolName`、`toolCallsJson`、`toolResultsJson` 字段
- 数据库版本 5 → 6 迁移

---

#### 4. 无参数工具兼容性修复

**问题**：`get_system_info` 等无参数工具在某些 API（如 Anthropic）中无法被调用

**原因**：无参数工具的 `parameters` 返回 `null`，但 Anthropic 等 API 要求必须有有效的 `input_schema`

**修复**：无参数工具改为返回空对象 `InputSchema.Obj(emptyMap(), emptyList())`

---

### 涉及文件

| 模块 | 文件 | 修改 |
|------|------|------|
| network | AIProvider.kt | 添加工具调用相关数据类 |
| network | OpenAIProvider.kt | 支持 Function Calling |
| network | AnthropicProvider.kt | 支持 Tool Use（工具调用） |
| network | GeminiProvider.kt | 支持 Function Calling |
| data | Tool.kt | 工具定义和执行接口 |
| data | LocalTools.kt | 本地工具实现 |
| data | Message.kt | 添加 ToolCallData、ToolResultData |
| data | MessageEntity.kt | 添加工具相关字段 |
| data | ChatRepository.kt | 添加 ChatConfig 配置 |
| data | ChatRepositoryImpl.kt | 工具调用循环实现 |
| data | AppDatabase.kt | 版本 6，工具字段迁移 |
| feature-chat | ChatScreen.kt | 工具开关按钮 |
| feature-chat | MessageItem.kt | 工具结果展示 UI |
| app | MainActivity.kt | LocalTools 集成 |

---




# v1.1

### 新增功能

- 流式信息输出
- 输出内容 Token 上行/下行/TPS（每秒 Token 数）/首字延时 显示
- 持久化数据存储（支持 API 提供商配置和本地对话的持久化存储）
- 多对话数据接收优化
- 优化对话页面显示，支持在对话页面直接选择模型
- 支持单个提供商配置多个模型

---

### 技术改进详情

#### 1. 切换聊天时继续接收 AI 消息

**问题**：之前切换聊天会取消正在进行的 AI 流式响应

**解决方案**：Application 级别 Scope + MessageSender 单例
- MessageSender 单例管理所有聊天的发送任务
- 使用 `Map<chatId, Job>` 独立管理每个聊天
- 切换聊天只取消数据库订阅，不取消发送任务

---

#### 2. Token 统计信息持久化

**问题**：数据库没有保存 Token 统计信息

**修复**：
- MessageEntity 添加 `inputTokens`、`outputTokens`、`tokensPerSecond`、`firstTokenLatency`
- 数据库版本 1 → 2 迁移

---

#### 3. AI 回复重新生成功能

**功能**：用户可以让 AI 重新生成回复，新旧回复作为变体共存

**实现**：
- 用户消息下方显示 🔄 刷新按钮
- 点击后 AI 重新生成回复
- 新回复作为变体添加，不覆盖旧回复

---

#### 4. 多变体切换功能

**功能**：当 AI 消息有多个变体时，可以切换查看

**实现**：
- AI 消息下方显示 `< 1/3 >` 变体选择器
- 点击 `<` `>` 循环切换不同版本
- 变体以 JSON 格式存储在数据库

---

#### 5. OpenAI 流式响应修复

**问题**：某些 API 返回 usage 时提前退出导致内容为空

**修复**：
- 先处理内容（choices），再保存 usage
- 等待 `[DONE]` 标记再发送 Done
- 避免 usage 导致提前退出

---

### 涉及文件

| 模块 | 文件 | 修改 |
|------|------|------|
| data | Message.kt | 添加 MessageVariant、变体字段 |
| data | MessageEntity.kt | 添加统计和变体字段 |
| data | AppDatabase.kt | 版本 3，两次迁移 |
| data | MessageDao.kt | 变体更新方法 |
| data | ChatRepository.kt | regenerateMessage、selectVariant 接口 |
| data | ChatRepositoryImpl.kt | 重新生成、变体选择实现 |
| data | MessageSender.kt | Application Scope 单例 |
| feature-chat | ChatViewModel.kt | 重新生成、变体选择方法 |
| feature-chat | MessageItem.kt | 刷新按钮、变体选择器 UI |
| feature-chat | MessageList.kt | 传递回调 |
| feature-chat | ChatScreen.kt | 连接回调 |
| network | OpenAIProvider.kt | 修复流式响应 |
| app | MainActivity.kt | MessageSender 初始化 |



# v1.0

### 首个正式版本发布！
### 核心功能
- **多服务商支持**
  - Open AI
  - Anthropic Claude
  - Gemini
  **灵活的服务商配置**
  - 自定义 API Key
  - 自定义 API 端点（支持第三方代理）
  - 从 API 自动拉取可用模型列表
  - 手动输入自定义模型名称
  - 多服务商配置管理，一键切换
- **聊天功能**
  - 非流式回复，实时显示 AI 响应
  - 多会话管理
  - 侧边栏聊天记录导航
  - 新建/删除/切换对话

###还没实现的功能：
  - 流式信息输出
  - 输出内容TPS/token速度/tokens数量显示
  - 持久化数据存储（目前仅支持API提供商的服务持久化存储）




## 许可证

[License](LICENSE)
