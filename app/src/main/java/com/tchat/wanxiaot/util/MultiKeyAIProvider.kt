package com.tchat.wanxiaot.util

import com.tchat.data.apikey.ApiKeyInfo
import com.tchat.data.apikey.ApiKeySelector
import com.tchat.data.apikey.ApiKeyStatus as DataApiKeyStatus
import com.tchat.data.apikey.KeySelectionResult
import com.tchat.data.apikey.KeySelectionStrategy as DataKeySelectionStrategy
import com.tchat.network.provider.AIProvider
import com.tchat.network.provider.AIProviderException
import com.tchat.network.provider.AIProviderFactory
import com.tchat.network.provider.ChatMessage
import com.tchat.network.provider.CustomParams
import com.tchat.network.provider.StreamChunk
import com.tchat.network.provider.ToolDefinition
import com.tchat.wanxiaot.settings.ApiKeyEntry
import com.tchat.wanxiaot.settings.ApiKeyStatus
import com.tchat.wanxiaot.settings.KeySelectionStrategy
import com.tchat.wanxiaot.settings.ProviderConfig
import com.tchat.wanxiaot.settings.SettingsManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Multi-key wrapper for [AIProvider].
 *
 * - Selects an API key per request (strategy + health status)
 * - Updates key usage/status back into [SettingsManager]
 * - Optionally retries once on auth/rate-limit failures when no content emitted
 */
