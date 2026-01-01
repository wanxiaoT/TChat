package com.tchat.data.tool

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.StatFs
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * 本地工具集合
 *
 * 设计思路：
 * - 根据用户选择的工具类型，提供对应的功能
 * - 所有工具使用JSON格式进行输入输出
 * - 文件操作需要考虑Android存储权限
 */
class LocalTools(private val context: Context) {

    /**
     * 文件读取工具
     */
    val fileReadTool by lazy {
        Tool(
            name = "read_file",
            description = "读取指定文件的内容",
            parameters = {
                InputSchema.Obj(
                    properties = mapOf(
                        "path" to PropertyDef(
                            type = "string",
                            description = "文件的绝对路径"
                        ),
                        "encoding" to PropertyDef(
                            type = "string",
                            description = "字符编码，默认UTF-8"
                        )
                    ),
                    required = listOf("path")
                )
            },
            execute = { args ->
                val path = args.optString("path", "")
                val encoding = args.optString("encoding", "UTF-8")

                JSONObject().apply {
                    if (path.isBlank()) {
                        put("success", false)
                        put("error", "路径不能为空")
                    } else {
                        try {
                            val file = File(path)
                            when {
                                !file.exists() -> {
                                    put("success", false)
                                    put("error", "文件不存在: $path")
                                }
                                !file.isFile -> {
                                    put("success", false)
                                    put("error", "路径不是文件: $path")
                                }
                                !file.canRead() -> {
                                    put("success", false)
                                    put("error", "无法读取文件，请检查权限")
                                }
                                else -> {
                                    val content = file.readText(charset(encoding))
                                    put("success", true)
                                    put("content", content)
                                    put("size", file.length())
                                }
                            }
                        } catch (e: Exception) {
                            put("success", false)
                            put("error", "读取失败: ${e.message}")
                        }
                    }
                }
            }
        )
    }

    /**
     * 文件写入工具
     */
    val fileWriteTool by lazy {
        Tool(
            name = "write_file",
            description = "将内容写入指定文件",
            parameters = {
                InputSchema.Obj(
                    properties = mapOf(
                        "path" to PropertyDef(
                            type = "string",
                            description = "文件的绝对路径"
                        ),
                        "content" to PropertyDef(
                            type = "string",
                            description = "要写入的内容"
                        ),
                        "append" to PropertyDef(
                            type = "boolean",
                            description = "是否追加写入，默认false"
                        )
                    ),
                    required = listOf("path", "content")
                )
            },
            execute = { args ->
                val path = args.optString("path", "")
                val content = args.optString("content", "")
                val append = args.optBoolean("append", false)

                JSONObject().apply {
                    if (path.isBlank()) {
                        put("success", false)
                        put("error", "路径不能为空")
                    } else {
                        try {
                            val file = File(path)
                            file.parentFile?.mkdirs()
                            if (append) {
                                file.appendText(content)
                            } else {
                                file.writeText(content)
                            }
                            put("success", true)
                            put("path", path)
                            put("size", file.length())
                        } catch (e: Exception) {
                            put("success", false)
                            put("error", "写入失败: ${e.message}")
                        }
                    }
                }
            }
        )
    }

    /**
     * 目录列表工具
     */
    val listDirectoryTool by lazy {
        Tool(
            name = "list_directory",
            description = "列出指定目录下的文件和子目录",
            parameters = {
                InputSchema.Obj(
                    properties = mapOf(
                        "path" to PropertyDef(
                            type = "string",
                            description = "目录的绝对路径"
                        )
                    ),
                    required = listOf("path")
                )
            },
            execute = { args ->
                val path = args.optString("path", "")

                JSONObject().apply {
                    if (path.isBlank()) {
                        put("success", false)
                        put("error", "路径不能为空")
                    } else {
                        try {
                            val dir = File(path)
                            when {
                                !dir.exists() -> {
                                    put("success", false)
                                    put("error", "目录不存在: $path")
                                }
                                !dir.isDirectory -> {
                                    put("success", false)
                                    put("error", "路径不是目录: $path")
                                }
                                else -> {
                                    val files = dir.listFiles() ?: emptyArray()
                                    put("success", true)
                                    put("path", path)
                                    put("items", JSONArray().apply {
                                        files.forEach { file ->
                                            put(JSONObject().apply {
                                                put("name", file.name)
                                                put("isDirectory", file.isDirectory)
                                                put("size", file.length())
                                                put("lastModified", file.lastModified())
                                            })
                                        }
                                    })
                                    put("count", files.size)
                                }
                            }
                        } catch (e: Exception) {
                            put("success", false)
                            put("error", "列表失败: ${e.message}")
                        }
                    }
                }
            }
        )
    }

