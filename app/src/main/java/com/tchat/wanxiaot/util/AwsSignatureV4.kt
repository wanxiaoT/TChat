package com.tchat.wanxiaot.util

import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.TreeMap
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * AWS Signature Version 4 签名工具
 * 用于 Cloudflare R2 (S3 兼容) API 认证
 */
object AwsSignatureV4 {

    private const val ALGORITHM = "AWS4-HMAC-SHA256"
    private const val SERVICE = "s3"
    private const val REGION = "auto"  // R2 使用 auto 作为 region
    private const val TERMINATOR = "aws4_request"

    /**
     * 签名请求并返回需要添加的 Headers
     */
    fun signRequest(
        method: String,
        url: String,
        headers: Map<String, String>,
        payload: ByteArray?,
        accessKeyId: String,
        secretAccessKey: String,
        date: Date = Date()
    ): Map<String, String> {
        val parsedUrl = URL(url)
        val host = parsedUrl.host
        val path = parsedUrl.path.ifEmpty { "/" }
        val query = parsedUrl.query ?: ""

        // 格式化日期
        val dateFormat = SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val amzDate = dateFormat.format(date)
        val dateStamp = amzDate.substring(0, 8)

        // 计算 payload hash
        val payloadHash = sha256Hex(payload ?: ByteArray(0))

        // 构建要签名的 headers
        val signedHeaders = TreeMap<String, String>(String.CASE_INSENSITIVE_ORDER)
        signedHeaders["host"] = host
        signedHeaders["x-amz-date"] = amzDate
        signedHeaders["x-amz-content-sha256"] = payloadHash
        headers.forEach { (key, value) ->
            signedHeaders[key.lowercase()] = value.trim()
        }

        // 创建规范请求
        val canonicalRequest = createCanonicalRequest(
            method = method,
            canonicalUri = path,
            canonicalQueryString = canonicalizeQueryString(query),
            signedHeaders = signedHeaders,
            payloadHash = payloadHash
        )

        // 创建签名字符串
        val credentialScope = "$dateStamp/$REGION/$SERVICE/$TERMINATOR"
        val stringToSign = createStringToSign(amzDate, credentialScope, canonicalRequest)

        // 计算签名
        val signingKey = getSignatureKey(secretAccessKey, dateStamp, REGION, SERVICE)
        val signature = hmacSha256Hex(signingKey, stringToSign)

        // 构建 Authorization header
        val signedHeaderNames = signedHeaders.keys.joinToString(";") { it.lowercase() }
        val authorization = "$ALGORITHM Credential=$accessKeyId/$credentialScope, " +
                "SignedHeaders=$signedHeaderNames, Signature=$signature"

        // 返回需要添加的 headers
        return mapOf(
            "Authorization" to authorization,
            "x-amz-date" to amzDate,
            "x-amz-content-sha256" to payloadHash
        )
    }

    /**
     * 创建规范请求
     */
    private fun createCanonicalRequest(
        method: String,
        canonicalUri: String,
        canonicalQueryString: String,
        signedHeaders: Map<String, String>,
        payloadHash: String
    ): String {
        val canonicalHeaders = signedHeaders.entries
            .sortedBy { it.key.lowercase() }
            .joinToString("") { "${it.key.lowercase()}:${it.value}\n" }

        val signedHeaderNames = signedHeaders.keys
            .map { it.lowercase() }
            .sorted()
            .joinToString(";")

        return listOf(
            method.uppercase(),
            canonicalUri,
            canonicalQueryString,
            canonicalHeaders,
            signedHeaderNames,
            payloadHash
        ).joinToString("\n")
    }

    /**
     * 规范化查询字符串
     */
    private fun canonicalizeQueryString(query: String): String {
        if (query.isEmpty()) return ""

        return query.split("&")
            .map { param ->
                val parts = param.split("=", limit = 2)
                val key = URLEncoder.encode(parts[0], "UTF-8")
                val value = if (parts.size > 1) URLEncoder.encode(parts[1], "UTF-8") else ""
                "$key=$value"
            }
            .sorted()
            .joinToString("&")
    }

    /**
     * 创建签名字符串
     */
    private fun createStringToSign(
        amzDate: String,
        credentialScope: String,
        canonicalRequest: String
    ): String {
        return listOf(
            ALGORITHM,
            amzDate,
            credentialScope,
            sha256Hex(canonicalRequest.toByteArray(Charsets.UTF_8))
        ).joinToString("\n")
    }

    /**
     * 获取签名密钥
     */
    private fun getSignatureKey(
        secretKey: String,
        dateStamp: String,
        region: String,
        service: String
    ): ByteArray {
        val kDate = hmacSha256("AWS4$secretKey".toByteArray(Charsets.UTF_8), dateStamp)
        val kRegion = hmacSha256(kDate, region)
        val kService = hmacSha256(kRegion, service)
        return hmacSha256(kService, TERMINATOR)
    }

    /**
     * HMAC-SHA256
     */
    private fun hmacSha256(key: ByteArray, data: String): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data.toByteArray(Charsets.UTF_8))
    }

    /**
     * HMAC-SHA256 并返回十六进制字符串
     */
    private fun hmacSha256Hex(key: ByteArray, data: String): String {
        return hmacSha256(key, data).toHexString()
    }

    /**
     * SHA-256 并返回十六进制字符串
     */
    private fun sha256Hex(data: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(data).toHexString()
    }

    /**
     * ByteArray 转十六进制字符串
     */
    private fun ByteArray.toHexString(): String {
        return joinToString("") { "%02x".format(it) }
    }
}
