# TChat NAAPI 官方服务与自定义服务并存开发记录

- 日期：2026-05-10（Asia/Shanghai）
- 需求文档：`C:\1Git\tchat对接naapi方案\TChat_产品蓝图_官方服务与自定义服务并存.md`
- 仓库：`C:\Users\Administrator\AndroidStudioProjects\TChat`

## progression

1. Provider 配置模型扩展
   - 新增 `ServiceMode`：官方服务 / 自定义服务 / 本地模型。
   - 新增 `ProviderBillingMode`：官方套餐 / NAAPI License / 自有 Key / 本地 / 团队。
   - 新增 `ProviderAuthType`：Bearer / License Code / API Key / Gateway Key / 无鉴权。
   - 新增 `ModelCapabilityConfig`，支持模型展示名、厂商、分类、视觉、工具、Responses、图片生成、Embedding、速度、质量、成本、推荐标记。
   - `ProviderConfig` 增加端点路径、鉴权 Header、代理、模型目录、模型能力标签等字段，并提供路径与鉴权辅助方法。
   - `AIProviderType` 增加 OpenAI Responses、DeepSeek、OpenRouter、Ollama、TChat 官方服务。

2. 设置存储兼容
   - `SettingsManager` 的 provider JSON 读写已覆盖新增字段。
   - 模型能力标签与自定义 Header 通过 JSON 保存，不需要 Room schema 变更。

3. 网络层适配
   - `AIProviderFactory` 支持 OpenAI Responses Provider，并允许 Chat、Images 路径与鉴权 Header 从配置传入。
   - `OpenAIProvider` 支持自定义聊天路径、图片路径、鉴权 Header 与 Header 去重。
   - 新增 `OpenAIResponsesProvider`，以现有 `ChatMessage` / `StreamChunk` 接口适配 `/v1/responses` 流式返回，并处理文本增量、拒答、完成、失败、incomplete 与错误事件。

4. 官方服务链路
   - `NaapiTChatSupport.activateDevice()` 保留旧版兑换码激活，并支持解析 `gateway_key` / `gatewayKey` 与 `gateway_base_url` / `gatewayBaseUrl`。
   - `NaapiLicenseClient` 支持套餐读取、订单创建、订单查询、License Code 解析、License 设备绑定、用量摘要、设备列表、模型目录、用量明细、订单记录。
   - 购买成功后优先写入 `license_code`，鉴权方式设为 `LICENSE_CODE`，并保留 `X-TChat-Device-Id` 设备 Header；旧版 Gateway Key 继续兼容。

5. Provider 设置 UI
   - Provider 编辑页加入服务模式、计费来源、鉴权类型、鉴权 Header、路径、代理、自定义 Header、模型能力标签等配置。
   - 官方服务支持套餐读取、下单、打开支付页、支付轮询、License Code 写入、License 设备绑定与旧版兑换码激活。
   - 模型列表读取支持自定义 models path；官方服务可读取模型目录并转换成能力标签。
   - 增加连通测试与模型能力标签编辑弹窗。

6. Provider 列表入口
   - 未配置官方服务时展示 TChat 官方服务推荐卡片。
   - Provider 卡片显示服务模式与计费来源，方便区分官方服务、自定义服务和本地模型。

7. 服务与套餐页面
   - 新增 `OfficialServiceScreen`，展示官方服务状态、余额、今日用量、本月用量、请求次数、到期时间、最近请求、设备列表、订单记录。
   - 增加刷新、续费或升级套餐、查看完整订单记录入口。
   - `SettingsScreen` 新增“服务与套餐”页面入口，并兼容手机与平板布局。

8. 导入导出与二维码
   - Provider 导入导出支持服务模式、计费模式、鉴权类型、API 路径、模型路径、图片路径、Embedding 路径、模型目录路径、鉴权 Header、代理、自定义 Header、模型能力标签。
   - Provider 二维码兼容新增字段，并扩展 Responses、DeepSeek、OpenRouter、Ollama 类型编码。

