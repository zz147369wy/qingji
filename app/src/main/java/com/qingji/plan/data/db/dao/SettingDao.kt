package com.qingji.plan.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.qingji.plan.data.db.entity.SettingEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SettingDao {

    @Query("SELECT * FROM settings WHERE `key` = :key LIMIT 1")
    suspend fun get(key: String): SettingEntity?

    @Query("SELECT * FROM settings")
    suspend fun getAll(): List<SettingEntity>

    @Query("SELECT * FROM settings WHERE `key` = :key LIMIT 1")
    fun observe(key: String): Flow<SettingEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(entity: SettingEntity)

    @Query("DELETE FROM settings WHERE `key` = :key")
    suspend fun delete(key: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putAll(items: List<SettingEntity>)

    @Query("DELETE FROM settings")
    suspend fun clear()
}
