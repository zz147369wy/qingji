package com.qingji.plan.data.repo

import com.qingji.plan.data.db.dao.ReviewDao
import com.qingji.plan.data.db.entity.ReviewEntity
import com.qingji.plan.util.DateUtils
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/**
 * 每日结算记录仓库。
 *
 * 一天只保留一条记录（重复结算时覆盖旧的），与原站行为一致。
 */
class ReviewRepository(private val dao: ReviewDao) {

    suspend fun getByDate(date: LocalDate): ReviewEntity? = dao.getByDate(DateUtils.toKey(date))

    fun observeRecent(limit: Int = 30): Flow<List<ReviewEntity>> = dao.observeRecent(limit)

    suspend fun getRecent(limit: Int = 30): List<ReviewEntity> = dao.getRecent(limit)

    suspend fun count(): Int = dao.count()

    suspend fun averageScore(): Double? = dao.averageScore()

    /**
     * 保存某天的结算。同一天已有记录就覆盖，不新增。
     */
    suspend fun save(date: LocalDate, result: com.qingji.plan.util.SettleResult): ReviewEntity {
        val key = DateUtils.toKey(date)
        val now = System.currentTimeMillis()
        val existing = dao.getByDate(key)

        val entity = ReviewEntity(
            id = existing?.id ?: 0,
            uid = existing?.uid ?: com.qingji.plan.util.Ids.uid(),
            date = key,
            score = result.score,
            doneCount = result.doneCount,
            totalCount = result.totalCount,
            onTimeCount = result.onTimeCount,
            summary = result.summary,
            feedback = result.feedback,
            improvement = result.improvement,
            aiFeedback = existing?.aiFeedback,
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
        )
        dao.insert(entity)
        return entity
    }

    /** 保存 AI 写的深度复盘 */
    suspend fun saveAiFeedback(review: ReviewEntity, text: String): ReviewEntity {
        val updated = review.copy(aiFeedback = text, updatedAt = System.currentTimeMillis())
        dao.update(updated)
        return updated
    }
}
