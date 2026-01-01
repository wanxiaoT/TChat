package com.tchat.wanxiaot.ui.knowledge

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.BookOpen
import com.composables.icons.lucide.Lucide
import com.tchat.data.database.entity.KnowledgeBaseEntity
import com.tchat.wanxiaot.settings.AIProviderType
import com.tchat.wanxiaot.settings.ProviderConfig
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 知识库列表页面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KnowledgeScreen(
    viewModel: KnowledgeViewModel,
    onBack: () -> Unit,
    onBaseClick: (String) -> Unit
) {
    val context = LocalContext.current
    val knowledgeBases by viewModel.knowledgeBases.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    var showCreateSheet by remember { mutableStateOf(false) }
    var baseToEdit by remember { mutableStateOf<KnowledgeBaseEntity?>(null) }
    var baseToDelete by remember { mutableStateOf<KnowledgeBaseEntity?>(null) }

    val embeddingProviders = remember { viewModel.getEmbeddingProviders() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("知识库") },
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
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    if (embeddingProviders.isEmpty()) {
                        Toast.makeText(context, "请先配置支持Embedding的服务商（OpenAI或Gemini）", Toast.LENGTH_LONG).show()
                    } else {
                        showCreateSheet = true
                    }
                }
            ) {
                Icon(Icons.Default.Add, contentDescription = "添加知识库")
            }
        },
        containerColor = MaterialTheme.colorScheme.surface
    ) { innerPadding ->
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (knowledgeBases.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Lucide.BookOpen,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Text(
                        text = "暂无知识库",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "点击右下角按钮创建新知识库",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(knowledgeBases, key = { it.id }) { base ->
                    KnowledgeBaseCard(
                        base = base,
                        onClick = { onBaseClick(base.id) },
                        onEdit = { baseToEdit = base },
                        onDelete = { baseToDelete = base }
                    )
                }
            }
        }
    }

    // 创建知识库对话框
    if (showCreateSheet) {
        CreateKnowledgeBaseDialog(
            providers = embeddingProviders,
            onDismiss = { showCreateSheet = false },
            onCreate = { name, description, providerId, modelId ->
                viewModel.createBase(name, description, providerId, modelId)
                showCreateSheet = false
                Toast.makeText(context, "已创建知识库", Toast.LENGTH_SHORT).show()
            },
            getDefaultModel = { viewModel.getDefaultEmbeddingModel(it) }
        )
    }

    // 编辑知识库对话框
    baseToEdit?.let { base ->
        EditKnowledgeBaseDialog(
            base = base,
            providers = embeddingProviders,
            onDismiss = { baseToEdit = null },
            onUpdate = { updatedBase ->
                viewModel.updateBase(updatedBase)
                baseToEdit = null
                Toast.makeText(context, "已更新知识库", Toast.LENGTH_SHORT).show()
            }
        )
    }

    // 删除确认对话框
    baseToDelete?.let { base ->
        AlertDialog(
            onDismissRequest = { baseToDelete = null },
            title = { Text("删除知识库") },
            text = { Text("确定要删除「${base.name}」吗？\n所有知识条目和向量数据将被永久删除。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteBase(base.id)
                        baseToDelete = null
                        Toast.makeText(context, "已删除", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { baseToDelete = null }) {
                    Text("取消")
                }
            }
        )
    }
}

/**
 * 知识库卡片
 */
@Composable
private fun KnowledgeBaseCard(
    base: KnowledgeBaseEntity,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
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
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = base.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val desc = base.description
                if (!desc.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = desc,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = formatDateTime(base.updatedAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }

            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "更多")
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("编辑") },
                        onClick = {
                            showMenu = false
                            onEdit()
                        },
                        leadingIcon = { Icon(Icons.Default.Edit, null) }
                    )
                    DropdownMenuItem(
                        text = { Text("删除") },
                        onClick = {
                            showMenu = false
                            onDelete()
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Delete,
                                null,
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    )
                }
            }

            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 创建知识库对话框
 */
