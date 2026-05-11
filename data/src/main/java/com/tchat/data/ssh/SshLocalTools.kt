package com.tchat.data.ssh

import android.content.Context
import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import com.tchat.data.tool.InputSchema
import com.tchat.data.tool.PropertyDef
import com.tchat.data.tool.Tool
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.Properties
import kotlin.math.min

class SshLocalTools(context: Context) {
    private val profileStore = SshProfileStore(context)

    val listProfilesTool by lazy {
        Tool(
            name = "ssh_list_profiles",
            description = "列出用户在 Android 本地配置且允许 AI 使用的 SSH profile alias。不会返回密码、私钥或真实密钥内容。",
            parameters = {
                InputSchema.Obj(properties = emptyMap(), required = emptyList())
            },
            execute = {
                val profiles = profileStore.getProfiles()
                JSONObject().apply {
                    put("success", true)
                    put("profiles", JSONArray().apply {
                        profiles.forEach { profile ->
                            put(JSONObject().apply {
                                put("alias", profile.alias)
                                put("username", profile.username)
                                put("authType", profile.authType.value)
                                put("strictHostKeyChecking", profile.strictHostKeyChecking)
                            })
                        }
                    })
                    put("count", profiles.size)
                }
            }
        )
    }

    val testConnectionTool by lazy {
        Tool(
            name = "ssh_test_connection",
            description = "测试指定 SSH profile alias 是否能从 Android 本地连接。只接收 alias，不接收密码或私钥。",
            parameters = {
                InputSchema.Obj(
                    properties = mapOf(
                        "profileAlias" to PropertyDef("string", "SSH profile alias")
                    ),
                    required = listOf("profileAlias")
                )
            },
            execute = { args ->
                runWithProfile(args.optString("profileAlias")) { profile ->
                    val startedAt = System.currentTimeMillis()
                    openSession(profile).useSession { session ->
                        session.connect(DEFAULT_CONNECT_TIMEOUT_MS)
                    }
                    JSONObject().apply {
                        put("success", true)
                        put("profileAlias", profile.alias)
                        put("latencyMs", System.currentTimeMillis() - startedAt)
                    }
                }
            }
        )
    }

    val listDirectoryTool by lazy {
        Tool(
            name = "ssh_list_dir",
            description = "通过 SFTP 列出远程目录内容。只用于查看目录，不写入远程服务器。",
            parameters = {
                InputSchema.Obj(
                    properties = mapOf(
                        "profileAlias" to PropertyDef("string", "SSH profile alias"),
                        "path" to PropertyDef("string", "远程目录路径")
                    ),
                    required = listOf("profileAlias", "path")
                )
            },
            execute = { args ->
                val path = args.optString("path", "")
                if (path.isBlank()) {
                    errorJson("path 不能为空")
                } else if (isSensitivePath(path)) {
                    errorJson("路径被安全策略拦截")
                } else {
                    runWithProfile(args.optString("profileAlias")) { profile ->
                        withSftp(profile) { channel ->
                            val items = JSONArray()
                            @Suppress("UNCHECKED_CAST")
                            val entries = channel.ls(path) as java.util.Vector<ChannelSftp.LsEntry>
                            entries.take(MAX_LIST_ITEMS).forEach { entry ->
                                items.put(JSONObject().apply {
                                    put("name", entry.filename)
                                    put("isDirectory", entry.attrs.isDir)
                                    put("size", entry.attrs.size)
                                    put("permissions", entry.attrs.permissionsString)
                                })
                            }
                            JSONObject().apply {
                                put("success", true)
                                put("profileAlias", profile.alias)
                                put("path", path)
                                put("items", items)
                                put("truncated", entries.size > MAX_LIST_ITEMS)
                            }
                        }
                    }
                }
            }
        )
    }

    val readFileTool by lazy {
        Tool(
            name = "ssh_read_file",
            description = "通过 SFTP 读取远程文件内容。敏感路径会被拦截，输出会截断和脱敏。",
            parameters = {
                InputSchema.Obj(
                    properties = mapOf(
                        "profileAlias" to PropertyDef("string", "SSH profile alias"),
                        "path" to PropertyDef("string", "远程文件路径"),
                        "maxChars" to PropertyDef("integer", "最大返回字符数，默认 16000")
                    ),
                    required = listOf("profileAlias", "path")
                )
            },
            execute = { args ->
                val path = args.optString("path", "")
                val maxChars = args.optInt("maxChars", DEFAULT_MAX_OUTPUT_CHARS)
                    .coerceIn(1_000, HARD_MAX_OUTPUT_CHARS)
                if (path.isBlank()) {
                    errorJson("path 不能为空")
                } else if (isSensitivePath(path)) {
                    errorJson("路径被安全策略拦截")
                } else {
                    runWithProfile(args.optString("profileAlias")) { profile ->
                        withSftp(profile) { channel ->
                            val raw = channel.get(path).use { input ->
                                readStreamLimited(input, maxChars)
                            }
                            val sanitized = sanitizeOutput(raw.text, maxChars)
                            JSONObject().apply {
                                put("success", true)
                                put("profileAlias", profile.alias)
                                put("path", path)
                                put("content", sanitized.text)
                                put("truncated", raw.truncated || sanitized.truncated)
                            }
                        }
                    }
                }
            }
        )
    }

