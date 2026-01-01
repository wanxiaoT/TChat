package com.tchat.data.database.dao

import androidx.room.*
import com.tchat.data.database.entity.McpServerEntity
import kotlinx.coroutines.flow.Flow

/**
 * MCP 服务器 DAO
 */
@Dao
interface McpServerDao {

    @Query("SELECT * FROM mcp_servers ORDER BY createdAt DESC")
    fun getAllServers(): Flow<List<McpServerEntity>>

    @Query("SELECT * FROM mcp_servers WHERE enabled = 1 ORDER BY createdAt DESC")
    fun getEnabledServers(): Flow<List<McpServerEntity>>

    @Query("SELECT * FROM mcp_servers WHERE id = :id")
    suspend fun getServerById(id: String): McpServerEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertServer(server: McpServerEntity)

    @Update
    suspend fun updateServer(server: McpServerEntity)

    @Delete
    suspend fun deleteServer(server: McpServerEntity)

    @Query("DELETE FROM mcp_servers WHERE id = :id")
    suspend fun deleteServerById(id: String)
}
