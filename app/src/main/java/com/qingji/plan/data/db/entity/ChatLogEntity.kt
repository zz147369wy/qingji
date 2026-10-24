package com.qingji.plan.data.db.entity

import androidx.room.Entity
import kotlinx.serialization.Serializable
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * AI 聊天记录。
 *
 * `actionsJson` 用来存「AI 提出但用户还没确认」的操作清单——
 * 确认流程要求 AI 返回操作后**先不执行**，等用户回复「确认」。
 * 这个待确认状态就存在这里，App 重启后也不会丢。
 */
@Entity(
    tableName = "chat_log",
    indices = [Index("uid"), Index("createdAt")],
)
@Serializable
data class ChatLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val uid: String,
    /** user / assistant */
    val role: String,
    val content: String,
    /** AI 返回的待执行操作（JSON 原文），仅 assistant 消息可能有 */
    val actionsJson: String? = null,
    /** 待确认操作的状态：null=普通消息，pending=等待确认，done=已执行，cancelled=已取消 */
    val actionState: String? = null,

    val createdAt: Long,
) {
    val isUser: Boolean get() = role == "user"
    val isPending: Boolean get() = actionState == "pending"
}