9. 友好错误提示
   - 聊天错误转换为用户可理解的提示，覆盖 Key 无效、余额不足、请求频繁、模型不可用、端点或路径错误、服务不可用、网络连通失败等常见情况。

10. 需求文档已有基础能力确认
    - 项目已有会话列表、Markdown、代码块、Mermaid、图片输入/预览、消息复制、重新生成、编辑后重发、知识库、MCP、深度研究、导入导出、用量统计等模块。
    - 本次补齐文档中“官方服务与自定义服务并存”相关的客户端主链路，并为后续账号、云同步、团队、资料库云端等服务端能力保留配置结构。

## fix

1. 修正 `DatabaseBackupManager.getAppVersion()` 在 minSdk 26 下访问 `PackageInfo.longVersionCode` 的 lint 错误。
   - Android P 及以上使用 `longVersionCode`。
   - Android P 以下使用已兼容处理的 `versionCode.toLong()`。

2. 修正 OpenAI Responses Provider 中 NetworkLogger 响应记录参数的问题，避免日志调用参数错位。

3. 修正 Provider 列表页新增官方服务卡片时缺少图标 import 的问题。

4. 修正服务与套餐页面 `ReceiptLong` 图标的 deprecation warning，改用 AutoMirrored 图标。

5. 官方服务信息读取增强容错：摘要和设备为核心信息；用量明细、订单记录接口暂不可用时，本页仍可展示已读取的信息并给出提示。

## 需求文档一致性比对

| 文档目标 | 当前状态 |
| --- | --- |
| 官方服务与自定义服务共存 | 已完成 Provider 服务模式、计费模式、鉴权模式、列表展示、编辑页配置。 |
| 普通用户购买、支付、自动激活 | 已完成套餐读取、下单、支付页打开、订单轮询、License Code 写入。 |
| License Code 主凭证 | 已完成客户端保存、Bearer 鉴权 Header、设备 Header、设备绑定接口。 |
| Gateway Key | 已保留兼容；旧版兑换码激活后仍可保存 Gateway Key。 |
| 自定义服务 | 已完成 OpenAI Compatible、Responses、Claude、Gemini、DeepSeek、OpenRouter、Ollama、本地模型、自定义 Header、自定义 Path。 |
| 模型目录与能力标签 | 已完成模型目录读取、能力标签映射、编辑、保存、导入导出。 |
| 用量透明 | 已完成余额、今日、本月、请求数、最近请求、设备、订单记录入口。 |
| 错误提示 | 已完成主要网络、鉴权、余额、模型、端点类错误的友好说明。 |
| 数据可迁移 | 已完成 Provider 新字段的文件导入导出与二维码兼容。 |
| 会话体验、移动端体验、知识库、MCP 等 | 仓库已有相应模块，本次未改变主流程，仅补齐官方服务/自定义服务衔接点。 |
| 账号体系、云同步、团队空间、服务端管理 | 属于 t.naapi.cc / 后端配合范围；客户端已预留计费、模型目录、用量、设备、订单等接口接入点。 |

## verification

- `C:\Users\Administrator\AndroidStudioProjects\TChat> .\gradlew.bat :app:assembleDebug --console=plain`
  - 结果：BUILD SUCCESSFUL
- `C:\Users\Administrator\AndroidStudioProjects\TChat> .\gradlew.bat testDebugUnitTest --console=plain`
  - 结果：BUILD SUCCESSFUL
- `C:\Users\Administrator\AndroidStudioProjects\TChat> .\gradlew.bat :app:assembleDebug`
  - 结果：BUILD SUCCESSFUL
- `C:\Users\Administrator\AndroidStudioProjects\TChat> .\gradlew.bat testDebugUnitTest`
  - 结果：BUILD SUCCESSFUL
- `C:\Users\Administrator\AndroidStudioProjects\TChat> .\gradlew.bat :app:lintDebug --console=plain`
  - 结果：BUILD SUCCESSFUL
  - Lint 报告：`C:\Users\Administrator\AndroidStudioProjects\TChat\app\build\reports\lint-results-debug.html`
