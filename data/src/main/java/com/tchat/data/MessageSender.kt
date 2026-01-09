package com.tchat.data

import com.tchat.core.util.Result
import com.tchat.data.model.Message
import com.tchat.data.model.MessageRole
import com.tchat.data.repository.ChatConfig
import com.tchat.data.repository.ChatRepository
import com.tchat.data.tts.TtsService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 消息发送器（单例）
 *
 * 使用 Application 级别的 CoroutineScope，支持：
 * - 切换聊天时继续接收 AI 响应
 * - 多个聊天同时进行流式响应
 * - 工具调用循环
 * - TTS 语音朗读
 */
class MessageSender(
    private val scope: CoroutineScope
) {
    private var repository: ChatRepository? = null

    // 当前聊天配置
    private var currentConfig: ChatConfig? = null

    // 每个聊天的发送任务
    private val sendingJobs = mutableMapOf<String, Job>()

    // 每个聊天的流式消息状态 (chatId -> 当前流式消息)
    private val _streamingMessages = MutableStateFlow<Map<String, Message>>(emptyMap())
    val streamingMessages: StateFlow<Map<String, Message>> = _streamingMessages.asStateFlow()

    // 发送错误回调
    private val _errors = MutableStateFlow<Pair<String, Throwable>?>(null)
    val errors: StateFlow<Pair<String, Throwable>?> = _errors.asStateFlow()

    // 消息完成回调（用于 TTS 等功能）
    private val _completedMessages = MutableStateFlow<Message?>(null)
    val completedMessages: StateFlow<Message?> = _completedMessages.asStateFlow()

    // TTS 设置
    private var ttsEnabled: Boolean = false
    private var ttsAutoSpeak: Boolean = false

    fun init(repository: ChatRepository) {
        this.repository = repository
    }

    /**
     * 更新 TTS 设置
     */
    fun updateTtsSettings(enabled: Boolean, autoSpeak: Boolean) {
        ttsEnabled = enabled
        ttsAutoSpeak = autoSpeak
    }

    /**
     * 设置当前聊天配置（包含工具、系统提示等）
     */
    fun setConfig(config: ChatConfig?) {
        this.currentConfig = config
    }

    /**
     * 处理消息完成（触发 TTS 等）
     */
    private fun onMessageCompleted(message: Message) {
        _completedMessages.value = message

        // 如果启用了自动朗读，触发 TTS
        if (ttsEnabled && ttsAutoSpeak && message.role == MessageRole.ASSISTANT) {
            val textContent = message.getTextContent()
            if (textContent.isNotBlank()) {
                TtsService.getInstanceOrNull()?.speak(textContent, message.id)
            }
        }
    }

    /**
     * 清除已完成消息状态
     */
    fun clearCompletedMessage() {
        _completedMessages.value = null
    }

    /**
     * 发送消息到已有聊天
     */
    fun sendMessage(chatId: String, content: String, config: ChatConfig? = null) {
        val repo = repository ?: return
        val chatConfig = config ?: currentConfig

        // 取消同一个聊天的之前请求（避免重复发送）
        sendingJobs[chatId]?.cancel()

        sendingJobs[chatId] = scope.launch {
            repo.sendMessage(chatId, content, chatConfig).collect { result ->
                when (result) {
                    is Result.Success -> {
                        val message = result.data
                        if (message.role == MessageRole.ASSISTANT) {
                            if (message.isStreaming) {
                                // 更新流式消息状态
                                updateStreamingMessage(chatId, message)
                            } else {
                                // 流式结束，更新为最终消息状态（保留 toolResults 等信息）
                                // 不立即移除，让数据库 Flow 有时间更新
                                updateStreamingMessage(chatId, message)
                                // 触发消息完成回调（TTS 等）
                                onMessageCompleted(message)
                            }
                        }
                    }
                    is Result.Error -> {
                        removeStreamingMessage(chatId)
                        _errors.value = chatId to result.exception
                    }
                    is Result.Loading -> {}
                }
            }
            // 任务完成，移除流式消息状态并从 Map 移除
            removeStreamingMessage(chatId)
            sendingJobs.remove(chatId)
        }
    }

    /**
     * 发送消息到新聊天（懒创建）
     * @return 创建的 chatId
     */
    fun sendMessageToNewChat(content: String, config: ChatConfig? = null, onChatCreated: (String) -> Unit) {
        val repo = repository ?: return
        val chatConfig = config ?: currentConfig

        // 使用临时 ID 标识新聊天任务
        val tempId = "new_${System.currentTimeMillis()}"

        sendingJobs[tempId] = scope.launch {
            var realChatId: String? = null

            repo.sendMessageToNewChat(content, chatConfig).collect { result ->
                when (result) {
                    is Result.Success -> {
                        val messageResult = result.data
                        val message = messageResult.message

                        // 第一次收到消息时，获取真实的 chatId
                        if (realChatId == null) {
                            realChatId = messageResult.chatId
                            onChatCreated(messageResult.chatId)

                            // 将任务从临时 ID 移到真实 chatId
                            sendingJobs.remove(tempId)?.let {
                                sendingJobs[messageResult.chatId] = it
                            }
                        }

                        if (message.role == MessageRole.ASSISTANT) {
                            if (message.isStreaming) {
                                updateStreamingMessage(messageResult.chatId, message)
                            } else {
                                removeStreamingMessage(messageResult.chatId)
                                // 触发消息完成回调（TTS 等）
                                onMessageCompleted(message)
                            }
                        }
                    }
                    is Result.Error -> {
                        realChatId?.let { removeStreamingMessage(it) }
                        _errors.value = (realChatId ?: tempId) to result.exception
                    }
                    is Result.Loading -> {}
                }
            }

            // 任务完成
            realChatId?.let { sendingJobs.remove(it) }
            sendingJobs.remove(tempId)
        }
    }

    /**
     * 取消指定聊天的发送任务
     */
    fun cancelSending(chatId: String) {
        sendingJobs[chatId]?.cancel()
        sendingJobs.remove(chatId)
        removeStreamingMessage(chatId)
    }

    /**
     * 检查指定聊天是否正在发送
     */
    fun isSending(chatId: String): Boolean {
        return sendingJobs[chatId]?.isActive == true
    }

    /**
     * 重新生成 AI 回复
     */
    fun regenerateMessage(
        chatId: String,
        userMessageId: String,
        aiMessageId: String,
        config: ChatConfig? = null
    ) {
        val repo = repository ?: return
        val chatConfig = config ?: currentConfig

        // 取消同一个聊天的之前请求
        sendingJobs[chatId]?.cancel()

        sendingJobs[chatId] = scope.launch {
            repo.regenerateMessage(chatId, userMessageId, aiMessageId, chatConfig).collect { result ->
                when (result) {
                    is Result.Success -> {
                        val message = result.data
                        if (message.isStreaming) {
                            updateStreamingMessage(chatId, message)
                        } else {
                            removeStreamingMessage(chatId)
                            // 触发消息完成回调（TTS 等）
                            onMessageCompleted(message)
                        }
                    }
                    is Result.Error -> {
                        removeStreamingMessage(chatId)
                        _errors.value = chatId to result.exception
                    }
                    is Result.Loading -> {}
                }
            }
            sendingJobs.remove(chatId)
        }
    }

    /**
     * 选择消息的变体
     */
    fun selectVariant(messageId: String, variantIndex: Int) {
        val repo = repository ?: return
        scope.launch {
            repo.selectVariant(messageId, variantIndex)
        }
    }

    /**
     * 清除错误状态
     */
    fun clearError() {
        _errors.value = null
    }

    private fun updateStreamingMessage(chatId: String, message: Message) {
        val current = _streamingMessages.value.toMutableMap()
        current[chatId] = message
        _streamingMessages.value = current
    }

    private fun removeStreamingMessage(chatId: String) {
        val current = _streamingMessages.value.toMutableMap()
        current.remove(chatId)
        _streamingMessages.value = current
    }

    companion object {
        @Volatile
        private var instance: MessageSender? = null

        fun getInstance(scope: CoroutineScope): MessageSender {
            return instance ?: synchronized(this) {
                instance ?: MessageSender(scope).also { instance = it }
            }
        }

        fun getInstanceOrNull(): MessageSender? = instance
    }
}
