package com.tchat.data.tts

import android.content.Context
import android.media.AudioAttributes
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference
import java.util.Locale
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

private const val TAG = "TtsService"

/**
 * TTS 引擎类型
 */
enum class TtsEngineType {
    SYSTEM,  // 系统 TTS 引擎
    DOUBAO   // 豆包 TTS（火山引擎）
}

/**
 * TTS 引擎信息
 */
data class TtsEngineInfo(
    val name: String,       // 显示名称
    val packageName: String, // 包名
    val isDefault: Boolean = false
)

/**
 * TTS 服务
 * 封装 Android TextToSpeech API 和豆包 TTS，提供语音朗读功能
 */
class TtsService private constructor(context: Context) {

    private val appContext: Context = context.applicationContext
    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private var initFailed = false
    private var currentEnginePackage: String = ""
    private val initSequence = AtomicInteger(0)

    @Volatile
    private var contextRef: WeakReference<Context> = WeakReference(context)

    // 豆包 TTS 服务
    private val doubaoTtsService: DoubaoTtsService by lazy {
        DoubaoTtsService(appContext.cacheDir)
    }

    // 当前引擎类型
    private var currentEngineType: TtsEngineType = TtsEngineType.SYSTEM

    // 豆包 TTS 配置
    private var doubaoAppId: String = ""
    private var doubaoAccessToken: String = ""
    private var doubaoCluster: String = "volcano_tts"
    private var doubaoVoiceType: String = "zh_female_tianmei_moon_bigtts"

    // 协程作用域
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // TTS 状态
    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    // 初始化状态
    private val _initStatus = MutableStateFlow<InitStatus>(InitStatus.Initializing)
    val initStatus: StateFlow<InitStatus> = _initStatus.asStateFlow()

    // 可用引擎列表
    private val _availableEngines = MutableStateFlow<List<TtsEngineInfo>>(emptyList())
    val availableEngines: StateFlow<List<TtsEngineInfo>> = _availableEngines.asStateFlow()

    // 当前朗读的消息ID
    private val _currentMessageId = MutableStateFlow<String?>(null)
    val currentMessageId: StateFlow<String?> = _currentMessageId.asStateFlow()

    // 当前系统引擎朗读的 utteranceId（用于过滤回调）
    @Volatile
    private var currentUtteranceId: String? = null

    private val systemUtteranceListener = object : UtteranceProgressListener() {
        private fun isCurrent(callbackUtteranceId: String?): Boolean {
            val active = currentUtteranceId ?: return false
            return callbackUtteranceId == null || callbackUtteranceId == active
        }

        override fun onStart(utteranceId: String?) {
            if (!isCurrent(utteranceId)) return
            Log.i(TAG, "TTS 开始朗读: $utteranceId")
            _isSpeaking.value = true
        }

        override fun onDone(utteranceId: String?) {
            if (!isCurrent(utteranceId)) return
            Log.i(TAG, "TTS 朗读完成: $utteranceId")
            _isSpeaking.value = false
            _currentMessageId.value = null
            currentUtteranceId = null
        }

        @Suppress("OVERRIDE_DEPRECATION")
        @Deprecated("Deprecated in Java", ReplaceWith("onError(utteranceId, errorCode)"))
        override fun onError(utteranceId: String?) {
            if (!isCurrent(utteranceId)) return
            Log.e(TAG, "TTS 朗读错误: $utteranceId")
            _isSpeaking.value = false
            _currentMessageId.value = null
            currentUtteranceId = null
        }

        override fun onError(utteranceId: String?, errorCode: Int) {
            if (!isCurrent(utteranceId)) return
            Log.e(TAG, "TTS 朗读错误: $utteranceId, errorCode=$errorCode")
            _isSpeaking.value = false
            _currentMessageId.value = null
            currentUtteranceId = null
        }

        override fun onStop(utteranceId: String?, interrupted: Boolean) {
            if (!isCurrent(utteranceId)) return
            Log.i(TAG, "TTS 停止朗读: $utteranceId, interrupted=$interrupted")
            _isSpeaking.value = false
            _currentMessageId.value = null
            currentUtteranceId = null
        }
    }

    private fun updateContext(context: Context) {
        contextRef = WeakReference(context)
    }

    private fun resolveTtsContext(): Context {
        return contextRef.get() ?: appContext
    }

