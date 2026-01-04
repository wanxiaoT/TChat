package com.tchat.wanxiaot.util

import com.tchat.wanxiaot.settings.ProviderConfig
import org.json.JSONArray
import org.json.JSONObject

/**
 * 导出数据类型
 */
enum class ExportDataType {
    PROVIDERS,           // 供应商配置
    MODELS,              // 模型列表
    API_CONFIG,          // API配置
    KNOWLEDGE_BASE,      // 知识库
    CHAT_FOLDERS,        // 聊天文件夹
    ASSISTANTS           // 助手配置
}

/**
 * 导出数据包装器
 */
data class ExportData(
    val type: ExportDataType,
    val version: Int = 1,
    val timestamp: Long = System.currentTimeMillis(),
    val encrypted: Boolean = false,
    val data: String  // JSON字符串
) {
    fun toJson(): String {
        val json = JSONObject()
        json.put("type", type.name)
        json.put("version", version)
        json.put("timestamp", timestamp)
        json.put("encrypted", encrypted)
        json.put("data", data)
        return json.toString()
    }

    companion object {
        fun fromJson(jsonString: String): ExportData {
            val json = JSONObject(jsonString)
            return ExportData(
                type = ExportDataType.valueOf(json.getString("type")),
                version = json.getInt("version"),
                timestamp = json.getLong("timestamp"),
                encrypted = json.getBoolean("encrypted"),
                data = json.getString("data")
            )
        }
    }
}

/**
 * 供应商配置导出数据
 */
data class ProvidersExportData(
    val providers: List<ProviderConfig>
) {
    fun toJson(): String {
        val json = JSONObject()
        val providersArray = JSONArray()
        providers.forEach { provider ->
            val providerJson = JSONObject()
            providerJson.put("id", provider.id)
            providerJson.put("name", provider.name)
            providerJson.put("providerType", provider.providerType.name)
            providerJson.put("apiKey", provider.apiKey)
            providerJson.put("endpoint", provider.endpoint)
            providerJson.put("selectedModel", provider.selectedModel)
            providerJson.put("availableModels", JSONArray(provider.availableModels))

            // 序列化模型自定义参数
            val paramsJson = JSONObject()
            provider.modelCustomParams.forEach { (modelName, params) ->
                val paramObj = JSONObject()
                params.temperature?.let { paramObj.put("temperature", it.toDouble()) }
                params.topP?.let { paramObj.put("topP", it.toDouble()) }
                params.topK?.let { paramObj.put("topK", it) }
                params.presencePenalty?.let { paramObj.put("presencePenalty", it.toDouble()) }
                params.frequencyPenalty?.let { paramObj.put("frequencyPenalty", it.toDouble()) }
                params.repetitionPenalty?.let { paramObj.put("repetitionPenalty", it.toDouble()) }
                params.maxTokens?.let { paramObj.put("maxTokens", it) }
                if (params.extraParams.isNotEmpty() && params.extraParams != "{}") {
                    paramObj.put("extraParams", params.extraParams)
                }
                paramsJson.put(modelName, paramObj)
            }
            providerJson.put("modelCustomParams", paramsJson)
            providersArray.put(providerJson)
        }
        json.put("providers", providersArray)
        return json.toString()
    }

    companion object {
        fun fromJson(jsonString: String): ProvidersExportData {
            val json = JSONObject(jsonString)
            val providersArray = json.getJSONArray("providers")
            val providers = mutableListOf<ProviderConfig>()

            for (i in 0 until providersArray.length()) {
                val providerJson = providersArray.getJSONObject(i)

                // 解析模型自定义参数
                val modelCustomParams = mutableMapOf<String, com.tchat.wanxiaot.settings.ModelCustomParams>()
                if (providerJson.has("modelCustomParams")) {
                    val paramsJson = providerJson.getJSONObject("modelCustomParams")
                    val keys = paramsJson.keys()
                    while (keys.hasNext()) {
                        val modelName = keys.next()
                        val paramObj = paramsJson.getJSONObject(modelName)
                        val params = com.tchat.wanxiaot.settings.ModelCustomParams(
                            modelName = modelName,
                            temperature = if (paramObj.has("temperature")) paramObj.getDouble("temperature").toFloat() else null,
                            topP = if (paramObj.has("topP")) paramObj.getDouble("topP").toFloat() else null,
                            topK = if (paramObj.has("topK")) paramObj.getInt("topK") else null,
                            presencePenalty = if (paramObj.has("presencePenalty")) paramObj.getDouble("presencePenalty").toFloat() else null,
                            frequencyPenalty = if (paramObj.has("frequencyPenalty")) paramObj.getDouble("frequencyPenalty").toFloat() else null,
                            repetitionPenalty = if (paramObj.has("repetitionPenalty")) paramObj.getDouble("repetitionPenalty").toFloat() else null,
                            maxTokens = if (paramObj.has("maxTokens")) paramObj.getInt("maxTokens") else null,
                            extraParams = if (paramObj.has("extraParams")) paramObj.getString("extraParams") else "{}"
                        )
                        modelCustomParams[modelName] = params
                    }
                }

                // 解析可用模型列表
                val modelsArray = providerJson.getJSONArray("availableModels")
                val availableModels = mutableListOf<String>()
                for (j in 0 until modelsArray.length()) {
                    availableModels.add(modelsArray.getString(j))
                }

                // 解析供应商类型
                val providerType = try {
                    com.tchat.wanxiaot.settings.AIProviderType.valueOf(providerJson.getString("providerType"))
                } catch (e: Exception) {
                    com.tchat.wanxiaot.settings.AIProviderType.OPENAI
                }

                val provider = ProviderConfig(
                    id = providerJson.getString("id"),
                    name = providerJson.getString("name"),
                    providerType = providerType,
                    apiKey = providerJson.getString("apiKey"),
                    endpoint = providerJson.getString("endpoint"),
                    selectedModel = providerJson.getString("selectedModel"),
                    availableModels = availableModels,
                    modelCustomParams = modelCustomParams
                )
                providers.add(provider)
            }

            return ProvidersExportData(providers)
        }
    }
}

