# TChat Provider Registry 迁移记录

- 日期：2026-05-10（Asia/Shanghai）
- 需求文档：本文件，`C:\Users\Administrator\AndroidStudioProjects\TChat\TCHAT_PROVIDER_REGISTRY_PROGRESS.md`
- 仓库：`C:\Users\Administrator\AndroidStudioProjects\TChat`

## 需求

完成 TChat 自己的 `ProviderDefinition` / `ModelRegistry` 抽象，把 `AIProviderFactory.kt` 从集中 `when` 分支工厂逐步迁到声明式 Provider 定义，同时保持现有调用方兼容。

执行约束：

- 先完成代码实现和验证。
- 再重新核对代码与需求文档是否一致。
- 完成 progression 和 fix 记录后，才进入下一个需求文档。
- 不复制 Chatbox 源码。
- 不引入后端、支付、许可证服务端、NewAPI 管理逻辑、密钥或部署配置到 Android 开源仓库。
- TChat 官方 NAAPI 客户端默认服务端仍指向 `https://t.naapi.cc/v1`。

## progression

1. 现状梳理
   - 检查 `AIProviderFactory`、`ChatRepositoryImpl`、`MainActivity`、`MultiKeyAIProvider`、`VisionOcrService`、Deep Research 调用点。
   - 确认外部仍依赖 `AIProviderFactory.ProviderType` 和旧 `create(...)` 入口，因此本轮必须保持旧入口源码兼容。

2. 新增 Provider 声明模型
   - 新增 `ProviderApiStyle`，描述 OpenAI Chat Completions、OpenAI Responses、Anthropic Messages、Gemini Generate Content 等 API 风格。
   - 新增 `ProviderCapability` 和 `ModelCapability`，为后续 UI 和发送链路提供能力判断基础。
   - 新增 `ModelDefinition`，集中描述模型 ID、展示名、能力、上下文窗口和最大输出 token 等元数据。
   - 新增 `ProviderDefinition`，集中描述 provider id、名称、默认 endpoint、默认 path、默认模型、别名、能力和创建函数。

3. 新增注册表
   - 新增 `ProviderRegistry`，负责注册和按 id/alias 查询 Provider 定义。
   - 新增 `ModelRegistry`，负责按 provider/model 查询模型定义和模型能力。
   - 内置 Provider 定义集中放到 `BuiltinProviderDefinitions`。

4. 内置 Provider 定义
   - 注册 `openai`、`openai-responses`、`anthropic`、`gemini`。
   - 注册 OpenAI 兼容服务：`deepseek`、`openrouter`、`ollama`、`naapi-tchat`。
   - `naapi-tchat` 默认 endpoint 明确为 `https://t.naapi.cc/v1`，别名包含 `naapi`、`naapi_tchat`、`tchat-official`。
   - OpenAI 兼容服务复用现有 `OpenAIProvider`，未引入新的网络协议实现。

5. 迁移 AIProviderFactory
   - `AIProviderFactory.ProviderType` 增加 `registryId`，保留枚举名，避免破坏现有调用。
   - `AIProviderFactory.ProviderConfig` 增加可选 `providerId`，用于 OpenAI 兼容类服务命中自己的声明式定义。
   - `AIProviderFactory.create(ProviderConfig)` 改为先查 `providerId`，再按旧 `ProviderType.registryId` 查 `ProviderRegistry`。
   - 字符串入口 `AIProviderFactory.create(providerType = "...")` 改为按 registry id/alias 查找；未知类型仍按 `openai` 兼容格式处理，保持旧行为。
   - `createOpenAI()`、`createOpenAIResponses()`、`createGemini()`、`createAnthropic()` 保留旧公开方法，但创建逻辑已通过 registry 定义进入 Provider 实例。

6. 主聊天路径接入具体 provider id
   - `MainActivity` 创建单 key Provider 时把 `currentProvider.providerType.name.lowercase()` 写入 `ProviderConfig.providerId`。
   - `MultiKeyAIProvider` 新增可选 `providerDefinitionId`，轮询 key 创建 delegate 时继续把具体 provider id 传给 `AIProviderFactory`。
   - 这样 DeepSeek/OpenRouter/Ollama/NAAPI 仍可复用 OpenAI 兼容实现，但不再只能按泛化 `OPENAI` 定义解析默认值。

