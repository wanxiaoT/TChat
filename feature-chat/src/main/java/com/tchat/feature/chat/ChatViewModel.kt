package com.tchat.feature.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tchat.data.MessageSender
import com.tchat.data.model.Chat
import com.tchat.data.model.Message
import com.tchat.data.repository.ChatConfig
import com.tchat.data.repository.ChatRepository
import com.tchat.data.tool.Tool
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class ChatViewModel(
    private val repository: ChatRepository,
    private val messageSender: MessageSender
) : ViewModel() {

    private val _uiState = MutableStateFlow<ChatUiState>(ChatUiState.Success(emptyList()))
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private val _currentChat = MutableStateFlow<Chat?>(null)
    val currentChat: StateFlow<Chat?> = _currentChat.asStateFlow()

    // 错误信息（用于显示 Snackbar）
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    // 实际的聊天ID（懒创建后会更新）
    private val _actualChatId = MutableStateFlow<String?>(null)
    val actualChatId: StateFlow<String?> = _actualChatId.asStateFlow()

    // 数据库中的消息
    private val _dbMessages = MutableStateFlow<List<Message>>(emptyList())

    // 当前聊天配置（包含工具、系统提示等）
    private val _chatConfig = MutableStateFlow<ChatConfig?>(null)
    val chatConfig: StateFlow<ChatConfig?> = _chatConfig.asStateFlow()

    private var currentChatId: String? = null

    // Flow订阅的Job
    private var chatLoadJob: Job? = null
    private var messagesLoadJob: Job? = null
    private var streamingObserveJob: Job? = null

    init {
        // 观察流式消息变化，合并到 UI 状态
        streamingObserveJob = viewModelScope.launch {
            combine(
                _dbMessages,
                messageSender.streamingMessages
            ) { dbMessages, streamingMap ->
                val chatId = currentChatId ?: _actualChatId.value
                val streamingMessage = chatId?.let { streamingMap[it] }

                if (streamingMessage != null) {
                    // 合并数据库消息和流式消息
                    val merged = dbMessages.toMutableList()
                    val existingIndex = merged.indexOfFirst { it.id == streamingMessage.id }
                    if (existingIndex >= 0) {
                        merged[existingIndex] = streamingMessage
                    } else {
                        merged.add(streamingMessage)
                    }
                    merged
                } else {
                    dbMessages
                }
            }.collect { messages ->
                _uiState.value = ChatUiState.Success(messages)
            }
        }

        // 观察错误
        viewModelScope.launch {
            messageSender.errors.collect { error ->
                if (error != null) {
                    val (chatId, throwable) = error
                    if (chatId == currentChatId || chatId == _actualChatId.value) {
                        // 显示错误信息给用户
                        _errorMessage.value = throwable.message ?: "发送消息失败"
                    }
                    messageSender.clearError()
                }
            }
        }
    }

    /**
     * 设置聊天配置（包含工具、系统提示等）
     */
    fun setChatConfig(config: ChatConfig?) {
        _chatConfig.value = config
        messageSender.setConfig(config)
    }

    /**
     * 设置可用的工具
     */
    fun setTools(tools: List<Tool>, systemPrompt: String? = null) {
        val config = ChatConfig(
            systemPrompt = systemPrompt,
            tools = tools
        )
        setChatConfig(config)
    }

    /**
     * 加载聊天
     * @param chatId 聊天ID，null表示新建聊天（懒创建模式）
     */
    fun loadChat(chatId: String?) {
        if (currentChatId == chatId) return

        // 取消之前的数据库订阅（但不取消发送任务！）
        chatLoadJob?.cancel()
        messagesLoadJob?.cancel()

        currentChatId = chatId

        // 新建聊天模式：显示空消息列表
        if (chatId == null) {
            _actualChatId.value = null
            _currentChat.value = null
            _dbMessages.value = emptyList()
            return
        }

        _actualChatId.value = chatId

        chatLoadJob = viewModelScope.launch {
            // 加载聊天信息
            repository.getChatById(chatId).collect { chat ->
                _currentChat.value = chat
            }
        }

        messagesLoadJob = viewModelScope.launch {
            // 从数据库加载消息
            repository.getMessagesByChatId(chatId).collect { messages ->
                _dbMessages.value = messages
            }
        }
    }

    /**
     * 发送消息
     * @param chatId 聊天ID，null表示新建聊天
     */
    fun sendMessage(chatId: String?, content: String) {
        if (chatId == null) {
            // 懒创建模式
            messageSender.sendMessageToNewChat(content, _chatConfig.value) { newChatId ->
                // 聊天创建后，更新 ID
                if (_actualChatId.value == null) {
                    _actualChatId.value = newChatId
                    currentChatId = newChatId

                    // 开始监听新聊天的消息
                    messagesLoadJob?.cancel()
                    messagesLoadJob = viewModelScope.launch {
                        repository.getMessagesByChatId(newChatId).collect { messages ->
                            _dbMessages.value = messages
                        }
                    }
                }
            }
        } else {
            // 已有聊天
            messageSender.sendMessage(chatId, content, _chatConfig.value)
        }
    }

    /**
     * 取消当前聊天的发送
     */
    fun cancelCurrentSending() {
        val chatId = currentChatId ?: _actualChatId.value ?: return
        messageSender.cancelSending(chatId)
    }

    /**
     * 检查当前聊天是否正在发送
     */
    fun isCurrentChatSending(): Boolean {
        val chatId = currentChatId ?: _actualChatId.value ?: return false
        return messageSender.isSending(chatId)
    }

    /**
     * 重新生成 AI 回复
     * @param userMessageId 用户消息ID
     * @param aiMessageId AI消息ID
     */
    fun regenerateMessage(userMessageId: String, aiMessageId: String) {
        val chatId = currentChatId ?: _actualChatId.value ?: return
        messageSender.regenerateMessage(chatId, userMessageId, aiMessageId, _chatConfig.value)
    }

    /**
     * 选择消息的变体
     * @param messageId 消息ID
     * @param variantIndex 变体索引
     */
    fun selectVariant(messageId: String, variantIndex: Int) {
        messageSender.selectVariant(messageId, variantIndex)
    }

    /**
     * 清除错误信息
     */
    fun clearError() {
        _errorMessage.value = null
    }

    override fun onCleared() {
        super.onCleared()
        streamingObserveJob?.cancel()
    }
}
