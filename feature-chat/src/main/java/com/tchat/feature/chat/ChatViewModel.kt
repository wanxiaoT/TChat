package com.tchat.feature.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tchat.core.util.Result
import com.tchat.data.model.Chat
import com.tchat.data.model.Message
import com.tchat.data.repository.ChatRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ChatViewModel(
    private val repository: ChatRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<ChatUiState>(ChatUiState.Loading)
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private val _currentChat = MutableStateFlow<Chat?>(null)
    val currentChat: StateFlow<Chat?> = _currentChat.asStateFlow()

    fun loadChat(chatId: String) {
        viewModelScope.launch {
            repository.getChatById(chatId).collect { chat ->
                _currentChat.value = chat
                _uiState.value = ChatUiState.Success(chat?.messages ?: emptyList())
            }
        }
    }

    fun sendMessage(chatId: String, content: String) {
        viewModelScope.launch {
            repository.sendMessage(chatId, content).collect { result ->
                when (result) {
                    is Result.Success -> {
                        // Message added successfully
                    }
                    is Result.Error -> {
                        _uiState.value = ChatUiState.Error(result.exception.message ?: "Unknown error")
                    }
                    is Result.Loading -> {
                        // Handle loading state
                    }
                }
            }
        }
    }
}
