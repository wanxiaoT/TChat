package com.tchat.wanxiaot.settings

data class AppSettings(
    val provider: AIProvider = AIProvider.OPENAI,
    val apiKey: String = "",
    val endpoint: String = "",
    val selectedModel: String = "",
    val availableModels: List<String> = emptyList()
)

enum class AIProvider(
    val displayName: String,
    val defaultEndpoint: String,
    val defaultModels: List<String>
) {
    OPENAI(
        "OpenAI",
        "https://api.openai.com/v1",
        listOf("gpt-4o", "gpt-4o-mini", "gpt-4-turbo", "gpt-3.5-turbo")
    ),
    ANTHROPIC(
        "Anthropic (Claude)",
        "https://api.anthropic.com/v1",
        listOf("claude-3-5-sonnet-20241022", "claude-3-5-haiku-20241022", "claude-3-opus-20240229")
    ),
    GEMINI(
        "Google Gemini",
        "https://generativelanguage.googleapis.com/v1",
        listOf("gemini-2.0-flash-exp", "gemini-1.5-pro", "gemini-1.5-flash")
    )
}
