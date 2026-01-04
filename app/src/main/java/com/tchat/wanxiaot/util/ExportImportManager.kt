package com.tchat.wanxiaot.util

import android.content.Context
import android.graphics.Bitmap
import com.tchat.data.database.AppDatabase
import com.tchat.wanxiaot.settings.ProviderConfig
import com.tchat.wanxiaot.settings.SettingsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File
import java.util.UUID

/**
 * 导出导入管理器
 * 统一管理所有数据的导出和导入
 */
class ExportImportManager(private val context: Context) {
    private val settingsManager = SettingsManager(context)
    private val database = AppDatabase.getInstance(context)

    // ============= 供应商配置导出导入 =============

    /**
     * 导出多个供应商配置到文件
     * @param providerIds 要导出的供应商ID列表
     * @param outputFile 输出文件路径
     * @param encrypted 是否加密
     * @param password 加密密码（如果encrypted为true）
     */
    suspend fun exportProvidersToFile(
        providerIds: List<String>,
        outputFile: File,
        encrypted: Boolean = false,
        password: String? = null
    ) = withContext(Dispatchers.IO) {
        val settings = settingsManager.settings.value
        val providers = settings.providers.filter { it.id in providerIds }

        val exportData = ProvidersExportData(providers)
        val wrappedData = ExportData(
            type = ExportDataType.PROVIDERS,
            encrypted = encrypted,
            data = exportData.toJson()
        )

        val content = if (encrypted && password != null) {
            EncryptionUtils.encrypt(wrappedData.toJson(), password)
        } else {
            wrappedData.toJson()
        }

        outputFile.writeText(content)
    }

    /**
     * 导出供应商配置到二维码
     * @param providerIds 要导出的供应商ID列表
     * @param encrypted 是否加密
     * @param password 加密密码（如果encrypted为true）
     * @param size 二维码尺寸
     * @return 二维码Bitmap
     */
    suspend fun exportProvidersToQRCode(
        providerIds: List<String>,
        encrypted: Boolean = false,
        password: String? = null,
        size: Int = 512
    ): Bitmap? = withContext(Dispatchers.Default) {
        val settings = settingsManager.settings.value
        val providers = settings.providers.filter { it.id in providerIds }

        val exportData = ProvidersExportData(providers)
        val wrappedData = ExportData(
            type = ExportDataType.PROVIDERS,
            encrypted = encrypted,
            data = exportData.toJson()
        )

        QRCodeUtils.exportDataToQRCode(wrappedData, password, size)
    }

    /**
     * 从文件导入供应商配置
     * @param inputFile 输入文件
     * @param password 解密密码（如果文件已加密）
     * @return 导入的供应商数量
     */
    suspend fun importProvidersFromFile(
        inputFile: File,
        password: String? = null
    ): Int = withContext(Dispatchers.IO) {
        val content = inputFile.readText()

        val jsonContent = if (password != null) {
            try {
                EncryptionUtils.decrypt(content, password)
            } catch (e: Exception) {
                throw IllegalArgumentException("密码错误或文件损坏")
            }
        } else {
            content
        }

        val wrappedData = ExportData.fromJson(jsonContent)
        if (wrappedData.type != ExportDataType.PROVIDERS) {
            throw IllegalArgumentException("文件类型不匹配，期望: PROVIDERS，实际: ${wrappedData.type}")
        }

        val exportData = ProvidersExportData.fromJson(wrappedData.data)
        val settings = settingsManager.settings.value

        // 合并供应商配置（防止ID冲突）
        val existingIds = settings.providers.map { it.id }.toSet()
        val newProviders = exportData.providers.map { provider ->
            if (provider.id in existingIds) {
                // 生成新ID
                provider.copy(id = UUID.randomUUID().toString())
            } else {
                provider
            }
        }

        val updatedSettings = settings.copy(
            providers = settings.providers + newProviders
        )
        settingsManager.updateSettings(updatedSettings)

        newProviders.size
    }

