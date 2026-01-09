package com.tchat.data.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.UUID

/**
 * TTS 服务
 * 封装 Android TextToSpeech API，提供语音朗读功能
 */
class TtsService private constructor(context: Context) {

    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private var initFailed = false

    // TTS 状态
    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    // 初始化状态
    private val _initStatus = MutableStateFlow<InitStatus>(InitStatus.Initializing)
    val initStatus: StateFlow<InitStatus> = _initStatus.asStateFlow()

    // 当前朗读的消息ID
    private val _currentMessageId = MutableStateFlow<String?>(null)
    val currentMessageId: StateFlow<String?> = _currentMessageId.asStateFlow()

    // TTS 设置
    private var speechRate: Float = 1.0f
    private var pitch: Float = 1.0f
    private var language: Locale = Locale.CHINESE

    sealed class InitStatus {
        data object Initializing : InitStatus()
        data object Ready : InitStatus()
        data class Failed(val reason: String) : InitStatus()
    }

    init {
        try {
            tts = TextToSpeech(context.applicationContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    isInitialized = true
                    initFailed = false

                    val result = tts?.setLanguage(language)
                    if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                        // 中文不支持，尝试使用默认语言
                        tts?.setLanguage(Locale.getDefault())
                    }

                    tts?.setSpeechRate(speechRate)
                    tts?.setPitch(pitch)

                    tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {
                            _isSpeaking.value = true
                        }

                        override fun onDone(utteranceId: String?) {
                            _isSpeaking.value = false
                            _currentMessageId.value = null
                        }

                        @Deprecated("Deprecated in Java", ReplaceWith("onError(utteranceId, errorCode)"))
                        override fun onError(utteranceId: String?) {
                            _isSpeaking.value = false
                            _currentMessageId.value = null
                        }

                        override fun onError(utteranceId: String?, errorCode: Int) {
                            _isSpeaking.value = false
                            _currentMessageId.value = null
                        }
                    })

                    _initStatus.value = InitStatus.Ready
                } else {
                    isInitialized = false
                    initFailed = true
                    _initStatus.value = InitStatus.Failed("TTS 引擎初始化失败，请检查系统是否安装了 TTS 引擎")
                }
            }
        } catch (e: Exception) {
            isInitialized = false
            initFailed = true
            _initStatus.value = InitStatus.Failed("TTS 初始化异常: ${e.message}")
        }
    }

    /**
     * 更新 TTS 设置
     */
    fun updateSettings(rate: Float, pitchValue: Float, locale: Locale) {
        speechRate = rate.coerceIn(0.1f, 3.0f)
        pitch = pitchValue.coerceIn(0.1f, 2.0f)
        language = locale

        tts?.let {
            it.setSpeechRate(speechRate)
            it.setPitch(pitch)
            it.language = language
        }
    }

    /**
     * 朗读文本
     * @param text 要朗读的文本
     * @param messageId 消息ID（用于追踪）
     */
    fun speak(text: String, messageId: String? = null) {
        if (!isInitialized || text.isBlank()) return

        // 清理文本（移除 Markdown 格式等）
        val cleanText = cleanTextForTts(text)
        if (cleanText.isBlank()) return

        _currentMessageId.value = messageId
        val utteranceId = messageId ?: UUID.randomUUID().toString()

        tts?.speak(cleanText, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }

    /**
     * 追加朗读（不打断当前朗读）
     */
    fun speakAdd(text: String, messageId: String? = null) {
        if (!isInitialized || text.isBlank()) return

        val cleanText = cleanTextForTts(text)
        if (cleanText.isBlank()) return

        val utteranceId = messageId ?: UUID.randomUUID().toString()
        tts?.speak(cleanText, TextToSpeech.QUEUE_ADD, null, utteranceId)
    }

    /**
     * 停止朗读
     */
    fun stop() {
        tts?.stop()
        _isSpeaking.value = false
        _currentMessageId.value = null
    }

    /**
     * 暂停朗读（API 21+）
     */
    fun pause() {
        // Android TTS 没有原生暂停功能，只能停止
        stop()
    }

    /**
     * 检查 TTS 是否可用
     */
    fun isAvailable(): Boolean = isInitialized

    /**
     * 获取可用的语言列表
     */
    fun getAvailableLanguages(): List<Locale> {
        return tts?.availableLanguages?.toList() ?: emptyList()
    }

    /**
     * 清理文本，移除不适合朗读的内容
     */
    private fun cleanTextForTts(text: String): String {
        return text
            // 移除代码块
            .replace(Regex("```[\\s\\S]*?```"), "代码块已省略。")
            // 移除行内代码
            .replace(Regex("`[^`]+`"), "")
            // 移除链接，保留链接文字
            .replace(Regex("\\[([^\\]]+)\\]\\([^)]+\\)"), "$1")
            // 移除图片
            .replace(Regex("!\\[([^\\]]*)\\]\\([^)]+\\)"), "图片：$1")
            // 移除 HTML 标签
            .replace(Regex("<[^>]+>"), "")
            // 移除多余的空白
            .replace(Regex("\\s+"), " ")
            // 移除 Markdown 标题符号
            .replace(Regex("^#{1,6}\\s*", RegexOption.MULTILINE), "")
            // 移除粗体/斜体标记
            .replace(Regex("\\*{1,2}([^*]+)\\*{1,2}"), "$1")
            .replace(Regex("_{1,2}([^_]+)_{1,2}"), "$1")
            // 移除删除线
            .replace(Regex("~~([^~]+)~~"), "$1")
            // 移除引用符号
            .replace(Regex("^>\\s*", RegexOption.MULTILINE), "")
            // 移除列表符号
            .replace(Regex("^[\\-*+]\\s+", RegexOption.MULTILINE), "")
            .replace(Regex("^\\d+\\.\\s+", RegexOption.MULTILINE), "")
            // 移除水平线
            .replace(Regex("^[-*_]{3,}$", RegexOption.MULTILINE), "")
            .trim()
    }

    /**
     * 释放资源
     */
    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isInitialized = false
        instance = null
    }

    companion object {
        @Volatile
        private var instance: TtsService? = null

        fun getInstance(context: Context): TtsService {
            return instance ?: synchronized(this) {
                instance ?: TtsService(context).also { instance = it }
            }
        }

        fun getInstanceOrNull(): TtsService? = instance
    }
}
