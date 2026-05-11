package com.tchat.wanxiaot.ui.ssh

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tchat.data.ssh.SshAuthType
import com.tchat.data.ssh.SshProfile
import com.tchat.data.ssh.SshProfileStore
import com.tchat.wanxiaot.ui.components.AppEmptyState
import com.tchat.wanxiaot.ui.components.AppPageScaffold
import com.tchat.wanxiaot.ui.components.AppPill
import com.tchat.wanxiaot.ui.components.SettingsGroupCard
import com.tchat.wanxiaot.ui.components.SettingsSurface
import kotlinx.coroutines.launch

@Composable
fun SshProfilesScreen(
    onBack: () -> Unit,
    showTopBar: Boolean = true
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember(context) { SshProfileStore(context) }
    var profiles by remember { mutableStateOf<List<SshProfile>>(emptyList()) }
    var showDialog by remember { mutableStateOf(false) }
    var editingProfile by remember { mutableStateOf<SshProfile?>(null) }

    fun reload() {
        scope.launch {
            profiles = store.getProfiles()
        }
    }

    LaunchedEffect(Unit) {
        profiles = store.getProfiles()
    }

    AppPageScaffold(
        title = "SSH 配置",
        eyebrow = "Local SSH",
        subtitle = if (profiles.isEmpty()) {
            "本地保存用户 SSH profile"
        } else {
            "已配置 ${profiles.size} 个 SSH profile"
        },
        showTopBar = showTopBar,
        onBack = onBack,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    editingProfile = null
                    showDialog = true
                },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("添加 SSH") }
            )
        }
    ) { innerPadding ->
        if (profiles.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.Center
            ) {
                AppEmptyState(
                    icon = Icons.Default.Add,
                    title = "暂无 SSH profile",
                    description = "添加后，启用“SSH 只读”本地工具的助手可以按 alias 查看远程目录、文件和日志。"
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                items(profiles, key = { it.id }) { profile ->
                    SshProfileCard(
                        profile = profile,
                        onEdit = {
                            editingProfile = profile
                            showDialog = true
                        },
                        onDelete = {
                            scope.launch {
                                store.deleteProfile(profile.id)
                                reload()
                                Toast.makeText(context, "已删除 ${profile.alias}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }
                item { Spacer(modifier = Modifier.height(84.dp)) }
            }
        }
    }

    if (showDialog) {
        SshProfileDialog(
            profile = editingProfile,
            onDismiss = { showDialog = false },
            onSave = { profile ->
                scope.launch {
                    store.upsertProfile(profile)
                    showDialog = false
                    reload()
                    Toast.makeText(context, "已保存 ${profile.alias}", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }
}

@Composable
private fun SshProfileCard(
    profile: SshProfile,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf(false) }

    SettingsSurface {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = profile.alias,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${profile.username}@${profile.host}:${profile.port}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppPill(
                    text = if (profile.authType == SshAuthType.PASSWORD) "密码" else "私钥",
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
                AppPill(
                    text = if (profile.strictHostKeyChecking) "严格主机校验" else "未启用主机校验",
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(
                    onClick = onEdit,
                    label = { Text("编辑") },
                    leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) }
                )
                AssistChip(
                    onClick = { showDeleteDialog = true },
                    label = { Text("删除") },
                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) }
                )
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("删除 SSH profile") },
            text = { Text("确定要删除「${profile.alias}」吗？") },
            confirmButton = {
                TextButton(onClick = {
                    onDelete()
                    showDeleteDialog = false
                }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
private fun SshProfileDialog(
    profile: SshProfile?,
    onDismiss: () -> Unit,
    onSave: (SshProfile) -> Unit
) {
    var alias by remember { mutableStateOf(profile?.alias ?: "") }
    var host by remember { mutableStateOf(profile?.host ?: "") }
    var port by remember { mutableStateOf((profile?.port ?: 22).toString()) }
    var username by remember { mutableStateOf(profile?.username ?: "") }
    var authType by remember { mutableStateOf(profile?.authType ?: SshAuthType.PASSWORD) }
    var strictHostKeyChecking by remember { mutableStateOf(profile?.strictHostKeyChecking ?: false) }
    var password by remember { mutableStateOf("") }
    var privateKey by remember { mutableStateOf("") }
    var passphrase by remember { mutableStateOf("") }

    val validPort = port.toIntOrNull()?.takeIf { it in 1..65535 }
    val secretReady = when (authType) {
        SshAuthType.PASSWORD -> password.isNotBlank()
        SshAuthType.PRIVATE_KEY -> privateKey.isNotBlank()
    }
    val canSave = alias.isNotBlank() &&
        host.isNotBlank() &&
        username.isNotBlank() &&
        validPort != null &&
        secretReady

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (profile == null) "添加 SSH profile" else "编辑 SSH profile") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = alias,
                    onValueChange = { alias = it },
                    label = { Text("Alias") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = host,
                    onValueChange = { host = it },
                    label = { Text("Host") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = port,
                        onValueChange = { port = it.filter(Char::isDigit).take(5) },
                        label = { Text("Port") },
                        singleLine = true,
                        modifier = Modifier.weight(0.35f)
                    )
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text("Username") },
                        singleLine = true,
                        modifier = Modifier.weight(0.65f)
                    )
                }

                SettingsGroupCard(title = "认证方式") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = authType == SshAuthType.PASSWORD,
                            onClick = { authType = SshAuthType.PASSWORD },
                            label = { Text("密码") }
                        )
                        FilterChip(
                            selected = authType == SshAuthType.PRIVATE_KEY,
                            onClick = { authType = SshAuthType.PRIVATE_KEY },
                            label = { Text("私钥") }
                        )
                    }
                }

                if (authType == SshAuthType.PASSWORD) {
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Password") },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    OutlinedTextField(
                        value = privateKey,
                        onValueChange = { privateKey = it },
                        label = { Text("Private key") },
                        minLines = 4,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = passphrase,
                        onValueChange = { passphrase = it },
                        label = { Text("Passphrase（可选）") },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                HorizontalDivider()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = strictHostKeyChecking,
                        onCheckedChange = { strictHostKeyChecking = it }
                    )
                    Text(
                        text = "严格主机密钥校验",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Text(
                    text = "编辑时需要重新输入密码或私钥。敏感内容仅保存在本机，并通过 Android Keystore 加密。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        SshProfile(
                            id = profile?.id ?: java.util.UUID.randomUUID().toString(),
                            alias = alias,
                            host = host,
                            port = validPort ?: 22,
                            username = username,
                            authType = authType,
                            strictHostKeyChecking = strictHostKeyChecking,
                            createdAt = profile?.createdAt ?: System.currentTimeMillis(),
                            password = password.takeIf { authType == SshAuthType.PASSWORD },
                            privateKey = privateKey.takeIf { authType == SshAuthType.PRIVATE_KEY },
                            passphrase = passphrase.takeIf { authType == SshAuthType.PRIVATE_KEY && it.isNotBlank() }
                        )
                    )
                },
                enabled = canSave
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
