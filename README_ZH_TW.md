[简体中文](README.md) | 繁體中文（中國台灣） | [繁體中文（中國香港）](README_HK.md) | [繁體中文（中國澳門）](README_ZH_MO.MD) | [English](README_EN.md)


<img src="zDocumentsAssets/TChat.jpg" width="400">

# TChat
## 開源的安卓端AI聊天軟體


TChat的作者是大陸人，交流最好用簡體中文，如果不會使用那就用翻譯，謝謝

[TChat](https://github.com/wanxiaoT/TChat) By [wanxiaoT](https://github.com/wanxiaoT)

[QQ 群：819083916](https://qm.qq.com/cgi-bin/qm/qr?k=MhzseFKAGyXOC18WbtNtz3Dh1kl-5uj-&jump_from=webapi&authKey=2Oz35S0JdSNfRwutsIyQZ8Y5k/3NG9iKfpJDUPVvjoxuu4NVYYh5WuIrKSyoFXhB)

## 功能特色

- 多服務商支援（OpenAI、Anthropic Claude、Google Gemini）
- 自訂 API Key 和端點設定
- 模型選擇和從 API 拉取模型列表
- 手動輸入模型支援
- 側邊欄聊天記錄導航
- 串流式聊天回應
- Material 3 UI 搭配 Jetpack Compose
- 知識庫（RAG）功能
- MCP（Model Context Protocol）工具伺服器支援

[自願贊助](https://tchat.wanxiaot.com/donate.html)


# v1.5

### 新增功能

- **助手參數增強**
  - 新增 Top-p 參數調節，支援開關控制和滑桿調節（0.0~1.0）
  - 上下文訊息數量改為 RadioButton 選擇：
    - 不限制（預設）：保留所有歷史訊息
    - 限制數量：滑桿快速調節（1~200）+ 輸入框手動填寫任意值

- **UI/UX 改進**
  - RadioButton 選項支援點擊整行文字選擇，使用 Material You `selectable` 互動
  - 訊息數量輸入框從邊框樣式改為下劃線樣式（Material You TextField）
  - 輸入框文字置中顯示，視覺更和諧
  - 修復設定頁面多級導航動畫方向：從詳情頁返回列表頁時正確使用返回動畫（從左滑入）

---

# v1.4

### 新增功能

- **聊天介面優化**
  - 停用 Markdown 連結自動解析，`[名字](連結)` 格式顯示為純文字
  - 頂部標題列顯示當前助手名稱，副標題顯示 `服務商 > 模型`
  - AI 頭像移至訊息上方，頭像旁顯示模型名稱
  - 工具選擇彈窗優化：已授權時隱藏權限狀態，只在未授權時顯示

- **使用統計功能**
  - 設定頁面新增「使用統計」入口
  - 顯示上行 Token（輸入）總計
  - 顯示下行 Token（輸出）總計
  - 顯示總呼叫次數
  - 按模型分類顯示呼叫次數

- **MCP（Model Context Protocol）工具伺服器支援**
  - 支援連接外部 MCP 伺服器，擴展 AI 工具能力
  - 支援 SSE 和 Streamable HTTP 兩種傳輸協定
  - 伺服器管理：新增/編輯/刪除/啟用/停用
  - 連接測試功能，顯示可用工具數量
  - 自訂請求標頭和逾時設定

- **MCP 伺服器管理頁面**
  - 設定頁面新增「MCP 伺服器」入口
  - 卡片式伺服器列表，顯示名稱、描述、URL
  - 一鍵測試連接狀態
  - 支援批次管理多個 MCP 伺服器

- **助手 MCP 工具設定**
  - 助手詳情頁新增「MCP工具」標籤頁
  - 為每個助手獨立選擇啟用的 MCP 伺服器
  - MCP 工具與本地工具、知識庫工具協同運作

- **聊天整合**
  - AI 可自動呼叫已啟用的 MCP 伺服器工具
  - 工具呼叫結果即時顯示
  - 支援多輪工具呼叫

---

### 技術改進詳情

#### 1. 聊天介面優化

**功能**：改進聊天訊息顯示佈局

**實現**：
- 移除 `LinkifyPlugin`，停用連結自動解析
- TopAppBar 添加副標題顯示 `服務商 > 模型`
- AI 訊息佈局改為垂直結構：頭像+模型名稱在上，內容在下

---

#### 2. 使用統計功能

**功能**：統計 Token 使用量和模型呼叫次數

**實現**：
- `MessageEntity` 添加 `modelName` 欄位
- `MessageDao` 添加統計查詢方法
- `UsageStatsScreen` 使用統計頁面
- 資料庫版本 9 → 10 遷移

---

#### 3. MCP 用戶端實現

**功能**：支援 MCP 協議的 SSE 用戶端

**實現**：
- `McpClient` 介面定義連接、工具列表、工具呼叫操作
- `McpSseClient` 實現 SSE 傳輸協議
- JSON-RPC 2.0 訊息格式
- 支援 session 管理

---

#### 4. MCP 資料層

**功能**：MCP 伺服器設定持久化

**實現**：
- `McpServerEntity` 資料庫實體
- `McpServerDao` 資料存取物件
- `McpServerRepository` 儲存庫介面和實現
- 資料庫版本 8 → 9 遷移

---

#### 5. MCP 工具服務

**功能**：將 MCP 工具轉換為本地 Tool 物件

**實現**：
- `McpToolService` 工具轉換服務
- 工具快取機制，避免重複請求
- 自動處理工具呼叫和結果返回

---

#### 6. 助手模型擴展

**修改**：
- `Assistant` 添加 `mcpServerIds` 欄位
- `AssistantEntity` 添加 `mcpServerIds` 欄位
- 支援為每個助手設定不同的 MCP 伺服器

---

### 涉及檔案

| 模組 | 檔案 | 修改 |
|------|------|------|
| feature-chat | MarkdownText.kt | 移除 LinkifyPlugin |
| feature-chat | MessageItem.kt | AI 頭像移至上方，顯示模型名稱 |
| feature-chat | MessageList.kt | 傳遞 modelName 參數 |
| feature-chat | ChatScreen.kt | 傳遞 modelName 到 ViewModel |
| feature-chat | ChatViewModel.kt | setTools 支援 modelName |
| data | MessageEntity.kt | 添加 modelName 欄位 |
| data | MessageDao.kt | 添加統計查詢方法 |
| data | Message.kt | 添加 modelName 欄位 |
| data | ChatRepository.kt | ChatConfig 添加 modelName |
| data | ChatRepositoryImpl.kt | 儲存訊息時記錄模型名稱 |
| data | AppDatabase.kt | 版本 10，modelName 遷移 |
| data | McpServer.kt | MCP 伺服器模型定義 |
| data | McpServerEntity.kt | MCP 伺服器資料庫實體 |
| data | McpServerDao.kt | MCP 伺服器 DAO |
| data | McpClient.kt | MCP 用戶端介面 |
| data | McpSseClient.kt | SSE 用戶端實現 |
| data | McpClientFactory.kt | 用戶端工廠 |
| data | McpServerRepository.kt | Repository 介面 |
| data | McpServerRepositoryImpl.kt | Repository 實現 |
| data | McpToolService.kt | MCP 工具服務 |
| data | Assistant.kt | 添加 mcpServerIds 欄位 |
| data | AssistantEntity.kt | 添加 mcpServerIds 欄位 |
| data | AssistantRepositoryImpl.kt | 更新轉換邏輯 |
| data | AppDatabase.kt | 版本 9，MCP 表遷移 |
| data | build.gradle.kts | 添加 OkHttp SSE 依賴 |
| app | McpViewModel.kt | MCP 管理 ViewModel |
| app | McpScreen.kt | MCP 伺服器管理頁面 |
| app | SettingsScreen.kt | 添加 MCP 設定入口 |
| app | AssistantDetailScreen.kt | 添加 MCP 工具標籤頁 |
| app | AssistantDetailViewModel.kt | 添加 MCP 伺服器支援 |
| app | MainActivity.kt | 整合 MCP 工具到聊天，添加副標題 |
| app | UsageStatsScreen.kt | 使用統計頁面（新增） |
| app | SettingsScreen.kt | 添加使用統計入口 |
| feature-chat | ToolSelectorSheet.kt | 已授權時隱藏權限狀態顯示 |

---

<img width="544" height="945" alt="image" src="https://github.com/user-attachments/assets/f45d79d0-07fd-4a1e-91cf-5620cfa9136f" />


# v1.3

### 新增功能

- **知識庫（RAG）功能**
  - 支援建立和管理多個知識庫
  - 內容匯入支援：文字筆記、URL網頁抓取、檔案上傳（TXT/MD）
  - 向量嵌入生成與相似度檢索
  - 支援 OpenAI 和 Gemini 的 Embedding API

- **知識庫管理**
  - 建立/編輯/刪除知識庫
  - 選擇 Embedding 服務商和模型
  - 批次處理待處理條目
  - 處理狀態顯示（待處理/處理中/已完成/失敗）

- **知識條目管理**
  - Tab 切換檢視（全部/檔案/筆記/URL）
  - 新增/編輯/刪除條目
  - 單獨處理或批次處理
  - 語意搜尋功能

- **設定入口**
  - 設定頁面新增「知識庫」入口
  - 位於「一般」分組下

- **工具呼叫參數儲存**
  - 儲存完整的工具呼叫參數（JSON 格式）
  - 記錄工具執行耗時（毫秒級）
  - 持久化儲存到資料庫，重新載入對話可檢視歷史呼叫

- **工具呼叫 UI 改進**
  - 全新的工具呼叫卡片設計
  - 顯示工具名稱、參數摘要、執行時間
  - 點擊展開檢視完整的輸入參數和執行結果
  - 格式化 JSON 顯示，更易讀
  - 成功/失敗狀態圖示區分

- **錯誤處理優化**
  - 安全處理無參數工具呼叫
  - 相容舊版本損壞資料，提供友善提示
  - JSON 解析失敗時顯示友善錯誤訊息

---

### 技術改進詳情

#### 1. Embedding API 支援

**功能**：支援 OpenAI 和 Gemini 的向量嵌入 API

**實現**：
- `EmbeddingProvider` 介面定義嵌入操作
- `OpenAIEmbeddingProvider` 呼叫 `/embeddings` 端點
- `GeminiEmbeddingProvider` 呼叫 `embedContent` 端點
- 支援批次嵌入處理

---

#### 2. 知識庫資料層

**功能**：完整的知識庫資料管理

**實現**：
- `KnowledgeRepository` 介面和實現
- `KnowledgeService` 處理內容載入、分塊、向量化
- 文件載入器：`TextLoader`、`UrlLoader`、`FileLoader`
- 資料庫版本 6 → 7 遷移，添加 status/errorMessage 欄位

---

#### 3. 向量檢索

**功能**：基於餘弦相似度的語意搜尋

**實現**：
- 文字分塊（按段落，支援重疊）
- 向量儲存為 JSON 格式
- 餘弦相似度計算
- Top-K 結果返回，支援閾值過濾

---

#### 4. ToolResultData 模型擴展

**修改**：
- 新增 `arguments` 欄位儲存工具呼叫參數
- 新增 `executionTimeMs` 欄位記錄執行耗時
- JSON 序列化/反序列化支援新欄位

---

#### 5. 無參數工具呼叫修復

**問題**：Gemini 等 API 返回無參數工具呼叫時，arguments 為空字串導致解析失敗

**修復**：
- 執行前檢查 `toolCall.arguments.ifBlank { "{}" }`
- 確保空參數被轉換為有效的空 JSON 物件

---

#### 6. 舊資料相容處理

**功能**：偵測並處理之前儲存的損壞資料

**實現**：
- 載入時偵測 "End of input at character 0" 錯誤
- 對損壞資料顯示友善提示
- 自動修正空參數欄位

---

### 涉及檔案

| 模組 | 檔案 | 修改 |
|------|------|------|
| network | EmbeddingProvider.kt | Embedding 介面定義 |
| network | OpenAIEmbeddingProvider.kt | OpenAI Embedding 實現 |
| network | GeminiEmbeddingProvider.kt | Gemini Embedding 實現 |
| network | EmbeddingProviderFactory.kt | Embedding 工廠類別 |
| data | KnowledgeItemEntity.kt | 添加 status/errorMessage 欄位 |
| data | KnowledgeRepository.kt | 知識庫 Repository 介面 |
| data | KnowledgeRepositoryImpl.kt | Repository 實現 |
| data | KnowledgeService.kt | 知識庫核心服務 |
| data | DocumentLoader.kt | 文件載入器介面 |
| data | TextLoader.kt | 文字載入器 |
| data | UrlLoader.kt | URL 網頁載入器 |
| data | FileLoader.kt | 檔案載入器 |
| data | AppDatabase.kt | 版本 7，status 欄位遷移 |
| data | Message.kt | ToolResultData 添加 arguments、executionTimeMs 欄位 |
| data | ChatRepositoryImpl.kt | 工具執行記錄參數和耗時，舊資料相容處理 |
| app | KnowledgeViewModel.kt | 知識庫 ViewModel |
| app | KnowledgeScreen.kt | 知識庫列表頁面 |
| app | KnowledgeDetailScreen.kt | 知識庫詳情頁面 |
| app | SettingsScreen.kt | 添加知識庫入口 |
| feature-chat | MessageItem.kt | 新工具卡片 UI，安全 JSON 解析 |

---

# v1.2

### 新增功能

- **服務商多模型管理**
  - 服務商設定支援從 API 拉取模型列表
  - 拉取後彈窗選擇要儲存的模型（支援多選）
  - 已儲存模型可單獨刪除
  - 支援手動添加自訂模型

- **聊天頁面模型選擇**
  - 在聊天頁面輸入框上方工具欄選擇模型
  - 使用 **Lucide Icon** 顯示模型類型圖示
    - OpenAI → ✨ Sparkles
    - Claude → 🤖 Bot
    - Gemini → 🧠 BrainCircuit

- **二維碼分享優化**
  - 使用 ModalBottomSheet 樣式替代彈窗
  - 支援選擇是否包含模型列表
  - 顯示預計資料大小
  - 二維碼卡片顯示服務商名稱和端點

- **Material You 介面重構**
  - 服務商列表頁使用 ElevatedCard 卡片設計
  - 服務商編輯頁使用卡片分組佈局
  - 使用 FAB 浮動按鈕替代底部固定按鈕
  - 刪除「設為當前使用」按鈕（簡化操作）

- **本地工具調用功能**
  - 支援 AI 調用本地工具完成任務
  - **三大提供商全支援**：OpenAI、Anthropic Claude、Google Gemini 均可使用工具
  - 聊天工具欄添加工具開關按鈕
  - 工具執行結果可展開查看詳情
  - 支援的工具：
    - `read_file` - 讀取檔案內容
    - `write_file` - 寫入檔案內容
    - `list_directory` - 列出目錄檔案
    - `delete_file` - 刪除檔案
    - `create_directory` - 建立目錄
    - `web_fetch` - 網頁內容抓取
    - `get_system_info` - 取得裝置資訊

---

### 技術改進詳情

#### 1. 工具呼叫循環機制

**功能**：AI 可以連續呼叫多個工具完成複雜任務

**實現**：
- 傳送訊息時攜帶工具定義給 AI
- AI 返回工具呼叫請求時自動執行
- 執行結果傳送回 AI 繼續對話
- 最多支援 10 輪工具呼叫，避免無限循環

---

#### 2. 工具結果視覺化

**功能**：在聊天介面顯示工具執行詳情

**實現**：
- 每個工具呼叫顯示為獨立的可點擊卡片
- 顯示「呼叫 xxx」，點擊可展開查看詳細執行結果
- 成功使用主題色，失敗使用錯誤色

---

#### 3. 資料庫支援工具資料

**修改**：
- MessageEntity 添加 `toolCallId`、`toolName`、`toolCallsJson`、`toolResultsJson` 欄位
- 資料庫版本 5 → 6 遷移

---

#### 4. 無參數工具相容性修復

**問題**：`get_system_info` 等無參數工具在某些 API（如 Anthropic）中無法被呼叫

**原因**：無參數工具的 `parameters` 返回 `null`，但 Anthropic 等 API 要求必須有有效的 `input_schema`

**修復**：無參數工具改為返回空物件 `InputSchema.Obj(emptyMap(), emptyList())`

---

### 涉及檔案

| 模組 | 檔案 | 修改 |
|------|------|------|
| network | AIProvider.kt | 添加工具呼叫相關資料類別 |
| network | OpenAIProvider.kt | 支援 Function Calling |
| network | AnthropicProvider.kt | 支援 Tool Use（工具呼叫） |
| network | GeminiProvider.kt | 支援 Function Calling |
| data | Tool.kt | 工具定義和執行介面 |
| data | LocalTools.kt | 本地工具實現 |
| data | Message.kt | 添加 ToolCallData、ToolResultData |
| data | MessageEntity.kt | 添加工具相關欄位 |
| data | ChatRepository.kt | 添加 ChatConfig 設定 |
| data | ChatRepositoryImpl.kt | 工具呼叫循環實現 |
| data | AppDatabase.kt | 版本 6，工具欄位遷移 |
| feature-chat | ChatScreen.kt | 工具開關按鈕 |
| feature-chat | MessageItem.kt | 工具結果展示 UI |
| app | MainActivity.kt | LocalTools 整合 |

---

# v1.1

### 新增功能

- 串流式訊息輸出
- 輸出內容 Token 上行/下行/TPS（每秒 Token 數）/首字延時 顯示
- 持久化資料儲存（支援 API 提供商設定和本地對話的持久化儲存）
- 多對話資料接收優化
- 優化對話頁面顯示，支援在對話頁面直接選擇模型
- 支援單個提供商設定多個模型

---

### 技術改進詳情

#### 1. 切換聊天時繼續接收 AI 訊息

**問題**：之前切換聊天會取消正在進行的 AI 串流回應

**解決方案**：Application 級別 Scope + MessageSender 單例
- MessageSender 單例管理所有聊天的傳送任務
- 使用 `Map<chatId, Job>` 獨立管理每個聊天
- 切換聊天只取消資料庫訂閱，不取消傳送任務

---

#### 2. Token 統計資訊持久化

**問題**：資料庫沒有儲存 Token 統計資訊

**修復**：
- MessageEntity 添加 `inputTokens`、`outputTokens`、`tokensPerSecond`、`firstTokenLatency`
- 資料庫版本 1 → 2 遷移

---

#### 3. AI 回覆重新生成功能

**功能**：使用者可以讓 AI 重新生成回覆，新舊回覆作為變體共存

**實現**：
- 使用者訊息下方顯示 🔄 重新整理按鈕
- 點擊後 AI 重新生成回覆
- 新回覆作為變體添加，不覆蓋舊回覆

---

#### 4. 多變體切換功能

**功能**：當 AI 訊息有多個變體時，可以切換查看

**實現**：
- AI 訊息下方顯示 `< 1/3 >` 變體選擇器
- 點擊 `<` `>` 循環切換不同版本
- 變體以 JSON 格式儲存在資料庫

---

#### 5. OpenAI 串流回應修復

**問題**：某些 API 返回 usage 時提前退出導致內容為空

**修復**：
- 先處理內容（choices），再儲存 usage
- 等待 `[DONE]` 標記再傳送 Done
- 避免 usage 導致提前退出

---

### 涉及檔案

| 模組 | 檔案 | 修改 |
|------|------|------|
| data | Message.kt | 添加 MessageVariant、變體欄位 |
| data | MessageEntity.kt | 添加統計和變體欄位 |
| data | AppDatabase.kt | 版本 3，兩次遷移 |
| data | MessageDao.kt | 變體更新方法 |
| data | ChatRepository.kt | regenerateMessage、selectVariant 介面 |
| data | ChatRepositoryImpl.kt | 重新生成、變體選擇實現 |
| data | MessageSender.kt | Application Scope 單例 |
| feature-chat | ChatViewModel.kt | 重新生成、變體選擇方法 |
| feature-chat | MessageItem.kt | 重新整理按鈕、變體選擇器 UI |
| feature-chat | MessageList.kt | 傳遞回呼 |
| feature-chat | ChatScreen.kt | 連接回呼 |
| network | OpenAIProvider.kt | 修復串流回應 |
| app | MainActivity.kt | MessageSender 初始化 |

---

# v1.0

### 首個正式版本發布！
### 核心功能
- **多服務商支援**
  - OpenAI
  - Anthropic Claude
  - Gemini
  **靈活的服務商設定**
  - 自訂 API Key
  - 自訂 API 端點（支援第三方代理）
  - 從 API 自動拉取可用模型列表
  - 手動輸入自訂模型名稱
  - 多服務商設定管理，一鍵切換
- **聊天功能**
  - 非串流式回應，即時顯示 AI 回應
  - 多會話管理
  - 側邊欄聊天記錄導航
  - 新建/刪除/切換對話

### 還沒實現的功能：
  - 串流式訊息輸出
  - 輸出內容TPS/token速度/tokens數量顯示
  - 持久化資料儲存（目前僅支援API提供商的服務持久化儲存）



## 授權條款

[License](LICENSE)
