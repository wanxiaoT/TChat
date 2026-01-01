package com.tchat.wanxiaot.ui.knowledge

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tchat.data.database.entity.KnowledgeBaseEntity
import com.tchat.data.database.entity.KnowledgeItemEntity
import com.tchat.data.database.entity.KnowledgeItemType
import com.tchat.data.database.entity.ProcessingStatus
import com.tchat.data.repository.KnowledgeRepository
import com.tchat.data.service.KnowledgeService
import com.tchat.network.provider.EmbeddingProvider
import com.tchat.network.provider.EmbeddingProviderFactory
import com.tchat.wanxiaot.settings.AIProviderType
import com.tchat.wanxiaot.settings.ProviderConfig
import com.tchat.wanxiaot.settings.SettingsManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * 知识库 ViewModel
 */
@OptIn(ExperimentalUuidApi::class)
class KnowledgeViewModel(
    private val repository: KnowledgeRepository,
    private val knowledgeService: KnowledgeService,
    private val settingsManager: SettingsManager
) : ViewModel() {

    private val _knowledgeBases = MutableStateFlow<List<KnowledgeBaseEntity>>(emptyList())
    val knowledgeBases: StateFlow<List<KnowledgeBaseEntity>> = _knowledgeBases.asStateFlow()

    private val _currentItems = MutableStateFlow<List<KnowledgeItemEntity>>(emptyList())
    val currentItems: StateFlow<List<KnowledgeItemEntity>> = _currentItems.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    private val _searchResults = MutableStateFlow<List<KnowledgeService.SearchResult>>(emptyList())
    val searchResults: StateFlow<List<KnowledgeService.SearchResult>> = _searchResults.asStateFlow()

    private var currentBaseId: String? = null

    init {
        loadKnowledgeBases()
    }

    /**
     * 加载所有知识库
     */
    private fun loadKnowledgeBases() {
        viewModelScope.launch {
            repository.getAllBases().collect { bases ->
                _knowledgeBases.value = bases
            }
        }
    }

    /**
     * 选择知识库，加载其条目
     */
    fun selectBase(baseId: String) {
        currentBaseId = baseId
        viewModelScope.launch {
            repository.getItemsByBaseId(baseId).collect { items ->
                _currentItems.value = items
            }
        }
    }

    /**
     * 创建知识库
     */
    fun createBase(
        name: String,
        description: String?,
        embeddingProviderId: String,
        embeddingModelId: String
    ) {
        viewModelScope.launch {
            val base = KnowledgeBaseEntity(
                id = Uuid.random().toString(),
                name = name,
                description = description,
                embeddingProviderId = embeddingProviderId,
                embeddingModelId = embeddingModelId
            )
            repository.createBase(base)
        }
    }

    /**
     * 更新知识库
     */
    fun updateBase(base: KnowledgeBaseEntity) {
        viewModelScope.launch {
            repository.updateBase(base.copy(updatedAt = Instant.now().toEpochMilli()))
        }
    }

    /**
     * 删除知识库
     */
    fun deleteBase(id: String) {
        viewModelScope.launch {
            repository.deleteBase(id)
        }
    }

    /**
     * 添加文本笔记
     */
    fun addNote(baseId: String, title: String, content: String) {
        viewModelScope.launch {
            val item = KnowledgeItemEntity(
                id = Uuid.random().toString(),
                knowledgeBaseId = baseId,
                title = title,
                content = content,
                sourceType = "text",
                status = ProcessingStatus.PENDING.name
            )
            repository.addItem(item)
        }
    }

    /**
     * 添加URL
     */
    fun addUrl(baseId: String, url: String, title: String?) {
        viewModelScope.launch {
            val item = KnowledgeItemEntity(
                id = Uuid.random().toString(),
                knowledgeBaseId = baseId,
                title = title ?: url,
                content = url,
                sourceType = "url",
                sourceUri = url,
                status = ProcessingStatus.PENDING.name
            )
            repository.addItem(item)
        }
    }

    /**
     * 添加文件
     */
    fun addFile(baseId: String, file: File) {
        viewModelScope.launch {
            val item = KnowledgeItemEntity(
                id = Uuid.random().toString(),
                knowledgeBaseId = baseId,
                title = file.name,
                content = "",
                sourceType = "file",
                sourceUri = file.absolutePath,
                status = ProcessingStatus.PENDING.name
            )
            repository.addItem(item)
        }
    }

    /**
     * 更新笔记
     */
    fun updateNote(itemId: String, baseId: String, title: String, content: String) {
        viewModelScope.launch {
            val item = repository.getItemById(itemId)
            if (item != null) {
                // 删除旧的块
                repository.deleteChunksByItemId(itemId)
                // 更新条目
                repository.updateItem(
                    item.copy(
                        title = title,
                        content = content,
                        status = ProcessingStatus.PENDING.name,
                        updatedAt = Instant.now().toEpochMilli()
                    )
                )
            }
        }
    }

    /**
     * 更新URL
     */
    fun updateUrl(itemId: String, baseId: String, url: String, title: String?) {
        viewModelScope.launch {
            val item = repository.getItemById(itemId)
            if (item != null) {
                // 删除旧的块
                repository.deleteChunksByItemId(itemId)
                // 更新条目
                repository.updateItem(
                    item.copy(
                        title = title ?: url,
                        content = url,
                        sourceUri = url,
                        status = ProcessingStatus.PENDING.name,
                        updatedAt = Instant.now().toEpochMilli()
                    )
                )
            }
        }
    }

    /**
     * 删除条目
     */
    fun deleteItem(id: String) {
        viewModelScope.launch {
            repository.deleteItem(id)
        }
    }

    /**
     * 处理单个条目
     */
    fun processItem(item: KnowledgeItemEntity) {
        viewModelScope.launch {
            _isProcessing.value = true
            try {
                val base = repository.getBaseById(item.knowledgeBaseId) ?: return@launch
                val provider = getEmbeddingProvider(base) ?: return@launch

                knowledgeService.processItem(item, provider, base.embeddingModelId)
            } finally {
                _isProcessing.value = false
            }
        }
    }

    /**
     * 处理知识库中所有待处理的条目
     */
    fun processAllPending(baseId: String) {
        viewModelScope.launch {
            _isProcessing.value = true
            try {
                val base = repository.getBaseById(baseId) ?: return@launch
                val provider = getEmbeddingProvider(base) ?: return@launch

                knowledgeService.processAllPending(baseId, provider, base.embeddingModelId)
            } finally {
                _isProcessing.value = false
            }
        }
    }

    /**
     * 搜索知识库
     */
    fun search(baseId: String, query: String) {
        viewModelScope.launch {
            try {
                val base = repository.getBaseById(baseId) ?: return@launch
                val provider = getEmbeddingProvider(base) ?: return@launch

                val results = knowledgeService.search(
                    baseId = baseId,
                    query = query,
                    embeddingProvider = provider,
                    embeddingModel = base.embeddingModelId
                )
                _searchResults.value = results
            } catch (e: Exception) {
                _searchResults.value = emptyList()
            }
        }
    }

    /**
     * 清除搜索结果
     */
    fun clearSearchResults() {
        _searchResults.value = emptyList()
    }

    /**
     * 获取支持 Embedding 的服务商列表
     */
    fun getEmbeddingProviders(): List<ProviderConfig> {
        return settingsManager.settings.value.providers.filter { provider ->
            provider.providerType == AIProviderType.OPENAI || provider.providerType == AIProviderType.GEMINI
        }
    }

    /**
     * 获取 Embedding Provider
     */
    private fun getEmbeddingProvider(base: KnowledgeBaseEntity): EmbeddingProvider? {
        val settings = settingsManager.settings.value
        val providerConfig = settings.providers.find { provider -> provider.id == base.embeddingProviderId }
            ?: return null

        val providerType = when (providerConfig.providerType) {
            AIProviderType.OPENAI -> EmbeddingProviderFactory.EmbeddingProviderType.OPENAI
            AIProviderType.GEMINI -> EmbeddingProviderFactory.EmbeddingProviderType.GEMINI
            else -> return null
        }

        return EmbeddingProviderFactory.create(
            type = providerType,
            apiKey = providerConfig.apiKey,
            baseUrl = providerConfig.endpoint.ifBlank { null }
        )
    }

    /**
     * 获取默认 Embedding 模型
     */
    fun getDefaultEmbeddingModel(providerType: AIProviderType): String {
        return when (providerType) {
            AIProviderType.OPENAI -> "text-embedding-3-small"
            AIProviderType.GEMINI -> "text-embedding-004"
            else -> ""
        }
    }
}