    /**
     * 从二维码导入供应商配置
     * @param bitmap 包含二维码的Bitmap
     * @param password 解密密码（如果二维码已加密）
     * @return 导入的供应商数量
     */
    suspend fun importProvidersFromQRCode(
        bitmap: Bitmap,
        password: String? = null
    ): Int = withContext(Dispatchers.Default) {
        val wrappedData = QRCodeUtils.qrCodeToExportData(bitmap, password)
            ?: throw IllegalArgumentException("二维码解析失败")

        if (wrappedData.type != ExportDataType.PROVIDERS) {
            throw IllegalArgumentException("二维码类型不匹配，期望: PROVIDERS，实际: ${wrappedData.type}")
        }

        val exportData = ProvidersExportData.fromJson(wrappedData.data)
        val settings = settingsManager.settings.value

        // 合并供应商配置（防止ID冲突）
        val existingIds = settings.providers.map { it.id }.toSet()
        val newProviders = exportData.providers.map { provider ->
            if (provider.id in existingIds) {
                provider.copy(id = UUID.randomUUID().toString())
            } else {
                provider
            }
        }

        val updatedSettings = settings.copy(
            providers = settings.providers + newProviders
        )
        settingsManager.updateSettings(updatedSettings)

        newProviders.size
    }

    // ============= 单个供应商模型列表导出导入 =============

    /**
     * 导出单个供应商的模型列表到文件
     */
    suspend fun exportModelsToFile(
        providerId: String,
        outputFile: File,
        encrypted: Boolean = false,
        password: String? = null
    ) = withContext(Dispatchers.IO) {
        val settings = settingsManager.settings.value
        val provider = settings.providers.find { it.id == providerId }
            ?: throw IllegalArgumentException("供应商不存在")

        val exportData = ModelsExportData(
            providerId = provider.id,
            providerName = provider.name,
            providerType = provider.providerType.name,
            models = provider.availableModels
        )

        val wrappedData = ExportData(
            type = ExportDataType.MODELS,
            encrypted = encrypted,
            data = exportData.toJson()
        )

        val content = if (encrypted && password != null) {
            EncryptionUtils.encrypt(wrappedData.toJson(), password)
        } else {
            wrappedData.toJson()
        }

        outputFile.writeText(content)
    }

    /**
     * 导出单个供应商的模型列表到二维码
     */
    suspend fun exportModelsToQRCode(
        providerId: String,
        encrypted: Boolean = false,
        password: String? = null,
        size: Int = 512
    ): Bitmap? = withContext(Dispatchers.Default) {
        val settings = settingsManager.settings.value
        val provider = settings.providers.find { it.id == providerId }
            ?: throw IllegalArgumentException("供应商不存在")

        val exportData = ModelsExportData(
            providerId = provider.id,
            providerName = provider.name,
            providerType = provider.providerType.name,
            models = provider.availableModels
        )

        val wrappedData = ExportData(
            type = ExportDataType.MODELS,
            encrypted = encrypted,
            data = exportData.toJson()
        )

        QRCodeUtils.exportDataToQRCode(wrappedData, password, size)
    }

    /**
     * 从文件导入模型列表到指定供应商
     */
    suspend fun importModelsFromFile(
        inputFile: File,
        targetProviderId: String,
        password: String? = null
    ) = withContext(Dispatchers.IO) {
        val content = inputFile.readText()

        val jsonContent = if (password != null) {
            try {
                EncryptionUtils.decrypt(content, password)
            } catch (e: Exception) {
                throw IllegalArgumentException("密码错误或文件损坏")
            }
        } else {
            content
        }

        val wrappedData = ExportData.fromJson(jsonContent)
        if (wrappedData.type != ExportDataType.MODELS) {
            throw IllegalArgumentException("文件类型不匹配")
        }

        val exportData = ModelsExportData.fromJson(wrappedData.data)
        val settings = settingsManager.settings.value

        val updatedProviders = settings.providers.map { provider ->
            if (provider.id == targetProviderId) {
                // 合并模型列表（去重）
                val mergedModels = (provider.availableModels + exportData.models).distinct()
                provider.copy(availableModels = mergedModels)
            } else {
                provider
            }
        }

        val updatedSettings = settings.copy(providers = updatedProviders)
        settingsManager.updateSettings(updatedSettings)
    }

