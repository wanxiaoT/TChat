package com.tchat.wanxiaot.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.BookOpen
import com.composables.icons.lucide.Lucide
import com.tchat.data.database.AppDatabase
import com.tchat.data.repository.impl.AssistantRepositoryImpl
import com.tchat.data.repository.impl.KnowledgeRepositoryImpl
import com.tchat.data.service.KnowledgeService
import com.tchat.wanxiaot.settings.SettingsManager
import com.tchat.wanxiaot.ui.assistant.AssistantDetailScreen
import com.tchat.wanxiaot.ui.assistant.AssistantDetailViewModel
import com.tchat.wanxiaot.ui.assistant.AssistantScreen
import com.tchat.wanxiaot.ui.assistant.AssistantViewModel
import com.tchat.wanxiaot.ui.knowledge.KnowledgeDetailScreen
import com.tchat.wanxiaot.ui.knowledge.KnowledgeScreen
import com.tchat.wanxiaot.ui.knowledge.KnowledgeViewModel

/**
 * 设置子页面类型
 */
private sealed class SettingsSubPage {
    data object MAIN : SettingsSubPage()
    data object PROVIDERS : SettingsSubPage()
    data object ABOUT : SettingsSubPage()
    data object LOGCAT : SettingsSubPage()
    data object ASSISTANTS : SettingsSubPage()
    data class ASSISTANT_DETAIL(val id: String) : SettingsSubPage()
    data object KNOWLEDGE : SettingsSubPage()
    data class KNOWLEDGE_DETAIL(val id: String) : SettingsSubPage()
}

/**
 * 设置页面 - 设置菜单
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settingsManager: SettingsManager,
    database: AppDatabase,
    onBack: () -> Unit
) {
    var currentSubPage by remember { mutableStateOf<SettingsSubPage>(SettingsSubPage.MAIN) }

    // 创建助手Repository
    val assistantRepository = remember(database) {
        AssistantRepositoryImpl(database.assistantDao())
    }

    // 创建知识库Repository和Service
    val knowledgeRepository = remember(database) {
        KnowledgeRepositoryImpl(
            database.knowledgeBaseDao(),
            database.knowledgeItemDao(),
            database.knowledgeChunkDao()
        )
    }
    val knowledgeService = remember(knowledgeRepository) {
        KnowledgeService(knowledgeRepository)
    }

    // 处理系统返回键
    BackHandler {
        when (currentSubPage) {
            is SettingsSubPage.MAIN -> onBack()
            is SettingsSubPage.ASSISTANT_DETAIL -> currentSubPage = SettingsSubPage.ASSISTANTS
            is SettingsSubPage.KNOWLEDGE_DETAIL -> currentSubPage = SettingsSubPage.KNOWLEDGE
            else -> currentSubPage = SettingsSubPage.MAIN
        }
    }

    AnimatedContent(
        targetState = currentSubPage,
        transitionSpec = {
            val animationDuration = 100
            if (targetState is SettingsSubPage.MAIN) {
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
            is SettingsSubPage.MAIN -> {
                SettingsMainContent(
                    onBack = onBack,
                    onProvidersClick = { currentSubPage = SettingsSubPage.PROVIDERS },
                    onAboutClick = { currentSubPage = SettingsSubPage.ABOUT },
                    onLogcatClick = { currentSubPage = SettingsSubPage.LOGCAT },
                    onAssistantsClick = { currentSubPage = SettingsSubPage.ASSISTANTS },
                    onKnowledgeClick = { currentSubPage = SettingsSubPage.KNOWLEDGE }
                )
            }
            is SettingsSubPage.PROVIDERS -> {
                ProvidersScreen(
                    settingsManager = settingsManager,
                    onBack = { currentSubPage = SettingsSubPage.MAIN }
                )
            }
            is SettingsSubPage.ABOUT -> {
                AboutScreen(onBack = { currentSubPage = SettingsSubPage.MAIN })
            }
            is SettingsSubPage.LOGCAT -> {
                LogcatScreen(onBack = { currentSubPage = SettingsSubPage.MAIN })
            }
            is SettingsSubPage.ASSISTANTS -> {
                val viewModel = remember(assistantRepository) {
                    AssistantViewModel(assistantRepository)
                }
                AssistantScreen(
                    viewModel = viewModel,
                    onBack = { currentSubPage = SettingsSubPage.MAIN },
                    onAssistantClick = { id -> currentSubPage = SettingsSubPage.ASSISTANT_DETAIL(id) }
                )
            }
            is SettingsSubPage.ASSISTANT_DETAIL -> {
                val viewModel = remember(assistantRepository, knowledgeRepository, page.id) {
                    AssistantDetailViewModel(assistantRepository, knowledgeRepository, page.id)
                }
                AssistantDetailScreen(
                    viewModel = viewModel,
                    onBack = { currentSubPage = SettingsSubPage.ASSISTANTS }
                )
            }
            is SettingsSubPage.KNOWLEDGE -> {
                val viewModel = remember(knowledgeRepository, knowledgeService, settingsManager) {
                    KnowledgeViewModel(knowledgeRepository, knowledgeService, settingsManager)
                }
                KnowledgeScreen(
                    viewModel = viewModel,
                    onBack = { currentSubPage = SettingsSubPage.MAIN },
                    onBaseClick = { id -> currentSubPage = SettingsSubPage.KNOWLEDGE_DETAIL(id) }
                )
            }
            is SettingsSubPage.KNOWLEDGE_DETAIL -> {
                val viewModel = remember(knowledgeRepository, knowledgeService, settingsManager) {
                    KnowledgeViewModel(knowledgeRepository, knowledgeService, settingsManager)
                }
                KnowledgeDetailScreen(
                    baseId = page.id,
                    viewModel = viewModel,
                    onBack = { currentSubPage = SettingsSubPage.KNOWLEDGE }
                )
            }
        }
    }
}

/**
 * 设置主页面内容 - 卡片式设计
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsMainContent(
    onBack: () -> Unit,
    onProvidersClick: () -> Unit,
    onAboutClick: () -> Unit,
    onLogcatClick: () -> Unit,
    onAssistantsClick: () -> Unit,
    onKnowledgeClick: () -> Unit
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
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.surface
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 通用设置分组标题
            Text(
                text = "通用",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
            )

            // 服务商设置卡片
            OutlinedCard(
                onClick = onProvidersClick,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Settings,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "服务商",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "管理 AI 服务商配置",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 助手设置卡片
            OutlinedCard(
                onClick = onAssistantsClick,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Person,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "助手",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "管理AI助手和本地工具",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 知识库设置卡片
            OutlinedCard(
                onClick = onKnowledgeClick,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Lucide.BookOpen,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "知识库",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "管理RAG知识库和向量检索",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 其他分组标题
            Text(
                text = "其他",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
            )

            // 日志查看卡片
            OutlinedCard(
                onClick = onLogcatClick,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.BugReport,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "日志查看",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "查看应用运行日志",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 关于卡片
            OutlinedCard(
                onClick = onAboutClick,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "关于",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "版本信息与开发者",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
