package com.tchat.wanxiaot.settings

/**
 * Cloudflare R2 云备份设置
 */
data class R2Settings(
    val enabled: Boolean = false,
    val accountId: String = "",        // Cloudflare Account ID
    val accessKeyId: String = "",      // R2 Access Key ID
    val secretAccessKey: String = "",  // R2 Secret Access Key
    val bucketName: String = "",       // Bucket 名称
    val customEndpoint: String = ""    // 可选：自定义端点
) {
    /**
     * 获取 R2 端点 URL
     */
    val endpoint: String
        get() = customEndpoint.ifEmpty {
            if (accountId.isNotBlank()) {
                "https://$accountId.r2.cloudflarestorage.com"
            } else {
                ""
            }
        }

    /**
     * 检查是否已完成配置
     */
    val isConfigured: Boolean
        get() = accountId.isNotBlank() &&
                accessKeyId.isNotBlank() &&
                secretAccessKey.isNotBlank() &&
                bucketName.isNotBlank()

    /**
     * 获取脱敏的 Secret Access Key
     */
    fun getMaskedSecretKey(): String {
        return if (secretAccessKey.length > 8) {
            "${secretAccessKey.take(4)}****${secretAccessKey.takeLast(4)}"
        } else if (secretAccessKey.isNotEmpty()) {
            "****"
        } else {
            ""
        }
    }
}