    private fun resolveAlternateContext(used: Context): Context? {
        val alt = if (used === appContext) contextRef.get() else appContext
        return alt?.takeIf { it !== used }
    }

    // 豆包 TTS 错误信息
    val doubaoLastError: StateFlow<String?> get() = doubaoTtsService.lastError

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
        initTts(null)
        // 监听豆包 TTS 状态
        scope.launch {
            doubaoTtsService.isSpeaking.collect { speaking ->
                if (currentEngineType == TtsEngineType.DOUBAO) {
                    _isSpeaking.value = speaking
                }
            }
        }
        scope.launch {
            doubaoTtsService.currentMessageId.collect { messageId ->
                if (currentEngineType == TtsEngineType.DOUBAO) {
                    _currentMessageId.value = messageId
                }
            }
        }
    }

    /**
     * 初始化 TTS 引擎
     * @param enginePackage 引擎包名，null 或空字符串表示使用系统默认
     */
    private fun initTts(enginePackage: String?) {
        val sequence = initSequence.incrementAndGet()
        initTtsInternal(
            enginePackage = enginePackage,
            sequence = sequence,
            preferredContext = resolveTtsContext(),
            allowFallbackToDefaultEngine = true,
            allowFallbackToAlternateContext = true
        )
    }

    private fun initTtsInternal(
        enginePackage: String?,
        sequence: Int,
        preferredContext: Context,
        allowFallbackToDefaultEngine: Boolean,
        allowFallbackToAlternateContext: Boolean
    ) {
        _initStatus.value = InitStatus.Initializing
        isInitialized = false
        currentUtteranceId = null
        _isSpeaking.value = false
        _currentMessageId.value = null

        val desiredEngine = enginePackage?.takeIf { it.isNotBlank() }
        currentEnginePackage = desiredEngine ?: ""

        // 先关闭旧的 TTS 实例
        val old = tts
        tts = null
        try {
            old?.stop()
            old?.shutdown()
        } catch (e: Exception) {
            Log.w(TAG, "关闭旧 TTS 实例失败", e)
        }

        val holder = AtomicReference<TextToSpeech?>(null)
        val initListener = TextToSpeech.OnInitListener { status ->
            val ttsInstance = holder.get()
            if (sequence != initSequence.get()) {
                try {
                    ttsInstance?.shutdown()
                } catch (_: Exception) {
                }
                return@OnInitListener
            }

            if (status == TextToSpeech.SUCCESS && ttsInstance != null) {
                isInitialized = true
                initFailed = false

                val languageResult = ttsInstance.setLanguage(language)
                if (languageResult == TextToSpeech.LANG_MISSING_DATA || languageResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                    ttsInstance.setLanguage(Locale.getDefault())
                }

                ttsInstance.setSpeechRate(speechRate)
                ttsInstance.setPitch(pitch)
                ttsInstance.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                ttsInstance.setOnUtteranceProgressListener(systemUtteranceListener)

                // 获取可用引擎列表
                refreshAvailableEngines()

                _initStatus.value = InitStatus.Ready
                Log.i(
                    TAG,
                    "TTS 引擎初始化成功: engine=${currentEnginePackage.ifBlank { "<default>" }}, context=${preferredContext::class.java.name}"
                )
                return@OnInitListener
            }

            isInitialized = false
            initFailed = true
            val engineLabel = currentEnginePackage.ifBlank { "<default>" }
            val reason = "TTS 引擎初始化失败: status=$status, engine=$engineLabel"
            _initStatus.value = InitStatus.Failed(reason)
            Log.e(TAG, "$reason, context=${preferredContext::class.java.name}")

            try {
                ttsInstance?.shutdown()
            } catch (_: Exception) {
            }

            // 指定引擎失败时，自动回退系统默认引擎
            if (allowFallbackToDefaultEngine && desiredEngine != null) {
                val nextSeq = initSequence.incrementAndGet()
                Log.w(TAG, "指定引擎初始化失败，回退系统默认引擎重试")
                initTtsInternal(
                    enginePackage = null,
                    sequence = nextSeq,
                    preferredContext = preferredContext,
                    allowFallbackToDefaultEngine = false,
                    allowFallbackToAlternateContext = allowFallbackToAlternateContext
                )
                return@OnInitListener
            }

            // 默认引擎仍失败时，尝试使用另一种 context（appContext <-> latest context）
            if (allowFallbackToAlternateContext) {
                val altContext = resolveAlternateContext(preferredContext)
                if (altContext != null) {
                    val nextSeq = initSequence.incrementAndGet()
                    Log.w(TAG, "TTS 初始化失败，使用备用 Context 重试: ${altContext::class.java.name}")
                    initTtsInternal(
                        enginePackage = enginePackage,
                        sequence = nextSeq,
                        preferredContext = altContext,
                        allowFallbackToDefaultEngine = allowFallbackToDefaultEngine,
                        allowFallbackToAlternateContext = false
                    )
                }
            }
        }

        try {
            Log.i(
                TAG,
                "开始初始化 TTS: engine=${currentEnginePackage.ifBlank { "<default>" }}, context=${preferredContext::class.java.name}, seq=$sequence"
            )
            val ttsInstance = if (desiredEngine == null) {
                TextToSpeech(preferredContext, initListener)
            } else {
                TextToSpeech(preferredContext, initListener, desiredEngine)
            }
            holder.set(ttsInstance)
            tts = ttsInstance
        } catch (e: Exception) {
            isInitialized = false
            initFailed = true
            val engineLabel = currentEnginePackage.ifBlank { "<default>" }
            val reason = "TTS 初始化异常: ${e.message ?: e.javaClass.simpleName}, engine=$engineLabel"
            _initStatus.value = InitStatus.Failed(reason)
            Log.e(TAG, reason, e)

            // 构造异常时也尝试回退（保持行为一致）
            if (allowFallbackToDefaultEngine && desiredEngine != null) {
                val nextSeq = initSequence.incrementAndGet()
                Log.w(TAG, "指定引擎初始化异常，回退系统默认引擎重试")
                initTtsInternal(
                    enginePackage = null,
                    sequence = nextSeq,
                    preferredContext = preferredContext,
                    allowFallbackToDefaultEngine = false,
                    allowFallbackToAlternateContext = allowFallbackToAlternateContext
                )
                return
            }
            if (allowFallbackToAlternateContext) {
                val altContext = resolveAlternateContext(preferredContext)
                if (altContext != null) {
                    val nextSeq = initSequence.incrementAndGet()
                    Log.w(TAG, "TTS 初始化异常，使用备用 Context 重试: ${altContext::class.java.name}")
                    initTtsInternal(
                        enginePackage = enginePackage,
                        sequence = nextSeq,
                        preferredContext = altContext,
                        allowFallbackToDefaultEngine = allowFallbackToDefaultEngine,
                        allowFallbackToAlternateContext = false
                    )
                }
            }
        }
    }

    /**
     * 刷新可用引擎列表
     */
    private fun refreshAvailableEngines() {
        try {
            val engines = tts?.engines ?: emptyList()
            val defaultEngine = tts?.defaultEngine ?: ""

            val engineList = mutableListOf<TtsEngineInfo>()

            // 添加"系统默认"选项
            engineList.add(TtsEngineInfo(
                name = "系统默认",
                packageName = "",
                isDefault = true
            ))

            // 添加所有可用引擎
            engines.forEach { engine ->
                val displayName = getEngineDisplayName(engine.name, engine.label)
                engineList.add(TtsEngineInfo(
                    name = displayName,
                    packageName = engine.name,
                    isDefault = engine.name == defaultEngine
                ))
            }

            _availableEngines.value = engineList
        } catch (e: Exception) {
            // 如果获取失败，至少提供系统默认选项
            _availableEngines.value = listOf(
                TtsEngineInfo(name = "系统默认", packageName = "", isDefault = true)
            )
        }
    }

    /**
     * 获取引擎显示名称
     */
    private fun getEngineDisplayName(packageName: String, label: String): String {
        // 常见引擎的友好名称
        return when {
            packageName.contains("google") -> "Google 文字转语音"
            packageName.contains("iflytek") || packageName.contains("xunfei") -> "讯飞语音"
            packageName.contains("samsung") -> "三星 TTS"
            packageName.contains("huawei") -> "华为 TTS"
            packageName.contains("xiaomi") -> "小米 TTS"
            packageName.contains("baidu") -> "百度语音"
            packageName.contains("tencent") -> "腾讯语音"
            label.isNotBlank() -> label
            else -> packageName.substringAfterLast(".")
        }
    }

    /**
     * 切换系统 TTS 引擎
     * @param enginePackage 引擎包名，空字符串表示使用系统默认
     */
    fun setEngine(enginePackage: String) {
        if (enginePackage != currentEnginePackage) {
            initTts(enginePackage.ifBlank { null })
        }
    }

    /**
     * 获取当前系统引擎包名
     */
    fun getCurrentEngine(): String = currentEnginePackage

    /**
     * 设置引擎类型（系统 TTS 或豆包 TTS）
     */
    fun setEngineType(type: TtsEngineType) {
        currentEngineType = type
    }

    /**
     * 获取当前引擎类型
     */
    fun getEngineType(): TtsEngineType = currentEngineType

    /**
     * 更新豆包 TTS 配置
     */
    fun updateDoubaoConfig(
        appId: String,
        accessToken: String,
        cluster: String = "volcano_tts",
        voiceType: String = "zh_female_tianmei_moon_bigtts"
    ) {
        doubaoAppId = appId
        doubaoAccessToken = accessToken
        doubaoCluster = cluster
        doubaoVoiceType = voiceType
    }

    /**
     * 检查豆包 TTS 是否已配置
     */
    fun isDoubaoConfigured(): Boolean {
        return doubaoAppId.isNotBlank() && doubaoAccessToken.isNotBlank()
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
        if (text.isBlank()) return

        when (currentEngineType) {
            TtsEngineType.SYSTEM -> speakWithSystemTts(text, messageId)
            TtsEngineType.DOUBAO -> speakWithDoubaoTts(text, messageId)
        }
    }

    /**
     * 使用系统 TTS 朗读（synthesizeToFile + MediaPlayer 方式）
     */
    private fun speakWithSystemTts(text: String, messageId: String?) {
        if (!isInitialized) {
            Log.w(TAG, "TTS 未初始化")
            return
        }

        // 清理文本（移除 Markdown 格式等）
        val cleanText = cleanTextForTts(text)
        if (cleanText.isBlank()) return

        // 停止之前的朗读
        tts?.stop()

        _currentMessageId.value = messageId
        val utteranceId = messageId ?: UUID.randomUUID().toString()
        currentUtteranceId = utteranceId

        // 使用 speak() 直接朗读，避免不同 TTS 引擎生成的文件格式导致 MediaPlayer 无法播放
        tts?.setOnUtteranceProgressListener(systemUtteranceListener)
        val result = tts?.speak(cleanText, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
        if (result != TextToSpeech.SUCCESS) {
            Log.e(TAG, "speak 失败: result=$result")
            _isSpeaking.value = false
            _currentMessageId.value = null
            currentUtteranceId = null
        }
    }

    /**
     * 使用豆包 TTS 朗读
     */
    private fun speakWithDoubaoTts(text: String, messageId: String?) {
        if (!isDoubaoConfigured()) return

        scope.launch {
            doubaoTtsService.speak(
                text = text,
                appId = doubaoAppId,
                accessToken = doubaoAccessToken,
                cluster = doubaoCluster,
                voiceType = doubaoVoiceType,
                speedRatio = speechRate,
                messageId = messageId
            )
        }
    }

    /**
     * 追加朗读（不打断当前朗读）
     * 注意：使用 synthesizeToFile 方式时，此方法会等待当前播放完成
     */
    fun speakAdd(text: String, messageId: String? = null) {
        // 简化实现：如果正在播放，则忽略；否则直接播放
        if (_isSpeaking.value) {
            Log.i(TAG, "正在播放中，忽略追加请求")
            return
        }
        speak(text, messageId)
    }

    /**
     * 停止朗读
     */
    fun stop() {
        tts?.stop()
        doubaoTtsService.stop()
        _isSpeaking.value = false
        _currentMessageId.value = null
        currentUtteranceId = null
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
     * 清理临时文件
     */
    private fun cleanupTempFiles() {
        // 兼容旧版本：历史上使用 synthesizeToFile() 会产生临时文件
        // 目前系统 TTS 使用 speak() 直接输出，无需清理
    }

    /**
     * 释放资源
     */
    fun shutdown() {
        stop()
        tts?.shutdown()
        tts = null
        isInitialized = false
        instance = null
    }

    companion object {
        @Volatile
        private var instance: TtsService? = null

        fun getInstance(context: Context): TtsService {
            return instance?.also { it.updateContext(context) } ?: synchronized(this) {
                instance?.also { it.updateContext(context) } ?: TtsService(context).also { instance = it }
            }
        }

        fun getInstanceOrNull(): TtsService? = instance
    }
}
