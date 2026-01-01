package com.tchat.data.repository

import com.tchat.data.model.Assistant
import kotlinx.coroutines.flow.Flow

/**
 * 助手Repository接口
 */
interface AssistantRepository {
    /**
     * 获取所有助手
     */
    fun getAllAssistants(): Flow<List<Assistant>>

    /**
     * 根据ID获取助手
     */
    suspend fun getAssistantById(id: String): Assistant?

    /**
     * 根据ID获取助手（Flow）
     */
    fun getAssistantByIdFlow(id: String): Flow<Assistant?>

    /**
     * 保存助手（新建或更新）
     */
    suspend fun saveAssistant(assistant: Assistant)

    /**
     * 删除助手
     */
    suspend fun deleteAssistant(id: String)

    /**
     * 获取助手数量
     */
    suspend fun getAssistantCount(): Int
}
