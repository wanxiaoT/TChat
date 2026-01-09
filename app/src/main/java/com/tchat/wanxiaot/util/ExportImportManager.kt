package com.tchat.wanxiaot.util

import android.content.Context
import android.graphics.Bitmap
import com.tchat.data.database.AppDatabase
import com.tchat.wanxiaot.settings.AppSettings
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
class ExportImportManager(
    private val context: Context,
    private val settingsManager: SettingsManager = SettingsManager(context)
) {
    private val database = AppDatabase.getInstance(context)

    private fun normalizedPassword(password: String?): String? = password?.takeIf { it.isNotBlank() }

    private fun normalizeImportedText(raw: String): String {
        // `trim()` does not reliably remove BOM, so strip it explicitly first.
        return raw.removePrefix("\uFEFF").trim()
    }

    private fun decryptImportPayload(raw: String, password: String): String {
        val trimmed = raw.trim()
        return try {
            EncryptionUtils.decrypt(trimmed, password)
        } catch (e: Exception) {
            // Some sharing channels may inject whitespace/newlines into base64 payloads.
            val compact = trimmed.filterNot(Char::isWhitespace)
            EncryptionUtils.decrypt(compact, password)
        }
    }

    private fun ensureCurrentProviderIsValid(settings: AppSettings, preferredProviderId: String? = null): AppSettings {
        val hasValidCurrent = settings.currentProviderId.isNotBlank() &&
                settings.providers.any { it.id == settings.currentProviderId }
        if (hasValidCurrent) return settings

        val chosenProvider = settings.providers.find { it.id == preferredProviderId }
            ?: settings.providers.firstOrNull()
            ?: return settings.copy(currentProviderId = "", currentModel = "")

        val defaultModel = chosenProvider.selectedModel.ifEmpty {
            chosenProvider.availableModels.firstOrNull() ?: ""
        }
        return settings.copy(currentProviderId = chosenProvider.id, currentModel = defaultModel)
    }

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
        if (encrypted && normalizedPassword(password) == null) {
            throw IllegalArgumentException("加密导出需要密码")
        }

        val settings = settingsManager.settings.value
        val providers = settings.providers.filter { it.id in providerIds }

        val exportData = ProvidersExportData(providers)
        val wrappedData = ExportData(
            type = ExportDataType.PROVIDERS,
            encrypted = encrypted,
            data = exportData.toJson()
        )

        val content = if (encrypted) {
            EncryptionUtils.encrypt(wrappedData.toJson(), normalizedPassword(password)!!)
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
        if (encrypted && normalizedPassword(password) == null) {
            throw IllegalArgumentException("加密导出需要密码")
        }

        val settings = settingsManager.settings.value
        val providers = settings.providers.filter { it.id in providerIds }

        val exportData = ProvidersExportData(providers)
        val wrappedData = ExportData(
            type = ExportDataType.PROVIDERS,
            encrypted = encrypted,
            data = exportData.toJson()
        )

        val qrPassword = if (encrypted) normalizedPassword(password) else null
        QRCodeUtils.exportDataToQRCode(wrappedData, qrPassword, size)
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
        val content = normalizeImportedText(inputFile.readText())

        val wrappedData = runCatching { ExportData.fromJson(content) }.getOrNull()
            ?: run {
                val normalized = normalizedPassword(password)
                    ?: throw IllegalArgumentException("文件可能已加密，请输入密码")
                val decrypted = runCatching { decryptImportPayload(content, normalized) }.getOrElse {
                    throw IllegalArgumentException("密码错误或文件损坏")
                }
                runCatching { ExportData.fromJson(decrypted) }.getOrElse {
                    throw IllegalArgumentException("文件内容无效")
                }
            }
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
        settingsManager.updateSettings(
            ensureCurrentProviderIsValid(
                settings = updatedSettings,
                preferredProviderId = newProviders.firstOrNull()?.id
            )
        )

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
        val wrappedData = QRCodeUtils.qrCodeToExportData(bitmap, normalizedPassword(password))
            ?: throw IllegalArgumentException("二维码解析失败")

        importProvidersFromExportData(wrappedData)
    }

    /**
     * 从已解析的导出数据导入供应商配置（通常来自二维码扫描）
     */
    suspend fun importProvidersFromExportData(
        exportData: ExportData
    ): Int = withContext(Dispatchers.IO) {
        if (exportData.type != ExportDataType.PROVIDERS) {
            throw IllegalArgumentException("数据类型不匹配，期望: PROVIDERS，实际: ${exportData.type}")
        }

        val providersData = ProvidersExportData.fromJson(exportData.data)
        val settings = settingsManager.settings.value

        // 合并供应商配置（防止ID冲突）
        val existingIds = settings.providers.map { it.id }.toSet()
        val newProviders = providersData.providers.map { provider ->
            if (provider.id in existingIds) {
                provider.copy(id = UUID.randomUUID().toString())
            } else {
                provider
            }
        }

        val updatedSettings = settings.copy(
            providers = settings.providers + newProviders
        )
        settingsManager.updateSettings(
            ensureCurrentProviderIsValid(
                settings = updatedSettings,
                preferredProviderId = newProviders.firstOrNull()?.id
            )
        )

        newProviders.size
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
        if (encrypted && normalizedPassword(password) == null) {
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

        val content = if (encrypted) {
            EncryptionUtils.encrypt(wrappedData.toJson(), normalizedPassword(password)!!)
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
        if (encrypted && normalizedPassword(password) == null) {
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

        val qrPassword = if (encrypted) normalizedPassword(password) else null
        QRCodeUtils.exportDataToQRCode(wrappedData, qrPassword, size)
    }

    /**
     * 从文件导入API配置
     */
    suspend fun importApiConfigFromFile(
        inputFile: File,
        password: String
    ): ProviderConfig = withContext(Dispatchers.IO) {
        val content = normalizeImportedText(inputFile.readText())

        val wrappedData = runCatching { ExportData.fromJson(content) }.getOrNull()
            ?: run {
                val normalized = normalizedPassword(password)
                    ?: throw IllegalArgumentException("文件可能已加密，请输入密码")
                val decrypted = runCatching { decryptImportPayload(content, normalized) }.getOrElse {
                    throw IllegalArgumentException("密码错误或文件损坏")
                }
                runCatching { ExportData.fromJson(decrypted) }.getOrElse {
                    throw IllegalArgumentException("文件内容无效")
                }
            }
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

        val updatedSettings = settings.copy(providers = settings.providers + newProvider)
        settingsManager.updateSettings(
            ensureCurrentProviderIsValid(
                settings = updatedSettings,
                preferredProviderId = newProvider.id
            )
        )

        newProvider
    }

    /**
     * 从已解析的导出数据导入API配置（通常来自二维码扫描）
     */
    suspend fun importApiConfigFromExportData(
        exportData: ExportData
    ): ProviderConfig = withContext(Dispatchers.IO) {
        if (exportData.type != ExportDataType.API_CONFIG) {
            throw IllegalArgumentException("数据类型不匹配，期望: API_CONFIG，实际: ${exportData.type}")
        }

        val apiData = ApiConfigExportData.fromJson(exportData.data)
        val settings = settingsManager.settings.value

        // 检查ID冲突
        val existingIds = settings.providers.map { it.id }.toSet()
        val newProvider = if (apiData.provider.id in existingIds) {
            apiData.provider.copy(id = UUID.randomUUID().toString())
        } else {
            apiData.provider
        }

        val updatedSettings = settings.copy(providers = settings.providers + newProvider)
        settingsManager.updateSettings(
            ensureCurrentProviderIsValid(
                settings = updatedSettings,
                preferredProviderId = newProvider.id
            )
        )

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
        val content = normalizeImportedText(inputFile.readText())
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

    /**
     * 从已解析的导出数据导入知识库（通常来自二维码扫描；知识库默认不支持二维码，但此方法保持通用）
     */
    suspend fun importKnowledgeBaseFromExportData(
        exportData: ExportData
    ): String = withContext(Dispatchers.IO) {
        if (exportData.type != ExportDataType.KNOWLEDGE_BASE) {
            throw IllegalArgumentException("数据类型不匹配，期望: KNOWLEDGE_BASE，实际: ${exportData.type}")
        }

        val kbData = KnowledgeBaseExportData.fromJson(exportData.data)
        val kbDao = database.knowledgeBaseDao()
        val itemDao = database.knowledgeItemDao()
        val chunkDao = database.knowledgeChunkDao()

        val newKbId = UUID.randomUUID().toString()
        val kb = kbData.knowledgeBase

        val kbEntity = com.tchat.data.database.entity.KnowledgeBaseEntity(
            id = newKbId,
            name = kb.name,
            description = kb.description,
            embeddingProviderId = kb.embeddingProviderId,
            embeddingModelId = kb.embeddingModelId
        )
        kbDao.insertBase(kbEntity)

        val idMapping = mutableMapOf<String, String>()
        kbData.items.forEach { item ->
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

        kbData.chunks.forEach { chunk ->
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

    // ============= Skills 导出导入 =============

    /**
     * 导出 Skills 到文件
     * @param skillIds 要导出的 Skill ID 列表，如果为空则导出所有
     * @param outputFile 输出文件路径
     */
    suspend fun exportSkillsToFile(
        skillIds: List<String>,
        outputFile: File
    ) = withContext(Dispatchers.IO) {
        val skillDao = database.skillDao()
        val allSkills = skillDao.getAllSkillsSync()

        val skillsToExport = if (skillIds.isEmpty()) {
            allSkills
        } else {
            allSkills.filter { it.id in skillIds }
        }

        val skillInfos = skillsToExport.map { entity ->
            SkillExportInfo(
                id = entity.id,
                name = entity.name,
                displayName = entity.displayName,
                description = entity.description,
                content = entity.content,
                triggerKeywords = entity.triggerKeywords.split(",").map { it.trim() }.filter { it.isNotEmpty() },
                priority = entity.priority,
                enabled = entity.enabled,
                isBuiltIn = entity.isBuiltIn,
                createdAt = entity.createdAt,
                updatedAt = entity.updatedAt
            )
        }

        val exportData = SkillsExportData(skillInfos)
        val wrappedData = ExportData(
            type = ExportDataType.SKILLS,
            encrypted = false,
            data = exportData.toJson()
        )

        outputFile.writeText(wrappedData.toJson())
    }

    /**
     * 从文件导入 Skills
     * @return 导入的 Skill 数量
     */
    suspend fun importSkillsFromFile(
        inputFile: File
    ): Int = withContext(Dispatchers.IO) {
        val content = normalizeImportedText(inputFile.readText())
        val wrappedData = ExportData.fromJson(content)

        if (wrappedData.type != ExportDataType.SKILLS) {
            throw IllegalArgumentException("文件类型不匹配，期望: SKILLS，实际: ${wrappedData.type}")
        }

        val exportData = SkillsExportData.fromJson(wrappedData.data)
        importSkillsFromData(exportData)
    }

    /**
     * 从已解析的导出数据导入 Skills
     */
    suspend fun importSkillsFromExportData(
        exportData: ExportData
    ): Int = withContext(Dispatchers.IO) {
        if (exportData.type != ExportDataType.SKILLS) {
            throw IllegalArgumentException("数据类型不匹配，期望: SKILLS，实际: ${exportData.type}")
        }

        val skillsData = SkillsExportData.fromJson(exportData.data)
        importSkillsFromData(skillsData)
    }

    private suspend fun importSkillsFromData(skillsData: SkillsExportData): Int {
        val skillDao = database.skillDao()
        val existingSkills = skillDao.getAllSkillsSync()
        val existingNames = existingSkills.map { it.name }.toSet()

        var importedCount = 0

        skillsData.skills.forEach { skill ->
            // 跳过内置技能
            if (skill.isBuiltIn) return@forEach

            // 检查名称冲突，生成新名称
            var newName = skill.name
            var suffix = 1
            while (newName in existingNames) {
                newName = "${skill.name}_$suffix"
                suffix++
            }

            val entity = com.tchat.data.database.entity.SkillEntity(
                id = UUID.randomUUID().toString(),
                name = newName,
                displayName = if (newName != skill.name) "${skill.displayName} ($suffix)" else skill.displayName,
                description = skill.description,
                content = skill.content,
                triggerKeywords = skill.triggerKeywords.joinToString(","),
                priority = skill.priority,
                enabled = skill.enabled,
                isBuiltIn = false,
                toolsJson = "[]",
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
            skillDao.insertSkill(entity)
            importedCount++
        }

        return importedCount
    }
}
