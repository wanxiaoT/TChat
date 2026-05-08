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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.BookOpen
import com.composables.icons.lucide.Lucide
import com.tchat.data.database.entity.KnowledgeBaseEntity
import com.tchat.wanxiaot.settings.AIProviderType
import com.tchat.wanxiaot.settings.ProviderConfig
import com.tchat.wanxiaot.ui.components.AppEmptyState
import com.tchat.wanxiaot.ui.components.AppIconTile
import com.tchat.wanxiaot.ui.components.AppPageScaffold
import com.tchat.wanxiaot.ui.components.AppPill
import com.tchat.wanxiaot.ui.components.AppSectionSurface
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KnowledgeScreen(
    viewModel: KnowledgeViewModel,
    onBack: () -> Unit,
    onBaseClick: (String) -> Unit,
    showTopBar: Boolean = true
) {
    val context = LocalContext.current
    val knowledgeBases by viewModel.knowledgeBases.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()

    var showCreateSheet by remember { mutableStateOf(false) }
    var baseToEdit by remember { mutableStateOf<KnowledgeBaseEntity?>(null) }
    var baseToDelete by remember { mutableStateOf<KnowledgeBaseEntity?>(null) }

    val embeddingProviders = remember { viewModel.getEmbeddingProviders() }

    AppPageScaffold(
        title = "知识库",
        showTopBar = showTopBar,
        onBack = onBack,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    if (embeddingProviders.isEmpty()) {
                        Toast.makeText(context, "请先配置支持 Embedding 的服务商", Toast.LENGTH_LONG).show()
                    } else {
                        showCreateSheet = true
                    }
                },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("新建知识库") }
            )
        }
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
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                if (knowledgeBases.isEmpty()) {
                    item {
                        AppEmptyState(
                            icon = Lucide.BookOpen,
                            title = "还没有知识库",
                            description = if (embeddingProviders.isEmpty()) {
                                "先去服务商中配置支持 Embedding 的连接，然后再创建知识库。"
                            } else {
                                "把文档、网页和笔记沉淀到知识库，后续才能让助手基于内容检索回答。"
                            }
                        )
                    }
                } else {
                    items(knowledgeBases, key = { it.id }) { base ->
                        KnowledgeBaseCard(
                            base = base,
                            onClick = { onBaseClick(base.id) },
                            onEdit = { baseToEdit = base },
                            onDelete = { baseToDelete = base }
                        )
                    }

                    item {
                        Spacer(modifier = Modifier.height(84.dp))
                    }
                }
            }
        }
    }

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

    baseToDelete?.let { base ->
        AlertDialog(
            onDismissRequest = { baseToDelete = null },
            title = { Text("删除知识库") },
            text = { Text("确定要删除「${base.name}」吗？所有知识条目和向量数据将被永久删除。") },
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

@Composable
private fun KnowledgeBaseCard(
    base: KnowledgeBaseEntity,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    AppSectionSurface {
        Surface(
            onClick = onClick,
            color = androidx.compose.ui.graphics.Color.Transparent
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                AppIconTile(icon = Lucide.BookOpen)

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = base.name,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Text(
                        text = base.description ?: "使用 ${base.embeddingModelId} 进行向量化检索",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AppPill(text = "Embedding")
                        AppPill(
                            text = formatModelLabel(base.embeddingModelId),
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
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

                    Text(
                        text = formatDateTime(base.updatedAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

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
        title = { Text("创建知识库") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("名称") },
                    placeholder = { Text("例如：产品文档库") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("描述（可选）") },
                    placeholder = { Text("说明知识范围和用途") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = "Embedding 服务商",
                    style = MaterialTheme.typography.labelLarge
                )

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    providers.forEach { provider ->
                        val isSelected = selectedProvider?.id == provider.id
                        Surface(
                            onClick = {
                                selectedProvider = provider
                                embeddingModel = getDefaultModel(provider.providerType)
                            },
                            shape = MaterialTheme.shapes.large,
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.78f)
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerLow
                            }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text(
                                        text = provider.name.ifBlank { provider.providerType.displayName },
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (isSelected) {
                                            MaterialTheme.colorScheme.onSecondaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.onSurface
                                        }
                                    )
                                    Text(
                                        text = provider.providerType.displayName,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (isSelected) {
                                            MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.72f)
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        }
                                    )
                                }
                                if (isSelected) {
                                    AppPill(text = "当前")
                                }
                            }
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
        title = { Text("编辑知识库") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
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

                Text(
                    text = "Embedding 服务商",
                    style = MaterialTheme.typography.labelLarge
                )

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    providers.forEach { provider ->
                        val isSelected = selectedProvider?.id == provider.id
                        Surface(
                            onClick = { selectedProvider = provider },
                            shape = MaterialTheme.shapes.large,
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.78f)
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerLow
                            }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text(
                                        text = provider.name.ifBlank { provider.providerType.displayName },
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (isSelected) {
                                            MaterialTheme.colorScheme.onSecondaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.onSurface
                                        }
                                    )
                                    Text(
                                        text = provider.providerType.displayName,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (isSelected) {
                                            MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.72f)
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        }
                                    )
                                }
                                if (isSelected) {
                                    AppPill(text = "当前")
                                }
                            }
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

private fun formatModelLabel(modelId: String): String {
    return if (modelId.length <= 20) {
        modelId
    } else {
        "${modelId.take(17)}..."
    }
}

private fun formatDateTime(timestamp: Long): String {
    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    val instant = Instant.ofEpochMilli(timestamp)
    return formatter.format(instant.atZone(ZoneId.systemDefault()))
}
