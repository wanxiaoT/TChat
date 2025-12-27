package com.tchat.feature.chat

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tchat.data.model.Message
import com.tchat.data.model.MessageRole

@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    chatId: String,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    var inputText by remember { mutableStateOf("") }

    LaunchedEffect(chatId) {
        viewModel.loadChat(chatId)
    }

    Column(modifier = modifier.fillMaxSize()) {
        when (val state = uiState) {
            is ChatUiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize()) {
                    CircularProgressIndicator()
                }
            }
            is ChatUiState.Success -> {
                MessageList(
                    messages = state.messages,
                    modifier = Modifier.weight(1f)
                )
                MessageInput(
                    text = inputText,
                    onTextChange = { inputText = it },
                    onSend = {
                        if (inputText.isNotBlank()) {
                            viewModel.sendMessage(chatId, inputText)
                            inputText = ""
                        }
                    }
                )
            }
            is ChatUiState.Error -> {
                Text(text = "Error: ${state.message}")
            }
        }
    }
}