@Composable
private fun CreateKnowledgeBaseDialog(
    providers: List<ProviderConfig>,
    onDismiss: () -> Unit,
    onCreate: (name: String, description: String?, providerId: String, modelId: String) -> Unit,
    getDefaultModel: (AIProviderType) -> String
) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var selectedProvider by remember { mutableStateOf(providers.firstOrNull()) }
    var embeddingModel by remember {
        mutableStateOf(
            selectedProvider?.let { getDefaultModel(it.providerType) } ?: "text-embedding-3-small"
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "创建知识库",
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("名称") },
                    placeholder = { Text("请输入知识库名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("描述（可选）") },
                    placeholder = { Text("请输入知识库描述") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )

                // 服务商选择
                Text(
                    text = "Embedding 服务商",
                    style = MaterialTheme.typography.labelLarge
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    providers.forEach { provider ->
                        val isSelected = selectedProvider?.id == provider.id
                        Card(
                            onClick = {
                                selectedProvider = provider
                                embeddingModel = getDefaultModel(provider.providerType)
                            },
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected)
                                    MaterialTheme.colorScheme.primaryContainer
                                else
                                    MaterialTheme.colorScheme.surfaceContainerLow
                            )
                        ) {
                            Text(
                                text = provider.name.ifBlank { provider.providerType.displayName },
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                color = if (isSelected)
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                else
                                    MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = embeddingModel,
                    onValueChange = { embeddingModel = it },
                    label = { Text("Embedding 模型") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    selectedProvider?.let { provider ->
                        onCreate(name, description.ifBlank { null }, provider.id, embeddingModel)
                    }
                },
                enabled = name.isNotBlank() && selectedProvider != null && embeddingModel.isNotBlank()
            ) {
                Text("创建")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

/**
 * 编辑知识库对话框
 */
@Composable
private fun EditKnowledgeBaseDialog(
    base: KnowledgeBaseEntity,
    providers: List<ProviderConfig>,
    onDismiss: () -> Unit,
    onUpdate: (KnowledgeBaseEntity) -> Unit
) {
    var name by remember { mutableStateOf(base.name) }
    var description by remember { mutableStateOf(base.description ?: "") }
    var selectedProvider by remember {
        mutableStateOf(providers.find { it.id == base.embeddingProviderId } ?: providers.firstOrNull())
    }
    var embeddingModel by remember { mutableStateOf(base.embeddingModelId) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "编辑知识库",
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("描述（可选）") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )

                // 服务商选择
                Text(
                    text = "Embedding 服务商",
                    style = MaterialTheme.typography.labelLarge
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    providers.forEach { provider ->
                        val isSelected = selectedProvider?.id == provider.id
                        Card(
                            onClick = { selectedProvider = provider },
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected)
                                    MaterialTheme.colorScheme.primaryContainer
                                else
                                    MaterialTheme.colorScheme.surfaceContainerLow
                            )
                        ) {
                            Text(
                                text = provider.name.ifBlank { provider.providerType.displayName },
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                color = if (isSelected)
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                else
                                    MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = embeddingModel,
                    onValueChange = { embeddingModel = it },
                    label = { Text("Embedding 模型") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    selectedProvider?.let { provider ->
                        onUpdate(
                            base.copy(
                                name = name,
                                description = description.ifBlank { null },
                                embeddingProviderId = provider.id,
                                embeddingModelId = embeddingModel
                            )
                        )
                    }
                },
                enabled = name.isNotBlank() && selectedProvider != null && embeddingModel.isNotBlank()
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

/**
 * 格式化日期时间
 */
private fun formatDateTime(timestamp: Long): String {
    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    val instant = Instant.ofEpochMilli(timestamp)
    return formatter.format(instant.atZone(ZoneId.systemDefault()))
}