/**
 * 模型列表导出数据
 */
data class ModelsExportData(
    val providerId: String,
    val providerName: String,
    val providerType: String,
    val models: List<String>
) {
    fun toJson(): String {
        val json = JSONObject()
        json.put("providerId", providerId)
        json.put("providerName", providerName)
        json.put("providerType", providerType)
        json.put("models", JSONArray(models))
        return json.toString()
    }

    companion object {
        fun fromJson(jsonString: String): ModelsExportData {
            val json = JSONObject(jsonString)
            val modelsArray = json.getJSONArray("models")
            val models = mutableListOf<String>()
            for (i in 0 until modelsArray.length()) {
                models.add(modelsArray.getString(i))
            }
            return ModelsExportData(
                providerId = json.getString("providerId"),
                providerName = json.getString("providerName"),
                providerType = json.getString("providerType"),
                models = models
            )
        }
    }
}

/**
 * API配置导出数据（单个供应商的完整配置，包括API密钥）
 */
data class ApiConfigExportData(
    val provider: ProviderConfig
) {
    fun toJson(): String {
        val json = JSONObject()
        json.put("id", provider.id)
        json.put("name", provider.name)
        json.put("providerType", provider.providerType.name)
        json.put("apiKey", provider.apiKey)
        json.put("endpoint", provider.endpoint)
        json.put("selectedModel", provider.selectedModel)
        json.put("availableModels", JSONArray(provider.availableModels))

        // 序列化模型自定义参数
        val paramsJson = JSONObject()
        provider.modelCustomParams.forEach { (modelName, params) ->
            val paramObj = JSONObject()
            params.temperature?.let { paramObj.put("temperature", it.toDouble()) }
            params.topP?.let { paramObj.put("topP", it.toDouble()) }
            params.topK?.let { paramObj.put("topK", it) }
            params.presencePenalty?.let { paramObj.put("presencePenalty", it.toDouble()) }
            params.frequencyPenalty?.let { paramObj.put("frequencyPenalty", it.toDouble()) }
            params.repetitionPenalty?.let { paramObj.put("repetitionPenalty", it.toDouble()) }
            params.maxTokens?.let { paramObj.put("maxTokens", it) }
            if (params.extraParams.isNotEmpty() && params.extraParams != "{}") {
                paramObj.put("extraParams", params.extraParams)
            }
            paramsJson.put(modelName, paramObj)
        }
        json.put("modelCustomParams", paramsJson)
        return json.toString()
    }

    companion object {
        fun fromJson(jsonString: String): ApiConfigExportData {
            val json = JSONObject(jsonString)

            // 解析模型自定义参数
            val modelCustomParams = mutableMapOf<String, com.tchat.wanxiaot.settings.ModelCustomParams>()
            if (json.has("modelCustomParams")) {
                val paramsJson = json.getJSONObject("modelCustomParams")
                val keys = paramsJson.keys()
                while (keys.hasNext()) {
                    val modelName = keys.next()
                    val paramObj = paramsJson.getJSONObject(modelName)
                    val params = com.tchat.wanxiaot.settings.ModelCustomParams(
                        modelName = modelName,
                        temperature = if (paramObj.has("temperature")) paramObj.getDouble("temperature").toFloat() else null,
                        topP = if (paramObj.has("topP")) paramObj.getDouble("topP").toFloat() else null,
                        topK = if (paramObj.has("topK")) paramObj.getInt("topK") else null,
                        presencePenalty = if (paramObj.has("presencePenalty")) paramObj.getDouble("presencePenalty").toFloat() else null,
                        frequencyPenalty = if (paramObj.has("frequencyPenalty")) paramObj.getDouble("frequencyPenalty").toFloat() else null,
                        repetitionPenalty = if (paramObj.has("repetitionPenalty")) paramObj.getDouble("repetitionPenalty").toFloat() else null,
                        maxTokens = if (paramObj.has("maxTokens")) paramObj.getInt("maxTokens") else null,
                        extraParams = if (paramObj.has("extraParams")) paramObj.getString("extraParams") else "{}"
                    )
                    modelCustomParams[modelName] = params
                }
            }

            // 解析可用模型列表
            val modelsArray = json.getJSONArray("availableModels")
            val availableModels = mutableListOf<String>()
            for (i in 0 until modelsArray.length()) {
                availableModels.add(modelsArray.getString(i))
            }

            // 解析供应商类型
            val providerType = try {
                com.tchat.wanxiaot.settings.AIProviderType.valueOf(json.getString("providerType"))
            } catch (e: Exception) {
                com.tchat.wanxiaot.settings.AIProviderType.OPENAI
            }

            val provider = ProviderConfig(
                id = json.getString("id"),
                name = json.getString("name"),
                providerType = providerType,
                apiKey = json.getString("apiKey"),
                endpoint = json.getString("endpoint"),
                selectedModel = json.getString("selectedModel"),
                availableModels = availableModels,
                modelCustomParams = modelCustomParams
            )

            return ApiConfigExportData(provider)
        }
    }
}

