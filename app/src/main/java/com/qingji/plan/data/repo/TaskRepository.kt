package com.qingji.plan.data.repo

import com.qingji.plan.data.db.dao.TaskDao
import com.qingji.plan.data.db.entity.TaskEntity
import com.qingji.plan.data.model.Priority
import com.qingji.plan.data.model.TaskSource
import com.qingji.plan.data.model.TaskStatus
import com.qingji.plan.util.DateUtils
import com.qingji.plan.util.Ids
import com.qingji.plan.util.TimeUtils
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/**
 * 任务仓库。界面只跟它打交道，不直接碰 DAO。
 *
 * 几条必须守住的行为：
 *   - 删除一律是**软删除**（打 deletedAt），否则统计页的数字会错
 *   - 勾选完成时要记 `completedAt`，结算算「守时」全靠它
 *   - 新建任务时，原站默认时长是 **15 分钟**
 */
class TaskRepository(private val dao: TaskDao) {

    fun observeByDate(date: LocalDate): Flow<List<TaskEntity>> =
        dao.observeByDate(DateUtils.toKey(date))

    fun observeBetween(from: LocalDate, to: LocalDate): Flow<List<TaskEntity>> =
        dao.observeBetween(DateUtils.toKey(from), DateUtils.toKey(to))

    suspend fun getByDate(date: LocalDate): List<TaskEntity> = dao.getByDate(DateUtils.toKey(date))

    suspend fun getBetween(from: LocalDate, to: LocalDate): List<TaskEntity> =
        dao.getBetween(DateUtils.toKey(from), DateUtils.toKey(to))

    /** 全部任务（不含已删除）。统计页要用 */
    fun observeAll(): Flow<List<TaskEntity>> = dao.observeAll()

    suspend fun getAll(): List<TaskEntity> = dao.getAll()

    suspend fun getById(id: Long): TaskEntity? = dao.getById(id)

    suspend fun getByUid(uid: String): TaskEntity? = dao.getByUid(uid)

    // ---------- 写 ----------

    /**
     * 新建任务。
     *
     * 时长按原站规则：给了时间才有时长（默认 15 分钟），没给时间就没有时长。
     */
    suspend fun create(
        title: String,
        date: LocalDate,
        time: String? = null,
        durationMinutes: Int? = null,
        category: String = "other",
        priority: Priority = Priority.MID,
        source: TaskSource = TaskSource.MANUAL,
        isLongTerm: Boolean = false,
        planUid: String? = null,
    ): Long {
        val now = System.currentTimeMillis()
        val dur = if (time == null) null else TimeUtils.clampDuration(durationMinutes ?: TimeUtils.MIN_DURATION)
        val task = TaskEntity(
            uid = Ids.uid(),
            date = DateUtils.toKey(date),
            title = title.trim(),
            category = category,
            time = time,
            endTime = if (time != null && dur != null) TimeUtils.endTimeOf(time, dur) else null,
            durationMinutes = dur,
            priority = priority.value,
            status = TaskStatus.TODO.value,
            isLongTerm = isLongTerm,
            planUid = planUid,
            completedAt = null,
            source = source.value,
            createdAt = now,
            updatedAt = now,
        )
        return dao.insert(task)
    }

    suspend fun update(task: TaskEntity) =
        dao.update(task.copy(updatedAt = System.currentTimeMillis()))

    /**
     * 保存一条任务。新建就插入，已有的就更新。
     *
     * 更新时会**保留原来的创建时间** —— 编辑弹层构造实体时用的是当前时刻，
     * 直接覆盖会把「这条任务是哪天建的」弄丢，统计页会受影响。
     */
    suspend fun save(task: TaskEntity, isNew: Boolean): Long {
        val now = System.currentTimeMillis()
        if (isNew) {
            return dao.insert(task.copy(createdAt = now, updatedAt = now))
        }
        val existing = dao.getById(task.id)
        val merged = task.copy(
            createdAt = existing?.createdAt ?: task.createdAt,
            updatedAt = now,
        )
        dao.update(merged)
        return merged.id
    }

    /** 改标题 */
    suspend fun rename(task: TaskEntity, newTitle: String) =
        update(task.copy(title = newTitle.trim()))

    /**
     * 给任务排时间（把「随时」区的任务拖到时间轴上时用）。
     */
    suspend fun schedule(task: TaskEntity, startHHmm: String, durationMinutes: Int) {
        val dur = TimeUtils.clampDuration(durationMinutes)
        update(
            task.copy(
                time = startHHmm,
                durationMinutes = dur,
                endTime = TimeUtils.endTimeOf(startHHmm, dur),
            )
        )
    }

    /**
     * 切换完成状态。
     *
     * 标记完成时写入 `completedAt`——结算算法靠它判断有没有按时完成。
     * 取消完成时把它清空，否则会残留一个错误的完成时刻。
     */
    suspend fun toggleDone(task: TaskEntity): TaskEntity {
        val now = System.currentTimeMillis()
        val updated = if (task.isDone) {
            task.copy(status = TaskStatus.TODO.value, completedAt = null, updatedAt = now)
        } else {
            task.copy(status = TaskStatus.DONE.value, completedAt = now, updatedAt = now)
        }
        dao.update(updated)
        return updated
    }

    /** 删除（软删除） */
    suspend fun delete(task: TaskEntity) = dao.softDelete(task.id)

    suspend fun deleteAll(ids: List<Long>) {
        if (ids.isNotEmpty()) dao.softDeleteAll(ids)
    }

    /** 清理：删除某天起的全部任务 */
    suspend fun clearFrom(date: LocalDate): Int = dao.softDeleteFrom(DateUtils.toKey(date))

    /** 清理：删除某一天的任务 */
    suspend fun clearOn(date: LocalDate): Int = dao.softDeleteOn(DateUtils.toKey(date))

    // ---------- 统计 ----------

    suspend fun countAll(): Int = dao.countAll()

    suspend fun countDone(): Int = dao.countDone()

    suspend fun countOn(date: LocalDate): Int = dao.countOn(DateUtils.toKey(date))
}
