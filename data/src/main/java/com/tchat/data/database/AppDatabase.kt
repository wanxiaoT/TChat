package com.tchat.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.tchat.data.database.dao.ChatDao
import com.tchat.data.database.dao.MessageDao
import com.tchat.data.database.dao.KnowledgeBaseDao
import com.tchat.data.database.dao.KnowledgeItemDao
import com.tchat.data.database.dao.KnowledgeChunkDao
import com.tchat.data.database.dao.AssistantDao
import com.tchat.data.database.dao.McpServerDao
import com.tchat.data.database.dao.DeepResearchHistoryDao
import com.tchat.data.database.dao.ChatFolderDao
import com.tchat.data.database.dao.GroupChatDao
import com.tchat.data.database.dao.AppSettingsDao
import com.tchat.data.database.dao.SkillDao
import com.tchat.data.database.entity.ChatEntity
import com.tchat.data.database.entity.MessageEntity
import com.tchat.data.database.entity.KnowledgeBaseEntity
import com.tchat.data.database.entity.KnowledgeItemEntity
import com.tchat.data.database.entity.KnowledgeChunkEntity
import com.tchat.data.database.entity.AssistantEntity
import com.tchat.data.database.entity.McpServerEntity
import com.tchat.data.database.entity.DeepResearchHistoryEntity
import com.tchat.data.database.entity.ChatFolderEntity
import com.tchat.data.database.entity.ChatFolderRelationEntity
import com.tchat.data.database.entity.GroupChatEntity
import com.tchat.data.database.entity.GroupMemberEntity
import com.tchat.data.database.entity.LocalToolOptionConverter
import com.tchat.data.database.entity.AppSettingsEntity
import com.tchat.data.database.entity.SkillEntity

/**
 * TChat数据库
 *
 * 借鉴cherry-studio的数据库设计，使用单例模式
 */
