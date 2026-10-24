package com.qingji.plan.data.remote

import com.qingji.plan.data.model.Cadence
import com.qingji.plan.data.model.Priority
import com.qingji.plan.data.model.TaskSource
import com.qingji.plan.data.repo.CourseRepository
import com.qingji.plan.data.repo.LongTermPlanRepository
import com.qingji.plan.data.repo.TaskRepository
import com.qingji.plan.util.DateUtils
import com.qingji.plan.util.TimeUtils
import java.time.LocalDate

/** 一次执行的结果 */
data class AiExecResult(
    /** 成功的条目，形如 `➕ 任务：开会` */
    val succeeded: List<String>,
    /** 失败的条目和原因 */
    val failed: List<String>,
) {
    val total: Int get() = succeeded.size + failed.size
    val allOk: Boolean get() = failed.isEmpty()

    /** 回执文案，与原站一致：「✅ 已执行 N 项：」 */
    fun receipt(): String = buildString {
        if (succeeded.isNotEmpty()) {
            append("✅ 已执行 ${succeeded.size} 项：\n")
            succeeded.forEach { append(it).append('\n') }
        }
        if (failed.isNotEmpty()) {
            if (succeeded.isNotEmpty()) append('\n')
            append("⚠️ ${failed.size} 项没做成：\n")
            failed.forEach { append(it).append('\n') }
        }
    }.trim()
}

/**
 * 执行 AI 给出的操作。
 *
 * 三条必须守住的行为（照抄原站）：
 *   1. **单条失败不影响其它条** —— 一条一条来，失败了记下来继续
 *   2. 靠**标题匹配**定位已有数据，规则见 [AiActionParser.titleMatches]
 *   3. 新建的任务打上 `source = ai` 标记，方便日后区分
 */
