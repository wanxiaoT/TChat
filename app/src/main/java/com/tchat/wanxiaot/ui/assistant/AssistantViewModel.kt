package com.tchat.wanxiaot.ui.assistant

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tchat.data.model.Assistant
import com.tchat.data.repository.AssistantRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 助手列表ViewModel
 */
class AssistantViewModel(
    private val repository: AssistantRepository
) : ViewModel() {

    private val _assistants = MutableStateFlow<List<Assistant>>(emptyList())
    val assistants: StateFlow<List<Assistant>> = _assistants.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        loadAssistants()
    }

    /**
     * 加载助手列表
     */
    private fun loadAssistants() {
        viewModelScope.launch {
            repository.getAllAssistants().collect { list ->
                _assistants.value = list
            }
        }
    }

    /**
     * 创建新助手
     */
    fun createAssistant(assistant: Assistant) {
        viewModelScope.launch {
            repository.saveAssistant(assistant)
        }
    }

    /**
     * 删除助手
     */
    fun deleteAssistant(id: String) {
        viewModelScope.launch {
            repository.deleteAssistant(id)
        }
    }

    /**
     * 复制助手
     */
    fun copyAssistant(assistant: Assistant) {
        viewModelScope.launch {
            val copy = assistant.copy(
                id = java.util.UUID.randomUUID().toString(),
                name = "${assistant.name} (副本)",
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
            repository.saveAssistant(copy)
        }
    }
}