7. 测试覆盖
   - 新增 `ProviderRegistryTest`。
   - 覆盖 registry 内置 provider 和 alias 查询。
   - 覆盖 NAAPI 默认 endpoint、默认模型和 path 解析。
   - 覆盖 `ModelRegistry` 模型能力查询。
   - 覆盖旧 `AIProviderFactory` 入口仍能创建对应 Provider 实例。

## fix

1. 自检 diff 时发现初版留下了未使用的 `createOpenAIDirect()` / `createOpenAIResponsesDirect()` helper，不利于工厂向 registry 收敛；已删除。

2. 自检后将 `createAnthropic()` 也改为通过 `ProviderRegistry` 创建，同时用 `CustomParams.copy(maxTokens = maxTokens)` 保留旧方法的 `maxTokens` 参数语义。

3. 完成修正后重新跑 network 单测、全量 JVM 单测、Debug APK 和 lint，确保最终状态而不是中间状态通过验证。

## 需求文档一致性比对

| 文档目标 | 当前状态 |
| --- | --- |
| 抽出 TChat 自己的 ProviderDefinition | 已新增 `ProviderDefinition`、`ProviderCreateConfig`、`ResolvedProviderConfig` 和 Provider 能力枚举。 |
| 抽出 ModelRegistry | 已新增 `ModelRegistry` 和 `ModelDefinition`，可查询默认模型和模型能力。 |
| AIProviderFactory 逐步迁到声明式配置 | `create(ProviderConfig)` 和字符串 `create(...)` 已改为 registry 驱动；旧入口和枚举保留兼容。 |
| 保持现有调用方兼容 | `ProviderType` 枚举名未变；旧 create 方法保留；主聊天、Deep Research、Vision OCR、MultiKey 路径编译通过。 |
| OpenAI 兼容类服务可保留各自定义 | 已注册 DeepSeek、OpenRouter、Ollama、NAAPI TChat，并让主聊天和多 key delegate 传入具体 provider id。 |
| NAAPI 官方服务不直连 naapi.cc | 本轮新增默认值只使用 `https://t.naapi.cc/v1`；未新增 `naapi.cc` 直连。 |
| 不复制 Chatbox 源码 | 本轮为 Kotlin/Android 原生实现，未引入 Chatbox TypeScript 代码。 |
| 不引入闭源后端内容 | 本轮仅新增客户端 provider 元数据和工厂兼容层；未加入后端、支付、密钥或部署配置。 |
| 完成验证 | network 单测、data 单测、全量 JVM 单测、Debug APK、Debug lint 均通过。 |
| 完成 progression/fix 记录 | 已在本文件记录。 |

## verification

- `C:\Users\Administrator\AndroidStudioProjects\TChat> .\gradlew.bat :network:testDebugUnitTest --console=plain`
  - 结果：BUILD SUCCESSFUL
- `C:\Users\Administrator\AndroidStudioProjects\TChat> .\gradlew.bat :data:testDebugUnitTest :app:assembleDebug --console=plain`
  - 结果：BUILD SUCCESSFUL
- `C:\Users\Administrator\AndroidStudioProjects\TChat> .\gradlew.bat testDebugUnitTest --console=plain`
  - 结果：BUILD SUCCESSFUL
- `C:\Users\Administrator\AndroidStudioProjects\TChat> .\gradlew.bat :network:testDebugUnitTest testDebugUnitTest --console=plain`
  - 结果：BUILD SUCCESSFUL
  - 说明：自检修正后重跑。
- `C:\Users\Administrator\AndroidStudioProjects\TChat> .\gradlew.bat :app:assembleDebug :app:lintDebug --console=plain`
  - 结果：BUILD SUCCESSFUL
  - 说明：自检修正后重跑最终构建和 lint。
- 安全边界检查：
  - 搜索新增 provider 相关代码，没有发现 backend secret、私钥、生产密钥或 NewAPI 管理逻辑。
  - 本轮新增 NAAPI 默认客户端 endpoint 为 `https://t.naapi.cc/v1`。