/**
 * 知识库导出数据
 */
data class KnowledgeBaseExportData(
    val knowledgeBase: KnowledgeBaseInfo,
    val items: List<KnowledgeItemInfo>,
    val chunks: List<KnowledgeChunkInfo>
) {
    fun toJson(): String {
        val json = JSONObject()

        // 知识库基本信息
        val kbJson = JSONObject()
        kbJson.put("id", knowledgeBase.id)
        kbJson.put("name", knowledgeBase.name)
        kbJson.put("description", knowledgeBase.description)
        kbJson.put("embeddingProviderId", knowledgeBase.embeddingProviderId)
        kbJson.put("embeddingModelId", knowledgeBase.embeddingModelId)
        json.put("knowledgeBase", kbJson)

        // 知识库项目（原始文件/URL）
        val itemsArray = JSONArray()
        items.forEach { item ->
            val itemJson = JSONObject()
            itemJson.put("id", item.id)
            itemJson.put("knowledgeBaseId", item.knowledgeBaseId)
            itemJson.put("title", item.title)
            itemJson.put("sourceType", item.sourceType)
            item.sourceUri?.let { itemJson.put("sourceUri", it) }
            item.content?.let { itemJson.put("content", it) }
            item.metadata?.let { itemJson.put("metadata", it) }
            itemJson.put("status", item.status)
            item.errorMessage?.let { itemJson.put("errorMessage", it) }
            itemJson.put("createdAt", item.createdAt)
            itemJson.put("updatedAt", item.updatedAt)
            itemsArray.put(itemJson)
        }
        json.put("items", itemsArray)

        // 向量数据块
        val chunksArray = JSONArray()
        chunks.forEach { chunk ->
            val chunkJson = JSONObject()
            chunkJson.put("id", chunk.id)
            chunkJson.put("itemId", chunk.itemId)
            chunkJson.put("knowledgeBaseId", chunk.knowledgeBaseId)
            chunkJson.put("content", chunk.content)
            chunkJson.put("embedding", JSONArray(chunk.embedding))
            chunkJson.put("chunkIndex", chunk.chunkIndex)
            chunkJson.put("createdAt", chunk.createdAt)
            chunksArray.put(chunkJson)
        }
        json.put("chunks", chunksArray)

        return json.toString()
    }

    companion object {
        fun fromJson(jsonString: String): KnowledgeBaseExportData {
            val json = JSONObject(jsonString)

            // 解析知识库基本信息
            val kbJson = json.getJSONObject("knowledgeBase")
            val knowledgeBase = KnowledgeBaseInfo(
                id = kbJson.getString("id"),
                name = kbJson.getString("name"),
                description = if (kbJson.has("description")) kbJson.getString("description") else null,
                embeddingProviderId = kbJson.getString("embeddingProviderId"),
                embeddingModelId = kbJson.getString("embeddingModelId")
            )

            // 解析知识库项目
            val itemsArray = json.getJSONArray("items")
            val items = mutableListOf<KnowledgeItemInfo>()
            for (i in 0 until itemsArray.length()) {
                val itemJson = itemsArray.getJSONObject(i)
                val sourceType = when {
                    itemJson.has("sourceType") -> itemJson.optString("sourceType", "text")
                    itemJson.has("type") -> itemJson.optString("type", "text")
                    else -> "text"
                }
                val content = if (itemJson.has("content") && !itemJson.isNull("content")) {
                    itemJson.getString("content")
                } else {
                    null
                }
                val sourceUri = if (itemJson.has("sourceUri") && !itemJson.isNull("sourceUri")) {
                    itemJson.getString("sourceUri")
                } else {
                    null
                }
                val metadata = if (itemJson.has("metadata") && !itemJson.isNull("metadata")) {
                    itemJson.getString("metadata")
                } else {
                    null
                }
                val errorMessage = if (itemJson.has("errorMessage") && !itemJson.isNull("errorMessage")) {
                    itemJson.getString("errorMessage")
                } else {
                    null
                }
                items.add(
                    KnowledgeItemInfo(
                        id = itemJson.getString("id"),
                        knowledgeBaseId = itemJson.optString("knowledgeBaseId", ""),
                        title = itemJson.getString("title"),
                        sourceType = sourceType,
                        sourceUri = sourceUri,
                        content = content,
                        metadata = metadata,
                        status = itemJson.optString("status", "PENDING"),
                        errorMessage = errorMessage,
                        createdAt = itemJson.optLong("createdAt", System.currentTimeMillis()),
                        updatedAt = itemJson.optLong("updatedAt", System.currentTimeMillis())
                    )
                )
            }

            // 解析向量数据块
            val chunksArray = json.getJSONArray("chunks")
            val chunks = mutableListOf<KnowledgeChunkInfo>()
            for (i in 0 until chunksArray.length()) {
                val chunkJson = chunksArray.getJSONObject(i)
                val embedding = chunkJson.optJSONArray("embedding")?.let { embeddingArray ->
                    FloatArray(embeddingArray.length()) { j ->
                        embeddingArray.getDouble(j).toFloat()
                    }
                } ?: run {
                    val embeddingJson = chunkJson.optString("embedding", "[]")
                    val embeddingArray = JSONArray(embeddingJson)
                    FloatArray(embeddingArray.length()) { j ->
                        embeddingArray.getDouble(j).toFloat()
                    }
                }
                chunks.add(
                    KnowledgeChunkInfo(
                        id = chunkJson.getString("id"),
                        itemId = chunkJson.getString("itemId"),
                        knowledgeBaseId = chunkJson.optString("knowledgeBaseId", ""),
                        content = chunkJson.getString("content"),
                        embedding = embedding,
                        chunkIndex = chunkJson.getInt("chunkIndex"),
                        createdAt = chunkJson.optLong("createdAt", System.currentTimeMillis())
                    )
                )
            }

            return KnowledgeBaseExportData(knowledgeBase, items, chunks)
        }
    }
}

/**
 * 知识库基本信息
 */
data class KnowledgeBaseInfo(
    val id: String,
    val name: String,
    val description: String?,
    val embeddingProviderId: String,
    val embeddingModelId: String
)

/**
 * 知识库项目信息
 */
data class KnowledgeItemInfo(
    val id: String,
    val knowledgeBaseId: String,
    val title: String,
    val sourceType: String,
    val sourceUri: String?,
    val content: String?,
    val metadata: String?,
    val status: String,
    val errorMessage: String?,
    val createdAt: Long,
    val updatedAt: Long
)

/**
 * 知识库向量块信息
 */
data class KnowledgeChunkInfo(
    val id: String,
    val itemId: String,
    val knowledgeBaseId: String,
    val content: String,
    val embedding: FloatArray,
    val chunkIndex: Int,
    val createdAt: Long
)
