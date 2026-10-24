package com.qingji.plan.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.qingji.plan.data.db.entity.CourseEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CourseDao {

    @Query("SELECT * FROM courses WHERE deletedAt IS NULL ORDER BY weekday, time")
    suspend fun getAll(): List<CourseEntity>

    @Query("SELECT * FROM courses WHERE deletedAt IS NULL ORDER BY weekday, time")
    fun observeAll(): Flow<List<CourseEntity>>

    @Query("SELECT * FROM courses WHERE deletedAt IS NULL AND weekday = :weekday ORDER BY time")
    suspend fun getByWeekday(weekday: Int): List<CourseEntity>

    @Query("SELECT * FROM courses WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): CourseEntity?

    @Query("SELECT * FROM courses WHERE uid = :uid AND deletedAt IS NULL LIMIT 1")
    suspend fun getByUid(uid: String): CourseEntity?

    @Query("SELECT * FROM courses WHERE deletedAt IS NULL AND title LIKE '%' || :title || '%' ORDER BY weekday LIMIT 1")
    suspend fun findByTitle(title: String): CourseEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(course: CourseEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(courses: List<CourseEntity>)

    @Update
    suspend fun update(course: CourseEntity)

    @Query("UPDATE courses SET deletedAt = :at, updatedAt = :at WHERE id = :id")
    suspend fun softDelete(id: Long, at: Long = System.currentTimeMillis())

    @Query("DELETE FROM courses")
    suspend fun deleteAll()
}
