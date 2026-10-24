package com.qingji.plan.ui.calendar

import com.qingji.plan.data.db.entity.CourseEntity
import com.qingji.plan.data.model.PeriodSchedules
import com.qingji.plan.data.model.SchoolPeriod
import com.qingji.plan.util.TimeUtils

/**
 * 课表某一行：一节，加它 7 天里各有什么课。
 *
 * @param period 这一节的作息
 * @param cells 长度固定 7，`cells[0]` 是周一。一格里可能是空的、一门课、或并排的几门
 */
data class PeriodRow(
    val period: SchoolPeriod,
    val cells: List<List<CourseEntity>>,
) {
    /** 这一天有几门课（用来决定并排分几栏） */
    fun columnCount(weekday: Int): Int = cells.getOrNull(weekday)?.size ?: 0
}

/**
 * 课表排布。
 *
 * ⚠️ **改版说明**：以前是「连续时间轴 + 课程按真实时间绝对定位」，
 * 纵轴会被作息之外的课撑长，节次只是背景带。
 *
 * 用户明确说了**他的课永远落在作息这 6 节里，不会出现超出作息的课**，
 * 而且嫌背景格子太多。所以现在改成：
 *
 *   - **一节一行，行行等高**（不再按真实时间比例）
 *   - 一门课归到「**它的开始时间落在哪一节的区间里**」那一行
 *   - 落不进任何一节的课**不会丢**，由 [outside] 收集起来单独列在课表下面
 *
 * 这样格子是整齐的，也不会再有课间/午休那一片片空白。
 */
object CourseTableLayout {

    /** 能排进课表的节次（作息表里可能有非法行，那些跳过） */
    fun validPeriods(periods: List<SchoolPeriod>): List<SchoolPeriod> =
        periods.filter { PeriodSchedules.isSane(it) }.sortedBy { it.startMinute }

    /** 一节课属于哪一节。开始时间落在 `[start, end)` 里就算；落不进返回 null */
    fun periodIndexOf(course: CourseEntity, periods: List<SchoolPeriod>): Int? {
        val start = TimeUtils.parseHHmm(course.time) ?: return null
        return validPeriods(periods)
            .firstOrNull { start >= it.startMinute && start < it.endMinute }
            ?.index
    }

    /**
     * 排成一行一节。
     *
     * 同一格里有几门课就几门——渲染时并排分栏，`cells[weekday].size` 就是栏数。
     */
    fun rows(
        courses: List<CourseEntity>,
        periods: List<SchoolPeriod>,
    ): List<PeriodRow> {
        val valid = validPeriods(periods)

        // 节次号 → 星期 → 课
        val buckets = mutableMapOf<Int, MutableMap<Int, MutableList<CourseEntity>>>()

        courses.forEach { course ->
            val index = periodIndexOf(course, periods) ?: return@forEach
            if (course.weekday !in 0..6) return@forEach
            buckets.getOrPut(index) { mutableMapOf() }
                .getOrPut(course.weekday) { mutableListOf() }
                .add(course)
        }

        return valid.map { period ->
            val byDay = buckets[period.index]
            PeriodRow(
                period = period,
                cells = (0..6).map { weekday ->
                    byDay?.get(weekday)
                        ?.sortedBy { it.title }
                        ?.toList()
                        ?: emptyList()
                },
            )
        }
    }

    /**
     * 有时间、但落不进任何一节的课。
     *
     * 用户说不会出现这种课，但**真出现了也不能把它藏起来** —— 那就静默丢数据了。
     * 这些课会单独列在课表下方。
     */
    fun outside(
        courses: List<CourseEntity>,
        periods: List<SchoolPeriod>,
    ): List<CourseEntity> = courses
        .filter { !it.time.isNullOrBlank() }
        .filter { periodIndexOf(it, periods) == null }
}
