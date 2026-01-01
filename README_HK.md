[简体中文](README.md) | [繁體中文（中國台灣）](README_ZH_TW.md) | 繁體中文（中國香港） | [繁體中文（中國澳門）](README_ZH_MO.MD) | [English](README_EN.md)


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

# v1.1

### 新增功能：
  - 串流式訊息輸出
  - 輸出內容token上行/下行/TPS（每秒token數）顯示/首字延時 顯示
  - 持久化資料儲存（而家支援API提供商嘅服務持久化儲存同本地對話持久化儲存）
  - 多對話資料接收優化
  - 優化對話頁面顯示，支援對話頁面揀模型而唔係設定提供商頁面設定對話模型
  - 支援提供商對多模型

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
