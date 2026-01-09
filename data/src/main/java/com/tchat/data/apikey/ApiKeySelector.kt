package com.tchat.data.apikey

import kotlin.random.Random

/**
 * API Key 状态
 */
enum class ApiKeyStatus {
    ACTIVE,       // 正常可用
    DISABLED,     // 已禁用（用户手动禁用）
    ERROR,        // 错误状态（连续失败后自动标记）
    RATE_LIMITED  // 限流中
}

/**
 * Key 选择策略
 */
enum class KeySelectionStrategy {
    ROUND_ROBIN,  // 轮询
    PRIORITY,     // 优先级（数字越小优先级越高）
    RANDOM,       // 随机
    LEAST_USED    // 最少使用
}

/**
 * API Key 条目（data 模块内部使用的简化版本）
 */
data class ApiKeyInfo(
    val id: String,
    val key: String,
    val name: String = "",
    val isEnabled: Boolean = true,
    val priority: Int = 5,
    val requestCount: Int = 0,
    val successCount: Int = 0,
    val failureCount: Int = 0,
    val lastUsedAt: Long = 0,
    val lastError: String? = null,
    val status: ApiKeyStatus = ApiKeyStatus.ACTIVE,
    val statusChangedAt: Long = 0
)

/**
 * Key 选择结果
 */
sealed class KeySelectionResult {
    data class Success(val keyInfo: ApiKeyInfo) : KeySelectionResult()
    data class NoAvailableKey(val reason: String) : KeySelectionResult()
}

/**
 * Key 使用报告
 */
sealed class KeyUsageReport {
    data class Success(val keyId: String) : KeyUsageReport()
    data class Failure(
        val keyId: String,
        val error: String,
        val isAuthError: Boolean = false,
        val isRateLimited: Boolean = false
    ) : KeyUsageReport()
}

/**
 * API Key 选择器
 * 负责根据策略选择可用的 Key，并管理 Key 状态
 */
class ApiKeySelector {

    /**
     * 根据策略选择下一个可用的 Key
     *
     * @param apiKeys 所有 Key 列表
     * @param strategy 选择策略
     * @param currentIndex 当前轮询索引（仅用于 ROUND_ROBIN 策略）
     * @param autoRecoveryMinutes 自动恢复时间（分钟）
     * @return 选择结果和更新后的索引
     */
    fun selectKey(
        apiKeys: List<ApiKeyInfo>,
        strategy: KeySelectionStrategy,
        currentIndex: Int = 0,
        autoRecoveryMinutes: Int = 5
    ): Pair<KeySelectionResult, Int> {
        if (apiKeys.isEmpty()) {
            return KeySelectionResult.NoAvailableKey("没有配置任何 API Key") to currentIndex
        }

        // 先检查并恢复可能已经过了恢复时间的 Key
        val recoveredKeys = checkAndRecoverKeys(apiKeys, autoRecoveryMinutes)

        // 获取可用的 Key（启用且状态为 ACTIVE）
        val availableKeys = recoveredKeys.filter {
            it.isEnabled && it.status == ApiKeyStatus.ACTIVE
        }

        if (availableKeys.isEmpty()) {
            val disabledCount = recoveredKeys.count { !it.isEnabled }
            val errorCount = recoveredKeys.count { it.status == ApiKeyStatus.ERROR }
            val rateLimitedCount = recoveredKeys.count { it.status == ApiKeyStatus.RATE_LIMITED }

            val reason = buildString {
                append("没有可用的 API Key")
                if (disabledCount > 0) append("，$disabledCount 个已禁用")
                if (errorCount > 0) append("，$errorCount 个错误状态")
                if (rateLimitedCount > 0) append("，$rateLimitedCount 个限流中")
            }
            return KeySelectionResult.NoAvailableKey(reason) to currentIndex
        }

        val (selectedKey, newIndex) = when (strategy) {
            KeySelectionStrategy.ROUND_ROBIN -> selectRoundRobin(availableKeys, currentIndex)
            KeySelectionStrategy.PRIORITY -> selectByPriority(availableKeys) to currentIndex
            KeySelectionStrategy.RANDOM -> selectRandom(availableKeys) to currentIndex
            KeySelectionStrategy.LEAST_USED -> selectLeastUsed(availableKeys) to currentIndex
        }

        return KeySelectionResult.Success(selectedKey) to newIndex
    }

    /**
     * 轮询选择
     */
    private fun selectRoundRobin(keys: List<ApiKeyInfo>, currentIndex: Int): Pair<ApiKeyInfo, Int> {
        val index = currentIndex % keys.size
        val nextIndex = (currentIndex + 1) % keys.size
        return keys[index] to nextIndex
    }

