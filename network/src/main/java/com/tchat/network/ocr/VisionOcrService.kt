package com.tchat.network.ocr

import com.tchat.network.provider.AIProviderFactory
import com.tchat.network.provider.ChatMessage
import com.tchat.network.provider.MessageContent
import com.tchat.network.provider.MessageRole
import com.tchat.network.provider.StreamChunk

/**
 * AI 视觉 OCR 服务
 * 使用 AI 视觉模型进行图像文字识别
 */
class VisionOcrService(
    private val providerType: String,
    private val apiKey: String,
    private val baseUrl: String,
    private val model: String
) {
    /**
     * 识别图像中的文字
     * @param imageBase64 Base64 编码的图像数据
     * @param mimeType 图像 MIME 类型
     * @param prompt OCR 提示词
     * @return 识别结果文本
     */
    suspend fun recognizeText(
        imageBase64: String,
        mimeType: String = "image/png",
        prompt: String
    ): String {
        val provider = AIProviderFactory.create(
            providerType = providerType,
            apiKey = apiKey,
            baseUrl = baseUrl,
            model = model
        )

        val messages = listOf(
            ChatMessage(
                role = MessageRole.USER,
                content = "",
                contentParts = listOf(
                    MessageContent.Text(prompt),
                    MessageContent.Image(imageBase64, mimeType)
                )
            )
        )

        val result = StringBuilder()
        provider.streamChat(messages).collect { chunk ->
            when (chunk) {
                is StreamChunk.Content -> result.append(chunk.text)
                is StreamChunk.Error -> throw chunk.error
                is StreamChunk.Done -> { /* 完成 */ }
                is StreamChunk.ToolCall -> { /* OCR 不需要工具调用 */ }
            }
        }

        return result.toString()
    }
}
