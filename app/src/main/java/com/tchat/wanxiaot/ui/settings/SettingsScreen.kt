package com.tchat.wanxiaot.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.tchat.wanxiaot.settings.SettingsManager

/**
 * 设置子页面类型
 */
private enum class SettingsSubPage {
    MAIN, PROVIDERS, ABOUT
}

/**
 * 设置页面 - 设置菜单
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settingsManager: SettingsManager,
    onBack: () -> Unit
) {
    var currentSubPage by remember { mutableStateOf(SettingsSubPage.MAIN) }

    // 处理系统返回键
    BackHandler {
        if (currentSubPage == SettingsSubPage.MAIN) {
            onBack()
        } else {
            currentSubPage = SettingsSubPage.MAIN
        }
    }

    AnimatedContent(
        targetState = currentSubPage,
        transitionSpec = {
            val animationDuration = 100
            if (targetState == SettingsSubPage.MAIN) {
                // 返回主页面：从左边滑入，旧页面向右滑出
                slideInHorizontally(
                    animationSpec = tween(animationDuration),
                    initialOffsetX = { -it }
                ) togetherWith slideOutHorizontally(
                    animationSpec = tween(animationDuration),
                    targetOffsetX = { it }
                )
            } else {
                // 进入子页面：从右边滑入，旧页面向左滑出
                slideInHorizontally(
                    animationSpec = tween(animationDuration),
                    initialOffsetX = { it }
                ) togetherWith slideOutHorizontally(
                    animationSpec = tween(animationDuration),
                    targetOffsetX = { -it }
                )
            }
        },
        label = "settings_page_transition"
    ) { page ->
        when (page) {
            SettingsSubPage.MAIN -> {
                SettingsMainContent(
                    onBack = onBack,
                    onProvidersClick = { currentSubPage = SettingsSubPage.PROVIDERS },
                    onAboutClick = { currentSubPage = SettingsSubPage.ABOUT }
                )
            }
            SettingsSubPage.PROVIDERS -> {
                ProvidersScreen(
                    settingsManager = settingsManager,
                    onBack = { currentSubPage = SettingsSubPage.MAIN }
                )
            }
            SettingsSubPage.ABOUT -> {
                AboutScreen(onBack = { currentSubPage = SettingsSubPage.MAIN })
            }
        }
    }
}

/**
 * 设置主页面内容
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsMainContent(
    onBack: () -> Unit,
    onProvidersClick: () -> Unit,
    onAboutClick: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // 服务商条目
            ListItem(
                headlineContent = { Text("服务商") },
                supportingContent = { Text("管理 AI 服务商配置") },
                leadingContent = {
                    Icon(
                        Icons.Default.Settings,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                trailingContent = {
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                modifier = Modifier.clickable { onProvidersClick() }
            )

            HorizontalDivider()

            // 关于条目
            ListItem(
                headlineContent = { Text("关于") },
                supportingContent = { Text("版本信息与开发者") },
                leadingContent = {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                trailingContent = {
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                modifier = Modifier.clickable { onAboutClick() }
            )
        }
    }
}
