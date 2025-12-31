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

# v1.1

### 新增功能：
  - 串流式訊息輸出
  - 輸出內容token上行/下行/TPS（每秒token數）顯示/首字延時 顯示
  - 持久化資料儲存（目前支援API提供商的服務持久化儲存和本地對話持久化儲存）
  - 多對話資料接收優化
  - 優化對話頁面顯示，支援對話頁面選擇模型而不是設定提供商頁面設定對話模型
  - 支援提供商對多模型



## 授權條款

[License](LICENSE)
