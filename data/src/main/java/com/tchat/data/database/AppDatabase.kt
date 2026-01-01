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
import com.tchat.data.database.entity.ChatEntity
import com.tchat.data.database.entity.MessageEntity
import com.tchat.data.database.entity.KnowledgeBaseEntity
import com.tchat.data.database.entity.KnowledgeItemEntity
import com.tchat.data.database.entity.KnowledgeChunkEntity
import com.tchat.data.database.entity.AssistantEntity
import com.tchat.data.database.entity.LocalToolOptionConverter

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
        AssistantEntity::class
    ],
    version = 6,
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

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "tchat_database"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