    val tailLogTool by lazy {
        Tool(
            name = "ssh_tail_log",
            description = "读取远程日志末尾内容。只执行 tail 读取，不写入远程服务器。",
            parameters = {
                InputSchema.Obj(
                    properties = mapOf(
                        "profileAlias" to PropertyDef("string", "SSH profile alias"),
                        "path" to PropertyDef("string", "远程日志文件路径"),
                        "lines" to PropertyDef("integer", "读取末尾行数，默认 200，最多 1000"),
                        "maxChars" to PropertyDef("integer", "最大返回字符数，默认 16000")
                    ),
                    required = listOf("profileAlias", "path")
                )
            },
            execute = { args ->
                val path = args.optString("path", "")
                val lines = args.optInt("lines", 200).coerceIn(1, 1000)
                val maxChars = args.optInt("maxChars", DEFAULT_MAX_OUTPUT_CHARS)
                    .coerceIn(1_000, HARD_MAX_OUTPUT_CHARS)
                if (path.isBlank()) {
                    errorJson("path 不能为空")
                } else if (isSensitivePath(path)) {
                    errorJson("路径被安全策略拦截")
                } else {
                    val command = "tail -n $lines ${shellQuote(path)}"
                    runExec(args.optString("profileAlias"), command, null, DEFAULT_COMMAND_TIMEOUT_SECONDS, maxChars)
                }
            }
        )
    }

    val execReadonlyTool by lazy {
        Tool(
            name = "ssh_exec_readonly",
            description = "执行受限的远程只读 SSH 命令。仅允许查看类命令，危险命令会被拦截。",
            parameters = {
                InputSchema.Obj(
                    properties = mapOf(
                        "profileAlias" to PropertyDef("string", "SSH profile alias"),
                        "command" to PropertyDef("string", "只读远程命令"),
                        "cwd" to PropertyDef("string", "可选工作目录"),
                        "timeoutSeconds" to PropertyDef("integer", "超时时间，默认 20 秒，最多 60 秒"),
                        "maxChars" to PropertyDef("integer", "最大返回字符数，默认 16000")
                    ),
                    required = listOf("profileAlias", "command")
                )
            },
            execute = { args ->
                val command = args.optString("command", "")
                val cwd = args.optString("cwd", "").ifBlank { null }
                val timeoutSeconds = args.optInt("timeoutSeconds", DEFAULT_COMMAND_TIMEOUT_SECONDS)
                    .coerceIn(1, HARD_MAX_TIMEOUT_SECONDS)
                val maxChars = args.optInt("maxChars", DEFAULT_MAX_OUTPUT_CHARS)
                    .coerceIn(1_000, HARD_MAX_OUTPUT_CHARS)
                when {
                    command.isBlank() -> errorJson("command 不能为空")
                    cwd != null && isSensitivePath(cwd) -> errorJson("工作目录被安全策略拦截")
                    isSensitivePath(command) -> errorJson("命令包含敏感路径，已拦截")
                    !isReadonlyCommandAllowed(command) -> errorJson("命令不在 SSH 只读工具允许列表内")
                    else -> runExec(args.optString("profileAlias"), command, cwd, timeoutSeconds, maxChars)
                }
            }
        )
    }

    fun getReadOnlyTools(): List<Tool> = listOf(
        listProfilesTool,
        testConnectionTool,
        listDirectoryTool,
        readFileTool,
        tailLogTool,
        execReadonlyTool
    )

