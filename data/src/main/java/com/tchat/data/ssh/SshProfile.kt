package com.tchat.data.ssh

import java.util.UUID

/**
 * User-owned SSH profile stored locally on the Android device.
 */
data class SshProfile(
    val id: String = UUID.randomUUID().toString(),
    val alias: String,
    val host: String,
    val port: Int = 22,
    val username: String,
    val authType: SshAuthType,
    val strictHostKeyChecking: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val password: String? = null,
    val privateKey: String? = null,
    val passphrase: String? = null
)

enum class SshAuthType(val value: String) {
    PASSWORD("password"),
    PRIVATE_KEY("private_key");

    companion object {
        fun fromValue(value: String): SshAuthType {
            return entries.find { it.value == value } ?: PASSWORD
        }
    }
}