class MultiKeyAIProvider(
    private val settingsManager: SettingsManager,
    private val providerId: String,
    private val providerType: AIProviderFactory.ProviderType,
    private val baseUrl: String,
    private val model: String,
    private val customParams: CustomParams? = null,
    private val maxRetryAttempts: Int = 3
) : AIProvider {

    private val selector = ApiKeySelector()
    private val stateMutex = Mutex()

    @Volatile
    private var currentDelegate: AIProvider? = null

    override suspend fun streamChat(messages: List<ChatMessage>): Flow<StreamChunk> {
        return streamChatWithTools(messages, emptyList())
    }

    override suspend fun streamChatWithTools(
        messages: List<ChatMessage>,
        tools: List<ToolDefinition>
    ): Flow<StreamChunk> = flow {
        var attempt = 0
        val triedKeyIds = mutableSetOf<String>()

        while (true) {
            attempt++
            val selection = stateMutex.withLock {
                val provider = settingsManager.settings.value.providers.firstOrNull { it.id == providerId }
                    ?: return@withLock SelectedKey.None("服务商配置不存在: $providerId")
                selectKeyAndPersistIndex(provider, triedKeyIds)
            }

            when (selection) {
                is SelectedKey.Single -> {
                    val delegate = createDelegate(selection.key)
                    currentDelegate = delegate
                    emitAllWithNoRetry(delegate, messages, tools)
                    return@flow
                }
                is SelectedKey.Multi -> {
                    val delegate = createDelegate(selection.key)
                    currentDelegate = delegate

                    var emittedAnyPayload = false
                    var shouldRetry = false

                    delegate.streamChatWithTools(messages, tools).collect { chunk ->
                        when (chunk) {
                            is StreamChunk.Content,
                            is StreamChunk.ToolCall -> {
                                emittedAnyPayload = true
                                emit(chunk)
                            }
                            is StreamChunk.Done -> {
                                updateKeyAfterResult(selection.keyId, success = true, error = null)
                                emit(chunk)
                            }
                            is StreamChunk.Error -> {
                                updateKeyAfterResult(selection.keyId, success = false, error = chunk.error)

                                shouldRetry = !emittedAnyPayload &&
                                    isRetryableError(chunk.error) &&
                                    attempt < maxRetryAttempts

                                if (!shouldRetry) {
                                    emit(chunk)
                                }
                            }
                        }
                    }

                    if (!shouldRetry) {
                        return@flow
                    }

                    triedKeyIds.add(selection.keyId)
                    continue
                }
                is SelectedKey.None -> {
                    emit(StreamChunk.Error(AIProviderException.InvalidRequestError(selection.reason)))
                    return@flow
                }
            }
        }
    }

    override fun cancel() {
        currentDelegate?.cancel()
    }

    private fun createDelegate(apiKey: String): AIProvider {
        return AIProviderFactory.create(
            AIProviderFactory.ProviderConfig(
                type = providerType,
                apiKey = apiKey,
                baseUrl = baseUrl,
                model = model,
                customParams = customParams
            )
        )
    }

    private suspend fun FlowCollector<StreamChunk>.emitAllWithNoRetry(
        delegate: AIProvider,
        messages: List<ChatMessage>,
        tools: List<ToolDefinition>
    ) {
        delegate.streamChatWithTools(messages, tools).collect { chunk ->
            emit(chunk)
        }
    }

    private sealed class SelectedKey {
        data class Single(val key: String) : SelectedKey()
        data class Multi(val key: String, val keyId: String) : SelectedKey()
        data class None(val reason: String) : SelectedKey()
    }

    private fun selectKeyAndPersistIndex(
        provider: ProviderConfig,
        excludeKeyIds: Set<String>
    ): SelectedKey {
        val trimmedSingle = provider.apiKey.trim()
        val multiKeyEnabled = provider.multiKeyEnabled
        val configuredKeys = provider.apiKeys

        if (!multiKeyEnabled || configuredKeys.isEmpty()) {
            return if (trimmedSingle.isNotBlank()) {
                SelectedKey.Single(trimmedSingle)
            } else {
                SelectedKey.None("未配置 API Key")
            }
        }

        val filteredKeys = configuredKeys.filter { it.id !in excludeKeyIds }
        if (filteredKeys.isEmpty()) {
            return SelectedKey.None("没有可用的 API Key（所有 Key 均已尝试）")
        }

        val (result, newIndex) = selector.selectKey(
            apiKeys = filteredKeys.map { it.toData() },
            strategy = provider.keySelectionStrategy.toData(),
            currentIndex = provider.roundRobinIndex,
            autoRecoveryMinutes = provider.autoRecoveryMinutes
        )

        if (newIndex != provider.roundRobinIndex) {
            settingsManager.updateProvider(provider.copy(roundRobinIndex = newIndex))
        }

        return when (result) {
            is KeySelectionResult.Success -> SelectedKey.Multi(result.keyInfo.key, result.keyInfo.id)
            is KeySelectionResult.NoAvailableKey -> SelectedKey.None(result.reason)
        }
    }

    private suspend fun updateKeyAfterResult(keyId: String, success: Boolean, error: AIProviderException?) {
        stateMutex.withLock {
            val provider = settingsManager.settings.value.providers.firstOrNull { it.id == providerId } ?: return@withLock
            val existing = provider.apiKeys.firstOrNull { it.id == keyId } ?: return@withLock

            val existingInfo = existing.toData()
            val updatedInfo = if (success) {
                selector.reportSuccess(existingInfo)
            } else {
                selector.reportFailure(
                    key = existingInfo,
                    error = error?.message ?: "unknown error",
                    isAuthError = error is AIProviderException.AuthenticationError,
                    isRateLimited = error is AIProviderException.RateLimitError,
                    maxFailuresBeforeDisable = provider.maxFailuresBeforeDisable
                )
            }

            val updatedEntry = updatedInfo.toApp()
            val newKeys = provider.apiKeys.map { if (it.id == keyId) updatedEntry else it }
            settingsManager.updateProvider(provider.copy(apiKeys = newKeys))
        }
    }

    private fun isRetryableError(error: AIProviderException): Boolean {
        return error is AIProviderException.AuthenticationError || error is AIProviderException.RateLimitError
    }
}

private fun KeySelectionStrategy.toData(): DataKeySelectionStrategy {
    return runCatching { DataKeySelectionStrategy.valueOf(name) }.getOrDefault(DataKeySelectionStrategy.ROUND_ROBIN)
}

private fun ApiKeyEntry.toData(): ApiKeyInfo {
    return ApiKeyInfo(
        id = id,
        key = key,
        name = name,
        isEnabled = isEnabled,
        priority = priority,
        requestCount = requestCount,
        successCount = successCount,
        failureCount = failureCount,
        lastUsedAt = lastUsedAt,
        lastError = lastError,
        status = runCatching { DataApiKeyStatus.valueOf(status.name) }.getOrDefault(DataApiKeyStatus.ACTIVE),
        statusChangedAt = statusChangedAt
    )
}

private fun ApiKeyInfo.toApp(): ApiKeyEntry {
    return ApiKeyEntry(
        id = id,
        key = key,
        name = name,
        isEnabled = isEnabled,
        priority = priority,
        requestCount = requestCount,
        successCount = successCount,
        failureCount = failureCount,
        lastUsedAt = lastUsedAt,
        lastError = lastError,
        status = runCatching { ApiKeyStatus.valueOf(status.name) }.getOrDefault(ApiKeyStatus.ACTIVE),
        statusChangedAt = statusChangedAt
    )
}
