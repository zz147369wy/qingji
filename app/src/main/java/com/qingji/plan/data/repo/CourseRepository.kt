package com.qingji.plan.data.repo

import com.qingji.plan.data.db.dao.CourseDao
import com.qingji.plan.data.db.entity.CourseEntity
import com.qingji.plan.util.Ids
import kotlinx.coroutines.flow.Flow

/**
 * 课程仓库。
 *
 * 课程是「影子」数据：它不进任务列表，只是每周固定铺到时间轴和课表上。
 * 原站课程默认时长 **45 分钟**（任务默认是 15 分钟，别搞混）。
 */
class CourseRepository(private val dao: CourseDao) {

    companion object {
        const val DEFAULT_DURATION = 45
    }

    fun observeAll(): Flow<List<CourseEntity>> = dao.observeAll()

    suspend fun getAll(): List<CourseEntity> = dao.getAll()

    suspend fun getByWeekday(weekday: Int): List<CourseEntity> = dao.getByWeekday(weekday)

    suspend fun getById(id: Long): CourseEntity? = dao.getById(id)

    suspend fun findByTitle(title: String): CourseEntity? = dao.findByTitle(title)

    /**
     * 某一天有哪些课。weekday 用原站约定：0 = 周一。
     */
    suspend fun getForDate(dayOfWeekValue: Int): List<CourseEntity> =
        // java.time 的 dayOfWeek.value 是 1=周一，减 1 换成原站约定
        getByWeekday(dayOfWeekValue - 1)

    suspend fun create(
        title: String,
        weekday: Int,
        time: String? = null,
        durationMinutes: Int = DEFAULT_DURATION,
        room: String? = null,
        category: String = "study",
    ): Long {
        val now = System.currentTimeMillis()
        return dao.insert(
            CourseEntity(
                uid = Ids.uid(),
                title = title.trim(),
                weekday = weekday.coerceIn(0, 6),
                time = time,
                durationMinutes = durationMinutes,
                room = room?.trim()?.ifBlank { null },
                category = category,
                createdAt = now,
                updatedAt = now,
            )
        )
    }

    suspend fun update(course: CourseEntity) =
        dao.update(course.copy(updatedAt = System.currentTimeMillis()))

    /** 保存一条课程。新建就插入，已有的就更新，更新时保留原创建时间 */
    suspend fun save(course: CourseEntity, isNew: Boolean): Long {
        val now = System.currentTimeMillis()
        if (isNew) return dao.insert(course.copy(createdAt = now, updatedAt = now))
        val existing = dao.getById(course.id)
        val merged = course.copy(createdAt = existing?.createdAt ?: course.createdAt, updatedAt = now)
        dao.update(merged)
        return merged.id
    }

    suspend fun delete(course: CourseEntity) = dao.softDelete(course.id)
}
