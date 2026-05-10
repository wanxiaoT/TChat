package com.tchat.network.provider

/**
 * TChat 内置 Provider 注册表。
 *
 * 这是 AIProviderFactory 的新单一数据源：Provider 的默认端点、默认模型、
 * API 风格和创建逻辑都先登记在这里，再由旧工厂兼容层读取。
 */
object ProviderRegistry {
    private val definitions = linkedMapOf<String, ProviderDefinition>()
    private val aliases = linkedMapOf<String, String>()

    init {
        BuiltinProviderDefinitions.all.forEach(::register)
    }

    @Synchronized
    fun register(definition: ProviderDefinition) {
        val key = normalize(definition.id)
        definitions[key] = definition
        aliases[key] = key
        definition.aliases.forEach { alias ->
            val normalizedAlias = normalize(alias)
            if (normalizedAlias.isNotBlank()) {
                aliases[normalizedAlias] = key
            }
        }
    }

    @Synchronized
    fun get(idOrAlias: String): ProviderDefinition? {
        val key = normalize(idOrAlias)
        val definitionKey = aliases[key] ?: key
        return definitions[definitionKey]
    }

    @Synchronized
    fun all(): List<ProviderDefinition> {
        return definitions.values.toList()
    }

    fun supports(providerId: String, capability: ProviderCapability): Boolean {
        return get(providerId)?.supports(capability) == true
    }

    private fun normalize(value: String): String {
        return value.trim().lowercase()
    }
}

object ModelRegistry {
    fun getModels(providerId: String): List<ModelDefinition> {
        return ProviderRegistry.get(providerId)?.defaultModels.orEmpty()
    }

    fun getModel(providerId: String, modelId: String): ModelDefinition? {
        return ProviderRegistry.get(providerId)?.findModel(modelId)
    }

    fun getDefaultModel(providerId: String): ModelDefinition? {
        val provider = ProviderRegistry.get(providerId) ?: return null
        return provider.findModel(provider.defaultModelId)
            ?: provider.defaultModels.firstOrNull()
    }

    fun supports(providerId: String, modelId: String, capability: ModelCapability): Boolean {
        val model = getModel(providerId, modelId) ?: return false
        return capability in model.capabilities
    }
}
