package com.tchat.wanxiaot.util

import android.content.Context
import com.tchat.data.database.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * 数据库备份管理器
 * 支持将整个数据库打包为 zip 文件进行备份和恢复
 */
class DatabaseBackupManager(private val context: Context) {

    companion object {
        private const val DATABASE_NAME = "tchat_database"
        private const val BACKUP_PREFIX = "TChat_Backup_"
        private const val BUFFER_SIZE = 8192
    }

    /**
     * 备份数据库到指定文件
     * @param outputFile 输出的 zip 文件
     */
    suspend fun backupDatabase(outputFile: File): Result<BackupInfo> = withContext(Dispatchers.IO) {
        try {
            // 获取数据库实例并关闭以确保数据完整性
            val database = AppDatabase.getInstance(context)

            // 执行 checkpoint 确保 WAL 日志写入主数据库
            database.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").use { }

            val databasePath = context.getDatabasePath(DATABASE_NAME)
            val databaseDir = databasePath.parentFile ?: return@withContext Result.failure(
                IllegalStateException("无法获取数据库目录")
            )

            // 收集所有数据库相关文件
            val dbFiles = mutableListOf<File>()

            // 主数据库文件
            if (databasePath.exists()) {
                dbFiles.add(databasePath)
            } else {
                return@withContext Result.failure(
                    IllegalStateException("数据库文件不存在")
                )
            }

            // WAL 和 SHM 文件（如果存在）
            val walFile = File(databaseDir, "$DATABASE_NAME-wal")
            val shmFile = File(databaseDir, "$DATABASE_NAME-shm")
            if (walFile.exists()) dbFiles.add(walFile)
            if (shmFile.exists()) dbFiles.add(shmFile)

            // 计算总大小
            var totalSize = 0L
            dbFiles.forEach { totalSize += it.length() }

            // 创建 zip 文件
            ZipOutputStream(BufferedOutputStream(FileOutputStream(outputFile))).use { zipOut ->
                dbFiles.forEach { file ->
                    val entryName = file.name
                    zipOut.putNextEntry(ZipEntry(entryName))

                    BufferedInputStream(FileInputStream(file), BUFFER_SIZE).use { input ->
                        val buffer = ByteArray(BUFFER_SIZE)
                        var len: Int
                        while (input.read(buffer).also { len = it } > 0) {
                            zipOut.write(buffer, 0, len)
                        }
                    }

                    zipOut.closeEntry()
                }

                // 添加备份元数据
                val metadata = createBackupMetadata(dbFiles.size, totalSize)
                zipOut.putNextEntry(ZipEntry("backup_info.txt"))
                zipOut.write(metadata.toByteArray())
                zipOut.closeEntry()
            }

            val backupInfo = BackupInfo(
                fileName = outputFile.name,
                fileSize = outputFile.length(),
                fileCount = dbFiles.size,
                timestamp = System.currentTimeMillis()
            )

            Result.success(backupInfo)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 从 zip 文件恢复数据库
     * @param inputFile 输入的 zip 备份文件
     */
    suspend fun restoreDatabase(inputFile: File): Result<RestoreInfo> = withContext(Dispatchers.IO) {
        try {
            // 验证备份文件
            if (!inputFile.exists()) {
                return@withContext Result.failure(
                    IllegalArgumentException("备份文件不存在")
                )
            }

            if (!isValidBackup(inputFile)) {
                return@withContext Result.failure(
                    IllegalArgumentException("无效的备份文件格式")
                )
            }

            val databasePath = context.getDatabasePath(DATABASE_NAME)
            val databaseDir = databasePath.parentFile ?: return@withContext Result.failure(
                IllegalStateException("无法获取数据库目录")
            )

            // 关闭当前数据库连接
            AppDatabase.closeDatabase()

            // 删除现有数据库文件
            val existingFiles = listOf(
                databasePath,
                File(databaseDir, "$DATABASE_NAME-wal"),
                File(databaseDir, "$DATABASE_NAME-shm"),
                File(databaseDir, "$DATABASE_NAME-journal")
            )
            existingFiles.forEach { file ->
                if (file.exists()) {
                    file.delete()
                }
            }

            // 解压备份文件
            var restoredCount = 0
            ZipInputStream(BufferedInputStream(FileInputStream(inputFile))).use { zipIn ->
                var entry: ZipEntry?
                while (zipIn.nextEntry.also { entry = it } != null) {
                    val entryName = entry!!.name

                    // 跳过元数据文件
                    if (entryName == "backup_info.txt") {
                        zipIn.closeEntry()
                        continue
                    }

                    // 只恢复数据库相关文件
                    if (entryName.startsWith(DATABASE_NAME)) {
                        val outputFile = File(databaseDir, entryName)

                        BufferedOutputStream(FileOutputStream(outputFile), BUFFER_SIZE).use { output ->
                            val buffer = ByteArray(BUFFER_SIZE)
                            var len: Int
                            while (zipIn.read(buffer).also { len = it } > 0) {
                                output.write(buffer, 0, len)
                            }
                        }
                        restoredCount++
                    }

                    zipIn.closeEntry()
                }
            }

            // 重新初始化数据库
            AppDatabase.getInstance(context)

            val restoreInfo = RestoreInfo(
                restoredFileCount = restoredCount,
                timestamp = System.currentTimeMillis()
            )

            Result.success(restoreInfo)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 生成默认备份文件名
     */
    fun generateBackupFileName(): String {
        val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
        val timestamp = dateFormat.format(Date())
        return "${BACKUP_PREFIX}${timestamp}.zip"
    }

    /**
     * 验证备份文件是否有效
     */
    private fun isValidBackup(file: File): Boolean {
        return try {
            var hasMainDb = false
            ZipInputStream(BufferedInputStream(FileInputStream(file))).use { zipIn ->
                var entry: ZipEntry?
                while (zipIn.nextEntry.also { entry = it } != null) {
                    if (entry!!.name == DATABASE_NAME) {
                        hasMainDb = true
                    }
                    zipIn.closeEntry()
                }
            }
            hasMainDb
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 创建备份元数据
     */
    private fun createBackupMetadata(fileCount: Int, totalSize: Long): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        return buildString {
            appendLine("TChat Database Backup")
            appendLine("=====================")
            appendLine("Created: ${dateFormat.format(Date())}")
            appendLine("Files: $fileCount")
            appendLine("Original Size: ${formatFileSize(totalSize)}")
            appendLine("App Version: ${getAppVersion()}")
            appendLine("Database: $DATABASE_NAME")
        }
    }

    /**
     * 获取应用版本
     */
    private fun getAppVersion(): String {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            "${packageInfo.versionName} (${packageInfo.longVersionCode})"
        } catch (e: Exception) {
            "Unknown"
        }
    }

    /**
     * 格式化文件大小
     */
    private fun formatFileSize(size: Long): String {
        return when {
            size < 1024 -> "$size B"
            size < 1024 * 1024 -> "${size / 1024} KB"
            size < 1024 * 1024 * 1024 -> "${size / (1024 * 1024)} MB"
            else -> "${size / (1024 * 1024 * 1024)} GB"
        }
    }

    /**
     * 备份信息
     */
    data class BackupInfo(
        val fileName: String,
        val fileSize: Long,
        val fileCount: Int,
        val timestamp: Long
    )

    /**
     * 恢复信息
     */
    data class RestoreInfo(
        val restoredFileCount: Int,
        val timestamp: Long
    )
}

/**
 * AppDatabase 扩展：关闭数据库
 */
fun AppDatabase.Companion.closeDatabase() {
    // 通过反射获取并关闭 INSTANCE
    try {
        val instanceField = AppDatabase::class.java.getDeclaredField("INSTANCE")
        instanceField.isAccessible = true
        val instance = instanceField.get(null) as? AppDatabase
        instance?.close()
        instanceField.set(null, null)
    } catch (e: Exception) {
        // 忽略反射错误
    }
}