class AiActionExecutor(
    private val tasks: TaskRepository,
    private val plans: LongTermPlanRepository,
    private val courses: CourseRepository,
    private val todayProvider: () -> LocalDate = { LocalDate.now() },
) {

    suspend fun execute(actions: List<AiAction>): AiExecResult {
        val ok = mutableListOf<String>()
        val bad = mutableListOf<String>()

        actions.forEach { action ->
            runCatching { apply(action) }
                .onSuccess { ok.add(action.describe) }
                .onFailure { bad.add("${action.describe} —— ${it.message ?: "执行失败"}") }
        }
        return AiExecResult(ok, bad)
    }

    private suspend fun apply(action: AiAction) {
        when (action.op) {
            "addTask" -> addTask(action.task ?: error("缺少任务内容"))
            "editTask" -> editTask(action.match ?: error("缺少匹配条件"), action.task ?: error("缺少修改内容"))
            "deleteTask" -> deleteTask(action.match ?: error("缺少匹配条件"))
            "addPlan" -> addPlan(action.plan ?: error("缺少计划内容"))
            "deletePlan" -> deletePlan(action.match ?: error("缺少匹配条件"))
            "addCourse" -> addCourse(action.course ?: error("缺少课程内容"))
            "editCourse" -> editCourse(action.match ?: error("缺少匹配条件"), action.course ?: error("缺少修改内容"))
            "deleteCourse" -> deleteCourse(action.match ?: error("缺少匹配条件"))
            else -> error("不认识的操作：${action.op}")
        }
    }

    // ---------- 任务 ----------

    private suspend fun addTask(spec: TaskSpec) {
        val title = spec.title?.trim().orEmpty()
        if (title.isEmpty()) error("任务缺少标题")

        val date = resolveDate(spec.date)
        val time = spec.time?.takeIf { it.isNotBlank() }
        val duration = TimeUtils.clampDuration(spec.durationMinutes ?: TimeUtils.MIN_DURATION)

        tasks.create(
            title = title,
            date = date,
            time = time,
            durationMinutes = if (time != null) duration else null,
            category = spec.category ?: "other",
            priority = if (spec.priority == "high") Priority.HIGH else Priority.MID,
            source = TaskSource.AI,
        )
    }

    private suspend fun editTask(match: MatchSpec, spec: TaskSpec) {
        val target = findTask(match) ?: error("未找到任务：${match.title}")

        var updated = target
        spec.title?.takeIf { it.isNotBlank() }?.let { updated = updated.copy(title = it.trim()) }
        spec.category?.takeIf { it.isNotBlank() }?.let { updated = updated.copy(category = it) }
        spec.priority?.let { updated = updated.copy(priority = if (it == "high") "high" else "mid") }

        // 时间：显式给空字符串 = 清掉时间，变回「随时」
        if (spec.time != null) {
            if (spec.time.isBlank()) {
                updated = updated.copy(time = null, endTime = null, durationMinutes = null)
            } else {
                val dur = TimeUtils.clampDuration(spec.durationMinutes ?: target.durationMinutes ?: TimeUtils.MIN_DURATION)
                updated = updated.copy(
                    time = spec.time,
                    durationMinutes = dur,
                    endTime = spec.endTime ?: TimeUtils.endTimeOf(spec.time, dur),
                )
            }
        } else if (spec.durationMinutes != null && target.time != null) {
            val dur = TimeUtils.clampDuration(spec.durationMinutes)
            updated = updated.copy(
                durationMinutes = dur,
                endTime = TimeUtils.endTimeOf(target.time, dur),
            )
        }

        spec.date?.takeIf { it.isNotBlank() }?.let {
            updated = updated.copy(date = DateUtils.toKey(resolveDate(it)))
        }

        tasks.update(updated)
    }

    private suspend fun deleteTask(match: MatchSpec) {
        val target = findTask(match) ?: error("未找到任务：${match.title}")
        tasks.delete(target)
    }

    private suspend fun findTask(match: MatchSpec) = tasks.getByDate(resolveDate(match.date)).firstOrNull {
        AiActionParser.titleMatches(it.title, match.title)
    } ?: tasks.getAll().firstOrNull {
        AiActionParser.titleMatches(it.title, match.title) &&
            (match.date.isNullOrBlank() || it.date == DateUtils.toKey(resolveDate(match.date)))
    }

    // ---------- 长期计划 ----------

    private suspend fun addPlan(spec: PlanSpec) {
        val title = spec.title?.trim().orEmpty()
        if (title.isEmpty()) error("计划缺少标题")

        plans.create(
            title = title,
            cadence = Cadence.from(spec.cadence),
            weekdays = spec.weekdays.orEmpty(),
            dayOfMonth = spec.dayOfMonth,
            time = spec.time,
            durationMinutes = spec.durationMinutes ?: TimeUtils.MIN_DURATION,
            category = spec.category ?: "other",
        )
    }

    private suspend fun deletePlan(match: MatchSpec) {
        val target = plans.getAll().firstOrNull {
            AiActionParser.titleMatches(it.title, match.title)
        } ?: error("未找到长期计划：${match.title}")
        plans.delete(target)
    }

    // ---------- 课程 ----------

    private suspend fun addCourse(spec: CourseSpec) {
        val title = spec.title?.trim().orEmpty()
        if (title.isEmpty()) error("课程缺少名称")

        courses.create(
            title = title,
            weekday = (spec.weekday ?: 0).coerceIn(0, 6),
            time = spec.time,
            // 课程默认 45 分钟，和任务不一样
            durationMinutes = spec.durationMinutes ?: CourseRepository.DEFAULT_DURATION,
            room = spec.room,
            category = spec.category ?: "study",
        )
    }

    private suspend fun editCourse(match: MatchSpec, spec: CourseSpec) {
        val target = courses.findByTitle(match.title) ?: error("未找到课程：${match.title}")

        val updated = target.copy(
            title = spec.title?.takeIf { it.isNotBlank() }?.trim() ?: target.title,
            weekday = spec.weekday?.coerceIn(0, 6) ?: target.weekday,
            time = if (spec.time != null) spec.time.takeIf { it.isNotBlank() } else target.time,
            durationMinutes = spec.durationMinutes ?: target.durationMinutes,
            room = if (spec.room != null) spec.room.takeIf { it.isNotBlank() } else target.room,
            category = spec.category?.takeIf { it.isNotBlank() } ?: target.category,
        )
        courses.update(updated)
    }

    private suspend fun deleteCourse(match: MatchSpec) {
        val target = courses.findByTitle(match.title) ?: error("未找到课程：${match.title}")
        courses.delete(target)
    }

    // ---------- 工具 ----------

    /** AI 给的日期可能缺失或格式不对，统一兜底到今天 */
    private fun resolveDate(raw: String?): LocalDate =
        DateUtils.fromKey(raw) ?: todayProvider()
}
