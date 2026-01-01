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
