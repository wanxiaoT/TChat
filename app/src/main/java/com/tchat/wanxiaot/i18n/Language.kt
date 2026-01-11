package com.tchat.wanxiaot.i18n

import java.util.Locale

/**
 * 支持的语言枚举
 */
enum class Language(
    val code: String,
    val displayName: String,
    val nativeName: String
) {
    SYSTEM("system", "Follow System", ""),  // 跟随系统，nativeName 动态获取
    ZH_CN("zh-CN", "Simplified Chinese", "简体中文"),
    ZH_TW("zh-TW", "Traditional Chinese", "繁體中文"),
    EN("en", "English", "English");

    companion object {
        fun fromCode(code: String): Language {
            return entries.find { it.code == code } ?: SYSTEM
        }

        /**
         * 根据系统语言获取实际的语言
         */
        fun getActualLanguage(language: Language): Language {
            if (language != SYSTEM) return language

            val systemLocale = Locale.getDefault()
            val systemLanguage = systemLocale.language
            val systemCountry = systemLocale.country

            return when {
                systemLanguage == "zh" && (systemCountry == "TW" || systemCountry == "HK" || systemCountry == "MO") -> ZH_TW
                systemLanguage == "zh" -> ZH_CN
                systemLanguage == "en" -> EN
                else -> ZH_CN  // 默认简体中文
            }
        }

        /**
         * 获取"跟随系统"选项的本地化名称
         */
        fun getSystemOptionNativeName(currentStrings: Strings): String {
            return currentStrings.languageFollowSystem
        }
    }
}
