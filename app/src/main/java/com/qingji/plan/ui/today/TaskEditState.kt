package com.qingji.plan.ui.today

import com.qingji.plan.data.db.entity.TaskEntity
import com.qingji.plan.data.model.Categories
import com.qingji.plan.data.model.Category
import com.qingji.plan.data.model.Priority
import com.qingji.plan.data.model.TaskStatus
import com.qingji.plan.util.DateUtils
import com.qingji.plan.util.TimeUtils
import java.time.LocalDate

/**
 * 任务编辑弹层的状态。
 *
 * 纯数据 + 纯函数，不依赖安卓，所以能直接测「标题为空不能保存」
 * 「15 分钟的任务块时间标签算得对不对」这类规则。
 */
data class TaskEditState(
    /** 0 表示新建 */
    val id: Long = 0,
    val uid: String = "",
    val title: String = "",
    val date: LocalDate,
    /** 有没有指定时间。没有的话就落在「随时」区 */
    val hasTime: Boolean = false,
    /** `HH:mm` */
    val time: String = "09:00",
    val durationMinutes: Int = TimeUtils.MIN_DURATION,
    val category: String = Categories.OTHER.id,
    val priority: Priority = Priority.MID,
    /** 已完成状态。编辑已有任务时保留 */
    val done: Boolean = false,
    val completedAt: Long? = null,
) {
    val isNew: Boolean get() = id == 0L

    /** 标题不能为空才允许保存 */
    val canSave: Boolean get() = title.isNotBlank()

    /** 结束时间。没有时间就没有结束时间 */
    val endTime: String?
        get() = if (!hasTime) null else TimeUtils.endTimeOf(time, durationMinutes)

    /** 会显示在任务块上的时间标签 */
    val timeLabel: String
        get() = if (!hasTime) "" else TimeUtils.timeLabel(time, endTime, durationMinutes)

    /** 时长文案，如「1小时30分」 */
    val durationLabel: String get() = TimeUtils.formatDuration(durationMinutes)

    /**
     * 转成数据库实体。用了 `updatedAt` 做时间戳注入，方便测试。
     */
    fun toEntity(now: Long = System.currentTimeMillis()): TaskEntity {
        val dur = if (hasTime) TimeUtils.clampDuration(durationMinutes) else null
        return TaskEntity(
            id = id,
            uid = uid.ifBlank { com.qingji.plan.util.Ids.uid() },
            date = DateUtils.toKey(date),
            title = title.trim(),
            category = category,
            time = if (hasTime) time else null,
            endTime = if (hasTime) TimeUtils.endTimeOf(time, dur!!) else null,
            durationMinutes = dur,
            priority = priority.value,
            status = if (done) TaskStatus.DONE.value else TaskStatus.TODO.value,
            completedAt = completedAt,
            createdAt = now,
            updatedAt = now,
        )
    }

    companion object {

        /** 可选的时长，单位分钟 */
        val DURATION_OPTIONS = listOf(15, 30, 45, 60, 90, 120)

        /** 新建一个空任务 */
        fun create(date: LocalDate, time: String? = null, durationMinutes: Int = TimeUtils.MIN_DURATION) =
            TaskEditState(
                date = date,
                hasTime = time != null,
                time = time ?: "09:00",
                durationMinutes = TimeUtils.clampDuration(durationMinutes),
            )

        /** 从已有任务打开编辑 */
        fun from(task: TaskEntity): TaskEditState = TaskEditState(
            id = task.id,
            uid = task.uid,
            title = task.title,
            date = DateUtils.fromKey(task.date) ?: LocalDate.now(),
            hasTime = task.hasTime,
            time = task.time ?: "09:00",
            durationMinutes = task.durationMinutes ?: TimeUtils.MIN_DURATION,
            category = task.category,
            priority = task.priorityEnum,
            done = task.isDone,
            completedAt = task.completedAt,
        )
    }
}

/**
 * 编辑弹层里跟界面无关的规则。
 */
object TaskEditLogic {

    /** 所有可选的分类 = 内置 + 自定义 */
    fun categoryOptions(customs: List<Category>): List<Category> = Categories.all(customs)

    /** 切换「有没有时间」时，时长要跟着合理变化 */
    fun toggleHasTime(state: TaskEditState, hasTime: Boolean): TaskEditState =
        state.copy(
            hasTime = hasTime,
            durationMinutes = if (hasTime) {
                TimeUtils.clampDuration(state.durationMinutes)
            } else {
                state.durationMinutes
            },
        )

    /**
     * 没有时间的任务不该显示时间标签。
     */
    fun labelFor(state: TaskEditState): String =
        if (state.hasTime) state.timeLabel else "随时"
}