    /**
     * 文件删除工具
     */
    val deleteFileTool by lazy {
        Tool(
            name = "delete_file",
            description = "删除指定文件或空目录",
            parameters = {
                InputSchema.Obj(
                    properties = mapOf(
                        "path" to PropertyDef(
                            type = "string",
                            description = "文件或目录的绝对路径"
                        )
                    ),
                    required = listOf("path")
                )
            },
            execute = { args ->
                val path = args.optString("path", "")

                JSONObject().apply {
                    if (path.isBlank()) {
                        put("success", false)
                        put("error", "路径不能为空")
                    } else {
                        try {
                            val file = File(path)
                            if (!file.exists()) {
                                put("success", false)
                                put("error", "文件不存在: $path")
                            } else if (file.delete()) {
                                put("success", true)
                                put("path", path)
                            } else {
                                put("success", false)
                                put("error", "删除失败，可能是目录非空或权限不足")
                            }
                        } catch (e: Exception) {
                            put("success", false)
                            put("error", "删除失败: ${e.message}")
                        }
                    }
                }
            }
        )
    }

    /**
     * 创建目录工具
     */
    val createDirectoryTool by lazy {
        Tool(
            name = "create_directory",
            description = "创建目录，包括所有必要的父目录",
            parameters = {
                InputSchema.Obj(
                    properties = mapOf(
                        "path" to PropertyDef(
                            type = "string",
                            description = "目录的绝对路径"
                        )
                    ),
                    required = listOf("path")
                )
            },
            execute = { args ->
                val path = args.optString("path", "")

                JSONObject().apply {
                    if (path.isBlank()) {
                        put("success", false)
                        put("error", "路径不能为空")
                    } else {
                        try {
                            val dir = File(path)
                            if (dir.exists()) {
                                put("success", true)
                                put("path", path)
                                put("message", "目录已存在")
                            } else if (dir.mkdirs()) {
                                put("success", true)
                                put("path", path)
                            } else {
                                put("success", false)
                                put("error", "创建目录失败")
                            }
                        } catch (e: Exception) {
                            put("success", false)
                            put("error", "创建失败: ${e.message}")
                        }
                    }
                }
            }
        )
    }

    /**
     * 网页抓取工具
     */
    val webFetchTool by lazy {
        Tool(
            name = "web_fetch",
            description = "获取指定URL的网页内容。当用户需要查看某个网页内容、抓取网站信息、获取在线数据时应该调用此工具",
            parameters = {
                InputSchema.Obj(
                    properties = mapOf(
                        "url" to PropertyDef(
                            type = "string",
                            description = "要获取的URL"
                        ),
                        "timeout" to PropertyDef(
                            type = "integer",
                            description = "超时时间（秒），默认30秒"
                        )
                    ),
                    required = listOf("url")
                )
            },
            execute = { args ->
                val urlString = args.optString("url", "")
                val timeout = args.optInt("timeout", 30)

                JSONObject().apply {
                    if (urlString.isBlank()) {
                        put("success", false)
                        put("error", "URL不能为空")
                    } else {
                        try {
                            val url = URL(urlString)
                            val connection = url.openConnection() as HttpURLConnection
                            connection.connectTimeout = timeout * 1000
                            connection.readTimeout = timeout * 1000
                            connection.setRequestProperty("User-Agent", "TChat/1.0")

                            val responseCode = connection.responseCode
                            if (responseCode == HttpURLConnection.HTTP_OK) {
                                val content = connection.inputStream.bufferedReader().use { it.readText() }
                                put("success", true)
                                put("url", urlString)
                                put("content", content)
                                put("contentLength", content.length)
                                put("contentType", connection.contentType ?: "unknown")
                            } else {
                                put("success", false)
                                put("error", "HTTP错误: $responseCode")
                            }
                            connection.disconnect()
                        } catch (e: Exception) {
                            put("success", false)
                            put("error", "请求失败: ${e.message}")
                        }
                    }
                }
            }
        )
    }

