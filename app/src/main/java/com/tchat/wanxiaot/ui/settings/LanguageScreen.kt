package com.tchat.wanxiaot.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tchat.wanxiaot.i18n.Language
import com.tchat.wanxiaot.i18n.strings
import com.tchat.wanxiaot.ui.components.AppHeroCard
import com.tchat.wanxiaot.ui.components.AppPageScaffold
import com.tchat.wanxiaot.ui.components.AppPill
import com.tchat.wanxiaot.ui.components.AppSectionSurface

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguageScreen(
    currentLanguage: Language,
    onLanguageSelected: (Language) -> Unit,
    onBack: () -> Unit
) {
    AppPageScaffold(
        title = strings.languageTitle,
        eyebrow = "Language",
        subtitle = "切换界面语言，无需重启应用",
        onBack = onBack
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                AppHeroCard(
                    eyebrow = "Localization",
                    title = "选择最顺手的界面语言",
                    description = "系统语言模式会自动跟随设备语言，手动选择则固定显示对应翻译。",
                    icon = Icons.Default.Language,
                    trailing = {
                        AppPill(text = currentLanguage.nativeName)
                    }
                )
            }

            items(Language.entries) { language ->
                LanguageItem(
                    language = language,
                    isSelected = language == currentLanguage,
                    onClick = { onLanguageSelected(language) }
                )
            }
        }
    }
}

@Composable
private fun LanguageItem(
    language: Language,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val displayNativeName = if (language == Language.SYSTEM) {
        strings.languageFollowSystem
    } else {
        language.nativeName
    }

    val displayName = if (language == Language.SYSTEM) {
        val systemLanguage = Language.getActualLanguage(Language.SYSTEM)
        "${language.displayName} (${systemLanguage.nativeName})"
    } else {
        language.displayName
    }

    AppSectionSurface {
        androidx.compose.material3.Surface(
            onClick = onClick,
            color = if (isSelected) {
                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.62f)
            } else {
                androidx.compose.ui.graphics.Color.Transparent
            }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 15.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Language,
                    contentDescription = null,
                    tint = if (isSelected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
                androidx.compose.foundation.layout.Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = displayNativeName,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium),
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        }
                    )
                    Text(
                        text = displayName,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.74f)
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}
