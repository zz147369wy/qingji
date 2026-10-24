package com.qingji.plan.ui.course

import com.qingji.plan.data.db.entity.CourseEntity
import com.qingji.plan.data.model.Categories
import com.qingji.plan.util.TimeUtils
import java.time.LocalDate

/**
 * 课程编辑的状态。
 *
 * 与原站 courses 表逐字段对齐。几个约定：
 *   - `weekday` 用**原站约定：0 = 周一**
 *   - 课程默认时长 **45 分钟**（任务默认是 15 分钟，两者不一样）
 *   - 课程可以没有固定时间，那样的课只列在课程表底部，不铺到时间轴上
 */
data class CourseEditState(
    /** 0 表示新建 */
    val id: Long = 0,
    val uid: String = "",
    val title: String = "",
    /** 0 = 周一 …… 6 = 周日 */
    val weekday: Int = 0,
    val hasTime: Boolean = true,
    /** `HH:mm` */
    val time: String = "08:00",
    val durationMinutes: Int = DEFAULT_DURATION,
    /** 教室，可空 */
    val room: String = "",
    val category: String = Categories.STUDY.id,
) {
    val isNew: Boolean get() = id == 0L

    val canSave: Boolean get() = title.isNotBlank()

    /** 「周一」这种标签 */
    val weekdayLabel: String get() = WEEKDAY_LABELS.getOrElse(weekday) { "周一" }

    val endTime: String?
        get() = if (!hasTime) null else TimeUtils.endTimeOf(time, durationMinutes)

    val timeLabel: String
        get() = if (!hasTime) "未排固定时间" else TimeUtils.timeLabel(time, endTime, durationMinutes)

    /** 课表格子上显示的文字 */
    val displayLabel: String
        get() = if (room.isBlank()) title.trim() else "${title.trim()} ·${room.trim()}"

    fun toEntity(now: Long = System.currentTimeMillis()): CourseEntity = CourseEntity(
        id = id,
        uid = uid.ifBlank { com.qingji.plan.util.Ids.uid() },
        title = title.trim(),
        weekday = weekday.coerceIn(0, 6),
        time = if (hasTime) time else null,
        // 原来这里还有一句 .coerceAtLeast(DEFAULT_DURATION)，会把用户选的 15/30 分钟
        // 悄悄改成 45。原站的「选择时间」面板对任务和课程是**同一个**「持续」档位（15 分起），
        // 所以这里不该再抬到 45——DEFAULT_DURATION 只是新建时的默认值。
        durationMinutes = TimeUtils.clampDuration(durationMinutes),
        room = room.trim().ifBlank { null },
        category = category,
        createdAt = now,
        updatedAt = now,
    )

    companion object {
        /** 课程的默认时长，原站就是 45 分钟 */
        const val DEFAULT_DURATION = 45

        val WEEKDAY_LABELS = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

        /** 课程可选的时长档位 */
        val DURATION_OPTIONS = listOf(45, 60, 90, 120)

        fun create(weekday: Int = 0) = CourseEditState(weekday = weekday.coerceIn(0, 6))

        /**
         * 今天对应的是周几（0 = 周一）。
         *
         * 原站新建课程时无条件把星期设成**今天**：`G((new Date().getDay()+6)%7)`，
         * 它连弹层的 `date` 参数都不用。所以安卓版也照着来。
         */
        fun todayWeekday(today: LocalDate = LocalDate.now()): Int = today.dayOfWeek.value - 1

        fun from(course: CourseEntity) = CourseEditState(
            id = course.id,
            uid = course.uid,
            title = course.title,
            weekday = course.weekday.coerceIn(0, 6),
            hasTime = course.hasTime,
            time = course.time ?: "08:00",
            durationMinutes = course.durationMinutes,
            room = course.room ?: "",
            category = course.category,
        )
    }
}

object CourseEditLogic {

    /**
     * 一周的每一天有哪些课，按时间排序。
     *
     * 课表页面用它来铺格子。没有时间的课不参与，它们单独列在底部。
     */
    fun groupByWeekday(courses: List<CourseEntity>): Map<Int, List<CourseEntity>> =
        courses.filter { it.deletedAt == null && it.hasTime }
            .groupBy { it.weekday }
            .mapValues { (_, list) -> list.sortedBy { it.time } }

    /** 没有排固定时间的课 */
    fun withoutTime(courses: List<CourseEntity>): List<CourseEntity> =
        courses.filter { it.deletedAt == null && !it.hasTime }

    /**
     * 课表要显示哪些小时。
     *
     * 原站的做法：默认 8 点到 21 点，有课超出这个范围就自动扩展。
     * 对应源码 `let h=8,m=21; if(l.length){ h=Math.min(8,...); m=Math.max(21,...) }`
     */
    fun hourRange(courses: List<CourseEntity>): IntRange {
        val timed = courses.filter { it.hasTime }
        if (timed.isEmpty()) return 8..21

        val starts = timed.mapNotNull { TimeUtils.parseHHmm(it.time) }
        if (starts.isEmpty()) return 8..21

        val ends = timed.mapNotNull { c ->
            val s = TimeUtils.parseHHmm(c.time) ?: return@mapNotNull null
            s + c.durationMinutes
        }

        val minHour = minOf(8, starts.min() / 60)
        val maxHour = maxOf(21, (ends.max() + 59) / 60)
        return minHour..maxHour
    }
}
