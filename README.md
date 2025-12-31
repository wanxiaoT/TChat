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




## 许可证

[License](LICENSE)
