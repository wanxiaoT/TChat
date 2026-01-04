package com.tchat.wanxiaot.util

import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * 加密工具类
 * 使用AES-256-CBC加密算法
 */
object EncryptionUtils {
    private const val ALGORITHM = "AES"
    private const val TRANSFORMATION = "AES/CBC/PKCS5Padding"
    private const val KEY_DERIVATION_ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val ITERATION_COUNT = 10000
    private const val KEY_LENGTH = 256
    private const val IV_LENGTH = 16
    private const val SALT_LENGTH = 32

    /**
     * 加密字符串
     * @param plainText 明文
     * @param password 密码
     * @return Base64编码的加密文本（包含salt和IV）
     */
    fun encrypt(plainText: String, password: String): String {
        // 生成随机salt
        val salt = ByteArray(SALT_LENGTH)
        SecureRandom().nextBytes(salt)

        // 使用PBKDF2派生密钥
        val key = deriveKey(password, salt)

        // 生成随机IV
        val iv = ByteArray(IV_LENGTH)
        SecureRandom().nextBytes(iv)

        // 加密
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, ALGORITHM), IvParameterSpec(iv))
        val encrypted = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))

        // 组合 salt + IV + 加密数据
        val combined = ByteArray(salt.size + iv.size + encrypted.size)
        System.arraycopy(salt, 0, combined, 0, salt.size)
        System.arraycopy(iv, 0, combined, salt.size, iv.size)
        System.arraycopy(encrypted, 0, combined, salt.size + iv.size, encrypted.size)

        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    /**
     * 解密字符串
     * @param encryptedText Base64编码的加密文本
     * @param password 密码
     * @return 明文
     */
    fun decrypt(encryptedText: String, password: String): String {
        val combined = Base64.decode(encryptedText, Base64.NO_WRAP)

        // 提取salt、IV和加密数据
        val salt = ByteArray(SALT_LENGTH)
        val iv = ByteArray(IV_LENGTH)
        val encrypted = ByteArray(combined.size - SALT_LENGTH - IV_LENGTH)

        System.arraycopy(combined, 0, salt, 0, SALT_LENGTH)
        System.arraycopy(combined, SALT_LENGTH, iv, 0, IV_LENGTH)
        System.arraycopy(combined, SALT_LENGTH + IV_LENGTH, encrypted, 0, encrypted.size)

        // 使用PBKDF2派生密钥
        val key = deriveKey(password, salt)

        // 解密
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, ALGORITHM), IvParameterSpec(iv))
        val decrypted = cipher.doFinal(encrypted)

        return String(decrypted, Charsets.UTF_8)
    }

    /**
     * 使用PBKDF2从密码派生密钥
     */
    private fun deriveKey(password: String, salt: ByteArray): ByteArray {
        val factory = SecretKeyFactory.getInstance(KEY_DERIVATION_ALGORITHM)
        val spec = PBEKeySpec(password.toCharArray(), salt, ITERATION_COUNT, KEY_LENGTH)
        val tmp = factory.generateSecret(spec)
        return tmp.encoded
    }

    /**
     * 生成密码的SHA-256哈希（用于验证密码）
     */
    fun hashPassword(password: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(password.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(hash, Base64.NO_WRAP)
    }

    /**
     * 验证密码是否正确
     * @param password 待验证的密码
     * @param hashedPassword 哈希后的密码
     */
    fun verifyPassword(password: String, hashedPassword: String): Boolean {
        return hashPassword(password) == hashedPassword
    }
}
