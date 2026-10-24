package com.qingji.plan.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.qingji.plan.data.db.entity.LongTermPlanEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LongTermPlanDao {

    @Query("SELECT * FROM long_term_plans WHERE deletedAt IS NULL ORDER BY createdAt DESC")
    suspend fun getAll(): List<LongTermPlanEntity>

    @Query("SELECT * FROM long_term_plans WHERE deletedAt IS NULL ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<LongTermPlanEntity>>

    @Query("SELECT * FROM long_term_plans WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): LongTermPlanEntity?

    @Query("SELECT * FROM long_term_plans WHERE uid = :uid AND deletedAt IS NULL LIMIT 1")
    suspend fun getByUid(uid: String): LongTermPlanEntity?

    @Query("SELECT * FROM long_term_plans WHERE deletedAt IS NULL AND title LIKE '%' || :title || '%' ORDER BY createdAt LIMIT 1")
    suspend fun findByTitle(title: String): LongTermPlanEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(plan: LongTermPlanEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(plans: List<LongTermPlanEntity>)

    @Update
    suspend fun update(plan: LongTermPlanEntity)

    @Query("UPDATE long_term_plans SET deletedAt = :at, updatedAt = :at WHERE id = :id")
    suspend fun softDelete(id: Long, at: Long = System.currentTimeMillis())

    @Query("DELETE FROM long_term_plans")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM long_term_plans WHERE deletedAt IS NULL")
    suspend fun count(): Int
}
