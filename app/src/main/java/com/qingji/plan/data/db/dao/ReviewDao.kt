package com.qingji.plan.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.qingji.plan.data.db.entity.ReviewEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ReviewDao {

    @Query("SELECT * FROM reviews WHERE date = :date LIMIT 1")
    suspend fun getByDate(date: String): ReviewEntity?

    @Query("SELECT * FROM reviews WHERE date = :date LIMIT 1")
    fun observeByDate(date: String): Flow<ReviewEntity?>

    @Query("SELECT * FROM reviews ORDER BY date DESC")
    suspend fun getAll(): List<ReviewEntity>

    /** 最近 N 条结算记录，统计页用 */
    @Query("SELECT * FROM reviews ORDER BY date DESC LIMIT :limit")
    suspend fun getRecent(limit: Int): List<ReviewEntity>

    @Query("SELECT * FROM reviews ORDER BY date DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<ReviewEntity>>

    @Query("SELECT * FROM reviews WHERE date BETWEEN :from AND :to ORDER BY date")
    suspend fun getBetween(from: String, to: String): List<ReviewEntity>

    @Query("SELECT COUNT(*) FROM reviews")
    suspend fun count(): Int

    @Query("SELECT AVG(score) FROM reviews")
    suspend fun averageScore(): Double?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(review: ReviewEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(reviews: List<ReviewEntity>)

    @Update
    suspend fun update(review: ReviewEntity)

    @Query("DELETE FROM reviews WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM reviews")
    suspend fun deleteAll()
}
