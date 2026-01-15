package com.tchat.wanxiaot.settings

enum class OcrModel(val displayName: String) {
    MLKIT_LATIN("ML Kit (Latin)"),
    MLKIT_CHINESE("ML Kit (Chinese)"),
    AI_VISION("AI 视觉模型");

    companion object {
        fun fromName(name: String?): OcrModel {
            return entries.firstOrNull { it.name == name } ?: MLKIT_LATIN
        }
    }
}