    private suspend fun runExec(
        alias: String,
        command: String,
        cwd: String?,
        timeoutSeconds: Int,
        maxChars: Int
    ): JSONObject {
        return runWithProfile(alias) { profile ->
            val fullCommand = cwd?.let { "cd ${shellQuote(it)} && $command" } ?: command
            val result = exec(profile, fullCommand, timeoutSeconds, maxChars)
            val sanitizedStdout = sanitizeOutput(result.stdout, maxChars)
            val sanitizedStderr = sanitizeOutput(result.stderr, maxChars)
            JSONObject().apply {
                put("success", result.exitCode == 0)
                put("profileAlias", profile.alias)
                put("command", command)
                cwd?.let { put("cwd", it) }
                put("exitCode", result.exitCode)
                put("stdout", sanitizedStdout.text)
                put("stderr", sanitizedStderr.text)
                put("truncated", result.truncated || sanitizedStdout.truncated || sanitizedStderr.truncated)
            }
        }
    }

    private suspend fun runWithProfile(
        alias: String,
        block: suspend (SshProfile) -> JSONObject
    ): JSONObject {
        if (alias.isBlank()) {
            return errorJson("profileAlias 不能为空")
        }
        val profile = profileStore.getProfileByAlias(alias)
            ?: return errorJson("未找到 SSH profile: $alias")

        return try {
            block(profile)
        } catch (e: Exception) {
            errorJson(e.message ?: "SSH 工具执行失败")
        }
    }

    private suspend fun <T> withSftp(profile: SshProfile, block: suspend (ChannelSftp) -> T): T =
        withContext(Dispatchers.IO) {
            val session = openSession(profile)
            var channel: ChannelSftp? = null
            try {
                session.connect(DEFAULT_CONNECT_TIMEOUT_MS)
                channel = session.openChannel("sftp") as ChannelSftp
                channel.connect(DEFAULT_CONNECT_TIMEOUT_MS)
                block(channel)
            } finally {
                channel?.disconnect()
                session.disconnect()
            }
        }

    private suspend fun exec(
        profile: SshProfile,
        command: String,
        timeoutSeconds: Int,
        maxChars: Int
    ): ExecResult = withContext(Dispatchers.IO) {
        val session = openSession(profile)
        var channel: ChannelExec? = null
        try {
            session.connect(DEFAULT_CONNECT_TIMEOUT_MS)
            channel = session.openChannel("exec") as ChannelExec
            channel.setCommand(command)

            val stdout = channel.inputStream
            val stderr = channel.errStream
            val stdoutBuffer = ByteArrayOutputStream()
            val stderrBuffer = ByteArrayOutputStream()
            val readBuffer = ByteArray(4096)
            val deadline = System.currentTimeMillis() + timeoutSeconds * 1000L
            var truncated = false

            channel.connect(DEFAULT_CONNECT_TIMEOUT_MS)
            while (true) {
                while (stdout.available() > 0) {
                    val count = stdout.read(readBuffer, 0, min(readBuffer.size, stdout.available()))
                    if (count > 0) {
                        truncated = appendLimited(stdoutBuffer, readBuffer, count, maxChars) || truncated
                    }
                }
                while (stderr.available() > 0) {
                    val count = stderr.read(readBuffer, 0, min(readBuffer.size, stderr.available()))
                    if (count > 0) {
                        truncated = appendLimited(stderrBuffer, readBuffer, count, maxChars) || truncated
                    }
                }
                if (channel.isClosed) break
                if (System.currentTimeMillis() > deadline) {
                    channel.disconnect()
                    throw IllegalStateException("SSH 命令执行超时")
                }
                Thread.sleep(50)
            }

            ExecResult(
                exitCode = channel.exitStatus,
                stdout = stdoutBuffer.toString(Charsets.UTF_8.name()),
                stderr = stderrBuffer.toString(Charsets.UTF_8.name()),
                truncated = truncated
            )
        } finally {
            channel?.disconnect()
            session.disconnect()
        }
    }

    private fun openSession(profile: SshProfile): Session {
        val jsch = JSch()
        if (profile.authType == SshAuthType.PRIVATE_KEY) {
            val key = profile.privateKey?.toByteArray(Charsets.UTF_8)
                ?: throw IllegalArgumentException("SSH profile 缺少私钥")
            val passphrase = profile.passphrase?.toByteArray(Charsets.UTF_8)
            jsch.addIdentity("tchat-${profile.alias}", key, null, passphrase)
        }

        val session = jsch.getSession(profile.username, profile.host, profile.port)
        if (profile.authType == SshAuthType.PASSWORD) {
            session.setPassword(profile.password ?: throw IllegalArgumentException("SSH profile 缺少密码"))
        }

        session.setConfig(Properties().apply {
            put("StrictHostKeyChecking", if (profile.strictHostKeyChecking) "yes" else "no")
            put("PreferredAuthentications", "publickey,password,keyboard-interactive")
        })
        return session
    }

