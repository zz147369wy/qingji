package com.qingji.plan.ui.today

import com.qingji.plan.data.db.entity.TaskEntity
import com.qingji.plan.util.TimeUtils

/**
 * 今日页的分组。
 *
 * 【2026-10 鲸鱼主题】分组不再只是「时间带」，而是一层层**往下潜的海水**：
 * 上午是浅滩、下午是珊瑚层、晚上是暮光层、凌晨是午夜层、随时是漂流瓶、长期计划是洋流。
 * 每一层用自己的深度色带（[TodayGroup.depth]）做背景，往下滚背景越来越深。
 *
 * 原来 label 里带的 🌙🕊📌 emoji 全部去掉了 —— emoji 随系统字体变样，也不跟画风。
 * 现在每个分组配一个自绘图标（[TodayGroup.icon]），见 [GroupIcon]。
 */
enum class TodayGroup(
    /** 分组名（列表里显示的那几个字，不带 emoji） */
    val label: String,
    /** 海水深度：0 浅滩 · 1 珊瑚层 · 2 暮光层 · 3 午夜层 · 4 漂流瓶 · 5 洋流。对应 ThemePalette.depths */
    val depth: Int,
    /** 深度标注，跟在组名后面的一行小字 */
    val depthLabel: String,
    /** 自绘图标 */
    val icon: GroupIcon,
) {
    /** 已过时间且未完成。只在看今天时出现 */
    OVERDUE("已过时", 0, "搁浅", GroupIcon.STRANDED),
    MORNING("上午", 0, "浅滩 · 5m", GroupIcon.SUNRISE),
    AFTERNOON("下午", 1, "珊瑚层 · 40m", GroupIcon.CORAL),
    EVENING("晚上", 2, "暮光层 · 200m", GroupIcon.MOON),

    /** 00:00–05:59。原站叫「睡眠区」，排在晚上之后 */
    LATE_NIGHT("凌晨", 3, "午夜层 · 1000m", GroupIcon.JELLYFISH),

    /** 没有时间的任务。「时间是柔性的」那条理念落在这里 */
    ANYTIME("随时", 4, "漂流瓶", GroupIcon.BOTTLE),

    /** 长期计划自动生成、且没有时间的任务 */
    LONG_TERM("长期计划", 5, "洋流", GroupIcon.ANCHOR),
}

/** 分组图标。用自绘矢量图，不引第三方图标库 */
enum class GroupIcon { STRANDED, SUNRISE, CORAL, MOON, JELLYFISH, BOTTLE, ANCHOR }

/** 一个分组和它下面的任务。空分组不会出现在结果里 */
data class TaskGroup(
    val group: TodayGroup,
    val tasks: List<TaskEntity>,
)

object TodayGrouping {

    /** 上午的起点。与原站时间轴的起点一致（06:00） */
    const val MORNING_START = 6 * 60

    const val AFTERNOON_START = 12 * 60

    const val EVENING_START = 18 * 60

    /**
     * 把任务分好组。
     *
     * @param timed 已经按时间排好序的「有时间」任务（**已按当前筛选过滤过**）
     * @param anytime 没有时间的普通任务
     * @param longTerm 没有时间的长期计划任务
     * @param nowMinute 当前时刻换算成的「当天第几分钟」。看历史/未来日期时传 null —— 那种情况下不判「已过时」
     */
    fun group(
        timed: List<TaskEntity>,
        anytime: List<TaskEntity>,
        longTerm: List<TaskEntity>,
        nowMinute: Int?,
    ): List<TaskGroup> {
        val buckets = linkedMapOf<TodayGroup, MutableList<TaskEntity>>()

        timed.forEach { task ->
            val minute = TimeUtils.parseHHmm(task.time) ?: return@forEach
            val group = when {
                // 已过时只在「看今天」时有意义：历史日期全是过时的，未来日期全都不是
                nowMinute != null &&
                    !task.isDone &&
                    endMinuteOf(task, minute) < nowMinute -> TodayGroup.OVERDUE

                minute < MORNING_START -> TodayGroup.LATE_NIGHT
                minute < AFTERNOON_START -> TodayGroup.MORNING
                minute < EVENING_START -> TodayGroup.AFTERNOON
                else -> TodayGroup.EVENING
            }
            buckets.getOrPut(group) { mutableListOf() } += task
        }

        if (anytime.isNotEmpty()) buckets[TodayGroup.ANYTIME] = anytime.toMutableList()
        if (longTerm.isNotEmpty()) buckets[TodayGroup.LONG_TERM] = longTerm.toMutableList()

        // 按枚举声明顺序输出，空组丢掉
        return TodayGroup.entries.mapNotNull { g ->
            buckets[g]?.takeIf { it.isNotEmpty() }?.let { TaskGroup(g, it) }
        }
    }

    /**
     * 任务「应该做完的时刻」（当天第几分钟）。
     *
     * 判「已过时」用的是**结束时间**而不是开始时间：一个正在进行中的任务不该被标成已过时。
     * 没有时长就按最小时长算，跟时间轴上的块高算法保持一致。
     */
    private fun endMinuteOf(task: TaskEntity, startMinute: Int): Int {
        val duration = task.durationMinutes?.takeIf { it > 0 } ?: TimeUtils.MIN_DURATION
        return startMinute + duration
    }
}
