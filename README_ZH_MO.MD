[简体中文](README.md) | [繁體中文（中國台灣）](README_ZH_TW.md) | [繁體中文（中國香港）](README_HK.md) | 繁體中文（中國澳門） | [English](README_EN.md)


<img src="zDocumentsAssets/TChat.jpg" width="400">

# TChat
## 開源嘅安卓端AI聊天軟件


TChat嘅作者係大陸人，交流最好用簡體中文，如果唔識用就用翻譯，唔該晒

[TChat](https://github.com/wanxiaoT/TChat) By [wanxiaoT](https://github.com/wanxiaoT)

[QQ 群：819083916](https://qm.qq.com/cgi-bin/qm/qr?k=MhzseFKAGyXOC18WbtNtz3Dh1kl-5uj-&jump_from=webapi&authKey=2Oz35S0JdSNfRwutsIyQZ8Y5k/3NG9iKfpJDUPVvjoxuu4NVYYh5WuIrKSyoFXhB)

## 功能特色

- 多服務商支援（OpenAI、Anthropic Claude、Google Gemini）
- 自訂 API Key 同端點設定
- 模型選擇同從 API 攞模型列表
- 手動輸入模型支援
- 側邊欄聊天記錄導航
- 串流式聊天回應
- Material 3 UI 配合 Jetpack Compose
- 知識庫（RAG）功能
- MCP（Model Context Protocol）工具伺服器支援
- 深度研究（Deep Research）功能
- **設定匯出/匯入**（檔案、二維碼、加密）
- **聊天資料夾管理**（巢狀、智能分組）
- **助手群聊**（多助手協作對話）

[自願贊助](https://tchat.wanxiaot.com/donate.html)



# v1.7

### 新增功能

- **Token 統計增強**
  - 按提供商分別統計 Token 使用量
  - 顯示每個提供商嘅輸入/輸出 Token 同調用次數
  - Token 記錄控制功能：
    - **開始**：啟用 Token 記錄
    - **暫停**：暫停記錄（保留已有數據）
    - **關閉**：完全關閉記錄功能
    - **清空**：清空所有統計數據（帶確認對話框）

- **設定匯出/匯入系統**
  - 供應商設定批量匯出/匯入（支援多選、檔案、二維碼）
  - 單個供應商模型列表匯出/匯入
  - API設定匯出/匯入（包含密鑰，強制加密）
  - 知識庫完整匯出（原始檔案+向量數據+設定）
  - AES-256-CBC 加密保護
  - 二維碼分享支援（可選加密）
  - 批量檔案匯入（自動防止ID衝突）

- **聊天資料夾系統**
  - 無限層級巢狀資料夾
  - 資料夾圖示同顏色自訂
  - 拖曳排序支援
  - 智能自動分組：
    - 按時間分組（今日/尋日/今個禮拜/今個月/更早）
    - 按模型分組（根據使用嘅AI模型）
    - 按助手分組（根據關聯嘅助手）
  - 資料夾樹狀展示（支援展開/摺疊）
  - 聊天快速分配到資料夾

- **助手群聊功能**
  - 多個助手喺同一聊天中協作對話
  - 4種訊息路由策略：
    - **自然模式**：基於話語權智能選擇
    - **輪流模式**：按成員優先順序發言
    - **隨機模式**：隨機揀助手回覆
    - **手動模式**：用戶手動揀發言者
  - 成員設定（優先順序、話語權、啟用/停用）
  - 自動模式支援（助手自動連續對話）
  - 群聊統計資訊

---

### 技術改進詳情

#### 1. 匯出/匯入系統

**功能**：完整嘅設定備份同遷移方案

**實現**：
- `ExportDataModels.kt` 定義匯出數據結構
- `EncryptionUtils.kt` AES-256-CBC加密
- `QRCodeUtils.kt` 二維碼生成/解析
- `ExportImportManager.kt` 統一管理器
- `ExportImportScreen.kt` Material 3 UI介面

**安全特性**：
- PBKDF2 密鑰派生（10000次迭代）
- API密鑰強制加密匯出
- 二維碼支援加密/非加密模式

---

#### 2. 聊天資料夾系統

**功能**：組織同管理聊天記錄

**實現**：
- `ChatFolder.kt` 資料夾數據模型
- `ChatFolderEntity.kt` 數據庫實體
- `ChatFolderDao.kt` 數據存取層
- `ChatFolderRepository.kt` 業務邏輯層
- `ChatFolderManagementScreen.kt` 資料夾管理UI

**核心演算法**：
- 資料夾樹建構演算法（遞迴）
- 循環參照偵測（移動資料夾時）
- 智能分組演算法（時間/模型/助手）

---

#### 3. 助手群聊系統

**功能**：多助手協作對話

**實現**：
- `GroupChat.kt` 群聊數據模型
- `GroupChatEntity.kt` + `GroupMemberEntity.kt` 數據庫實體
- `GroupChatDao.kt` 數據存取層
- `GroupChatRepository.kt` 業務邏輯層
- `GroupChatScreen.kt` 群聊UI介面

**訊息路由策略**：
- **LIST模式**：按優先順序輪流發言
- **POOLED模式**：隨機揀成員
- **NATURAL模式**：基於話語權加權隨機選擇
- **MANUAL模式**：用戶手動揀發言者

---

#### 4. 數據庫升級

**修改**：
- 數據庫版本 14 → 15
- 新增4個實體表：
  - `chat_folders` 聊天資料夾
  - `chat_folder_relations` 聊天-資料夾關聯
  - `group_chats` 群聊
  - `group_members` 群聊成員
- 完整嘅外鍵約束同索引優化

---

### 涉及檔案

| 模組 | 檔案 | 說明 |
|------|------|------|
| app/util | ExportDataModels.kt | 匯出數據模型定義 |
| app/util | EncryptionUtils.kt | AES-256-CBC加密工具 |
| app/util | QRCodeUtils.kt | 二維碼生成/解析工具 |
| app/util | ExportImportManager.kt | 匯出匯入統一管理器 |
| app/ui/settings | ExportImportScreen.kt | 匯出匯入UI介面 |
| app/ui/folder | ChatFolderManagementScreen.kt | 資料夾管理UI |
| app/ui/groupchat | GroupChatScreen.kt | 群聊UI介面 |
| data/model | ChatFolder.kt | 資料夾數據模型 |
| data/model | GroupChat.kt | 群聊數據模型 |
| data/database/entity | ChatFolderEntity.kt | 資料夾數據庫實體 |
| data/database/entity | GroupChatEntity.kt | 群聊數據庫實體 |
| data/database/dao | ChatFolderDao.kt | 資料夾DAO |
| data/database/dao | GroupChatDao.kt | 群聊DAO |
| data/repository | ChatFolderRepository.kt | 資料夾Repository介面 |
| data/repository | GroupChatRepository.kt | 群聊Repository介面 |
| data/repository/impl | ChatFolderRepositoryImpl.kt | 資料夾Repository實現 |
| data/repository/impl | GroupChatRepositoryImpl.kt | 群聊Repository實現 |
| data/database | AppDatabase.kt | 數據庫版本15遷移 |

同步平板同手機模式嘅設定圖示
 同步平板模式同手機模式嘅設定圖示

  修改內容

  喺 SettingsScreen.kt 嘅平板模式 SettingsListContent 函數中，添加咗兩個缺失嘅圖示定義：

  1. 助手群聊 (group_chat):
    - 使用 Lucide.Users 圖示
    - 同手機模式保持一致
  2. 正則表達式 (regex_rules):
    - 使用 Icons.Default.BugReport 圖示
    - 同手機模式保持一致

  當前圖示對應

  | 設定項     | 圖示                    | 類型        |
  |------------|-------------------------|-------------|
  | 助手       | Icons.Default.Person    | Material    |
  | 助手群聊   | Lucide.Users            | Lucide ✨   |
  | 服務商     | Icons.Default.Settings  | Material    |
  | 知識庫     | Lucide.BookOpen         | Lucide ✨   |
  | MCP 伺服器 | Icons.Default.Cloud     | Material    |
  | 深度研究   | Icons.Default.Search    | Material    |
  | 正則表達式 | Icons.Default.BugReport | Material ✨ |
  | 使用統計   | Icons.Default.BarChart  | Material    |
  | 日誌查看   | Lucide.ScrollText       | Lucide ✨   |
  | 網絡日誌   | Icons.Default.Cloud     | Material    |
  | 關於       | Icons.Default.Info      | Material    |

  而家平板模式同手機模式喺設定頁面中顯示嘅圖示完全一致喇！

---


# v1.6

### 新增功能

- **軟件內更新支援**
  - 應用程式內檢查更新、下載同安裝
  - 橫屏平板模式下選單列正常使用

- **正則表達式功能**
  - 根據社區建議新增正則表達式支援

- **服務商頁面列佈局選擇**
  - 支援一列、二列、三列顯示模式切換

- **網絡請求查看功能**
  - 新增網絡請求調試/查看入口

- **本地工具 - Sleep 工具**
  - AI 可調用 sleep 工具暫停一段時間後發送通知

---

### Bug 修復

- **修復 thinking 標籤解析問題**
  - 解決部分模型（如 Kimi-K2-Thinking）返回嘅 `<thinking>` 標籤無法正常解析嘅問題

- **修復工具調用問題**
  - 解決工具調用過程中嘅若干問題

- **優化本地文件系統工具描述**
  - 修復 LLM 唔知道系統係 Android 導致文件儲存路徑錯誤嘅問題
  - 改進本地工具-文件系統對於 LLM 嘅描述

---

# v1.5

### 新增功能

- **助手參數增強**
  - 新增 Top-p 參數調節，支援開關控制同滑桿調節（0.0~1.0）
  - 上下文訊息數量改為 RadioButton 選擇：
    - 不限制（預設）：保留所有歷史訊息
    - 限制數量：滑桿快速調節（1~200）+ 輸入框手動填寫任意值

- **UI/UX 改進**
  - RadioButton 選項支援點擊成行文字選擇，使用 Material You `selectable` 互動
  - 訊息數量輸入框從邊框樣式改為下劃線樣式（Material You TextField）
  - 輸入框文字居中顯示，視覺更和諧
  - 修復設定頁面多級導航動畫方向：從詳情頁返回列表頁時正確使用返回動畫（從左邊滑入）

- **平板適配**
  - 設定頁面支援平板 List-Detail 雙欄佈局
  - 螢幕寬度 ≥ 840dp 時自動切換為左右分欄模式
  - 左邊顯示設定列表，右邊顯示詳情內容
  - 選中項高亮顯示，支援快速切換
  - 新增搜尋功能，可快速過濾設定項

---

# v1.4

### 新增功能

- **聊天介面優化**
  - 停用 Markdown 連結自動解析，`[名字](連結)` 格式顯示為純文字
  - 頂部標題列顯示當前助手名稱，副標題顯示 `服務商 > 模型`
  - AI 頭像移到訊息上面，頭像旁邊顯示模型名稱
  - 工具選擇彈窗優化：已授權時隱藏權限狀態，淨係未授權時先顯示

- **使用統計功能**
  - 設定頁面新增「使用統計」入口
  - 顯示上行 Token（輸入）總計
  - 顯示下行 Token（輸出）總計
  - 顯示總調用次數
  - 按模型分類顯示調用次數

- **MCP（Model Context Protocol）工具伺服器支援**
  - 支援連接外部 MCP 伺服器，擴展 AI 工具能力
  - 支援 SSE 同 Streamable HTTP 兩種傳輸協議
  - 伺服器管理：添加/編輯/刪除/啟用/停用
  - 連接測試功能，顯示可用工具數量
  - 自訂請求頭同超時設定

- **MCP 伺服器管理頁面**
  - 設定頁面新增「MCP 伺服器」入口
  - 卡片式伺服器列表，顯示名稱、描述、URL
  - 一鍵測試連接狀態
  - 支援批量管理多個 MCP 伺服器

- **助手 MCP 工具設定**
  - 助手詳情頁新增「MCP工具」標籤頁
  - 為每個助手獨立揀啟用嘅 MCP 伺服器
  - MCP 工具同本地工具、知識庫工具協同運作

- **聊天整合**
  - AI 可自動調用已啟用嘅 MCP 伺服器工具
  - 工具調用結果即時顯示
  - 支援多輪工具調用

---

### 技術改進詳情

#### 1. 聊天介面優化

**功能**：改進聊天訊息顯示佈局

**實現**：
- 移除 `LinkifyPlugin`，停用連結自動解析
- TopAppBar 添加副標題顯示 `服務商 > 模型`
- AI 訊息佈局改為垂直結構：頭像+模型名稱喺上，內容喺下

---

#### 2. 使用統計功能

**功能**：統計 Token 使用量同模型調用次數

**實現**：
- `MessageEntity` 添加 `modelName` 欄位
- `MessageDao` 添加統計查詢方法
- `UsageStatsScreen` 使用統計頁面
- 資料庫版本 9 → 10 遷移

---

#### 3. MCP 客戶端實現

**功能**：支援 MCP 協議嘅 SSE 客戶端

**實現**：
- `McpClient` 介面定義連接、工具列表、工具調用操作
- `McpSseClient` 實現 SSE 傳輸協議
- JSON-RPC 2.0 訊息格式
- 支援 session 管理

---

#### 4. MCP 資料層

**功能**：MCP 伺服器設定持久化

**實現**：
- `McpServerEntity` 資料庫實體
- `McpServerDao` 資料存取物件
- `McpServerRepository` 儲存庫介面同實現
- 資料庫版本 8 → 9 遷移

---

#### 5. MCP 工具服務

**功能**：將 MCP 工具轉換為本地 Tool 物件

**實現**：
- `McpToolService` 工具轉換服務
- 工具快取機制，避免重複請求
- 自動處理工具調用同結果返回

---

#### 6. 助手模型擴展

**修改**：
- `Assistant` 添加 `mcpServerIds` 欄位
- `AssistantEntity` 添加 `mcpServerIds` 欄位
- 支援為每個助手設定唔同嘅 MCP 伺服器

---

### 涉及檔案

| 模組 | 檔案 | 修改 |
|------|------|------|
| feature-chat | MarkdownText.kt | 移除 LinkifyPlugin |
| feature-chat | MessageItem.kt | AI 頭像移到上面，顯示模型名稱 |
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
| data | McpClient.kt | MCP 客戶端介面 |
| data | McpSseClient.kt | SSE 客戶端實現 |
| data | McpClientFactory.kt | 客戶端工廠 |
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
  - 支援建立同管理多個知識庫
  - 內容導入支援：文字筆記、URL網頁抓取、檔案上傳（TXT/MD）
  - 向量嵌入生成同相似度檢索
  - 支援 OpenAI 同 Gemini 嘅 Embedding API

- **知識庫管理**
  - 建立/編輯/刪除知識庫
  - 揀 Embedding 服務商同模型
  - 批量處理待處理條目
  - 處理狀態顯示（待處理/處理中/已完成/失敗）

- **知識條目管理**
  - Tab 切換睇（全部/檔案/筆記/URL）
  - 添加/編輯/刪除條目
  - 單獨處理或批量處理
  - 語義搜尋功能

- **設定入口**
  - 設定頁面新增「知識庫」入口
  - 位於「通用」分組下

- **工具調用參數儲存**
  - 儲存完整嘅工具調用參數（JSON 格式）
  - 記錄工具執行耗時（毫秒級）
  - 持久化儲存到資料庫，重載對話可睇歷史調用

- **工具調用 UI 改進**
  - 全新嘅工具調用卡片設計
  - 顯示工具名稱、參數摘要、執行時間
  - 點擊展開睇完整嘅輸入參數同執行結果
  - 格式化 JSON 顯示，更易讀
  - 成功/失敗狀態圖標區分

- **錯誤處理優化**
  - 安全處理無參數工具調用
  - 兼容舊版本損壞數據，提供友好提示
  - JSON 解析失敗時顯示友好錯誤信息

---

### 技術改進詳情

#### 1. Embedding API 支援

**功能**：支援 OpenAI 同 Gemini 嘅向量嵌入 API

**實現**：
- `EmbeddingProvider` 介面定義嵌入操作
- `OpenAIEmbeddingProvider` 調用 `/embeddings` 端點
- `GeminiEmbeddingProvider` 調用 `embedContent` 端點
- 支援批量嵌入處理

---

#### 2. 知識庫資料層

**功能**：完整嘅知識庫資料管理

**實現**：
- `KnowledgeRepository` 介面同實現
- `KnowledgeService` 處理內容載入、分塊、向量化
- 文件載入器：`TextLoader`、`UrlLoader`、`FileLoader`
- 資料庫版本 6 → 7 遷移，添加 status/errorMessage 欄位

---

#### 3. 向量檢索

**功能**：基於餘弦相似度嘅語義搜尋

**實現**：
- 文字分塊（按段落，支援重疊）
- 向量儲存為 JSON 格式
- 餘弦相似度計算
- Top-K 結果返回，支援閾值過濾

---

#### 4. ToolResultData 模型擴展

**修改**：
- 新增 `arguments` 欄位儲存工具調用參數
- 新增 `executionTimeMs` 欄位記錄執行耗時
- JSON 序列化/反序列化支援新欄位

---

#### 5. 無參數工具調用修復

**問題**：Gemini 等 API 返回無參數工具調用時，arguments 為空字串導致解析失敗

**修復**：
- 執行前檢查 `toolCall.arguments.ifBlank { "{}" }`
- 確保空參數被轉換為有效嘅空 JSON 物件

---

#### 6. 舊資料兼容處理

**功能**：偵測並處理之前儲存嘅損壞資料

**實現**：
- 載入時偵測 "End of input at character 0" 錯誤
- 對損壞資料顯示友好提示
- 自動修正空參數欄位

---

### 涉及檔案

| 模組 | 檔案 | 修改 |
|------|------|------|
| network | EmbeddingProvider.kt | Embedding 介面定義 |
| network | OpenAIEmbeddingProvider.kt | OpenAI Embedding 實現 |
| network | GeminiEmbeddingProvider.kt | Gemini Embedding 實現 |
| network | EmbeddingProviderFactory.kt | Embedding 工廠類 |
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
| data | ChatRepositoryImpl.kt | 工具執行記錄參數同耗時，舊資料兼容處理 |
| app | KnowledgeViewModel.kt | 知識庫 ViewModel |
| app | KnowledgeScreen.kt | 知識庫列表頁面 |
| app | KnowledgeDetailScreen.kt | 知識庫詳情頁面 |
| app | SettingsScreen.kt | 添加知識庫入口 |
| feature-chat | MessageItem.kt | 新工具卡片 UI，安全 JSON 解析 |

---

# v1.2

### 新增功能

- **服務商多模型管理**
  - 服務商設定支援從 API 攞模型列表
  - 攞完彈窗揀要儲存嘅模型（支援多選）
  - 已儲存模型可以單獨刪除
  - 支援手動添加自訂模型

- **聊天頁面模型選擇**
  - 喺聊天頁面輸入框上面工具欄揀模型
  - 用 **Lucide Icon** 顯示模型類型圖示
    - OpenAI → ✨ Sparkles
    - Claude → 🤖 Bot
    - Gemini → 🧠 BrainCircuit

- **二維碼分享優化**
  - 用 ModalBottomSheet 樣式代替彈窗
  - 支援揀係咪包含模型列表
  - 顯示預計資料大小
  - 二維碼卡片顯示服務商名同端點

- **Material You 介面重構**
  - 服務商列表頁用 ElevatedCard 卡片設計
  - 服務商編輯頁用卡片分組佈局
  - 用 FAB 浮動按鈕代替底部固定按鈕
  - 刪除「設為當前使用」按鈕（簡化操作）

- **本地工具調用功能**
  - 支援 AI 調用本地工具完成任務
  - **三大提供商全支援**：OpenAI、Anthropic Claude、Google Gemini 都可以用工具
  - 聊天工具欄添加工具開關掣
  - 工具執行結果可以展開睇詳情
  - 支援嘅工具：
    - `read_file` - 讀取檔案內容
    - `write_file` - 寫入檔案內容
    - `list_directory` - 列出目錄檔案
    - `delete_file` - 刪除檔案
    - `create_directory` - 建立目錄
    - `web_fetch` - 網頁內容抓取
    - `get_system_info` - 攞裝置資訊

---

### 技術改進詳情

#### 1. 工具調用循環機制

**功能**：AI 可以連續調用多個工具完成複雜任務

**實現**：
- 發送訊息時攜帶工具定義畀 AI
- AI 返回工具調用請求時自動執行
- 執行結果發送返畀 AI 繼續對話
- 最多支援 10 輪工具調用，避免無限循環

---

#### 2. 工具結果視覺化

**功能**：喺聊天介面顯示工具執行詳情

**實現**：
- 每個工具調用顯示為獨立嘅可點擊卡片
- 顯示「調用 xxx」，點擊可展開睇詳細執行結果
- 成功用主題色，失敗用錯誤色

---

#### 3. 資料庫支援工具資料

**修改**：
- MessageEntity 添加 `toolCallId`、`toolName`、`toolCallsJson`、`toolResultsJson` 欄位
- 資料庫版本 5 → 6 遷移

---

#### 4. 無參數工具兼容性修復

**問題**：`get_system_info` 等無參數工具喺某啲 API（如 Anthropic）中無法被調用

**原因**：無參數工具嘅 `parameters` 返回 `null`，但 Anthropic 等 API 要求必須有有效嘅 `input_schema`

**修復**：無參數工具改為返回空物件 `InputSchema.Obj(emptyMap(), emptyList())`

---

### 涉及檔案

| 模組 | 檔案 | 修改 |
|------|------|------|
| network | AIProvider.kt | 添加工具調用相關資料類 |
| network | OpenAIProvider.kt | 支援 Function Calling |
| network | AnthropicProvider.kt | 支援 Tool Use（工具調用） |
| network | GeminiProvider.kt | 支援 Function Calling |
| data | Tool.kt | 工具定義同執行介面 |
| data | LocalTools.kt | 本地工具實現 |
| data | Message.kt | 添加 ToolCallData、ToolResultData |
| data | MessageEntity.kt | 添加工具相關欄位 |
| data | ChatRepository.kt | 添加 ChatConfig 設定 |
| data | ChatRepositoryImpl.kt | 工具調用循環實現 |
| data | AppDatabase.kt | 版本 6，工具欄位遷移 |
| feature-chat | ChatScreen.kt | 工具開關掣 |
| feature-chat | MessageItem.kt | 工具結果展示 UI |
| app | MainActivity.kt | LocalTools 整合 |

---

# v1.1

### 新增功能

- 串流式訊息輸出
- 輸出內容 Token 上行/下行/TPS（每秒 Token 數）/首字延時 顯示
- 持久化資料儲存（支援 API 提供商設定同本地對話嘅持久化儲存）
- 多對話資料接收優化
- 優化對話頁面顯示，支援喺對話頁面直接揀模型
- 支援單個提供商設定多個模型

---

### 技術改進詳情

#### 1. 切換聊天時繼續接收 AI 訊息

**問題**：之前切換聊天會取消正在進行嘅 AI 串流回應

**解決方案**：Application 級別 Scope + MessageSender 單例
- MessageSender 單例管理所有聊天嘅發送任務
- 使用 `Map<chatId, Job>` 獨立管理每個聊天
- 切換聊天只取消資料庫訂閱，唔取消發送任務

---

#### 2. Token 統計資訊持久化

**問題**：資料庫冇儲存 Token 統計資訊

**修復**：
- MessageEntity 添加 `inputTokens`、`outputTokens`、`tokensPerSecond`、`firstTokenLatency`
- 資料庫版本 1 → 2 遷移

---

#### 3. AI 回覆重新生成功能

**功能**：用戶可以令 AI 重新生成回覆，新舊回覆作為變體共存

**實現**：
- 用戶訊息下方顯示 🔄 刷新掣
- 點擊後 AI 重新生成回覆
- 新回覆作為變體添加，唔覆蓋舊回覆

---

#### 4. 多變體切換功能

**功能**：當 AI 訊息有多個變體時，可以切換睇

**實現**：
- AI 訊息下方顯示 `< 1/3 >` 變體選擇器
- 點擊 `<` `>` 循環切換唔同版本
- 變體以 JSON 格式儲存喺資料庫

---

#### 5. OpenAI 串流回應修復

**問題**：某啲 API 返回 usage 時提前退出導致內容為空

**修復**：
- 先處理內容（choices），再儲存 usage
- 等待 `[DONE]` 標記再發送 Done
- 避免 usage 導致提前退出

---

### 涉及檔案

| 模組 | 檔案 | 修改 |
|------|------|------|
| data | Message.kt | 添加 MessageVariant、變體欄位 |
| data | MessageEntity.kt | 添加統計同變體欄位 |
| data | AppDatabase.kt | 版本 3，兩次遷移 |
| data | MessageDao.kt | 變體更新方法 |
| data | ChatRepository.kt | regenerateMessage、selectVariant 介面 |
| data | ChatRepositoryImpl.kt | 重新生成、變體選擇實現 |
| data | MessageSender.kt | Application Scope 單例 |
| feature-chat | ChatViewModel.kt | 重新生成、變體選擇方法 |
| feature-chat | MessageItem.kt | 刷新掣、變體選擇器 UI |
| feature-chat | MessageList.kt | 傳遞回調 |
| feature-chat | ChatScreen.kt | 連接回調 |
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
  **靈活嘅服務商設定**
  - 自訂 API Key
  - 自訂 API 端點（支援第三方代理）
  - 從 API 自動攞可用模型列表
  - 手動輸入自訂模型名稱
  - 多服務商設定管理，一鍵切換
- **聊天功能**
  - 非串流式回應，即時顯示 AI 回應
  - 多會話管理
  - 側邊欄聊天記錄導航
  - 新建/刪除/切換對話

### 仲未實現嘅功能：
  - 串流式訊息輸出
  - 輸出內容TPS/token速度/tokens數量顯示
  - 持久化資料儲存（目前淨係支援API提供商嘅服務持久化儲存）



## 授權條款

[License](LICENSE)
