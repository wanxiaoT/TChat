package com.tchat.wanxiaot.ui.knowledge

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Clock
import com.composables.icons.lucide.File
import com.composables.icons.lucide.FileText
import com.composables.icons.lucide.Globe
import com.composables.icons.lucide.Loader
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.StickyNote
import com.tchat.data.database.entity.KnowledgeItemEntity
import com.tchat.data.database.entity.KnowledgeItemType
import com.tchat.data.database.entity.ProcessingStatus
import com.tchat.data.knowledge.FileLoader
import com.tchat.data.service.KnowledgeService
import java.io.File

/**
 * 知识库详情页面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KnowledgeDetailScreen(
    baseId: String,
    viewModel: KnowledgeViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val knowledgeBases by viewModel.knowledgeBases.collectAsState()
    val items by viewModel.currentItems.collectAsState()
    val isProcessing by viewModel.isProcessing.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()

    val base = remember(knowledgeBases, baseId) {
        knowledgeBases.find { it.id == baseId }
    }

    var selectedTab by remember { mutableIntStateOf(0) }
    var showAddMenu by remember { mutableStateOf(false) }
    var showAddNoteSheet by remember { mutableStateOf(false) }
    var showAddUrlSheet by remember { mutableStateOf(false) }
    var showEditNoteSheet by remember { mutableStateOf<KnowledgeItemEntity?>(null) }
    var showEditUrlSheet by remember { mutableStateOf<KnowledgeItemEntity?>(null) }
    var showSearchSheet by remember { mutableStateOf(false) }
    var itemToDelete by remember { mutableStateOf<KnowledgeItemEntity?>(null) }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            try {
                val inputStream = context.contentResolver.openInputStream(it)
                val fileName = it.lastPathSegment ?: "document.txt"
                val tempFile = File(context.cacheDir, fileName)
                inputStream?.use { input ->
                    tempFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                viewModel.addFile(baseId, tempFile)
                Toast.makeText(context, "已添加文件", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "添加文件失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    LaunchedEffect(baseId) {
        viewModel.selectBase(baseId)
    }

    if (base == null) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
        return
    }

    val tabs = listOf("全部", "文件", "笔记", "URL")

    val filteredItems = remember(items, selectedTab) {
        when (selectedTab) {
            1 -> items.filter { it.getItemType() == KnowledgeItemType.FILE }
            2 -> items.filter { it.getItemType() == KnowledgeItemType.TEXT }
            3 -> items.filter { it.getItemType() == KnowledgeItemType.URL }
            else -> items
        }
    }

    val pendingCount = remember(items) {
        items.count { it.getProcessingStatus() == ProcessingStatus.PENDING }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = base.name,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (items.isNotEmpty()) {
                            Text(
                                text = "${items.size} 个条目",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showSearchSheet = true }) {
                        Icon(Icons.Default.Search, contentDescription = "搜索")
                    }
                    if (pendingCount > 0) {
                        TextButton(
                            onClick = { viewModel.processAllPending(baseId) },
                            enabled = !isProcessing
                        ) {
                            if (isProcessing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(16.dp))
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("处理 ($pendingCount)")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            Box {
                FloatingActionButton(
                    onClick = { showAddMenu = true }
                ) {
                    Icon(Icons.Default.Add, contentDescription = "添加")
                }
                DropdownMenu(
                    expanded = showAddMenu,
                    onDismissRequest = { showAddMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("添加文件") },
                        onClick = {
                            showAddMenu = false
                            filePicker.launch("*/*")
                        },
                        leadingIcon = { Icon(Lucide.File, null) }
                    )
                    DropdownMenuItem(
                        text = { Text("添加笔记") },
                        onClick = {
                            showAddMenu = false
                            showAddNoteSheet = true
                        },
                        leadingIcon = { Icon(Lucide.StickyNote, null) }
                    )
                    DropdownMenuItem(
                        text = { Text("添加URL") },
                        onClick = {
                            showAddMenu = false
                            showAddUrlSheet = true
                        },
                        leadingIcon = { Icon(Lucide.Globe, null) }
                    )
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.surface
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (isProcessing) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            SecondaryTabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title) }
                    )
                }
            }

            if (filteredItems.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Lucide.FileText,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "暂无条目",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredItems, key = { it.id }) { item ->
                        KnowledgeItemCard(
                            item = item,
                            onProcess = { viewModel.processItem(item) },
                            onEdit = {
                                when (item.getItemType()) {
                                    KnowledgeItemType.TEXT -> showEditNoteSheet = item
                                    KnowledgeItemType.URL -> showEditUrlSheet = item
                                    KnowledgeItemType.FILE -> {} // 文件不可编辑
                                }
                            },
                            onDelete = { itemToDelete = item },
                            isProcessing = isProcessing
                        )
                    }
                }
            }
        }
    }

    // 添加笔记弹窗
    if (showAddNoteSheet) {
        AddNoteSheet(
            onDismiss = { showAddNoteSheet = false },
            onAdd = { title, content ->
                viewModel.addNote(baseId, title, content)
                showAddNoteSheet = false
                Toast.makeText(context, "已添加笔记", Toast.LENGTH_SHORT).show()
            }
        )
    }

    // 编辑笔记弹窗
    showEditNoteSheet?.let { item ->
        EditNoteSheet(
            item = item,
            onDismiss = { showEditNoteSheet = null },
            onUpdate = { title, content ->
                viewModel.updateNote(item.id, baseId, title, content)
                showEditNoteSheet = null
                Toast.makeText(context, "已更新笔记", Toast.LENGTH_SHORT).show()
            }
        )
    }

    // 添加URL弹窗
    if (showAddUrlSheet) {
        AddUrlSheet(
            onDismiss = { showAddUrlSheet = false },
            onAdd = { url, title ->
                viewModel.addUrl(baseId, url, title)
                showAddUrlSheet = false
                Toast.makeText(context, "已添加URL", Toast.LENGTH_SHORT).show()
            }
        )
    }

    // 编辑URL弹窗
    showEditUrlSheet?.let { item ->
        EditUrlSheet(
            item = item,
            onDismiss = { showEditUrlSheet = null },
            onUpdate = { url, title ->
                viewModel.updateUrl(item.id, baseId, url, title)
                showEditUrlSheet = null
                Toast.makeText(context, "已更新URL", Toast.LENGTH_SHORT).show()
            }
        )
    }

    // 搜索弹窗
    if (showSearchSheet) {
        SearchSheet(
            searchResults = searchResults,
            onSearch = { query -> viewModel.search(baseId, query) },
            onDismiss = {
                showSearchSheet = false
                viewModel.clearSearchResults()
            }
        )
    }

    // 删除确认对话框
    itemToDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { itemToDelete = null },
            title = { Text("删除条目") },
            text = { Text("确定要删除「${item.title}」吗？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteItem(item.id)
                        itemToDelete = null
                        Toast.makeText(context, "已删除", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { itemToDelete = null }) {
                    Text("取消")
                }
            }
        )
    }
}

