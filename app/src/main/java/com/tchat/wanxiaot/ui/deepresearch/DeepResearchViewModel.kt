package com.tchat.wanxiaot.ui.deepresearch

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tchat.data.deepresearch.DeepResearchManager
import com.tchat.data.deepresearch.NodeStatus
import com.tchat.data.deepresearch.ResearchNode
import com.tchat.data.deepresearch.ResearchSession
import com.tchat.data.deepresearch.ResearchState
import com.tchat.data.deepresearch.model.*
import com.tchat.data.deepresearch.repository.DeepResearchHistory
import com.tchat.data.deepresearch.repository.DeepResearchHistoryRepository
import com.tchat.data.deepresearch.repository.DeepResearchRepositoryFactory
import com.tchat.data.deepresearch.service.WebSearchServiceFactory
import com.tchat.network.provider.AIProvider
import com.tchat.network.provider.AIProviderFactory
import com.tchat.wanxiaot.settings.DeepResearchSettings
import com.tchat.wanxiaot.settings.SettingsManager
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * 深度研究 ViewModel
 * 使用 DeepResearchManager 来支持后台研究
 */
class DeepResearchViewModel(
    private val settingsManager: SettingsManager,
    private val historyRepository: DeepResearchHistoryRepository? = null
) : ViewModel() {

    init {
        // 初始化 Manager 的历史记录 Repository
        historyRepository?.let {
            DeepResearchManager.init(it)
        }
    }

    // 当前研究会话
    private val currentSession: StateFlow<ResearchSession?> = DeepResearchManager.currentSession

    // 研究状态（从会话中派生）
    val state: StateFlow<ResearchState> = currentSession
        .map { it?.state ?: ResearchState.Idle }
        .stateIn(viewModelScope, SharingStarted.Eagerly, ResearchState.Idle)

    // 研究节点（从会话中派生）
    val nodes: StateFlow<Map<String, ResearchNode>> = currentSession
        .map { it?.nodes ?: emptyMap() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    // 学习成果（从会话中派生）
    val learnings: StateFlow<List<Learning>> = currentSession
        .map { it?.learnings ?: emptyList() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // 报告内容（从会话中派生）
    val report: StateFlow<String> = currentSession
        .map { it?.report ?: "" }
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    // 深度研究设置（从 SettingsManager 加载）
    val deepResearchSettings: StateFlow<DeepResearchSettings> = settingsManager.settings
        .map { it.deepResearchSettings }
        .stateIn(viewModelScope, SharingStarted.Eagerly, DeepResearchSettings())

    // 研究配置（运行时配置，基于设置）
    private val _config = MutableStateFlow(DeepResearchConfig())
    val config: StateFlow<DeepResearchConfig> = _config.asStateFlow()

    // 历史记录列表
    val historyList: StateFlow<List<DeepResearchHistory>> = historyRepository?.getRecentHistory(50)
        ?.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
        ?: MutableStateFlow(emptyList())

    init {
        // 从设置同步配置
        viewModelScope.launch {
            deepResearchSettings.collect { settings ->
                _config.value = DeepResearchConfig(
                    breadth = settings.breadth,
                    maxDepth = settings.maxDepth,
                    maxSearchResults = settings.maxSearchResults,
                    language = settings.language,
                    searchLanguage = settings.searchLanguage,
                    concurrencyLimit = settings.concurrencyLimit
                )
            }
        }
    }

    /**
     * 更新深度研究设置（会持久化到 SettingsManager）
     */
    fun updateDeepResearchSettings(settings: DeepResearchSettings) {
        settingsManager.updateDeepResearchSettings(settings)
    }

    /**
     * 更新研究配置（仅运行时）
     */
    fun updateConfig(config: DeepResearchConfig) {
        _config.value = config
    }

    /**
     * 开始深度研究
     */
    fun startResearch(query: String) {
        // 创建 Repository
        val repository = createRepository()
        if (repository == null) {
            // 如果无法创建 Repository，设置错误状态
            // 由于我们使用 Manager，这里需要特殊处理
            return
        }

        // 使用 Manager 开始研究
        DeepResearchManager.startResearch(
            query = query,
            repository = repository,
            config = _config.value
        )
    }

    /**
     * 取消研究
     */
    fun cancelResearch() {
        DeepResearchManager.cancelResearch()
    }

    /**
     * 清除当前会话
     */
    fun clearSession() {
        DeepResearchManager.clearSession()
    }

    /**
     * 检查是否可以开始研究（配置是否完整）
     */
    fun canStartResearch(): Boolean {
        return createRepository() != null
    }

    /**
     * 获取配置错误信息
     */
    fun getConfigError(): String? {
        val settings = settingsManager.settings.value
        val deepResearchSettings = settings.deepResearchSettings

        if (deepResearchSettings.webSearchApiKey.isBlank()) {
            return "请先配置搜索 API Key"
        }

        if (getAIProvider() == null) {
            return "请先配置 AI 服务商"
        }

        return null
    }

    /**
     * 从历史记录加载
     */
    fun loadFromHistory(history: DeepResearchHistory) {
        DeepResearchManager.loadFromHistory(history)
    }

    /**
     * 删除历史记录
     */
    fun deleteHistory(id: String) {
        viewModelScope.launch {
            historyRepository?.deleteHistory(id)
        }
    }

    /**
     * 删除所有历史记录
     */
    fun deleteAllHistory() {
        viewModelScope.launch {
            historyRepository?.deleteAllHistory()
        }
    }

    private fun createRepository() = try {
        val settings = settingsManager.settings.value
        val deepResearchSettings = settings.deepResearchSettings

        // 检查搜索 API Key
        if (deepResearchSettings.webSearchApiKey.isBlank()) {
            null
        } else {
            // 获取 AI Provider
            val aiProvider = getAIProvider()
            if (aiProvider == null) {
                null
            } else {
                // 创建搜索服务
                val webSearchService = WebSearchServiceFactory.create(
                    provider = deepResearchSettings.webSearchProvider,
                    apiKey = deepResearchSettings.webSearchApiKey,
                    baseUrl = deepResearchSettings.webSearchApiBase,
                    advancedSearch = deepResearchSettings.tavilyAdvancedSearch,
                    searchTopic = deepResearchSettings.tavilySearchTopic
                )

                DeepResearchRepositoryFactory.create(aiProvider, webSearchService)
            }
        }
    } catch (e: Exception) {
        null
    }

    private fun getAIProvider(): AIProvider? {
        val settings = settingsManager.settings.value
        val deepResearchSettings = settings.deepResearchSettings

        // 如果深度研究有自己的配置，使用它
        if (deepResearchSettings.aiApiKey.isNotBlank()) {
            return AIProviderFactory.create(
                providerType = deepResearchSettings.aiProviderType,
                apiKey = deepResearchSettings.aiApiKey,
                baseUrl = deepResearchSettings.aiApiBase,
                model = deepResearchSettings.aiModel
            )
        }

        // 否则使用默认服务商
        val defaultProviderId = settings.defaultProviderId
        val providerConfig = settings.providers.find { it.id == defaultProviderId }
            ?: settings.providers.firstOrNull()
            ?: return null

        return AIProviderFactory.create(
            providerType = providerConfig.providerType.name.lowercase(),
            apiKey = providerConfig.apiKey,
            baseUrl = providerConfig.endpoint.ifBlank { null },
            model = providerConfig.selectedModel.ifEmpty { providerConfig.availableModels.firstOrNull() ?: "" }
        )
    }
}
