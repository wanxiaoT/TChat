package com.tchat.wanxiaot.i18n

/**
 * 字符串资源接口
 * 所有 UI 文本都通过此接口提供，支持多语言
 */
interface Strings {
    // ==================== 通用 ====================
    val appName: String
    val settings: String
    val back: String
    val save: String
    val cancel: String
    val delete: String
    val confirm: String
    val search: String
    val loading: String
    val edit: String
    val add: String
    val close: String
    val done: String
    val retry: String
    val copy: String
    val copied: String
    val share: String
    val refresh: String
    val more: String
    val yes: String
    val no: String
    val ok: String
    val error: String
    val success: String
    val warning: String
    val info: String
    val noData: String
    val notConfigured: String

    // ==================== 设置页面 ====================
    val settingsTitle: String
    val settingsGeneral: String
    val settingsOther: String
    val settingsSearchHint: String
    val settingsNoResults: String
    val settingsSelectHint: String

    // 设置项 - 通用
    val settingsAssistants: String
    val settingsAssistantsDesc: String
    val settingsGroupChat: String
    val settingsGroupChatDesc: String
    val settingsProviders: String
    val settingsProvidersDesc: String
    val settingsKnowledge: String
    val settingsKnowledgeDesc: String
    val settingsMcp: String
    val settingsMcpDesc: String
    val settingsDeepResearch: String
    val settingsDeepResearchDesc: String
    val settingsRegex: String
    val settingsRegexDesc: String
    val settingsSkills: String
    val settingsSkillsDesc: String
    val settingsTts: String
    val settingsTtsDesc: String
    val settingsOcr: String
    val settingsOcrDesc: String

    // 设置项 - 其他
    val settingsUsageStats: String
    val settingsUsageStatsDesc: String
    val settingsExportImport: String
    val settingsExportImportDesc: String
    val settingsCloudBackup: String
    val settingsCloudBackupDesc: String
    val settingsLogcat: String
    val settingsLogcatDesc: String
    val settingsNetworkLog: String
    val settingsNetworkLogDesc: String
    val settingsAbout: String
    val settingsAboutDesc: String
    val settingsLanguage: String
    val settingsLanguageDesc: String

    // ==================== 语言选择页面 ====================
    val languageTitle: String
    val languageFollowSystem: String

    // ==================== 聊天页面 ====================
    val chatTitle: String
    val chatNewChat: String
    val chatSendMessage: String
    val chatInputHint: String
    val chatNoProvider: String
    val chatOpenSettings: String
    val chatMenu: String
    val chatGroupChat: String
    val chatTokens: String
    val chatTps: String
    val chatLatency: String
    val chatRegenerate: String
    val chatStopGenerating: String
    val chatCopyMessage: String
    val chatDeleteMessage: String
    val chatEditMessage: String
    val chatTools: String
    val chatToolsWithCount: String  // 格式: "工具 (%d)"
    val chatDeepResearch: String
    val chatDeepResearchRunning: String
    val chatDeepResearchInProgress: String

    // ==================== 抽屉菜单 ====================
    val drawerChats: String
    val drawerGroupChats: String
    val drawerNewChat: String
    val drawerSettings: String
    val drawerDeleteChat: String
    val drawerDeleteChatConfirm: String

    // ==================== 服务商页面 ====================
    val providersTitle: String
    val providersAdd: String
    val providersEdit: String
    val providersName: String
    val providersType: String
    val providersApiKey: String
    val providersEndpoint: String
    val providersModels: String
    val providersTestConnection: String
    val providersTestSuccess: String
    val providersTestFailed: String
    val providersDeleteConfirm: String
    val providersMultiKey: String
    val providersMultiKeyDesc: String

    // ==================== 助手页面 ====================
    val assistantsTitle: String
    val assistantsAdd: String
    val assistantsEdit: String
    val assistantsName: String
    val assistantsAvatar: String
    val assistantsSystemPrompt: String
    val assistantsTemperature: String
    val assistantsMaxTokens: String
    val assistantsContextSize: String
    val assistantsStreamOutput: String
    val assistantsLocalTools: String
    val assistantsKnowledgeBase: String
    val assistantsMcpServers: String
    val assistantsRegexRules: String
    val assistantsDeleteConfirm: String
    val assistantsDefault: String

    // ==================== 知识库页面 ====================
    val knowledgeTitle: String
    val knowledgeAdd: String
    val knowledgeEdit: String
    val knowledgeName: String
    val knowledgeDescription: String
    val knowledgeEmbeddingProvider: String
    val knowledgeEmbeddingModel: String
    val knowledgeChunkSize: String
    val knowledgeChunkOverlap: String
    val knowledgeItems: String
    val knowledgeAddItem: String
    val knowledgeDeleteConfirm: String
    val knowledgeProcessing: String
    val knowledgeProcessed: String