@Database(
    entities = [
        ChatEntity::class,
        MessageEntity::class,
        KnowledgeBaseEntity::class,
        KnowledgeItemEntity::class,
        KnowledgeChunkEntity::class,
        AssistantEntity::class,
        McpServerEntity::class,
        DeepResearchHistoryEntity::class,
        ChatFolderEntity::class,
        ChatFolderRelationEntity::class,
        GroupChatEntity::class,
        GroupMemberEntity::class,
        AppSettingsEntity::class,
        SkillEntity::class
    ],
    version = 23,
    exportSchema = false
)
@TypeConverters(LocalToolOptionConverter::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun chatDao(): ChatDao
    abstract fun messageDao(): MessageDao
    abstract fun knowledgeBaseDao(): KnowledgeBaseDao
    abstract fun knowledgeItemDao(): KnowledgeItemDao
    abstract fun knowledgeChunkDao(): KnowledgeChunkDao
    abstract fun assistantDao(): AssistantDao
    abstract fun mcpServerDao(): McpServerDao
    abstract fun deepResearchHistoryDao(): DeepResearchHistoryDao
    abstract fun chatFolderDao(): ChatFolderDao
    abstract fun groupChatDao(): GroupChatDao
    abstract fun appSettingsDao(): AppSettingsDao
    abstract fun skillDao(): SkillDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        // 迁移：添加统计信息字段
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE messages ADD COLUMN inputTokens INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE messages ADD COLUMN outputTokens INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE messages ADD COLUMN tokensPerSecond REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE messages ADD COLUMN firstTokenLatency INTEGER NOT NULL DEFAULT 0")
            }
        }

        // 迁移：添加变体支持字段
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE messages ADD COLUMN variantsJson TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE messages ADD COLUMN selectedVariantIndex INTEGER NOT NULL DEFAULT 0")
            }
        }

        // 迁移:添加知识库表
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 创建知识库表
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS knowledge_bases (
                        id TEXT PRIMARY KEY NOT NULL,
                        name TEXT NOT NULL,
                        description TEXT,
                        embeddingProviderId TEXT NOT NULL,
                        embeddingModelId TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                """.trimIndent())

                // 创建知识条目表
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS knowledge_items (
                        id TEXT PRIMARY KEY NOT NULL,
                        knowledgeBaseId TEXT NOT NULL,
                        title TEXT NOT NULL,
                        content TEXT NOT NULL,
                        sourceType TEXT NOT NULL,
                        sourceUri TEXT,
                        metadata TEXT,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        FOREIGN KEY(knowledgeBaseId) REFERENCES knowledge_bases(id) ON DELETE CASCADE
                    )
                """.trimIndent())

                // 创建知识块表
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS knowledge_chunks (
                        id TEXT PRIMARY KEY NOT NULL,
                        itemId TEXT NOT NULL,
                        knowledgeBaseId TEXT NOT NULL,
                        content TEXT NOT NULL,
                        embedding TEXT NOT NULL,
                        chunkIndex INTEGER NOT NULL,
                        createdAt INTEGER NOT NULL,
                        FOREIGN KEY(itemId) REFERENCES knowledge_items(id) ON DELETE CASCADE
                    )
                """.trimIndent())

                // 创建索引
                db.execSQL("CREATE INDEX IF NOT EXISTS index_knowledge_items_knowledgeBaseId ON knowledge_items(knowledgeBaseId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_knowledge_chunks_itemId ON knowledge_chunks(itemId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_knowledge_chunks_knowledgeBaseId ON knowledge_chunks(knowledgeBaseId)")
            }
        }

        // 迁移:添加助手表
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS assistants (
                        id TEXT PRIMARY KEY NOT NULL,
                        name TEXT NOT NULL,
                        avatar TEXT,
                        systemPrompt TEXT NOT NULL,
                        temperature REAL,
                        topP REAL,
                        maxTokens INTEGER,
                        contextMessageSize INTEGER NOT NULL,
                        streamOutput INTEGER NOT NULL,
                        localTools TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                """.trimIndent())
            }
        }

        // 迁移:添加工具调用支持字段
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE messages ADD COLUMN toolCallId TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE messages ADD COLUMN toolName TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE messages ADD COLUMN toolCallsJson TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE messages ADD COLUMN toolResultsJson TEXT DEFAULT NULL")
            }
        }

        // 迁移:添加知识条目状态字段
        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE knowledge_items ADD COLUMN status TEXT NOT NULL DEFAULT 'PENDING'")
                db.execSQL("ALTER TABLE knowledge_items ADD COLUMN errorMessage TEXT DEFAULT NULL")
            }
        }

        // 迁移:为助手添加知识库关联字段
        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE assistants ADD COLUMN knowledgeBaseId TEXT DEFAULT NULL")
            }
        }

        // 迁移:添加MCP服务器表
        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS mcp_servers (
                        id TEXT PRIMARY KEY NOT NULL,
                        name TEXT NOT NULL,
                        description TEXT NOT NULL,
                        type TEXT NOT NULL,
                        url TEXT NOT NULL,
                        enabled INTEGER NOT NULL,
                        timeout INTEGER NOT NULL,
                        headers TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                """.trimIndent())
                // 为助手添加MCP服务器ID列表字段
                db.execSQL("ALTER TABLE assistants ADD COLUMN mcpServerIds TEXT NOT NULL DEFAULT '[]'")
            }
        }

        // 迁移:为消息添加模型名称字段
        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE messages ADD COLUMN modelName TEXT DEFAULT NULL")
            }
        }

        // 迁移:添加深度研究历史记录表
        private val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS deep_research_history (
                        id TEXT PRIMARY KEY NOT NULL,
                        query TEXT NOT NULL,
                        report TEXT NOT NULL,
                        learningsJson TEXT NOT NULL,
                        startTime INTEGER NOT NULL,
                        endTime INTEGER NOT NULL,
                        status TEXT NOT NULL
                    )
                """.trimIndent())
            }
        }

        // 迁移:为助手添加正则规则ID列表字段
        private val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 检查列是否已存在，避免重复添加导致崩溃
                val cursor = db.query("PRAGMA table_info(assistants)")
                var columnExists = false
                cursor.use {
                    val nameIndex = cursor.getColumnIndex("name")
                    while (cursor.moveToNext()) {
                        val columnName = cursor.getString(nameIndex)
                        if (columnName == "enabledRegexRuleIds") {
                            columnExists = true
                            break
                        }
                    }
                }
                if (!columnExists) {
                    db.execSQL("ALTER TABLE assistants ADD COLUMN enabledRegexRuleIds TEXT NOT NULL DEFAULT '[]'")
                }
            }
        }

        // 迁移:为聊天添加标题手动编辑标记字段
        private val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 检查列是否已存在，避免重复添加导致崩溃
                val cursor = db.query("PRAGMA table_info(chats)")
                var columnExists = false
                cursor.use {
                    val nameIndex = cursor.getColumnIndex("name")
                    while (cursor.moveToNext()) {
                        val columnName = cursor.getString(nameIndex)
                        if (columnName == "isNameManuallyEdited") {
                            columnExists = true
                            break
                        }
                    }
                }
                if (!columnExists) {
                    db.execSQL("ALTER TABLE chats ADD COLUMN isNameManuallyEdited INTEGER NOT NULL DEFAULT 0")
                }
            }
        }

        // 迁移:添加聊天文件夹和群聊表
        private val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 创建聊天文件夹表
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS chat_folders (
                        id TEXT PRIMARY KEY NOT NULL,
                        name TEXT NOT NULL,
                        parentId TEXT,
                        icon TEXT,
                        color TEXT,
                        sort_order INTEGER NOT NULL DEFAULT 0,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                """.trimIndent())

                // 创建聊天文件夹关联表
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS chat_folder_relations (
                        chatId TEXT NOT NULL,
                        folderId TEXT NOT NULL,
                        addedAt INTEGER NOT NULL,
                        PRIMARY KEY(chatId, folderId),
                        FOREIGN KEY(chatId) REFERENCES chats(id) ON DELETE CASCADE,
                        FOREIGN KEY(folderId) REFERENCES chat_folders(id) ON DELETE CASCADE
                    )
                """.trimIndent())

                // 创建群聊表
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS group_chats (
                        id TEXT PRIMARY KEY NOT NULL,
                        name TEXT NOT NULL,
                        avatar TEXT,
                        description TEXT,
                        activationStrategy TEXT NOT NULL,
                        generationMode TEXT NOT NULL,
                        autoModeEnabled INTEGER NOT NULL DEFAULT 0,
                        autoModeDelay INTEGER NOT NULL DEFAULT 5,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                """.trimIndent())

                // 创建群聊成员表
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS group_members (
                        groupId TEXT NOT NULL,
                        assistantId TEXT NOT NULL,
                        enabled INTEGER NOT NULL DEFAULT 1,
                        priority INTEGER NOT NULL DEFAULT 0,
                        talkativeness REAL NOT NULL DEFAULT 0.5,
                        joinedAt INTEGER NOT NULL,
                        PRIMARY KEY(groupId, assistantId),
                        FOREIGN KEY(groupId) REFERENCES group_chats(id) ON DELETE CASCADE,
                        FOREIGN KEY(assistantId) REFERENCES assistants(id) ON DELETE CASCADE
                    )
                """.trimIndent())

                // 创建索引
                db.execSQL("CREATE INDEX IF NOT EXISTS index_chat_folder_relations_chatId ON chat_folder_relations(chatId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_chat_folder_relations_folderId ON chat_folder_relations(folderId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_chat_folders_parentId ON chat_folders(parentId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_group_members_groupId ON group_members(groupId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_group_members_assistantId ON group_members(assistantId)")
            }
        }

        // 迁移:添加应用设置表
        private val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS app_settings (
                        id INTEGER PRIMARY KEY NOT NULL DEFAULT 1,
                        currentProviderId TEXT NOT NULL DEFAULT '',
                        currentModel TEXT NOT NULL DEFAULT '',
                        currentAssistantId TEXT NOT NULL DEFAULT '',
                        providersJson TEXT NOT NULL DEFAULT '[]',
                        deepResearchSettingsJson TEXT NOT NULL DEFAULT '{}',
                        providerGridColumnCount INTEGER NOT NULL DEFAULT 1,
                        regexRulesJson TEXT NOT NULL DEFAULT '[]'
                    )
                """.trimIndent())
            }
        }

        // 迁移:为消息添加提供商ID字段（用于按提供商统计token）
        private val MIGRATION_16_17 = object : Migration(16, 17) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 为消息表添加提供商ID字段
                db.execSQL("ALTER TABLE messages ADD COLUMN providerId TEXT DEFAULT NULL")
                // 为应用设置表添加token记录状态字段
                db.execSQL("ALTER TABLE app_settings ADD COLUMN tokenRecordingStatus TEXT NOT NULL DEFAULT 'ENABLED'")
            }
        }

        // 迁移:添加技能表和助手技能关联字段
        private val MIGRATION_17_18 = object : Migration(17, 18) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 创建技能表
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS skills (
                        id TEXT PRIMARY KEY NOT NULL,
                        name TEXT NOT NULL,
                        displayName TEXT NOT NULL,
                        description TEXT NOT NULL,
                        content TEXT NOT NULL,
                        triggerKeywords TEXT NOT NULL DEFAULT '[]',
                        priority INTEGER NOT NULL DEFAULT 0,
                        enabled INTEGER NOT NULL DEFAULT 1,
                        isBuiltIn INTEGER NOT NULL DEFAULT 0,
                        toolsJson TEXT NOT NULL DEFAULT '[]',
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                """.trimIndent())

                // 为助手表添加启用技能ID列表字段
                db.execSQL("ALTER TABLE assistants ADD COLUMN enabledSkillIds TEXT NOT NULL DEFAULT '[]'")
            }
        }

        // 迁移:为应用设置添加TTS设置字段
        private val MIGRATION_18_19 = object : Migration(18, 19) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE app_settings ADD COLUMN ttsSettingsJson TEXT NOT NULL DEFAULT '{}'")
            }
        }

        // 迁移:为应用设置添加R2云备份设置字段
        private val MIGRATION_19_20 = object : Migration(19, 20) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE app_settings ADD COLUMN r2SettingsJson TEXT NOT NULL DEFAULT '{}'")
            }
        }

        // 迁移:为应用设置添加语言字段
        private val MIGRATION_20_21 = object : Migration(20, 21) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE app_settings ADD COLUMN language TEXT NOT NULL DEFAULT 'zh-CN'")
            }
        }

        // 迁移:为应用设置添加 OCR 模型字段
        private val MIGRATION_21_22 = object : Migration(21, 22) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE app_settings ADD COLUMN ocrModel TEXT NOT NULL DEFAULT 'MLKIT_LATIN'")
            }
        }

        // 迁移:为应用设置添加 OCR 设置 JSON 字段
        private val MIGRATION_22_23 = object : Migration(22, 23) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE app_settings ADD COLUMN ocrSettingsJson TEXT NOT NULL DEFAULT '{}'")
            }
        }

        // 迁移:采用 MessagePart 架构，将旧字段迁移到 partsJson
        private val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1. 创建新表结构
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS messages_new (
                        id TEXT PRIMARY KEY NOT NULL,
                        chatId TEXT NOT NULL,
                        role TEXT NOT NULL,
                        partsJson TEXT NOT NULL,
                        timestamp INTEGER NOT NULL,
                        inputTokens INTEGER NOT NULL DEFAULT 0,
                        outputTokens INTEGER NOT NULL DEFAULT 0,
                        tokensPerSecond REAL NOT NULL DEFAULT 0.0,
                        firstTokenLatency INTEGER NOT NULL DEFAULT 0,
                        modelName TEXT DEFAULT NULL,
                        variantsJson TEXT DEFAULT NULL,
                        selectedVariantIndex INTEGER NOT NULL DEFAULT 0,
                        FOREIGN KEY(chatId) REFERENCES chats(id) ON DELETE CASCADE
                    )
                """.trimIndent())

                // 2. 查询所有旧消息
                val cursor = db.query("SELECT * FROM messages")

                cursor.use {
                    while (cursor.moveToNext()) {
                        val id = cursor.getString(cursor.getColumnIndexOrThrow("id"))
                        val chatId = cursor.getString(cursor.getColumnIndexOrThrow("chatId"))
                        val role = cursor.getString(cursor.getColumnIndexOrThrow("role"))
                        val content = cursor.getString(cursor.getColumnIndexOrThrow("content"))
                        val timestamp = cursor.getLong(cursor.getColumnIndexOrThrow("timestamp"))
                        val inputTokens = cursor.getInt(cursor.getColumnIndexOrThrow("inputTokens"))
                        val outputTokens = cursor.getInt(cursor.getColumnIndexOrThrow("outputTokens"))
                        val tokensPerSecond = cursor.getDouble(cursor.getColumnIndexOrThrow("tokensPerSecond"))
                        val firstTokenLatency = cursor.getLong(cursor.getColumnIndexOrThrow("firstTokenLatency"))
                        val modelName = cursor.getString(cursor.getColumnIndexOrThrow("modelName"))
                        val variantsJson = cursor.getString(cursor.getColumnIndexOrThrow("variantsJson"))
                        val selectedVariantIndex = cursor.getInt(cursor.getColumnIndexOrThrow("selectedVariantIndex"))
                        val toolCallsJson = cursor.getString(cursor.getColumnIndexOrThrow("toolCallsJson"))
                        val toolResultsJson = cursor.getString(cursor.getColumnIndexOrThrow("toolResultsJson"))

                        // 构建 partsJson
                        val parts = mutableListOf<String>()

                        // 添加文本内容
                        if (content.isNotEmpty()) {
                            val escapedContent = content
                                .replace("\\", "\\\\")
                                .replace("\"", "\\\"")
                                .replace("\n", "\\n")
                                .replace("\r", "\\r")
                                .replace("\t", "\\t")
                            parts.add("""{"type":"text","content":"$escapedContent"}""")
                        }

                        // 添加工具调用（如果有）
                        if (!toolCallsJson.isNullOrEmpty() && toolCallsJson != "null") {
                            try {
                                // 解析旧的 toolCallsJson 并转换为新格式
                                val toolCallsArray = org.json.JSONArray(toolCallsJson)
                                for (i in 0 until toolCallsArray.length()) {
                                    val tc = toolCallsArray.getJSONObject(i)
                                    val toolCallPart = org.json.JSONObject()
                                    toolCallPart.put("type", "tool_call")
                                    toolCallPart.put("toolCallId", tc.optString("id", ""))
                                    toolCallPart.put("toolName", tc.optString("name", ""))
                                    toolCallPart.put("arguments", tc.optString("arguments", "{}"))
                                    parts.add(toolCallPart.toString())
                                }
                            } catch (e: Exception) {
                                // 忽略解析错误
                            }
                        }

                        // 添加工具结果（如果有）
                        if (!toolResultsJson.isNullOrEmpty() && toolResultsJson != "null") {
                            try {
                                // 解析旧的 toolResultsJson 并转换为新格式
                                val toolResultsArray = org.json.JSONArray(toolResultsJson)
                                for (i in 0 until toolResultsArray.length()) {
                                    val tr = toolResultsArray.getJSONObject(i)
                                    val toolResultPart = org.json.JSONObject()
                                    toolResultPart.put("type", "tool_result")
                                    toolResultPart.put("toolCallId", tr.optString("toolCallId", ""))
                                    toolResultPart.put("toolName", tr.optString("name", ""))
                                    toolResultPart.put("arguments", tr.optString("arguments", "{}"))
                                    toolResultPart.put("result", tr.optString("result", ""))
                                    toolResultPart.put("isError", tr.optBoolean("isError", false))
                                    toolResultPart.put("executionTimeMs", tr.optLong("executionTimeMs", 0))
                                    parts.add(toolResultPart.toString())
                                }
                            } catch (e: Exception) {
                                // 忽略解析错误
                            }
                        }

                        // 如果没有任何部分，添加空文本
                        val partsJson = if (parts.isEmpty()) {
                            """[{"type":"text","content":""}]"""
                        } else {
                            "[${parts.joinToString(",")}]"
                        }

                        // 插入到新表
                        db.execSQL("""
                            INSERT INTO messages_new (
                                id, chatId, role, partsJson, timestamp,
                                inputTokens, outputTokens, tokensPerSecond, firstTokenLatency,
                                modelName, variantsJson, selectedVariantIndex
                            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """.trimIndent(), arrayOf(
                            id, chatId, role, partsJson, timestamp,
                            inputTokens, outputTokens, tokensPerSecond, firstTokenLatency,
                            modelName, variantsJson, selectedVariantIndex
                        ))
                    }
                }

                // 3. 删除旧表
                db.execSQL("DROP TABLE messages")

                // 4. 重命名新表
                db.execSQL("ALTER TABLE messages_new RENAME TO messages")

                // 5. 重建索引
                db.execSQL("CREATE INDEX IF NOT EXISTS index_messages_chatId ON messages(chatId)")
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "tchat_database"
                )
                    .addMigrations(
                        MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5,
                        MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9,
                        MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13,
                        MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16, MIGRATION_16_17,
                        MIGRATION_17_18, MIGRATION_18_19, MIGRATION_19_20, MIGRATION_20_21,
                        MIGRATION_21_22, MIGRATION_22_23
                    )
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