    /**
     * 系统信息工具
     */
    val systemInfoTool by lazy {
        Tool(
            name = "get_system_info",
            description = "获取用户设备的详细信息，包括：手机型号、品牌、制造商、Android版本、SDK级别、存储容量（总容量/已用/可用）、内存信息等。当用户询问「我的手机是什么型号」「我的设备是啥」「存储空间还有多少」「内存多大」等问题时，应该调用此工具",
            parameters = {
                // 无参数工具也需要定义空的对象，否则某些 API 无法识别
                InputSchema.Obj(
                    properties = emptyMap(),
                    required = emptyList()
                )
            },
            execute = {
                JSONObject().apply {
                    put("success", true)

                    // 设备信息
                    put("device", JSONObject().apply {
                        put("manufacturer", Build.MANUFACTURER)
                        put("model", Build.MODEL)
                        put("brand", Build.BRAND)
                        put("androidVersion", Build.VERSION.RELEASE)
                        put("sdkLevel", Build.VERSION.SDK_INT)
                    })

                    // 存储信息
                    try {
                        val externalStorage = Environment.getExternalStorageDirectory()
                        val stat = StatFs(externalStorage.path)
                        val totalBytes = stat.totalBytes
                        val freeBytes = stat.freeBytes

                        put("storage", JSONObject().apply {
                            put("totalGB", String.format("%.2f", totalBytes / 1_000_000_000.0))
                            put("freeGB", String.format("%.2f", freeBytes / 1_000_000_000.0))
                            put("usedGB", String.format("%.2f", (totalBytes - freeBytes) / 1_000_000_000.0))
                        })
                    } catch (e: Exception) {
                        put("storageError", e.message)
                    }

                    // 内存信息
                    try {
                        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                        val memInfo = ActivityManager.MemoryInfo()
                        activityManager.getMemoryInfo(memInfo)

                        put("memory", JSONObject().apply {
                            put("totalGB", String.format("%.2f", memInfo.totalMem / 1_000_000_000.0))
                            put("availableGB", String.format("%.2f", memInfo.availMem / 1_000_000_000.0))
                            put("lowMemory", memInfo.lowMemory)
                        })
                    } catch (e: Exception) {
                        put("memoryError", e.message)
                    }
                }
            }
        )
    }

    /**
     * 获取文件系统相关的所有工具
     */
    fun getFileSystemTools(): List<Tool> = listOf(
        fileReadTool,
        fileWriteTool,
        listDirectoryTool,
        deleteFileTool,
        createDirectoryTool
    )

    /**
     * 获取网页抓取工具
     */
    fun getWebFetchTools(): List<Tool> = listOf(webFetchTool)

    /**
     * 获取系统信息工具
     */
    fun getSystemInfoTools(): List<Tool> = listOf(systemInfoTool)

    /**
     * 根据 LocalToolOption 列表获取对应的工具
     */
    fun getToolsForOptions(options: List<com.tchat.data.model.LocalToolOption>): List<Tool> {
        return options.flatMap { option ->
            when (option) {
                is com.tchat.data.model.LocalToolOption.FileSystem -> getFileSystemTools()
                is com.tchat.data.model.LocalToolOption.WebFetch -> getWebFetchTools()
                is com.tchat.data.model.LocalToolOption.SystemInfo -> getSystemInfoTools()
            }
        }
    }

    /**
     * 获取所有可用的工具
     */
    fun getAllTools(): List<Tool> = getFileSystemTools() + getWebFetchTools() + getSystemInfoTools()

    /**
     * 根据工具名称执行工具
     */
    suspend fun executeTool(name: String, arguments: JSONObject): JSONObject {
        val tool = when (name) {
            "read_file" -> fileReadTool
            "write_file" -> fileWriteTool
            "list_directory" -> listDirectoryTool
            "delete_file" -> deleteFileTool
            "create_directory" -> createDirectoryTool
            "web_fetch" -> webFetchTool
            "get_system_info" -> systemInfoTool
            else -> return JSONObject().apply {
                put("success", false)
                put("error", "未知工具: $name")
            }
        }
        return tool.execute(arguments)
    }
}
