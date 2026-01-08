package com.tchat.wanxiaot.ui.settings

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tchat.data.database.AppDatabase
import com.tchat.data.database.entity.KnowledgeBaseEntity
import com.tchat.wanxiaot.settings.ProviderConfig
import com.tchat.wanxiaot.settings.SettingsManager
import com.tchat.wanxiaot.util.DatabaseBackupManager
import com.tchat.wanxiaot.util.ExportData
import com.tchat.wanxiaot.util.ExportImportManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

/**
 * 导出/导入功能的 ViewModel
 */
class ExportImportViewModel(
    private val context: Context,
    private val settingsManager: SettingsManager
) : ViewModel() {

    private val exportImportManager = ExportImportManager(context, settingsManager)
    private val databaseBackupManager = DatabaseBackupManager(context)
    private val database = AppDatabase.getInstance(context)

    private val _uiState = MutableStateFlow<ExportImportUiState>(ExportImportUiState.Idle)
    val uiState: StateFlow<ExportImportUiState> = _uiState.asStateFlow()

    private val _providers = MutableStateFlow<List<ProviderConfig>>(emptyList())
    val providers: StateFlow<List<ProviderConfig>> = _providers.asStateFlow()

    private val _selectedProviderIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedProviderIds: StateFlow<Set<String>> = _selectedProviderIds.asStateFlow()

    private val _knowledgeBases = MutableStateFlow<List<KnowledgeBaseEntity>>(emptyList())
    val knowledgeBases: StateFlow<List<KnowledgeBaseEntity>> = _knowledgeBases.asStateFlow()

    init {
        viewModelScope.launch {
            settingsManager.settings.collect { settings ->
                _providers.value = settings.providers
            }
        }

        viewModelScope.launch {
            database.knowledgeBaseDao().getAllBases().collect { bases ->
                _knowledgeBases.value = bases
            }
        }
    }

    private fun writeTempFileToUri(tempFile: File, uri: Uri) {
        context.contentResolver.openOutputStream(uri)?.use { output ->
            tempFile.inputStream().use { input ->
                input.copyTo(output)
            }
        } ?: throw IllegalArgumentException("无法写入所选文件")
    }

    // ============= 供应商配置导出导入 =============

    fun exportProvidersToFile(providerIds: List<String>, outputFile: File, encrypted: Boolean, password: String?) {
        viewModelScope.launch {
            _uiState.value = ExportImportUiState.Loading("导出供应商配置...")
            try {
                exportImportManager.exportProvidersToFile(providerIds, outputFile, encrypted, password)
                _uiState.value = ExportImportUiState.Success("成功导出 ${providerIds.size} 个供应商配置")
            } catch (e: Exception) {
                _uiState.value = ExportImportUiState.Error("导出失败: ${e.message}")
            }
        }
    }

    fun exportProvidersToUri(providerIds: List<String>, uri: Uri, encrypted: Boolean, password: String?) {
        viewModelScope.launch {
            if (providerIds.isEmpty()) {
                _uiState.value = ExportImportUiState.Error("请选择要导出的供应商")
                return@launch
            }
            if (encrypted && password.isNullOrBlank()) {
                _uiState.value = ExportImportUiState.Error("请输入加密密码")
                return@launch
            }

            _uiState.value = ExportImportUiState.Loading("导出供应商配置...")
            val tempFile = File(context.cacheDir, "providers_export_${System.currentTimeMillis()}.json")
            try {
                exportImportManager.exportProvidersToFile(providerIds, tempFile, encrypted, password)
                writeTempFileToUri(tempFile, uri)
                _uiState.value = ExportImportUiState.Success("成功导出 ${providerIds.size} 个供应商配置")
            } catch (e: Exception) {
                _uiState.value = ExportImportUiState.Error("导出失败: ${e.message}")
            } finally {
                tempFile.delete()
            }
        }
    }

    fun exportProvidersToQRCode(providerIds: List<String>, encrypted: Boolean, password: String?, onSuccess: (Bitmap) -> Unit) {
        viewModelScope.launch {
            _uiState.value = ExportImportUiState.Loading("生成二维码...")
            try {
                val qrCode = exportImportManager.exportProvidersToQRCode(providerIds, encrypted, password)
                if (qrCode != null) {
                    onSuccess(qrCode)
                    _uiState.value = ExportImportUiState.Idle
                } else {
                    _uiState.value = ExportImportUiState.Error("生成二维码失败")
                }
            } catch (e: Exception) {
                _uiState.value = ExportImportUiState.Error("生成二维码失败: ${e.message}")
            }
        }
    }

    fun importProvidersFromFile(inputFile: File, password: String?) {
        viewModelScope.launch {
            _uiState.value = ExportImportUiState.Loading("导入供应商配置...")
            try {
                val count = exportImportManager.importProvidersFromFile(inputFile, password)
                _uiState.value = ExportImportUiState.Success("成功导入 $count 个供应商配置")
            } catch (e: Exception) {
                _uiState.value = ExportImportUiState.Error("导入失败: ${e.message}")
            }
        }
    }

    fun importProvidersFromQRCode(bitmap: Bitmap, password: String?) {
        viewModelScope.launch {
            _uiState.value = ExportImportUiState.Loading("从二维码导入...")
            try {
                val count = exportImportManager.importProvidersFromQRCode(bitmap, password)
                _uiState.value = ExportImportUiState.Success("成功导入 $count 个供应商配置")
            } catch (e: Exception) {
                _uiState.value = ExportImportUiState.Error("导入失败: ${e.message}")
            }
        }
    }

    fun importProvidersFromExportData(exportData: ExportData) {
        viewModelScope.launch {
            _uiState.value = ExportImportUiState.Loading("从二维码导入...")
            try {
                val count = exportImportManager.importProvidersFromExportData(exportData)
                _uiState.value = ExportImportUiState.Success("成功导入 $count 个供应商配置")
            } catch (e: Exception) {
                _uiState.value = ExportImportUiState.Error("导入失败: ${e.message}")
            }
        }
    }

    // ============= API配置导出导入 =============

    fun exportApiConfigToFile(providerId: String, outputFile: File, encrypted: Boolean = true, password: String?) {
        viewModelScope.launch {
            _uiState.value = ExportImportUiState.Loading("导出API配置...")
            try {
                exportImportManager.exportApiConfigToFile(providerId, outputFile, encrypted, password)
                _uiState.value = ExportImportUiState.Success("成功导出API配置")
            } catch (e: Exception) {
                _uiState.value = ExportImportUiState.Error("导出失败: ${e.message}")
            }
        }
    }

    fun exportApiConfigToUri(providerId: String, uri: Uri, encrypted: Boolean = true, password: String?) {
        viewModelScope.launch {
            if (encrypted && password.isNullOrBlank()) {
                _uiState.value = ExportImportUiState.Error("请输入加密密码")
                return@launch
            }

            _uiState.value = ExportImportUiState.Loading("导出API配置...")
            val tempFile = File(context.cacheDir, "api_config_export_${System.currentTimeMillis()}.json")
            try {
                exportImportManager.exportApiConfigToFile(providerId, tempFile, encrypted, password)
                writeTempFileToUri(tempFile, uri)
                _uiState.value = ExportImportUiState.Success("成功导出API配置")
            } catch (e: Exception) {
                _uiState.value = ExportImportUiState.Error("导出失败: ${e.message}")
            } finally {
                tempFile.delete()
            }
        }
    }

    fun exportApiConfigToQRCode(providerId: String, encrypted: Boolean = true, password: String?, onSuccess: (Bitmap) -> Unit) {
        viewModelScope.launch {
            _uiState.value = ExportImportUiState.Loading("生成二维码...")
            try {
                val qrCode = exportImportManager.exportApiConfigToQRCode(providerId, encrypted, password)
                if (qrCode != null) {
                    onSuccess(qrCode)
                    _uiState.value = ExportImportUiState.Idle
                } else {
                    _uiState.value = ExportImportUiState.Error("生成二维码失败")
                }
            } catch (e: Exception) {
                _uiState.value = ExportImportUiState.Error("生成二维码失败: ${e.message}")
            }
        }
    }

    fun importApiConfigFromFile(inputFile: File, password: String) {
        viewModelScope.launch {
            _uiState.value = ExportImportUiState.Loading("导入API配置...")
            try {
                val provider = exportImportManager.importApiConfigFromFile(inputFile, password)
                _uiState.value = ExportImportUiState.Success("成功导入API配置: ${provider.name}")
            } catch (e: Exception) {
                _uiState.value = ExportImportUiState.Error("导入失败: ${e.message}")
            }
        }
    }

    fun importApiConfigFromExportData(exportData: ExportData) {
        viewModelScope.launch {
            _uiState.value = ExportImportUiState.Loading("从二维码导入...")
            try {
                val provider = exportImportManager.importApiConfigFromExportData(exportData)
                _uiState.value = ExportImportUiState.Success("成功导入API配置: ${provider.name}")
            } catch (e: Exception) {
                _uiState.value = ExportImportUiState.Error("导入失败: ${e.message}")
            }
        }
    }

    // ============= 知识库导出导入 =============

    fun exportKnowledgeBaseToFile(knowledgeBaseId: String, outputFile: File) {
        viewModelScope.launch {
            _uiState.value = ExportImportUiState.Loading("导出知识库...")
            try {
                exportImportManager.exportKnowledgeBaseToFile(knowledgeBaseId, outputFile)
                _uiState.value = ExportImportUiState.Success("成功导出知识库")
            } catch (e: Exception) {
                _uiState.value = ExportImportUiState.Error("导出失败: ${e.message}")
            }
        }
    }

    fun exportKnowledgeBaseToUri(knowledgeBaseId: String, uri: Uri) {
        viewModelScope.launch {
            _uiState.value = ExportImportUiState.Loading("导出知识库...")
            val tempFile = File(context.cacheDir, "knowledge_base_export_${System.currentTimeMillis()}.json")
            try {
                exportImportManager.exportKnowledgeBaseToFile(knowledgeBaseId, tempFile)
                writeTempFileToUri(tempFile, uri)
                _uiState.value = ExportImportUiState.Success("成功导出知识库")
            } catch (e: Exception) {
                _uiState.value = ExportImportUiState.Error("导出失败: ${e.message}")
            } finally {
                tempFile.delete()
            }
        }
    }

    fun importKnowledgeBaseFromFile(inputFile: File) {
        viewModelScope.launch {
            _uiState.value = ExportImportUiState.Loading("导入知识库...")
            try {
                val newId = exportImportManager.importKnowledgeBaseFromFile(inputFile)
                _uiState.value = ExportImportUiState.Success("成功导入知识库 (ID: $newId)")
            } catch (e: Exception) {
                _uiState.value = ExportImportUiState.Error("导入失败: ${e.message}")
            }
        }
    }

    fun importKnowledgeBaseFromExportData(exportData: ExportData) {
        viewModelScope.launch {
            _uiState.value = ExportImportUiState.Loading("从二维码导入...")
            try {
                val newId = exportImportManager.importKnowledgeBaseFromExportData(exportData)
                _uiState.value = ExportImportUiState.Success("成功导入知识库 (ID: $newId)")
            } catch (e: Exception) {
                _uiState.value = ExportImportUiState.Error("导入失败: ${e.message}")
            }
        }
    }

    // ============= 数据库备份恢复 =============

    /**
     * 生成备份文件名
     */
    fun generateBackupFileName(): String {
        return databaseBackupManager.generateBackupFileName()
    }

    /**
     * 备份数据库到 URI
     */
    fun backupDatabaseToUri(uri: Uri) {
        viewModelScope.launch {
            _uiState.value = ExportImportUiState.Loading("正在备份数据库...")
            val tempFile = File(context.cacheDir, "database_backup_${System.currentTimeMillis()}.zip")
            try {
                val result = databaseBackupManager.backupDatabase(tempFile)
                result.fold(
                    onSuccess = { backupInfo ->
                        // 将临时文件写入用户选择的位置
                        context.contentResolver.openOutputStream(uri)?.use { output ->
                            tempFile.inputStream().use { input ->
                                input.copyTo(output)
                            }
                        } ?: throw IllegalArgumentException("无法写入所选文件")
                        _uiState.value = ExportImportUiState.Success(
                            "备份成功！包含 ${backupInfo.fileCount} 个文件"
                        )
                    },
                    onFailure = { error ->
                        _uiState.value = ExportImportUiState.Error("备份失败: ${error.message}")
                    }
                )
            } catch (e: Exception) {
                _uiState.value = ExportImportUiState.Error("备份失败: ${e.message}")
            } finally {
                tempFile.delete()
            }
        }
    }

    /**
     * 从 URI 恢复数据库
     */
    fun restoreDatabaseFromUri(uri: Uri) {
        viewModelScope.launch {
            _uiState.value = ExportImportUiState.Loading("正在恢复数据库...")
            val tempFile = File(context.cacheDir, "database_restore_${System.currentTimeMillis()}.zip")
            try {
                // 将用户选择的文件复制到临时位置
                context.contentResolver.openInputStream(uri)?.use { input ->
                    tempFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                } ?: throw IllegalArgumentException("无法读取所选文件")

                val result = databaseBackupManager.restoreDatabase(tempFile)
                result.fold(
                    onSuccess = { restoreInfo ->
                        _uiState.value = ExportImportUiState.Success(
                            "恢复成功！已恢复 ${restoreInfo.restoredFileCount} 个文件，请重启应用"
                        )
                    },
                    onFailure = { error ->
                        _uiState.value = ExportImportUiState.Error("恢复失败: ${error.message}")
                    }
                )
            } catch (e: Exception) {
                _uiState.value = ExportImportUiState.Error("恢复失败: ${e.message}")
            } finally {
                tempFile.delete()
            }
        }
    }

    // ============= UI 状态管理 =============

    fun toggleProviderSelection(providerId: String) {
        val current = _selectedProviderIds.value.toMutableSet()
        if (providerId in current) {
            current.remove(providerId)
        } else {
            current.add(providerId)
        }
        _selectedProviderIds.value = current
    }

    fun clearProviderSelection() {
        _selectedProviderIds.value = emptySet()
    }

    fun clearUiState() {
        _uiState.value = ExportImportUiState.Idle
    }
}

/**
 * UI 状态
 */
sealed class ExportImportUiState {
    data object Idle : ExportImportUiState()
    data class Loading(val message: String) : ExportImportUiState()
    data class Success(val message: String) : ExportImportUiState()
    data class Error(val message: String) : ExportImportUiState()
}