    private fun appendLimited(
        buffer: ByteArrayOutputStream,
        source: ByteArray,
        count: Int,
        maxChars: Int
    ): Boolean {
        val remaining = maxChars - buffer.size()
        if (remaining <= 0) return true
        buffer.write(source, 0, min(count, remaining))
        return count > remaining
    }

    private fun readStreamLimited(input: InputStream, maxChars: Int): LimitedText {
        val buffer = ByteArrayOutputStream()
        val readBuffer = ByteArray(4096)
        var truncated = false

        while (true) {
            val count = input.read(readBuffer)
            if (count <= 0) break
            truncated = appendLimited(buffer, readBuffer, count, maxChars) || truncated
            if (truncated) break
        }

        return LimitedText(buffer.toString(Charsets.UTF_8.name()), truncated)
    }

    private fun sanitizeOutput(text: String, maxChars: Int): LimitedText {
        var sanitized = text
            .replace(PRIVATE_KEY_REGEX, "-----BEGIN PRIVATE KEY-----\n[REDACTED]\n-----END PRIVATE KEY-----")
            .replace(SECRET_ASSIGNMENT_REGEX, "$1=[REDACTED]")
            .replace(AUTH_HEADER_REGEX, "Authorization: [REDACTED]")

        val truncated = sanitized.length > maxChars
        if (truncated) {
            sanitized = sanitized.take(maxChars)
        }
        return LimitedText(sanitized, truncated)
    }

    private fun isSensitivePath(value: String): Boolean {
        val lower = value.lowercase()
        return SENSITIVE_PATH_MARKERS.any { lower.contains(it) }
    }

    private fun isReadonlyCommandAllowed(command: String): Boolean {
        val normalized = command.trim().replace(Regex("\\s+"), " ").lowercase()
        if (BLOCKED_COMMAND_MARKERS.any { normalized.contains(it) }) return false
        return ALLOWED_COMMAND_PREFIXES.any { prefix ->
            normalized == prefix || normalized.startsWith("$prefix ")
        }
    }

    private fun shellQuote(value: String): String {
        return "'" + value.replace("'", "'\"'\"'") + "'"
    }

    private fun errorJson(message: String): JSONObject {
        return JSONObject().apply {
            put("success", false)
            put("error", message)
        }
    }

    private fun Session.useSession(block: (Session) -> Unit) {
        try {
            block(this)
        } finally {
            disconnect()
        }
    }

    private data class ExecResult(
        val exitCode: Int,
        val stdout: String,
        val stderr: String,
        val truncated: Boolean
    )

    private data class LimitedText(
        val text: String,
        val truncated: Boolean
    )

    companion object {
        private const val DEFAULT_CONNECT_TIMEOUT_MS = 10_000
        private const val DEFAULT_COMMAND_TIMEOUT_SECONDS = 20
        private const val HARD_MAX_TIMEOUT_SECONDS = 60
        private const val DEFAULT_MAX_OUTPUT_CHARS = 16_000
        private const val HARD_MAX_OUTPUT_CHARS = 64_000
        private const val MAX_LIST_ITEMS = 300

        private val SENSITIVE_PATH_MARKERS = listOf(
            "~/.ssh",
            "/.ssh",
            ".env",
            "/id_rsa",
            "/id_ed25519",
            "/secrets",
            "/credentials",
            "/private"
        )

        private val ALLOWED_COMMAND_PREFIXES = listOf(
            "ls",
            "cat",
            "tail",
            "head",
            "grep",
            "find",
            "df",
            "du",
            "free",
            "ps",
            "whoami",
            "uname",
            "uptime",
            "pwd",
            "systemctl status",
            "journalctl",
            "docker ps"
        )

        private val BLOCKED_COMMAND_MARKERS = listOf(
            "rm ",
            "rm -",
            "mkfs",
            "dd if=",
            "shutdown",
            "reboot",
            "iptables",
            "ufw ",
            "systemctl restart",
            "systemctl stop",
            "docker rm",
            "docker system prune",
            "kubectl delete",
            "curl | sh",
            "wget | sh",
            "chmod ",
            "chown ",
            "sudo ",
            "su "
        )

        private val PRIVATE_KEY_REGEX = Regex(
            "-----BEGIN [A-Z ]*PRIVATE KEY-----[\\s\\S]*?-----END [A-Z ]*PRIVATE KEY-----"
        )
        private val SECRET_ASSIGNMENT_REGEX = Regex(
            "(?i)\\b(password|passwd|token|api[_-]?key|secret|authorization|private[_-]?key)\\b\\s*[:=]\\s*[^\\s,;]+"
        )
        private val AUTH_HEADER_REGEX = Regex("(?i)Authorization:\\s*[^\\r\\n]+")
    }
}
