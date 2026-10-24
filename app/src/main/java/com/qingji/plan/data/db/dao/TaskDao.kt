package com.qingji.plan.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.qingji.plan.data.db.entity.TaskEntity
import kotlinx.coroutines.flow.Flow

/**
 * 任务表。所有查询都过滤掉软删除的行（`deletedAt IS NULL`）——
 * 真删会让统计页的数字出错，所以删除一律只打标记。
 */
@Dao
interface TaskDao {

    // ---------- 读 ----------

    /** 某一天的任务，按时间排序（没有时间的排最前）。今日页用 */
    @Query(
        """
        SELECT * FROM tasks
        WHERE date = :date AND deletedAt IS NULL
        ORDER BY CASE WHEN time IS NULL OR time = '' THEN 0 ELSE 1 END, time, createdAt
        """
    )
    fun observeByDate(date: String): Flow<List<TaskEntity>>

    @Query(
        """
        SELECT * FROM tasks
        WHERE date = :date AND deletedAt IS NULL
        ORDER BY CASE WHEN time IS NULL OR time = '' THEN 0 ELSE 1 END, time, createdAt
        """
    )
    suspend fun getByDate(date: String): List<TaskEntity>

    /** 一段日期范围内的任务。日历页一个月一次查完 */
    @Query(
        """
        SELECT * FROM tasks
        WHERE date BETWEEN :from AND :to AND deletedAt IS NULL
        ORDER BY date, time
        """
    )
    suspend fun getBetween(from: String, to: String): List<TaskEntity>

    @Query(
        """
        SELECT * FROM tasks
        WHERE date BETWEEN :from AND :to AND deletedAt IS NULL
        ORDER BY date, time
        """
    )
    fun observeBetween(from: String, to: String): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE deletedAt IS NULL ORDER BY date DESC, time")
    suspend fun getAll(): List<TaskEntity>

    @Query("SELECT * FROM tasks WHERE deletedAt IS NULL")
    fun observeAll(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): TaskEntity?

    @Query("SELECT * FROM tasks WHERE uid = :uid AND deletedAt IS NULL LIMIT 1")
    suspend fun getByUid(uid: String): TaskEntity?

    /**
     * 按标题模糊找任务。AI 改任务时要用——
     * 原站是「标题完全相同，或一方包含另一方（长度≥2）就命中」。
     */
    @Query(
        """
        SELECT * FROM tasks
        WHERE deletedAt IS NULL AND date = :date AND title LIKE '%' || :title || '%'
        ORDER BY createdAt LIMIT 1
        """
    )
    suspend fun findByTitleOnDate(date: String, title: String): TaskEntity?

    /** 长期计划生成的任务，删计划时一并清理 */
    @Query("SELECT * FROM tasks WHERE planUid = :planUid AND deletedAt IS NULL")
    suspend fun getByPlanUid(planUid: String): List<TaskEntity>

    // ---------- 写 ----------

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(task: TaskEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(tasks: List<TaskEntity>)

    @Update
    suspend fun update(task: TaskEntity)

    @Query("UPDATE tasks SET deletedAt = :at, updatedAt = :at WHERE id = :id")
    suspend fun softDelete(id: Long, at: Long = System.currentTimeMillis())

    @Query("UPDATE tasks SET deletedAt = :at, updatedAt = :at WHERE id IN (:ids)")
    suspend fun softDeleteAll(ids: List<Long>, at: Long = System.currentTimeMillis())

    /** 清空指定日期之后的所有任务。设置页的「清理任务」用 */
    @Query("UPDATE tasks SET deletedAt = :at, updatedAt = :at WHERE date >= :from AND deletedAt IS NULL")
    suspend fun softDeleteFrom(from: String, at: Long = System.currentTimeMillis()): Int

    @Query("UPDATE tasks SET deletedAt = :at, updatedAt = :at WHERE date = :date AND deletedAt IS NULL")
    suspend fun softDeleteOn(date: String, at: Long = System.currentTimeMillis()): Int

    /** 恢复备份时要先腾空整张表 */
    @Query("DELETE FROM tasks")
    suspend fun deleteAll()

    // ---------- 统计 ----------

    @Query("SELECT COUNT(*) FROM tasks WHERE deletedAt IS NULL")
    suspend fun countAll(): Int

    @Query("SELECT COUNT(*) FROM tasks WHERE deletedAt IS NULL AND status = 'done'")
    suspend fun countDone(): Int

    @Query("SELECT COUNT(*) FROM tasks WHERE deletedAt IS NULL AND date = :date")
    suspend fun countOn(date: String): Int

    @Query("SELECT COUNT(*) FROM tasks WHERE deletedAt IS NULL AND status = 'done' AND date BETWEEN :from AND :to")
    suspend fun countDoneBetween(from: String, to: String): Int

    @Query("SELECT COUNT(*) FROM tasks WHERE deletedAt IS NULL AND date BETWEEN :from AND :to")
    suspend fun countBetween(from: String, to: String): Int
}
