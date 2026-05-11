package com.tchat.feature.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tchat.data.MessageSender
import com.tchat.data.model.Chat
import com.tchat.data.model.Message
import com.tchat.data.model.MessagePart
import com.tchat.data.repository.ChatConfig
import com.tchat.data.repository.ChatRepository
import com.tchat.data.tool.Tool
import com.tchat.data.util.RegexRuleData
import com.tchat.network.provider.AIProviderException
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

    private val _historyMessages = MutableStateFlow<List<Message>>(emptyList())
    private val _streamingMessage = MutableStateFlow<Message?>(null)
    private val _isLoadingHistory = MutableStateFlow(false)
    private val _hasMoreHistory = MutableStateFlow(false)

    // 当前聊天配置（包含工具、系统提示等）
    private val _chatConfig = MutableStateFlow<ChatConfig?>(null)
    val chatConfig: StateFlow<ChatConfig?> = _chatConfig.asStateFlow()

    private var currentChatId: String? = null
    private var olderMessages: List<Message> = emptyList()
    private var recentMessages: List<Message> = emptyList()

    // Flow订阅的Job
    private var chatLoadJob: Job? = null
    private var messagesLoadJob: Job? = null
    private var streamingObserveJob: Job? = null
    private var uiStateObserveJob: Job? = null

    init {
        uiStateObserveJob = viewModelScope.launch {
            combine(
                _historyMessages,
                _streamingMessage,
                _isLoadingHistory,
                _hasMoreHistory
            ) { messages, streamingMessage, isLoadingHistory, hasMoreHistory ->
                ChatUiState.Success(
                    messages = messages,
                    streamingMessage = streamingMessage,
                    isLoadingHistory = isLoadingHistory,
                    hasMoreHistory = hasMoreHistory
                )
            }.collect { state ->
                _uiState.value = state
            }
        }

        // 观察流式消息变化，只更新尾消息状态
        streamingObserveJob = viewModelScope.launch {
            combine(
                _actualChatId,
                messageSender.streamingMessages
            ) { chatId, streamingMap ->
                chatId?.let(streamingMap::get)
            }.collect { message ->
                _streamingMessage.value = message
            }
        }

        // 观察错误
        viewModelScope.launch {
            messageSender.errors.collect { error ->
                if (error != null) {
                    val (chatId, throwable) = error
                    if (chatId == currentChatId || chatId == _actualChatId.value) {
                        // 显示错误信息给用户
                        _errorMessage.value = throwable.toUserFriendlyMessage()
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
        messageSender.setConfig(currentChatId ?: _actualChatId.value, config)
    }

    /**
     * 设置可用的工具
     */
    fun setTools(
        tools: List<Tool>,
        systemPrompt: String? = null,
        modelName: String? = null,
        providerId: String? = null,
        shouldRecordTokens: Boolean = true,
        regexRules: List<RegexRuleData> = emptyList(),
        enabledSkillIds: List<String> = emptyList(),
        contextMessageSize: Int = 64
    ) {
        val config = ChatConfig(
            systemPrompt = systemPrompt,
            tools = tools,
            modelName = modelName,
            providerId = providerId,
            shouldRecordTokens = shouldRecordTokens,
            regexRules = regexRules,
            enabledSkillIds = enabledSkillIds,
            contextMessageSize = contextMessageSize
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
            resetMessageState()
            return
        }

        attachChat(chatId)
    }

    /**
     * 发送消息
     * @param chatId 聊天ID，null表示新建聊天
     */
    fun sendMessage(
        chatId: String?,
        content: String,
        mediaParts: List<MessagePart> = emptyList(),
        replyToMessageId: String? = null,
        replyPreview: String? = null
    ) {
        if (chatId == null) {
            // 懒创建模式
            messageSender.sendMessageToNewChat(
                content = content,
                config = _chatConfig.value,
                mediaParts = mediaParts,
                replyToMessageId = replyToMessageId,
                replyPreview = replyPreview
            ) { newChatId ->
                // 聊天创建后，更新 ID
                if (_actualChatId.value == null) {
                    attachChat(newChatId)
                }
            }
        } else {
            // 已有聊天
            messageSender.sendMessage(
                chatId = chatId,
                content = content,
                config = _chatConfig.value,
                mediaParts = mediaParts,
                replyToMessageId = replyToMessageId,
                replyPreview = replyPreview
            )
        }
    }

    /**
     * 生成图片
     */
    fun generateImage(chatId: String?, prompt: String) {
        if (chatId == null) {
            messageSender.generateImageToNewChat(prompt, _chatConfig.value) { newChatId ->
                if (_actualChatId.value == null) {
                    attachChat(newChatId)
                }
            }
        } else {
            messageSender.generateImage(chatId, prompt, _chatConfig.value)
        }
    }

    fun loadMoreHistory() {
        val chatId = currentChatId ?: _actualChatId.value ?: return
        if (_isLoadingHistory.value || !_hasMoreHistory.value) return

        val beforeTimestamp = _historyMessages.value.firstOrNull()?.timestamp ?: return

        viewModelScope.launch {
            _isLoadingHistory.value = true
            try {
                val olderPage = repository.getMessagesBefore(chatId, beforeTimestamp, PAGE_SIZE)
                if (currentChatId != chatId && _actualChatId.value != chatId) {
                    return@launch
                }

                if (olderPage.isNotEmpty()) {
                    olderMessages = olderPage + olderMessages
                    publishHistoryMessages()
                }
                _hasMoreHistory.value = olderPage.size >= PAGE_SIZE
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "加载历史消息失败"
            } finally {
                _isLoadingHistory.value = false
            }
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

    /**
     * 删除消息
     */
    fun deleteMessage(messageId: String) {
        viewModelScope.launch {
            repository.deleteMessage(messageId)
        }
    }

    fun toggleBookmark(message: Message) {
        viewModelScope.launch {
            repository.updateMessageBookmarked(message.id, !message.isBookmarked)
        }
    }

    fun createBranchFromMessage(messageId: String, onCreated: (String) -> Unit) {
        viewModelScope.launch {
            when (val result = repository.createBranchFromMessage(messageId)) {
                is com.tchat.core.util.Result.Success -> {
                    attachChat(result.data.id)
                    onCreated(result.data.id)
                }
                is com.tchat.core.util.Result.Error -> {
                    _errorMessage.value = result.exception.message ?: "创建分支失败"
                }
                is com.tchat.core.util.Result.Loading -> Unit
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        chatLoadJob?.cancel()
        messagesLoadJob?.cancel()
        streamingObserveJob?.cancel()
        uiStateObserveJob?.cancel()
    }

    private fun attachChat(chatId: String) {
        currentChatId = chatId
        _actualChatId.value = chatId
        messageSender.setConfig(chatId, _chatConfig.value)
        resetMessageState()

        chatLoadJob?.cancel()
        chatLoadJob = viewModelScope.launch {
            repository.getChatById(chatId).collect { chat ->
                _currentChat.value = chat
            }
        }

        messagesLoadJob?.cancel()
        messagesLoadJob = viewModelScope.launch {
            repository.observeRecentMessages(chatId, PAGE_SIZE).collect { messages ->
                if (currentChatId != chatId && _actualChatId.value != chatId) {
                    return@collect
                }

                recentMessages = messages
                if (olderMessages.isEmpty()) {
                    _hasMoreHistory.value = messages.size >= PAGE_SIZE
                }
                publishHistoryMessages()
            }
        }
    }

    private fun publishHistoryMessages() {
        _historyMessages.value = if (olderMessages.isEmpty()) {
            recentMessages
        } else {
            buildList(olderMessages.size + recentMessages.size) {
                addAll(olderMessages)
                addAll(recentMessages)
            }
        }
    }

    private fun resetMessageState() {
        olderMessages = emptyList()
        recentMessages = emptyList()
        _historyMessages.value = emptyList()
        _streamingMessage.value = null
        _isLoadingHistory.value = false
        _hasMoreHistory.value = false
    }

    private companion object {
        const val PAGE_SIZE = 100
    }
}

private fun Throwable.toUserFriendlyMessage(): String {
    return when (this) {
        is AIProviderException.AuthenticationError -> {
            "API Key 或 Gateway Key 无效，请检查服务配置后再试。"
        }
        is AIProviderException.RateLimitError -> {
            if (message?.contains("quota", ignoreCase = true) == true ||
                message?.contains("余额", ignoreCase = true) == true
            ) {
                "套餐余额不足，请续费后继续使用。"
            } else {
                "请求过于频繁，请稍后再试，或更换可用 Key。"
            }
        }
        is AIProviderException.InvalidRequestError -> {
            val raw = message.orEmpty()
            when {
                raw.contains("model", ignoreCase = true) || raw.contains("模型") ->
                    "当前模型暂不可用，请换一个模型或更新模型列表。"
                raw.contains("endpoint", ignoreCase = true) || raw.contains("端点") ->
                    "API 端点或路径不正确，请打开服务设置检查 Base URL 与 API Path。"
                else -> "请求参数不被当前服务接受，请检查模型和服务配置。"
            }
        }
        is AIProviderException.ServiceUnavailableError -> {
            "模型服务暂时不可用，请稍后再试，或换一个服务商。"
        }
        is AIProviderException.NetworkError -> {
            "网络连通失败，请检查网络、代理或稍后再试。"
        }
        else -> {
            message?.takeIf { it.isNotBlank() } ?: "发送消息失败，请稍后再试。"
        }
    }
}
