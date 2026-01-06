package com.tchat.wanxiaot.ui.settings

import android.content.Context
import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tchat.wanxiaot.settings.ProviderConfig
import com.tchat.wanxiaot.settings.SettingsManager
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

    private val exportImportManager = ExportImportManager(context)

    private val _uiState = MutableStateFlow<ExportImportUiState>(ExportImportUiState.Idle)
    val uiState: StateFlow<ExportImportUiState> = _uiState.asStateFlow()

    private val _providers = MutableStateFlow<List<ProviderConfig>>(emptyList())
    val providers: StateFlow<List<ProviderConfig>> = _providers.asStateFlow()

    private val _selectedProviderIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedProviderIds: StateFlow<Set<String>> = _selectedProviderIds.asStateFlow()

    init {
        viewModelScope.launch {
            settingsManager.settings.collect { settings ->
                _providers.value = settings.providers
            }
        }
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

    // ============= 模型列表导出导入 =============

    fun exportModelsToFile(providerId: String, outputFile: File, encrypted: Boolean, password: String?) {
        viewModelScope.launch {
            _uiState.value = ExportImportUiState.Loading("导出模型列表...")
            try {
                exportImportManager.exportModelsToFile(providerId, outputFile, encrypted, password)
                _uiState.value = ExportImportUiState.Success("成功导出模型列表")
            } catch (e: Exception) {
                _uiState.value = ExportImportUiState.Error("导出失败: ${e.message}")
            }
        }
    }

    fun exportModelsToQRCode(providerId: String, encrypted: Boolean, password: String?, onSuccess: (Bitmap) -> Unit) {
        viewModelScope.launch {
            _uiState.value = ExportImportUiState.Loading("生成二维码...")
            try {
                val qrCode = exportImportManager.exportModelsToQRCode(providerId, encrypted, password)
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

    fun importModelsFromFile(inputFile: File, targetProviderId: String, password: String?) {
        viewModelScope.launch {
            _uiState.value = ExportImportUiState.Loading("导入模型列表...")
            try {
                exportImportManager.importModelsFromFile(inputFile, targetProviderId, password)
                _uiState.value = ExportImportUiState.Success("成功导入模型列表")
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
