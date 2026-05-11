package com.tchat.data.ssh

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class SshProfileStore(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val crypto = SshSecretCrypto()
    private val lock = Any()

    suspend fun getProfiles(): List<SshProfile> = withContext(Dispatchers.IO) {
        synchronized(lock) {
            readProfilesLocked(includeSecrets = false)
        }
    }

    suspend fun getProfileByAlias(alias: String): SshProfile? = withContext(Dispatchers.IO) {
        val normalized = normalizeAlias(alias)
        synchronized(lock) {
            readProfilesLocked(includeSecrets = true)
                .firstOrNull { normalizeAlias(it.alias) == normalized }
        }
    }

    suspend fun getProfileById(id: String): SshProfile? = withContext(Dispatchers.IO) {
        synchronized(lock) {
            readProfilesLocked(includeSecrets = true).firstOrNull { it.id == id }
        }
    }

    suspend fun upsertProfile(profile: SshProfile) = withContext(Dispatchers.IO) {
        synchronized(lock) {
            val existing = readRawProfilesLocked()
            val now = System.currentTimeMillis()
            val index = (0 until existing.length()).firstOrNull { i ->
                existing.getJSONObject(i).optString("id") == profile.id
            }

            val createdAt = index?.let { existing.getJSONObject(it).optLong("createdAt") }
                ?: profile.createdAt

            val sanitized = profile.copy(
                alias = profile.alias.trim(),
                host = profile.host.trim(),
                username = profile.username.trim(),
                port = profile.port.coerceIn(1, 65535),
                createdAt = createdAt,
                updatedAt = now
            )

            val json = sanitized.toJson()
            if (index == null) {
                existing.put(json)
            } else {
                existing.put(index, json)
            }
            prefs.edit().putString(KEY_PROFILES, existing.toString()).apply()
        }
    }

    suspend fun deleteProfile(id: String) = withContext(Dispatchers.IO) {
        synchronized(lock) {
            val existing = readRawProfilesLocked()
            val next = JSONArray()
            for (i in 0 until existing.length()) {
                val item = existing.getJSONObject(i)
                if (item.optString("id") != id) {
                    next.put(item)
                }
            }
            prefs.edit().putString(KEY_PROFILES, next.toString()).apply()
        }
    }

    private fun readProfilesLocked(includeSecrets: Boolean): List<SshProfile> {
        val raw = readRawProfilesLocked()
        val profiles = mutableListOf<SshProfile>()
        for (i in 0 until raw.length()) {
            runCatching {
                profiles.add(raw.getJSONObject(i).toProfile(includeSecrets))
            }
        }
        return profiles
    }

    private fun readRawProfilesLocked(): JSONArray {
        return runCatching {
            JSONArray(prefs.getString(KEY_PROFILES, "[]") ?: "[]")
        }.getOrElse { JSONArray() }
    }

    private fun SshProfile.toJson(): JSONObject {
        return JSONObject().apply {
            put("id", id)
            put("alias", alias)
            put("host", host)
            put("port", port)
            put("username", username)
            put("authType", authType.value)
            put("strictHostKeyChecking", strictHostKeyChecking)
            put("createdAt", createdAt)
            put("updatedAt", updatedAt)
            put("encryptedPassword", crypto.encrypt(password))
            put("encryptedPrivateKey", crypto.encrypt(privateKey))
            put("encryptedPassphrase", crypto.encrypt(passphrase))
        }
    }

    private fun JSONObject.toProfile(includeSecrets: Boolean): SshProfile {
        return SshProfile(
            id = getString("id"),
            alias = getString("alias"),
            host = getString("host"),
            port = optInt("port", 22),
            username = getString("username"),
            authType = SshAuthType.fromValue(optString("authType", SshAuthType.PASSWORD.value)),
            strictHostKeyChecking = optBoolean("strictHostKeyChecking", false),
            createdAt = optLong("createdAt", System.currentTimeMillis()),
            updatedAt = optLong("updatedAt", System.currentTimeMillis()),
            password = if (includeSecrets) crypto.decrypt(encryptedOrNull("encryptedPassword")) else null,
            privateKey = if (includeSecrets) crypto.decrypt(encryptedOrNull("encryptedPrivateKey")) else null,
            passphrase = if (includeSecrets) crypto.decrypt(encryptedOrNull("encryptedPassphrase")) else null
        )
    }

    private fun JSONObject.encryptedOrNull(key: String): String? {
        return if (has(key) && !isNull(key)) optString(key) else null
    }

    private fun normalizeAlias(alias: String): String = alias.trim().lowercase()

    companion object {
        private const val PREFS_NAME = "tchat_ssh_profiles"
        private const val KEY_PROFILES = "profiles"
    }
}
