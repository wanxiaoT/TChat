package com.tchat.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.tchat.data.database.entity.AppSettingsEntity
import kotlinx.coroutines.flow.Flow

/**
 * 应用设置数据访问对象
 */
@Dao
interface AppSettingsDao {
    /**
     * 获取设置（Flow）
     */
    @Query("SELECT * FROM app_settings WHERE id = 1")
    fun getSettingsFlow(): Flow<AppSettingsEntity?>

    /**
     * 获取设置（同步）
     */
    @Query("SELECT * FROM app_settings WHERE id = 1")
    suspend fun getSettings(): AppSettingsEntity?

    /**
     * 获取设置（阻塞式，用于初始化）
     */
    @Query("SELECT * FROM app_settings WHERE id = 1")
    fun getSettingsSync(): AppSettingsEntity?

    /**
     * 插入或替换设置
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrReplace(settings: AppSettingsEntity)

    /**
     * 更新设置
     */
    @Update
    suspend fun update(settings: AppSettingsEntity)

    /**
     * 更新当前供应商ID
     */
    @Query("UPDATE app_settings SET currentProviderId = :providerId WHERE id = 1")
    suspend fun updateCurrentProviderId(providerId: String)

    /**
     * 更新当前模型
     */
    @Query("UPDATE app_settings SET currentModel = :model WHERE id = 1")
    suspend fun updateCurrentModel(model: String)

    /**
     * 更新当前助手ID
     */
    @Query("UPDATE app_settings SET currentAssistantId = :assistantId WHERE id = 1")
    suspend fun updateCurrentAssistantId(assistantId: String)

    /**
     * 更新供应商配置JSON
     */
    @Query("UPDATE app_settings SET providersJson = :providersJson WHERE id = 1")
    suspend fun updateProvidersJson(providersJson: String)

    /**
     * 更新深度研究设置JSON
     */
    @Query("UPDATE app_settings SET deepResearchSettingsJson = :json WHERE id = 1")
    suspend fun updateDeepResearchSettingsJson(json: String)

    /**
     * 更新正则规则JSON
     */
    @Query("UPDATE app_settings SET regexRulesJson = :json WHERE id = 1")
    suspend fun updateRegexRulesJson(json: String)

    /**
     * 更新供应商网格列数
     */
    @Query("UPDATE app_settings SET providerGridColumnCount = :count WHERE id = 1")
    suspend fun updateProviderGridColumnCount(count: Int)
}
