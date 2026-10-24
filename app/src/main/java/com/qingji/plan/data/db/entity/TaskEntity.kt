package com.qingji.plan.data.db.entity

import androidx.room.Entity
import kotlinx.serialization.Serializable
import androidx.room.Index
import androidx.room.PrimaryKey
import com.qingji.plan.data.model.Categories
import com.qingji.plan.data.model.Category
import com.qingji.plan.data.model.Priority
import com.qingji.plan.data.model.TaskSource
import com.qingji.plan.data.model.TaskStatus
import com.qingji.plan.util.TimeUtils

/**
 * 任务。整个 App 最核心的数据。
 *
 * 字段与原站的 tasks 表逐一对齐（见 `扒站/app.js` 的 addTask 分支）：
 * uid / date / title / category / time / endTime / durationMinutes /
 * priority / status / isLongTerm / source / createdAt / updatedAt
 * 另外补了 completedAt（结算算守时要用）、planUid（长期计划生成的任务要能追溯）、
 * deletedAt（软删除）。
 *
 * `time` 为空表示「没有指定时间」，这类任务会出现在时间轴上方的「🕊 随时」区。
 */
@Entity(
    tableName = "tasks",
    indices = [
        Index("uid"),
        Index("date"),
        Index("status"),
        Index("category"),
        Index("deletedAt"),
    ],
)
@Serializable
data class TaskEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val uid: String,
    /** 日期，格式 `YYYY-MM-DD` */
    val date: String,
    val title: String,
    /** 分类 id：work / study / life / health / other / 自定义 */
    val category: String = "other",
    /** 开始时间 `HH:mm`，为空表示「随时」 */
    val time: String? = null,
    /** 结束时间 `HH:mm` */
    val endTime: String? = null,
    /** 时长（分钟）。原站任务默认 15 分钟 */
    val durationMinutes: Int? = null,
    /** mid / high */
    val priority: String = "mid",
    /** todo / done */
    val status: String = "todo",
    /** 是否由长期计划生成 */
    val isLongTerm: Boolean = false,
    /** 由哪个长期计划生成（planUid），长期计划删除时据此清理 */
    val planUid: String? = null,
    /** 实际完成的时刻，结算算「守时」要用 */
    val completedAt: Long? = null,
    /** manual / ai */
    val source: String = "manual",

    val createdAt: Long,
    val updatedAt: Long,
    /** 软删除标记。所有删除都只打这个标记，不真删，否则统计会出错 */
    val deletedAt: Long? = null,
) {
    val statusEnum: TaskStatus get() = TaskStatus.from(status)
    val priorityEnum: Priority get() = Priority.from(priority)
    val sourceEnum: TaskSource get() = TaskSource.from(source)

    val isDone: Boolean get() = status == TaskStatus.DONE.value
    val isImportant: Boolean get() = priority == Priority.HIGH.value
    val hasTime: Boolean get() = !time.isNullOrBlank()

    /** **时间轴上任务块**的时间，如 `"09:00-10:00"`（普通连字符、无空格） */
    val timeLabel: String
        get() = TimeUtils.timeLabel(time, endTime, durationMinutes)

    /**
     * **列表行**（日历页任务行）那个 `.time-chip` 的时间，如 `"09:00 – 10:00"`。
     *
     * 跟 [timeLabel] 是两个格式，原站就是这样分的：任务块用 `yn(a)+"-"+yn(b)`，
     * 列表行用 `ny()`（空格 + en dash + 空格）。别合并成一个。
     */
    val chipTimeLabel: String
        get() = TimeUtils.chipTimeLabel(time, endTime, durationMinutes)

    fun categoryOf(customs: List<Category> = emptyList()): Category = Categories.of(category, customs)

    /** 副本：打上删除标记 */
    fun softDeleted(at: Long = System.currentTimeMillis()): TaskEntity =
        copy(deletedAt = at, updatedAt = at)
}
