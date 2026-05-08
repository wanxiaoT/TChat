package com.tchat.feature.chat

import com.tchat.data.model.Message

sealed class ChatUiState {
    data object Loading : ChatUiState()
    data class Success(
        val messages: List<Message>,
        val streamingMessage: Message? = null,
        val isLoadingHistory: Boolean = false,
        val hasMoreHistory: Boolean = false
    ) : ChatUiState()
    data class Error(val message: String) : ChatUiState()
}
