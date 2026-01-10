package com.tchat.data.tts

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
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
import java.io.File
import java.util.Locale
import java.util.UUID

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
 * 使用 synthesizeToFile() + MediaPlayer 方式，兼容性更好
 */
class TtsService private constructor(private val context: Context) {

    private var tts: TextToSpeech? = null
    private var mediaPlayer: MediaPlayer? = null
    private var isInitialized = false
    private var initFailed = false
    private var currentEnginePackage: String = ""

    // 临时音频文件目录
    private val tempDir: File by lazy {
        File(context.cacheDir, "tts_audio").also { it.mkdirs() }
    }

    // 豆包 TTS 服务
    private val doubaoTtsService: DoubaoTtsService by lazy {
        DoubaoTtsService(context.cacheDir)
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
        _initStatus.value = InitStatus.Initializing
        isInitialized = false

        // 先关闭旧的 TTS 实例
        tts?.stop()
        tts?.shutdown()
        tts = null

        try {
            val initListener = TextToSpeech.OnInitListener { status ->
                if (status == TextToSpeech.SUCCESS) {
                    isInitialized = true
                    initFailed = false

                    val result = tts?.setLanguage(language)
                    if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                        tts?.setLanguage(Locale.getDefault())
                    }

                    tts?.setSpeechRate(speechRate)
                    tts?.setPitch(pitch)

                    // 获取可用引擎列表
                    refreshAvailableEngines()

                    _initStatus.value = InitStatus.Ready
                    Log.i(TAG, "TTS 引擎初始化成功")
                } else {
                    isInitialized = false
                    initFailed = true
                    _initStatus.value = InitStatus.Failed("TTS 引擎初始化失败，请检查系统是否安装了 TTS 引擎")
                    Log.e(TAG, "TTS 引擎初始化失败: status=$status")
                }
            }

            // 根据是否指定引擎来创建 TTS 实例
            // 注意：某些设备/TTS引擎使用 applicationContext 会初始化失败，直接使用 context
            tts = if (enginePackage.isNullOrBlank()) {
                currentEnginePackage = ""
                TextToSpeech(context, initListener)
            } else {
                currentEnginePackage = enginePackage
                TextToSpeech(context, initListener, enginePackage)
            }
        } catch (e: Exception) {
            isInitialized = false
            initFailed = true
            _initStatus.value = InitStatus.Failed("TTS 初始化异常: ${e.message}")
            Log.e(TAG, "TTS 初始化异常", e)
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

        // 停止之前的播放
        stopMediaPlayer()

        _currentMessageId.value = messageId
        val utteranceId = messageId ?: UUID.randomUUID().toString()

        // 创建临时音频文件
        val audioFile = File(tempDir, "tts_${System.currentTimeMillis()}.wav")

        // 设置合成完成监听器
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                Log.i(TAG, "TTS 合成开始: $utteranceId")
            }

            override fun onDone(utteranceId: String?) {
                Log.i(TAG, "TTS 合成完成: $utteranceId")
                // 合成完成后，使用 MediaPlayer 播放
                scope.launch(Dispatchers.Main) {
                    playAudioFile(audioFile)
                }
            }

            @Deprecated("Deprecated in Java", ReplaceWith("onError(utteranceId, errorCode)"))
            override fun onError(utteranceId: String?) {
                Log.e(TAG, "TTS 合成错误: $utteranceId")
                _isSpeaking.value = false
                _currentMessageId.value = null
                audioFile.delete()
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                Log.e(TAG, "TTS 合成错误: $utteranceId, errorCode=$errorCode")
                _isSpeaking.value = false
                _currentMessageId.value = null
                audioFile.delete()
            }
        })

        // 使用 synthesizeToFile 合成到文件
        val result = tts?.synthesizeToFile(cleanText, null, audioFile, utteranceId)
        if (result != TextToSpeech.SUCCESS) {
            Log.e(TAG, "synthesizeToFile 失败: result=$result")
            _isSpeaking.value = false
            _currentMessageId.value = null
        }
    }

    /**
     * 播放音频文件
     */
    private fun playAudioFile(audioFile: File) {
        if (!audioFile.exists()) {
            Log.e(TAG, "音频文件不存在: ${audioFile.absolutePath}")
            _isSpeaking.value = false
            _currentMessageId.value = null
            return
        }

        try {
            stopMediaPlayer()

            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                setDataSource(audioFile.absolutePath)

                setOnPreparedListener {
                    Log.i(TAG, "MediaPlayer 准备完成，开始播放")
                    _isSpeaking.value = true
                    start()
                }

                setOnCompletionListener {
                    Log.i(TAG, "MediaPlayer 播放完成")
                    _isSpeaking.value = false
                    _currentMessageId.value = null
                    release()
                    mediaPlayer = null
                    audioFile.delete()
                }

                setOnErrorListener { _, what, extra ->
                    Log.e(TAG, "MediaPlayer 错误: what=$what, extra=$extra")
                    _isSpeaking.value = false
                    _currentMessageId.value = null
                    audioFile.delete()
                    true
                }

                prepareAsync()
            }
        } catch (e: Exception) {
            Log.e(TAG, "播放音频文件失败", e)
            _isSpeaking.value = false
            _currentMessageId.value = null
            audioFile.delete()
        }
    }

    /**
     * 停止 MediaPlayer
     */
    private fun stopMediaPlayer() {
        mediaPlayer?.let {
            try {
                if (it.isPlaying) {
                    it.stop()
                }
                it.release()
            } catch (e: Exception) {
                Log.w(TAG, "停止 MediaPlayer 时出错", e)
            }
        }
        mediaPlayer = null
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
        stopMediaPlayer()
        doubaoTtsService.stop()
        _isSpeaking.value = false
        _currentMessageId.value = null
    }

    /**
     * 暂停朗读（API 21+）
     */
    fun pause() {
        // MediaPlayer 支持暂停
        mediaPlayer?.let {
            if (it.isPlaying) {
                it.pause()
                _isSpeaking.value = false
            }
        }
    }

    /**
     * 恢复朗读
     */
    fun resume() {
        mediaPlayer?.let {
            it.start()
            _isSpeaking.value = true
        }
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
        try {
            tempDir.listFiles()?.forEach { it.delete() }
        } catch (e: Exception) {
            Log.w(TAG, "清理临时文件失败", e)
        }
    }

    /**
     * 释放资源
     */
    fun shutdown() {
        stop()
        tts?.shutdown()
        tts = null
        isInitialized = false
        cleanupTempFiles()
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