/**
 * 知识条目卡片
 */
@Composable
private fun KnowledgeItemCard(
    item: KnowledgeItemEntity,
    onProcess: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    isProcessing: Boolean
) {
    var showMenu by remember { mutableStateOf(false) }
    val status = item.getProcessingStatus()
    val itemType = item.getItemType()

    Card(
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
                imageVector = when (itemType) {
                    KnowledgeItemType.FILE -> Lucide.File
                    KnowledgeItemType.TEXT -> Lucide.StickyNote
                    KnowledgeItemType.URL -> Lucide.Globe
                },
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StatusChip(status = status)
                    val errorMsg = item.errorMessage
                    if (errorMsg != null && status == ProcessingStatus.FAILED) {
                        Text(
                            text = errorMsg,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            if (status == ProcessingStatus.PENDING || status == ProcessingStatus.FAILED) {
                IconButton(
                    onClick = onProcess,
                    enabled = !isProcessing
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "处理")
                }
            }

            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "更多")
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    if (itemType != KnowledgeItemType.FILE) {
                        DropdownMenuItem(
                            text = { Text("编辑") },
                            onClick = {
                                showMenu = false
                                onEdit()
                            },
                            leadingIcon = { Icon(Icons.Default.Edit, null) }
                        )
                    }
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
        }
    }
}

/**
 * 状态标签
 */
