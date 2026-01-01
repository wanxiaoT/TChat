package com.tchat.data.model

/**
 * 本地工具选项
 */
sealed class LocalToolOption(val id: String) {
    /**
     * 文件系统工具 - 支持文件读写、目录操作、搜索等
     */
    data object FileSystem : LocalToolOption("file_system")

    /**
     * 网页抓取工具 - 支持获取网页内容
     */
    data object WebFetch : LocalToolOption("web_fetch")

    /**
     * 系统信息工具 - 获取设备和存储信息
     */
    data object SystemInfo : LocalToolOption("system_info")

    companion object {
        /**
         * 获取所有可用的工具选项
         */
        fun allOptions(): List<LocalToolOption> = listOf(
            FileSystem,
            WebFetch,
            SystemInfo
        )

        /**
         * 根据ID获取工具选项
         */
        fun fromId(id: String): LocalToolOption? = when (id) {
            "file_system" -> FileSystem
            "web_fetch" -> WebFetch
            "system_info" -> SystemInfo
            else -> null
        }

        /**
         * 获取工具的显示名称
         */
        fun LocalToolOption.displayName(): String = when (this) {
            is FileSystem -> "文件系统"
            is WebFetch -> "网页抓取"
            is SystemInfo -> "系统信息"
        }

        /**
         * 获取工具的描述
         */
        fun LocalToolOption.description(): String = when (this) {
            is FileSystem -> "读写文件、目录操作、文件搜索等"
            is WebFetch -> "获取网页内容和数据"
            is SystemInfo -> "获取设备信息和存储状态"
        }
    }
}
