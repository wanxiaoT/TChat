package com.tchat.feature.chat

import com.tchat.data.model.Message

sealed class ChatUiState {
    data object Loading : ChatUiState()
    data class Success(val messages: List<Message>) : ChatUiState()
    data class Error(val message: String) : ChatUiState()
}
