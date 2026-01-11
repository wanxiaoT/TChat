package com.tchat.wanxiaot.i18n

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * CompositionLocal 用于提供当前语言的字符串资源
 */
val LocalStrings = staticCompositionLocalOf<Strings> { StringsZhCN }

/**
 * 提供字符串资源的 Composable
 * 根据语言设置提供对应的字符串实现
 */
@Composable
fun ProvideStrings(
    language: Language,
    content: @Composable () -> Unit
) {
    val actualLanguage = Language.getActualLanguage(language)
    val strings = remember(actualLanguage) {
        when (actualLanguage) {
            Language.ZH_CN -> StringsZhCN
            Language.ZH_TW -> StringsZhTW
            Language.EN -> StringsEn
            Language.SYSTEM -> StringsZhCN // 不会到达这里，但需要处理
        }
    }
    CompositionLocalProvider(LocalStrings provides strings) {
        content()
    }
}

/**
 * 便捷访问当前语言的字符串资源
 * 使用方式: strings.settingsTitle
 */
val strings: Strings
    @Composable
    @ReadOnlyComposable
    get() = LocalStrings.current
