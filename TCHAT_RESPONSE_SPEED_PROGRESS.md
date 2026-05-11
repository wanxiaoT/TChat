# TChat 响应速度彻查与优化记录

- 日期：2026-05-10（Asia/Shanghai）
- 需求文档：本文件，`C:\Users\Administrator\AndroidStudioProjects\TChat\TCHAT_RESPONSE_SPEED_PROGRESS.md`
- 仓库：`C:\Users\Administrator\AndroidStudioProjects\TChat`

## 需求

对 TChat 软件代码在响应速度方面进行彻底检查，并按可落地的子项逐个完成。每个需求文档必须先完成代码实现和验证，再重新核对实现与需求是否一致，完成 progression 和 fix 记录后，才进入下一个需求文档。

本轮聚焦第一份需求文档：聊天响应热路径优化。目标是降低长会话发送前准备耗时、降低流式回复期间 UI 重组压力、降低长输出日志内存占用，并避免消息列表和媒体缩略图造成明显卡顿。

## progression

1. 代码路径检查
   - 检查范围覆盖 `feature-chat` 聊天 UI、`ChatViewModel`、`MessageSender`、`ChatRepositoryImpl`、Room DAO、`network` Provider 与 `NetworkLogger`。
   - 已确认 `MessageSender` 当前已具备按 `chatId` 隔离配置的实现，本轮没有回退该并发修复。

2. 上下文窗口接入发送链路
   - `ChatConfig` 新增 `contextMessageSize`，默认 64，`<=0` 表示全量上下文。
   - 单聊从当前助手读取 `contextMessageSize` 与 `enabledSkillIds` 并传入 `ChatScreen` / `ChatViewModel`。
   - 群聊为每个助手配置写入 `contextMessageSize`，轮到该助手发言时使用对应上下文窗口。
   - `MessageDao` 增加最近 N 条和截至指定消息的上下文查询，避免长会话每次发送都读取全量消息。
   - `ChatRepositoryImpl.sendMessage()` 和 `regenerateMessage()` 均改为按上下文窗口取历史消息。

3. 流式响应合并与字符串构建优化
   - `executeWithToolCalls()` 和 `executeWithToolCallsForRegenerate()` 将 `String += chunk` 改为 `StringBuilder`，避免长回复 O(n²) 字符串拷贝。
   - 流式 UI 更新改为首包立即发，后续以 48ms 窗口合并，工具调用和结束时强制刷新，减少 Markdown 整段重排频率。

4. 消息列表首屏与滚动体验优化
   - `MessageList` 的入场 stagger 延迟从按完整列表 index 线性增长，改为最多 8 个条目、每级 24ms，避免打开 100 条历史消息时后排条目延迟数秒。

5. 聊天媒体缩略图优化
   - `AsyncBitmapLoader` 增加 24MB 内存 LRU 缓存，滚动返回已加载图片时复用缩略图，减少重复 decode 和 IO。

6. 网络调试日志优化
   - `NetworkLogger` 增加有界 `BodyCapture`，OpenAI、OpenAI Responses、Anthropic、Gemini Provider 在流式过程中只收集有限响应体用于调试日志。
   - 保留现有日志截断行为，同时避免长输出在 Debug 模式下先完整拼接到内存。

7. 测试覆盖
   - 新增 `ChatRepositoryImplTest`，验证配置 `contextMessageSize = 3` 时 Provider 只收到最近上下文。
   - 扩展 `NetworkLoggerTest`，验证流式日志响应体 capture 会被有界截断。

## fix

1. JVM 单元测试中 `org.json.JSONObject` 为 Android stub，新增 `data` 模块 `testImplementation("org.json:json:20240303")`，使 `MessagePartSerializer` 可在本地 JVM 测试中运行。

2. `:app:lintDebug` 第一次以 5 分钟超时被工具截断，未得到失败报告；随后单独以 10 分钟超时重跑，结果 `BUILD SUCCESSFUL`。

## 需求文档一致性比对

| 文档目标 | 当前状态 |
| --- | --- |
| 对响应速度进行彻查 | 已检查聊天 UI、ViewModel、发送器、Repository、DAO、Provider、日志路径。 |
| 可拆分但需逐个击破 | 本轮作为第一份需求文档，聚焦聊天响应热路径，已完成实现、验证、复核和记录。 |
| 代码实现优先 | 已完成上下文窗口、流式合并、列表动画上限、缩略图缓存、日志有界 capture。 |
| 完成验证 | 已通过数据模块测试、网络模块测试、全量 JVM 单元测试、Debug APK 构建、Debug lint。 |
| 重新核对代码与需求一致 | 已确认实现均对应响应准备、流式渲染、列表渲染、媒体加载、调试日志内存这五类响应速度问题。 |
| 完成 progression 和 fix 记录 | 已在本文件记录 progression 和 fix。 |
| 不破坏数据库迁移 | 本轮只增加 DAO 查询方法，不新增/修改表字段；Room schema 版本无需变更。 |

## verification

- `C:\Users\Administrator\AndroidStudioProjects\TChat> .\gradlew.bat :data:testDebugUnitTest --console=plain`
  - 结果：BUILD SUCCESSFUL
- `C:\Users\Administrator\AndroidStudioProjects\TChat> .\gradlew.bat :network:testDebugUnitTest --console=plain`
  - 结果：BUILD SUCCESSFUL
- `C:\Users\Administrator\AndroidStudioProjects\TChat> .\gradlew.bat testDebugUnitTest --console=plain`
  - 结果：BUILD SUCCESSFUL
- `C:\Users\Administrator\AndroidStudioProjects\TChat> .\gradlew.bat :app:assembleDebug --console=plain`
  - 结果：BUILD SUCCESSFUL
- `C:\Users\Administrator\AndroidStudioProjects\TChat> .\gradlew.bat :app:lintDebug --console=plain`
  - 结果：BUILD SUCCESSFUL
  - 说明：第一次 5 分钟超时被工具截断；第二次单独放宽到 10 分钟后成功。