    // ==================== MCP 页面 ====================
    val mcpTitle: String
    val mcpAdd: String
    val mcpEdit: String
    val mcpName: String
    val mcpUrl: String
    val mcpStatus: String
    val mcpConnected: String
    val mcpDisconnected: String
    val mcpTools: String
    val mcpDeleteConfirm: String

    // ==================== 深度研究页面 ====================
    val deepResearchTitle: String
    val deepResearchStart: String
    val deepResearchStop: String
    val deepResearchQuery: String
    val deepResearchQueryHint: String
    val deepResearchBreadth: String
    val deepResearchDepth: String
    val deepResearchLanguage: String
    val deepResearchSearchLanguage: String
    val deepResearchProgress: String
    val deepResearchGeneratingReport: String
    val deepResearchComplete: String
    val deepResearchHistory: String
    val deepResearchSettings: String
    val deepResearchAiSettings: String
    val deepResearchSearchSettings: String

    // ==================== 正则表达式页面 ====================
    val regexTitle: String
    val regexAdd: String
    val regexEdit: String
    val regexName: String
    val regexPattern: String
    val regexReplacement: String
    val regexDescription: String
    val regexEnabled: String
    val regexTest: String
    val regexTestInput: String
    val regexTestOutput: String
    val regexDeleteConfirm: String

    // ==================== Skills 页面 ====================
    val skillsTitle: String
    val skillsAdd: String
    val skillsEdit: String
    val skillsName: String
    val skillsTrigger: String
    val skillsPrompt: String
    val skillsEnabled: String
    val skillsDeleteConfirm: String

    // ==================== TTS 页面 ====================
    val ttsTitle: String
    val ttsEnabled: String
    val ttsAutoSpeak: String
    val ttsSpeechRate: String
    val ttsPitch: String
    val ttsLanguage: String
    val ttsEngine: String
    val ttsEngineSystem: String
    val ttsEngineDoubao: String
    val ttsVoice: String
    val ttsTest: String
    val ttsTestText: String

    // ==================== 使用统计页面 ====================
    val usageStatsTitle: String
    val usageStatsTotalTokens: String
    val usageStatsInputTokens: String
    val usageStatsOutputTokens: String
    val usageStatsByProvider: String
    val usageStatsByModel: String
    val usageStatsRecording: String
    val usageStatsRecordingEnabled: String
    val usageStatsRecordingPaused: String
    val usageStatsRecordingDisabled: String
    val usageStatsClear: String
    val usageStatsClearConfirm: String

    // ==================== 导出/导入页面 ====================
    val exportImportTitle: String
    val exportTitle: String
    val importTitle: String
    val exportSettings: String
    val exportChats: String
    val exportKnowledge: String
    val exportAll: String
    val importFromFile: String
    val exportSuccess: String
    val importSuccess: String
    val exportFailed: String
    val importFailed: String

    // ==================== 云备份页面 ====================
    val cloudBackupTitle: String
    val cloudBackupEnabled: String
    val cloudBackupAccountId: String
    val cloudBackupAccessKeyId: String
    val cloudBackupSecretKey: String
    val cloudBackupBucket: String
    val cloudBackupEndpoint: String
    val cloudBackupTest: String
    val cloudBackupBackupNow: String
    val cloudBackupRestore: String
    val cloudBackupLastBackup: String

    // ==================== 日志页面 ====================
    val logcatTitle: String
    val logcatClear: String
    val logcatFilter: String
    val logcatLevelVerbose: String
    val logcatLevelDebug: String
    val logcatLevelInfo: String
    val logcatLevelWarn: String
    val logcatLevelError: String

    // ==================== 网络日志页面 ====================
    val networkLogTitle: String
    val networkLogRequest: String
    val networkLogResponse: String
    val networkLogHeaders: String
    val networkLogBody: String
    val networkLogStatus: String
    val networkLogDuration: String

    // ==================== 关于页面 ====================
    val aboutTitle: String
    val aboutVersion: String
    val aboutDeveloper: String
    val aboutGithub: String
    val aboutLicense: String
    val aboutPrivacy: String
    val aboutFeedback: String

    // ==================== 群聊页面 ====================
    val groupChatTitle: String
    val groupChatCreate: String
    val groupChatEdit: String
    val groupChatName: String
    val groupChatMembers: String
    val groupChatSelectMembers: String
    val groupChatDeleteConfirm: String
    val groupChatNoMembers: String
    val groupChatAddMember: String
    val groupChatRemoveMember: String
    val groupChatSpeakingAssistant: String
    val groupChatSelectAssistant: String
    val groupChatPleaseSelectAssistant: String
}
