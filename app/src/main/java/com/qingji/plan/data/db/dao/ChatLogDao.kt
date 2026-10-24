package com.qingji.plan.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.qingji.plan.data.db.entity.ChatLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatLogDao {

    @Query("SELECT * FROM chat_log ORDER BY createdAt ASC")
    suspend fun getAll(): List<ChatLogEntity>

    @Query("SELECT * FROM chat_log ORDER BY createdAt ASC")
    fun observeAll(): Flow<List<ChatLogEntity>>

    /** 最近 N 条，作为 AI 对话的上下文 */
    @Query("SELECT * FROM chat_log ORDER BY createdAt DESC LIMIT :limit")
    suspend fun getRecent(limit: Int): List<ChatLogEntity>

    /** 当前是否有等待用户确认的操作（AI 改数据前必须先确认） */
    @Query("SELECT * FROM chat_log WHERE actionState = 'pending' ORDER BY createdAt DESC LIMIT 1")
    suspend fun getPending(): ChatLogEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: ChatLogEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(logs: List<ChatLogEntity>)

    @Query("UPDATE chat_log SET actionState = :state WHERE id = :id")
    suspend fun updateActionState(id: Long, state: String?)

    @Query("UPDATE chat_log SET actionState = 'cancelled' WHERE actionState = 'pending'")
    suspend fun cancelAllPending()

    /**
     * 用户点了「确认执行」。
     *
     * 跟「取消」分开记，虽然界面上效果一样（那条消息的按钮都会消失），
     * 但语义不同，以后要回看「这条到底执行没执行」时不会混。
     */
    @Query("UPDATE chat_log SET actionState = 'done' WHERE actionState = 'pending'")
    suspend fun markAllPendingDone()

    @Query("DELETE FROM chat_log")
    suspend fun deleteAll()
}