    // ============= API配置（含密钥）导出导入 =============

    /**
     * 导出单个供应商的完整API配置（包括密钥）
     */
    suspend fun exportApiConfigToFile(
        providerId: String,
        outputFile: File,
        encrypted: Boolean = true,
        password: String? = null
    ) = withContext(Dispatchers.IO) {
        if (encrypted && password == null) {
            throw IllegalArgumentException("导出API配置时必须提供加密密码")
        }

        val settings = settingsManager.settings.value
        val provider = settings.providers.find { it.id == providerId }
            ?: throw IllegalArgumentException("供应商不存在")

        val exportData = ApiConfigExportData(provider)
        val wrappedData = ExportData(
            type = ExportDataType.API_CONFIG,
            encrypted = encrypted,
            data = exportData.toJson()
        )

        val content = if (encrypted && password != null) {
            EncryptionUtils.encrypt(wrappedData.toJson(), password)
        } else {
            wrappedData.toJson()
        }

        outputFile.writeText(content)
    }

    /**
     * 导出API配置到二维码（强烈建议加密）
     */
    suspend fun exportApiConfigToQRCode(
        providerId: String,
        encrypted: Boolean = true,
        password: String? = null,
        size: Int = 512
    ): Bitmap? = withContext(Dispatchers.Default) {
        if (encrypted && password == null) {
            throw IllegalArgumentException("导出API配置时必须提供加密密码")
        }

        val settings = settingsManager.settings.value
        val provider = settings.providers.find { it.id == providerId }
            ?: throw IllegalArgumentException("供应商不存在")

        val exportData = ApiConfigExportData(provider)
        val wrappedData = ExportData(
            type = ExportDataType.API_CONFIG,
            encrypted = encrypted,
            data = exportData.toJson()
        )

        QRCodeUtils.exportDataToQRCode(wrappedData, password, size)
    }

    /**
     * 从文件导入API配置
     */
    suspend fun importApiConfigFromFile(
        inputFile: File,
        password: String
    ): ProviderConfig = withContext(Dispatchers.IO) {
        val content = inputFile.readText()

        val jsonContent = try {
            EncryptionUtils.decrypt(content, password)
        } catch (e: Exception) {
            // 尝试不解密直接解析
            content
        }

        val wrappedData = ExportData.fromJson(jsonContent)
        if (wrappedData.type != ExportDataType.API_CONFIG) {
            throw IllegalArgumentException("文件类型不匹配")
        }

        val exportData = ApiConfigExportData.fromJson(wrappedData.data)
        val settings = settingsManager.settings.value

        // 检查ID冲突
        val existingIds = settings.providers.map { it.id }.toSet()
        val newProvider = if (exportData.provider.id in existingIds) {
            exportData.provider.copy(id = UUID.randomUUID().toString())
        } else {
            exportData.provider
        }

        val updatedSettings = settings.copy(
            providers = settings.providers + newProvider
        )
        settingsManager.updateSettings(updatedSettings)

        newProvider
    }

    // ============= 知识库导出导入 =============

    /**
     * 导出知识库到文件
     */
    suspend fun exportKnowledgeBaseToFile(
        knowledgeBaseId: String,
        outputFile: File
    ) = withContext(Dispatchers.IO) {
        val kbDao = database.knowledgeBaseDao()
        val itemDao = database.knowledgeItemDao()
        val chunkDao = database.knowledgeChunkDao()

        val kbEntity = kbDao.getBaseById(knowledgeBaseId)
            ?: throw IllegalArgumentException("知识库不存在")

        val items = itemDao.getItemsByBaseIdSync(knowledgeBaseId)
        val chunks = mutableListOf<KnowledgeChunkInfo>()

        items.forEach { item ->
            val itemChunks = chunkDao.getChunksByItemId(item.id)
            itemChunks.forEach { chunk ->
                chunks.add(
                    KnowledgeChunkInfo(
                        id = chunk.id,
                        itemId = chunk.itemId,
                        knowledgeBaseId = chunk.knowledgeBaseId,
                        content = chunk.content,
                        embedding = jsonToFloatArray(chunk.embedding),
                        chunkIndex = chunk.chunkIndex,
                        createdAt = chunk.createdAt
                    )
                )
            }
        }

        val kbInfo = KnowledgeBaseInfo(
            id = kbEntity.id,
            name = kbEntity.name,
            description = kbEntity.description,
            embeddingProviderId = kbEntity.embeddingProviderId,
            embeddingModelId = kbEntity.embeddingModelId
        )

        val itemInfos = items.map { item ->
            KnowledgeItemInfo(
                id = item.id,
                knowledgeBaseId = item.knowledgeBaseId,
                title = item.title,
                sourceType = item.sourceType,
                sourceUri = item.sourceUri,
                content = item.content,
                metadata = item.metadata,
                status = item.status,
                errorMessage = item.errorMessage,
                createdAt = item.createdAt,
                updatedAt = item.updatedAt
            )
        }

        val exportData = KnowledgeBaseExportData(kbInfo, itemInfos, chunks)
        val wrappedData = ExportData(
            type = ExportDataType.KNOWLEDGE_BASE,
            encrypted = false,
            data = exportData.toJson()
        )

        outputFile.writeText(wrappedData.toJson())
    }

