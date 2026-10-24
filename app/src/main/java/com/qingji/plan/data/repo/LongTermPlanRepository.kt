package com.qingji.plan.data.repo

import com.qingji.plan.data.db.dao.LongTermPlanDao
import com.qingji.plan.data.db.dao.TaskDao
import com.qingji.plan.data.db.entity.LongTermPlanEntity
import com.qingji.plan.data.model.Cadence
import com.qingji.plan.data.model.TaskSource
import com.qingji.plan.data.model.TaskStatus
import com.qingji.plan.util.DateUtils
import com.qingji.plan.util.Ids
import com.qingji.plan.util.TimeUtils
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/**
 * 长期计划仓库。
 *
 * 职责有两块：
 *   1. 增删改查计划本身
 *   2. **到日子自动往「今日」生成任务**——生成的任务带 `planUid`，
 *      这样计划被删掉时能顺着这个标记把生成的任务一并清掉
 */
class LongTermPlanRepository(
    private val dao: LongTermPlanDao,
    private val taskDao: TaskDao,
) {

    fun observeAll(): Flow<List<LongTermPlanEntity>> = dao.observeAll()

    suspend fun getAll(): List<LongTermPlanEntity> = dao.getAll()

    suspend fun getById(id: Long): LongTermPlanEntity? = dao.getById(id)

    suspend fun findByTitle(title: String): LongTermPlanEntity? = dao.findByTitle(title)

    suspend fun count(): Int = dao.count()

    // ---------- 写 ----------

    suspend fun create(
        title: String,
        cadence: Cadence,
        weekdays: List<Int> = emptyList(),
        dayOfMonth: Int? = null,
        time: String? = null,
        durationMinutes: Int? = null,
        category: String = "other",
    ): Long {
        val now = System.currentTimeMillis()
        return dao.insert(
            LongTermPlanEntity(
                uid = Ids.uid(),
                title = title.trim(),
                cadence = cadence.value,
                weekdays = weekdays.filter { it in 0..6 }.distinct().sorted(),
                dayOfMonth = if (cadence == Cadence.MONTHLY) dayOfMonth else null,
                time = time,
                durationMinutes = durationMinutes,
                category = category,
                createdAt = now,
                updatedAt = now,
            )
        )
    }

    suspend fun update(plan: LongTermPlanEntity) =
        dao.update(plan.copy(updatedAt = System.currentTimeMillis()))

    /**
     * 新建或修改一条计划，由 [LongTermPlanEntity.id] 是不是 0 决定
     * （Room 自增主键的默认值就是 0）。
     *
     * 走这一条路是因为原站的「新建」和「编辑」本来就是同一个弹层
     * （`Lx({open, plan})`：`plan` 有值就是编辑），界面那边不该分成两套。
     */
    suspend fun save(plan: LongTermPlanEntity): Long {
        val now = System.currentTimeMillis()
        return if (plan.id == 0L) {
            dao.insert(plan.copy(createdAt = now, updatedAt = now))
        } else {
            // createdAt 必须原样保留 —— startDate 是从它推出来的，改了「从哪天起」就跟着变
            dao.update(plan.copy(createdAt = plan.createdAt, updatedAt = now))
            plan.id
        }
    }

    /**
     * 删除计划。**连带删掉它生成的任务**——原站就是这么做的
     * （先软删计划，再找出 planUid 匹配的任务一并软删）。
     */
    suspend fun delete(plan: LongTermPlanEntity) {
        val now = System.currentTimeMillis()
        dao.softDelete(plan.id, now)
        val generated = taskDao.getByPlanUid(plan.uid)
        if (generated.isNotEmpty()) {
            taskDao.softDeleteAll(generated.map { it.id }, now)
        }
    }

    // ---------- 生成任务 ----------

    /**
     * 为某一天生成所有命中的长期计划任务。
     *
     * 幂等的：已经生成过的（同一天 + 同一个 planUid）不会重复生成，
     * 否则每次打开 App 都会多出一堆重复任务。
     *
     * @return 新生成了几条
     */
    suspend fun generateFor(date: LocalDate): Int {
        val dateKey = DateUtils.toKey(date)
        val plans = dao.getAll()
        if (plans.isEmpty()) return 0

        val existing = taskDao.getByDate(dateKey).mapNotNull { it.planUid }.toSet()
        var created = 0

        plans.filter { it.matches(date) && it.uid !in existing }.forEach { plan ->
            val now = System.currentTimeMillis()
            val dur = plan.time?.let { TimeUtils.clampDuration(plan.durationMinutes ?: TimeUtils.MIN_DURATION) }
            taskDao.insert(
                com.qingji.plan.data.db.entity.TaskEntity(
                    uid = Ids.uid(),
                    date = dateKey,
                    title = plan.title,
                    category = plan.category,
                    time = plan.time,
                    endTime = if (plan.time != null && dur != null) TimeUtils.endTimeOf(plan.time, dur) else null,
                    durationMinutes = dur,
                    status = TaskStatus.TODO.value,
                    isLongTerm = true,
                    planUid = plan.uid,
                    source = TaskSource.MANUAL.value,
                    createdAt = now,
                    updatedAt = now,
                )
            )
            created++
        }
        return created
    }
}