    /**
     * 优先级选择（数字越小优先级越高）
     */
    private fun selectByPriority(keys: List<ApiKeyInfo>): ApiKeyInfo {
        return keys.minByOrNull { it.priority } ?: keys.first()
    }

    /**
     * 随机选择
     */
    private fun selectRandom(keys: List<ApiKeyInfo>): ApiKeyInfo {
        return keys[Random.nextInt(keys.size)]
    }

    /**
     * 最少使用选择
     */
    private fun selectLeastUsed(keys: List<ApiKeyInfo>): ApiKeyInfo {
        return keys.minByOrNull { it.requestCount } ?: keys.first()
    }

    /**
     * 检查并恢复错误状态的 Key
     */
    fun checkAndRecoverKeys(
        apiKeys: List<ApiKeyInfo>,
        autoRecoveryMinutes: Int
    ): List<ApiKeyInfo> {
        val currentTime = System.currentTimeMillis()
        val recoveryTimeMs = autoRecoveryMinutes * 60 * 1000L
        val rateLimitRecoveryMs = 60 * 1000L  // 限流恢复时间：1 分钟

        return apiKeys.map { key ->
            when {
                // ERROR 状态恢复
                key.status == ApiKeyStatus.ERROR &&
                key.statusChangedAt > 0 &&
                (currentTime - key.statusChangedAt) >= recoveryTimeMs -> {
                    key.copy(
                        status = ApiKeyStatus.ACTIVE,
                        failureCount = 0,
                        lastError = null,
                        statusChangedAt = currentTime
                    )
                }
                // RATE_LIMITED 状态恢复
                key.status == ApiKeyStatus.RATE_LIMITED &&
                key.statusChangedAt > 0 &&
                (currentTime - key.statusChangedAt) >= rateLimitRecoveryMs -> {
                    key.copy(
                        status = ApiKeyStatus.ACTIVE,
                        statusChangedAt = currentTime
                    )
                }
                else -> key
            }
        }
    }

    /**
     * 处理 Key 使用成功
     */
    fun reportSuccess(key: ApiKeyInfo): ApiKeyInfo {
        val currentTime = System.currentTimeMillis()
        return key.copy(
            requestCount = key.requestCount + 1,
            successCount = key.successCount + 1,
            failureCount = 0,  // 重置连续失败计数
            lastUsedAt = currentTime,
            lastError = null,
            status = ApiKeyStatus.ACTIVE,
            statusChangedAt = currentTime
        )
    }

    /**
     * 处理 Key 使用失败
     *
     * @param key 使用的 Key
     * @param error 错误信息
     * @param isAuthError 是否为认证错误（401/403）
     * @param isRateLimited 是否为限流错误（429）
     * @param maxFailuresBeforeDisable 连续失败多少次后禁用
     * @return 更新后的 Key
     */
    fun reportFailure(
        key: ApiKeyInfo,
        error: String,
        isAuthError: Boolean = false,
        isRateLimited: Boolean = false,
        maxFailuresBeforeDisable: Int = 3
    ): ApiKeyInfo {
        val currentTime = System.currentTimeMillis()
        val newFailureCount = key.failureCount + 1

        return when {
            // 认证错误，直接标记为 ERROR
            isAuthError -> key.copy(
                requestCount = key.requestCount + 1,
                failureCount = newFailureCount,
                lastUsedAt = currentTime,
                lastError = error,
                status = ApiKeyStatus.ERROR,
                statusChangedAt = currentTime
            )
            // 限流错误，标记为 RATE_LIMITED
            isRateLimited -> key.copy(
                requestCount = key.requestCount + 1,
                failureCount = newFailureCount,
                lastUsedAt = currentTime,
                lastError = error,
                status = ApiKeyStatus.RATE_LIMITED,
                statusChangedAt = currentTime
            )
            // 连续失败达到阈值，标记为 ERROR
            newFailureCount >= maxFailuresBeforeDisable -> key.copy(
                requestCount = key.requestCount + 1,
                failureCount = newFailureCount,
                lastUsedAt = currentTime,
                lastError = error,
                status = ApiKeyStatus.ERROR,
                statusChangedAt = currentTime
            )
            // 普通失败，只增加计数
            else -> key.copy(
                requestCount = key.requestCount + 1,
                failureCount = newFailureCount,
                lastUsedAt = currentTime,
                lastError = error
            )
        }
    }

    /**
     * 获取下一个可用的 Key（用于故障转移）
     * 排除指定的 Key ID
     */
    fun getNextAvailableKey(
        apiKeys: List<ApiKeyInfo>,
        excludeKeyId: String,
        strategy: KeySelectionStrategy,
        currentIndex: Int = 0
    ): Pair<KeySelectionResult, Int> {
        val filteredKeys = apiKeys.filter { it.id != excludeKeyId }
        return selectKey(filteredKeys, strategy, currentIndex)
    }
}