@Composable
private fun StatusChip(status: ProcessingStatus) {
    val (icon, color, text) = when (status) {
        ProcessingStatus.PENDING -> Triple(
            Lucide.Clock,
            MaterialTheme.colorScheme.tertiary,
            "待处理"
        )
        ProcessingStatus.PROCESSING -> Triple(
            Lucide.Loader,
            MaterialTheme.colorScheme.primary,
            "处理中"
        )
        ProcessingStatus.COMPLETED -> Triple(
            Icons.Default.CheckCircle,
            MaterialTheme.colorScheme.primary,
            "已完成"
        )
        ProcessingStatus.FAILED -> Triple(
            Icons.Default.Warning,
            MaterialTheme.colorScheme.error,
            "失败"
        )
    }

    FilterChip(
        selected = false,
        onClick = {},
        label = { Text(text, style = MaterialTheme.typography.labelSmall) },
        leadingIcon = { Icon(icon, null, modifier = Modifier.size(14.dp), tint = color) }
    )
}

/**
 * 添加笔记弹窗
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddNoteSheet(
    onDismiss: () -> Unit,
    onAdd: (title: String, content: String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "添加笔记",
                style = MaterialTheme.typography.titleLarge
            )

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("标题") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = content,
                onValueChange = { content = it },
                label = { Text("内容") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                maxLines = 10
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismiss) {
                    Text("取消")
                }
                TextButton(
                    onClick = { onAdd(title, content) },
                    enabled = title.isNotBlank() && content.isNotBlank()
                ) {
                    Text("添加")
                }
            }
        }
    }
}

/**
 * 编辑笔记弹窗
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditNoteSheet(
    item: KnowledgeItemEntity,
    onDismiss: () -> Unit,
    onUpdate: (title: String, content: String) -> Unit
) {
    var title by remember { mutableStateOf(item.title) }
    var content by remember { mutableStateOf(item.content) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "编辑笔记",
                style = MaterialTheme.typography.titleLarge
            )

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("标题") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = content,
                onValueChange = { content = it },
                label = { Text("内容") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                maxLines = 10
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismiss) {
                    Text("取消")
                }
                TextButton(
                    onClick = { onUpdate(title, content) },
                    enabled = title.isNotBlank() && content.isNotBlank()
                ) {
                    Text("保存")
                }
            }
        }
    }
}

/**
 * 添加URL弹窗
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddUrlSheet(
    onDismiss: () -> Unit,
    onAdd: (url: String, title: String?) -> Unit
) {
    var url by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "添加URL",
                style = MaterialTheme.typography.titleLarge
            )

            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text("URL") },
                placeholder = { Text("https://example.com") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("标题（可选）") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismiss) {
                    Text("取消")
                }
                TextButton(
                    onClick = { onAdd(url, title.ifBlank { null }) },
                    enabled = url.isNotBlank()
                ) {
                    Text("添加")
                }
            }
        }
    }
}

/**
 * 编辑URL弹窗
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditUrlSheet(
    item: KnowledgeItemEntity,
    onDismiss: () -> Unit,
    onUpdate: (url: String, title: String?) -> Unit
) {
    var url by remember { mutableStateOf(item.sourceUri ?: item.content) }
    var title by remember { mutableStateOf(item.title) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "编辑URL",
                style = MaterialTheme.typography.titleLarge
            )

            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text("URL") },
                placeholder = { Text("https://example.com") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("标题（可选）") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismiss) {
                    Text("取消")
                }
                TextButton(
                    onClick = { onUpdate(url, title.ifBlank { null }) },
                    enabled = url.isNotBlank()
                ) {
                    Text("保存")
                }
            }
        }
    }
}

/**
 * 搜索弹窗
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchSheet(
    searchResults: List<KnowledgeService.SearchResult>,
    onSearch: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "搜索知识库",
                style = MaterialTheme.typography.titleLarge
            )

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("搜索内容") },
                placeholder = { Text("输入搜索关键词") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = {
                    IconButton(
                        onClick = { onSearch(query) },
                        enabled = query.isNotBlank()
                    ) {
                        Icon(Icons.Default.Search, null)
                    }
                }
            )

            if (searchResults.isNotEmpty()) {
                Text(
                    text = "找到 ${searchResults.size} 个结果",
                    style = MaterialTheme.typography.labelMedium
                )

                LazyColumn(
                    modifier = Modifier.height(300.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(searchResults) { result ->
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                            )
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = result.item?.title ?: "未知",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = "%.2f".format(result.score),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = result.chunk.content,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismiss) {
                    Text("关闭")
                }
            }
        }
    }
}
