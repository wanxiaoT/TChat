package com.tchat.feature.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tchat.data.MessageSender
import com.tchat.data.model.*
import com.tchat.data.repository.ChatConfig
import com.tchat.data.repository.ChatRepository
import com.tchat.data.repository.GroupChatRepository
import com.tchat.data.tool.Tool
import com.tchat.data.util.RegexRuleData
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * 群聊 ViewModel
 * 管理群聊对话状态和多助手轮流回复逻辑
 */
class GroupChatViewModel(
    private val chatRepository: ChatRepository,
    private val groupChatRepository: GroupChatRepository,
    private val messageSender: MessageSender
) : ViewModel() {

    private val _uiState = MutableStateFlow<ChatUiState>(ChatUiState.Success(emptyList()))
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private val _currentGroupChat = MutableStateFlow<GroupChat?>(null)
    val currentGroupChat: StateFlow<GroupChat?> = _currentGroupChat.asStateFlow()

    private val _groupMembers = MutableStateFlow<List<GroupMemberConfig>>(emptyList())
    val groupMembers: StateFlow<List<GroupMemberConfig>> = _groupMembers.asStateFlow()

    // 当前正在发言的助手ID
    private val _currentSpeakerId = MutableStateFlow<String?>(null)
    val currentSpeakerId: StateFlow<String?> = _currentSpeakerId.asStateFlow()

    private val _autoModeRunning = MutableStateFlow(false)
    val autoModeRunning: StateFlow<Boolean> = _autoModeRunning.asStateFlow()

    private val _autoModeRemainingRounds = MutableStateFlow(0)
    val autoModeRemainingRounds: StateFlow<Int> = _autoModeRemainingRounds.asStateFlow()

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

    // 每个群成员对应的助手配置
    private val _assistantConfigs = MutableStateFlow<Map<String, ChatConfig>>(emptyMap())

    private var currentGroupChatId: String? = null
    private var currentChatId: String? = null
    private var olderMessages: List<Message> = emptyList()
    private var recentMessages: List<Message> = emptyList()
    private var autoModeJob: Job? = null

    // Flow订阅的Job
    private var groupLoadJob: Job? = null
    private var chatLoadJob: Job? = null
    private var membersLoadJob: Job? = null
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
                        _errorMessage.value = throwable.message ?: "发送消息失败"
                    }
                    messageSender.clearError()
                }
            }
        }
    }

    /**
     * 加载群聊
     * @param groupChatId 群聊ID
     * @param chatId 关联的聊天ID，null表示新建聊天（懒创建模式）
     */
    fun loadGroupChat(groupChatId: String, chatId: String? = null) {
        if (currentGroupChatId == groupChatId && currentChatId == chatId) return

        // 取消之前的数据库订阅
        groupLoadJob?.cancel()
        chatLoadJob?.cancel()
        membersLoadJob?.cancel()
        messagesLoadJob?.cancel()

        currentGroupChatId = groupChatId
        currentChatId = chatId

        // 加载群聊信息
        groupLoadJob = viewModelScope.launch {
            groupChatRepository.getGroupByIdFlow(groupChatId).collect { group ->
                _currentGroupChat.value = group

                val persistedChatId = group?.activeChatId
                if (currentChatId == null && _actualChatId.value == null && !persistedChatId.isNullOrBlank()) {
                    attachChat(persistedChatId)
                }
            }
        }

        viewModelScope.launch {
            messageSender.completedMessages.collect { message ->
                if (message?.role == MessageRole.ASSISTANT) {
                    scheduleAutoModeContinuation(message)
                }
            }
        }

        // 加载群聊成员
        membersLoadJob = viewModelScope.launch {
            groupChatRepository.getMembersFlow(groupChatId).collect { members ->
                _groupMembers.value = members
            }
        }

        // 如果有关联的聊天ID，加载聊天消息
        if (chatId != null) {
            attachChat(chatId)
        } else {
            // 新建聊天模式：显示空消息列表
            currentChatId = null
            _actualChatId.value = null
            resetMessageState()
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
     * 设置群成员各自的配置
     */
    fun setAssistantConfigs(configs: Map<String, ChatConfig>) {
        _assistantConfigs.value = configs
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
     * 发送用户消息并触发助手回复
     * @param chatId 聊天ID，null表示新建聊天
     * @param content 用户消息内容
     * @param selectedAssistantId 手动模式下选择的助手ID（可选）
     */
    fun sendMessage(
        chatId: String?,
        content: String,
        selectedAssistantId: String? = null,
        mediaParts: List<MessagePart> = emptyList()
    ) {
        val groupChat = _currentGroupChat.value ?: return
        val groupChatId = currentGroupChatId ?: return

        viewModelScope.launch {
            try {
                // 1. 根据激活策略选择下一个发言的助手
                val nextSpeakerId = when {
                    // 手动模式：使用用户选择的助手
                    selectedAssistantId != null -> selectedAssistantId
                    // 其他模式：使用策略自动选择
                    else -> groupChatRepository.selectNextSpeaker(
                        groupId = groupChatId,
                        lastSpeakerId = _currentSpeakerId.value,
                        userMessage = content
                    )
                } ?: run {
                    _errorMessage.value = "没有可用的助手"
                    return@launch
                }

                _currentSpeakerId.value = nextSpeakerId
                val resolvedConfig = resolveChatConfig(nextSpeakerId)

                // 2. 发送用户消息和助手回复
                if (chatId == null) {
                    // 懒创建模式
                    messageSender.sendMessageToNewChat(content, resolvedConfig, mediaParts) { newChatId ->
                        if (_actualChatId.value == null) {
                            attachChat(newChatId)
                        }
                        viewModelScope.launch {
                            groupChatRepository.updateActiveChatId(groupChatId, newChatId)
                        }
                    }
                } else {
                    // 已有聊天
                    currentChatId = chatId
                    _actualChatId.value = chatId
                    groupChatRepository.updateActiveChatId(groupChatId, chatId)
                    messageSender.sendMessage(chatId, content, resolvedConfig, mediaParts)
                }

                groupChatRepository.updateLastActiveTime(groupChatId)

                // 3. 如果启用了自动模式，可以继续触发下一个助手
                if (groupChat.autoModeEnabled) {
                    _autoModeRunning.value = true
                    _autoModeRemainingRounds.value = MAX_AUTO_MODE_ROUNDS
                }
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "发送消息失败"
            }
        }
    }

    /**
     * 生成图片（使用当前服务商能力）
     *
     * 注：该能力与“群聊多个助手轮流发言”逻辑解耦，这里仅在当前 chatId 上追加生成结果。
     */
    fun generateImage(chatId: String?, prompt: String, selectedAssistantId: String? = null) {
        val groupChatId = currentGroupChatId ?: return
        val trimmed = prompt.trim()
        if (trimmed.isBlank()) return

        viewModelScope.launch {
            try {
                val nextSpeakerId = when {
                    selectedAssistantId != null -> selectedAssistantId
                    else -> groupChatRepository.selectNextSpeaker(
                        groupId = groupChatId,
                        lastSpeakerId = _currentSpeakerId.value,
                        userMessage = trimmed
                    )
                } ?: run {
                    _errorMessage.value = "没有可用的助手"
                    return@launch
                }

                _currentSpeakerId.value = nextSpeakerId
                val resolvedConfig = resolveChatConfig(nextSpeakerId)

                if (chatId == null) {
                    messageSender.generateImageToNewChat(trimmed, resolvedConfig) { newChatId ->
                        if (_actualChatId.value == null) {
                            attachChat(newChatId)
                        }
                        viewModelScope.launch {
                            groupChatRepository.updateActiveChatId(groupChatId, newChatId)
                        }
                    }
                } else {
                    currentChatId = chatId
                    _actualChatId.value = chatId
                    groupChatRepository.updateActiveChatId(groupChatId, chatId)
                    messageSender.generateImage(chatId, trimmed, resolvedConfig)
                }

                groupChatRepository.updateLastActiveTime(groupChatId)
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "生成图片失败"
            }
        }
    }

    /**
     * 手动选择助手发言（仅在手动模式下使用）
     */
    fun selectAssistant(assistantId: String) {
        _currentSpeakerId.value = assistantId
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
                val assistantId = (_uiState.value as? ChatUiState.Success)
                    ?.messages
                    ?.firstOrNull { it.id == aiMessageId }
                    ?.groupMetadata
                    ?.assistantId
            ?: _currentSpeakerId.value

        if (assistantId != null) {
            _currentSpeakerId.value = assistantId
        }

        messageSender.regenerateMessage(
            chatId,
            userMessageId,
            aiMessageId,
            resolveChatConfig(assistantId)
        )
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

    fun pauseAutoMode() {
        autoModeJob?.cancel()
        autoModeJob = null
        _autoModeRunning.value = false
    }

    fun resumeAutoMode() {
        val groupChat = _currentGroupChat.value ?: return
        if (!groupChat.autoModeEnabled) return
        _autoModeRemainingRounds.value = MAX_AUTO_MODE_ROUNDS
        _autoModeRunning.value = true
        (_uiState.value as? ChatUiState.Success)
            ?.messages
            ?.lastOrNull { it.role == MessageRole.ASSISTANT && it.groupMetadata?.groupId == groupChat.id }
            ?.let(::scheduleAutoModeContinuation)
    }

    /**
     * 删除消息
     */
    fun deleteMessage(messageId: String) {
        viewModelScope.launch {
            chatRepository.deleteMessage(messageId)
        }
    }

    fun toggleBookmark(message: Message) {
        viewModelScope.launch {
            chatRepository.updateMessageBookmarked(message.id, !message.isBookmarked)
        }
    }

    fun loadMoreHistory() {
        val chatId = currentChatId ?: _actualChatId.value ?: return
        if (_isLoadingHistory.value || !_hasMoreHistory.value) return

        val beforeTimestamp = _historyMessages.value.firstOrNull()?.timestamp ?: return

        viewModelScope.launch {
            _isLoadingHistory.value = true
            try {
                val olderPage = chatRepository.getMessagesBefore(chatId, beforeTimestamp, PAGE_SIZE)
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

    override fun onCleared() {
        super.onCleared()
        groupLoadJob?.cancel()
        chatLoadJob?.cancel()
        membersLoadJob?.cancel()
        messagesLoadJob?.cancel()
        streamingObserveJob?.cancel()
        uiStateObserveJob?.cancel()
        autoModeJob?.cancel()
    }

    private fun scheduleAutoModeContinuation(completedMessage: Message) {
        val groupChat = _currentGroupChat.value ?: return
        val groupChatId = currentGroupChatId ?: return
        val chatId = currentChatId ?: _actualChatId.value ?: return
        val metadata = completedMessage.groupMetadata ?: return
        if (metadata.groupId != groupChatId || groupChat.id != groupChatId) return
        if (!groupChat.autoModeEnabled || !_autoModeRunning.value) return
        if (_autoModeRemainingRounds.value <= 0) {
            _autoModeRunning.value = false
            return
        }

        autoModeJob?.cancel()
        autoModeJob = viewModelScope.launch {
            delay(groupChat.autoModeDelay.coerceAtLeast(1) * 1000L)
            if (!_autoModeRunning.value || messageSender.isSending(chatId)) return@launch

            val nextSpeakerId = groupChatRepository.selectNextSpeaker(
                groupId = groupChatId,
                lastSpeakerId = metadata.assistantId,
                userMessage = AUTO_MODE_PROMPT
            ) ?: groupChatRepository.getEnabledMembers(groupChatId)
                .firstOrNull { it.assistantId != metadata.assistantId }
                ?.assistantId
                ?: return@launch

            _currentSpeakerId.value = nextSpeakerId
            _autoModeRemainingRounds.value = (_autoModeRemainingRounds.value - 1).coerceAtLeast(0)
            messageSender.sendMessage(
                chatId = chatId,
                content = AUTO_MODE_PROMPT,
                config = resolveChatConfig(nextSpeakerId)
            )
            groupChatRepository.updateLastActiveTime(groupChatId)
        }
    }

    private fun attachChat(chatId: String) {
        currentChatId = chatId
        _actualChatId.value = chatId
        messageSender.setConfig(chatId, _chatConfig.value)
        resetMessageState()

        messagesLoadJob?.cancel()
        messagesLoadJob = viewModelScope.launch {
            chatRepository.observeRecentMessages(chatId, PAGE_SIZE).collect { messages ->
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

    private fun resolveChatConfig(assistantId: String?): ChatConfig? {
        val baseConfig = _chatConfig.value
        val assistantConfig = assistantId?.let { _assistantConfigs.value[it] }

        if (baseConfig == null) return assistantConfig
        if (assistantConfig == null) return baseConfig

        return ChatConfig(
            systemPrompt = mergeSystemPrompt(
                assistantConfig.systemPrompt,
                baseConfig.systemPrompt
            ),
            tools = (baseConfig.tools + assistantConfig.tools)
                .distinctBy { it.name },
            temperature = assistantConfig.temperature ?: baseConfig.temperature,
            topP = assistantConfig.topP ?: baseConfig.topP,
            maxTokens = assistantConfig.maxTokens ?: baseConfig.maxTokens,
            modelName = baseConfig.modelName ?: assistantConfig.modelName,
            providerId = baseConfig.providerId ?: assistantConfig.providerId,
            shouldRecordTokens = baseConfig.shouldRecordTokens,
            regexRules = (baseConfig.regexRules + assistantConfig.regexRules)
                .distinctBy { Triple(it.pattern, it.replacement, it.order) },
            enabledSkillIds = (baseConfig.enabledSkillIds + assistantConfig.enabledSkillIds)
                .distinct(),
            contextMessageSize = assistantConfig.contextMessageSize,
            groupMetadata = assistantConfig.groupMetadata?.copy(
                generationId = java.util.UUID.randomUUID().toString()
            ) ?: baseConfig.groupMetadata
        )
    }

    private fun mergeSystemPrompt(primary: String?, secondary: String?): String? {
        val prompts = listOf(primary, secondary)
            .mapNotNull { it?.trim()?.takeIf(String::isNotEmpty) }
        return prompts.takeIf { it.isNotEmpty() }?.joinToString("\n\n")
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
        const val MAX_AUTO_MODE_ROUNDS = 6
        const val AUTO_MODE_PROMPT = "[自动模式] 请作为下一位群聊成员，基于上文继续推进讨论。"
    }
}
