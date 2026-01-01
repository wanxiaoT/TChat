package com.tchat.wanxiaot.ui.assistant

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tchat.data.database.entity.KnowledgeBaseEntity
import com.tchat.data.model.Assistant
import com.tchat.data.repository.AssistantRepository
import com.tchat.data.repository.KnowledgeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 助手详情ViewModel
 */
class AssistantDetailViewModel(
    private val repository: AssistantRepository,
    private val knowledgeRepository: KnowledgeRepository,
    private val assistantId: String
) : ViewModel() {

    private val _assistant = MutableStateFlow<Assistant?>(null)
    val assistant: StateFlow<Assistant?> = _assistant.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _knowledgeBases = MutableStateFlow<List<KnowledgeBaseEntity>>(emptyList())
    val knowledgeBases: StateFlow<List<KnowledgeBaseEntity>> = _knowledgeBases.asStateFlow()

    init {
        loadAssistant()
        loadKnowledgeBases()
    }

    /**
     * 加载助手
     */
    private fun loadAssistant() {
        viewModelScope.launch {
            _isLoading.value = true
            repository.getAssistantByIdFlow(assistantId).collect { assistant ->
                _assistant.value = assistant
                _isLoading.value = false
            }
        }
    }

    /**
     * 加载知识库列表
     */
    private fun loadKnowledgeBases() {
        viewModelScope.launch {
            knowledgeRepository.getAllBases().collect { bases ->
                _knowledgeBases.value = bases
            }
        }
    }

    /**
     * 更新助手
     */
    fun updateAssistant(assistant: Assistant) {
        viewModelScope.launch {
            repository.saveAssistant(assistant.copy(updatedAt = System.currentTimeMillis()))
        }
    }
}
