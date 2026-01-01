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
 * 助手详情ViewModel
 */
class AssistantDetailViewModel(
    private val repository: AssistantRepository,
    private val assistantId: String
) : ViewModel() {

    private val _assistant = MutableStateFlow<Assistant?>(null)
    val assistant: StateFlow<Assistant?> = _assistant.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        loadAssistant()
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
     * 更新助手
     */
    fun updateAssistant(assistant: Assistant) {
        viewModelScope.launch {
            repository.saveAssistant(assistant.copy(updatedAt = System.currentTimeMillis()))
        }
    }
}
