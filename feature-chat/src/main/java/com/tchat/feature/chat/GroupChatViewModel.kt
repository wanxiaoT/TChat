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

    private var currentGroupChatId: String? = null
    private var currentChatId: String? = null

    // Flow订阅的Job
    private var groupLoadJob: Job? = null
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
     * 加载群聊
     * @param groupChatId 群聊ID
     * @param chatId 关联的聊天ID，null表示新建聊天（懒创建模式）
     */
    fun loadGroupChat(groupChatId: String, chatId: String? = null) {
        if (currentGroupChatId == groupChatId && currentChatId == chatId) return

        // 取消之前的数据库订阅
        groupLoadJob?.cancel()
        chatLoadJob?.cancel()
        messagesLoadJob?.cancel()

        currentGroupChatId = groupChatId
        currentChatId = chatId

        // 加载群聊信息
        groupLoadJob = viewModelScope.launch {
            groupChatRepository.getGroupByIdFlow(groupChatId).collect { group ->
                _currentGroupChat.value = group
            }
        }

        // 加载群聊成员
        viewModelScope.launch {
            groupChatRepository.getMembersFlow(groupChatId).collect { members ->
                _groupMembers.value = members
            }
        }

        // 如果有关联的聊天ID，加载聊天消息
        if (chatId != null) {
            _actualChatId.value = chatId

            messagesLoadJob = viewModelScope.launch {
                // 从数据库加载消息
                chatRepository.getMessagesByChatId(chatId).collect { messages ->
                    _dbMessages.value = messages
                }
            }
        } else {
            // 新建聊天模式：显示空消息列表
            _actualChatId.value = null
            _dbMessages.value = emptyList()
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
    fun setTools(
        tools: List<Tool>,
        systemPrompt: String? = null,
        modelName: String? = null,
        regexRules: List<RegexRuleData> = emptyList()
    ) {
        val config = ChatConfig(
            systemPrompt = systemPrompt,
            tools = tools,
            modelName = modelName,
            regexRules = regexRules
        )
        setChatConfig(config)
    }

    /**
     * 发送用户消息并触发助手回复
     * @param chatId 聊天ID，null表示新建聊天
     * @param content 用户消息内容
     * @param selectedAssistantId 手动模式下选择的助手ID（可选）
     */
    fun sendMessage(chatId: String?, content: String, selectedAssistantId: String? = null) {
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

                // 2. 发送用户消息和助手回复
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
                                chatRepository.getMessagesByChatId(newChatId).collect { messages ->
                                    _dbMessages.value = messages
                                }
                            }
                        }
                    }
                } else {
                    // 已有聊天
                    messageSender.sendMessage(chatId, content, _chatConfig.value)
                }

                // 3. 如果启用了自动模式，可以继续触发下一个助手
                if (groupChat.autoModeEnabled) {
                    // TODO: 实现自动模式逻辑
                }
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "发送消息失败"
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
