package com.tchat.data.tts

import android.media.AudioAttributes
import android.media.MediaPlayer
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * 豆包 TTS 服务（火山引擎语音合成）
 *
 * 使用火山引擎的语音合成 API 进行文字转语音
 * API 文档: https://www.volcengine.com/docs/6561/79823
 */
class DoubaoTtsService(
    private val cacheDir: File
) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private var mediaPlayer: MediaPlayer? = null

    // 播放状态
    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    // 当前朗读的消息ID
    private val _currentMessageId = MutableStateFlow<String?>(null)
    val currentMessageId: StateFlow<String?> = _currentMessageId.asStateFlow()

    // 错误信息
    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    /**
     * 合成并播放语音
     *
     * @param text 要合成的文本
     * @param appId 火山引擎 App ID
     * @param accessToken 火山引擎 Access Token
     * @param cluster 服务集群，默认 "volcano_tts"
     * @param voiceType 音色类型
     * @param speedRatio 语速比例 (0.2 - 3.0)
     * @param messageId 消息ID（用于追踪）
     */
    suspend fun speak(
        text: String,
        appId: String,
        accessToken: String,
        cluster: String = "volcano_tts",
        voiceType: String = "zh_female_tianmei_moon_bigtts",
        speedRatio: Float = 1.0f,
        messageId: String? = null
    ) {
        if (text.isBlank()) return

        // 清理文本
        val cleanText = cleanTextForTts(text)
        if (cleanText.isBlank()) return

        _currentMessageId.value = messageId
        _lastError.value = null

        try {
            // 调用 API 合成语音
            val audioData = synthesize(
                text = cleanText,
                appId = appId,
                accessToken = accessToken,
                cluster = cluster,
                voiceType = voiceType,
                speedRatio = speedRatio
            )

            if (audioData != null) {
                // 播放音频
                playAudio(audioData)
            }
        } catch (e: Exception) {
            _lastError.value = e.message ?: "语音合成失败"
            _isSpeaking.value = false
            _currentMessageId.value = null
        }
    }

    /**
     * 调用豆包 TTS API 合成语音
     */
    private suspend fun synthesize(
        text: String,
        appId: String,
        accessToken: String,
        cluster: String,
        voiceType: String,
        speedRatio: Float
    ): ByteArray? = withContext(Dispatchers.IO) {
        val requestId = UUID.randomUUID().toString()

        // 构建请求体
        val requestBody = JSONObject().apply {
            put("app", JSONObject().apply {
                put("appid", appId)
                put("token", accessToken)
                put("cluster", cluster)
            })
            put("user", JSONObject().apply {
                put("uid", "android_user_${System.currentTimeMillis()}")
            })
            put("audio", JSONObject().apply {
                put("voice_type", voiceType)
                put("encoding", "mp3")
                put("speed_ratio", speedRatio.coerceIn(0.2f, 3.0f))
            })
            put("request", JSONObject().apply {
                put("reqid", requestId)
                put("text", text)
                put("operation", "query")
            })
        }

        val request = Request.Builder()
            .url("https://openspeech.bytedance.com/api/v1/tts")
            .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
            .header("Authorization", "Bearer;$accessToken")
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string() ?: ""

                if (!response.isSuccessful) {
                    val errorMsg = try {
                        JSONObject(responseBody).optString("message", "请求失败: ${response.code}")
                    } catch (e: Exception) {
                        "请求失败: ${response.code}"
                    }
                    throw Exception(errorMsg)
                }

                val json = JSONObject(responseBody)
                val code = json.optInt("code", -1)

                if (code != 3000) {
                    val message = json.optString("message", "未知错误")
                    throw Exception("豆包 TTS 错误 ($code): $message")
                }

                val audioBase64 = json.optString("data", "")
                if (audioBase64.isBlank()) {
                    throw Exception("返回的音频数据为空")
                }

                Base64.decode(audioBase64, Base64.DEFAULT)
            }
        } catch (e: Exception) {
            _lastError.value = e.message
            throw e
        }
    }

    /**
     * 播放音频数据
     */
    private suspend fun playAudio(audioData: ByteArray) = withContext(Dispatchers.IO) {
        // 停止之前的播放
        stopInternal()

        // 将音频数据写入临时文件
        val tempFile = File(cacheDir, "tts_temp_${System.currentTimeMillis()}.mp3")
        try {
            FileOutputStream(tempFile).use { fos ->
                fos.write(audioData)
            }

            withContext(Dispatchers.Main) {
                mediaPlayer = MediaPlayer().apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .build()
                    )
                    setDataSource(tempFile.absolutePath)

                    setOnPreparedListener {
                        _isSpeaking.value = true
                        start()
                    }

                    setOnCompletionListener {
                        _isSpeaking.value = false
                        _currentMessageId.value = null
                        release()
                        mediaPlayer = null
                        tempFile.delete()
                    }

                    setOnErrorListener { _, what, extra ->
                        _lastError.value = "播放错误: what=$what, extra=$extra"
                        _isSpeaking.value = false
                        _currentMessageId.value = null
                        tempFile.delete()
                        true
                    }

                    prepareAsync()
                }
            }
        } catch (e: Exception) {
            tempFile.delete()
            throw e
        }
    }

    /**
     * 停止播放
     */
    fun stop() {
        stopInternal()
        _isSpeaking.value = false
        _currentMessageId.value = null
    }

    private fun stopInternal() {
        mediaPlayer?.let {
            try {
                if (it.isPlaying) {
                    it.stop()
                }
                it.release()
            } catch (e: Exception) {
                // 忽略释放时的错误
            }
        }
        mediaPlayer = null
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
    fun release() {
        stop()
    }
}
