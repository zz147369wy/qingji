package com.qingji.plan.data.db.entity

import androidx.room.Entity
import kotlinx.serialization.Serializable
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 每日结算记录。一天一条。
 *
 * 对应原站的 reviews 表。打分公式见 `SettleCalculator`。
 */
@Entity(
    tableName = "reviews",
    indices = [Index("uid"), Index("date")],
)
@Serializable
data class ReviewEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val uid: String,
    /** 日期 `YYYY-MM-DD` */
    val date: String,

    /** 综合得分 0-100 */
    val score: Int,
    /** 完成了几项 */
    val doneCount: Int,
    /** 一共几项 */
    val totalCount: Int,
    /** 其中按时完成的有几项 */
    val onTimeCount: Int,

    /** 一句话总结，如「完成 3/5 项，守时 2/3 项」 */
    val summary: String,
    /** 按分数分档的评语 */
    val feedback: String,
    /** 具体改进建议 */
    val improvement: String,
    /** 让 AI 写的深度复盘，可空 */
    val aiFeedback: String? = null,

    val createdAt: Long,
    val updatedAt: Long,
)