    /**
     * 从文件导入知识库
     */
    suspend fun importKnowledgeBaseFromFile(
        inputFile: File
    ): String = withContext(Dispatchers.IO) {
        val content = inputFile.readText()
        val wrappedData = ExportData.fromJson(content)

        if (wrappedData.type != ExportDataType.KNOWLEDGE_BASE) {
            throw IllegalArgumentException("文件类型不匹配")
        }

        val exportData = KnowledgeBaseExportData.fromJson(wrappedData.data)
        val kbDao = database.knowledgeBaseDao()
        val itemDao = database.knowledgeItemDao()
        val chunkDao = database.knowledgeChunkDao()

        // 检查ID冲突，生成新ID
        val newKbId = UUID.randomUUID().toString()
        val kb = exportData.knowledgeBase

        // 插入知识库
        val kbEntity = com.tchat.data.database.entity.KnowledgeBaseEntity(
            id = newKbId,
            name = kb.name,
            description = kb.description,
            embeddingProviderId = kb.embeddingProviderId,
            embeddingModelId = kb.embeddingModelId
        )
        kbDao.insertBase(kbEntity)

        // 插入知识库项目
        val idMapping = mutableMapOf<String, String>()
        exportData.items.forEach { item ->
            val newItemId = UUID.randomUUID().toString()
            idMapping[item.id] = newItemId

            val itemEntity = com.tchat.data.database.entity.KnowledgeItemEntity(
                id = newItemId,
                knowledgeBaseId = newKbId,
                title = item.title,
                content = item.content ?: "",
                sourceType = item.sourceType.ifBlank { "text" },
                sourceUri = item.sourceUri,
                metadata = item.metadata,
                status = item.status.ifBlank { "PENDING" },
                errorMessage = item.errorMessage,
                createdAt = item.createdAt,
                updatedAt = item.updatedAt
            )
            itemDao.insertItem(itemEntity)
        }

        // 插入向量数据块
        exportData.chunks.forEach { chunk ->
            val newItemId = idMapping[chunk.itemId] ?: return@forEach
            val chunkEntity = com.tchat.data.database.entity.KnowledgeChunkEntity(
                id = UUID.randomUUID().toString(),
                itemId = newItemId,
                knowledgeBaseId = newKbId,
                content = chunk.content,
                embedding = floatArrayToJson(chunk.embedding),
                chunkIndex = chunk.chunkIndex,
                createdAt = chunk.createdAt
            )
            chunkDao.insertChunk(chunkEntity)
        }

        newKbId
    }

    private fun jsonToFloatArray(json: String): FloatArray {
        return runCatching {
            val jsonArray = JSONArray(json)
            FloatArray(jsonArray.length()) { i ->
                jsonArray.getDouble(i).toFloat()
            }
        }.getOrDefault(FloatArray(0))
    }

    private fun floatArrayToJson(array: FloatArray): String {
        val jsonArray = JSONArray()
        array.forEach { value -> jsonArray.put(value.toDouble()) }
        return jsonArray.toString()
    }
}
