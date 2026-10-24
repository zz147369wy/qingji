package com.qingji.plan.service

import com.qingji.plan.data.db.entity.TaskEntity
import com.qingji.plan.data.model.Priority
import com.qingji.plan.util.TimeUtils

/** 一段被占用的时间 */
data class BusyRange(val startMinute: Int, val endMinute: Int)

/** 排好的一条 */
data class ScheduledSlot(
    val task: TaskEntity,
    val startMinute: Int,
    val durationMinutes: Int,
) {
    val startHHmm: String get() = TimeUtils.formatHHmm(startMinute)
    val endMinute: Int get() = startMinute + durationMinutes
    val label: String get() = "$startHHmm-${TimeUtils.formatHHmm(endMinute)}"
}

/** 排期的结果 */
data class ScheduleResult(
    val placed: List<ScheduledSlot>,
    /** 时间不够、放不下的那些 */
    val skipped: List<TaskEntity>,
) {
    val hasAnything: Boolean get() = placed.isNotEmpty()
}

/**
 * AI 自动排期。
 *
 * 对应原站那句：
 * > AI 会把今天「未排时间」的待办任务，自动安排到 08:00-21:00 不冲突的时间段
 * > （已排时间的任务保持不变）。
 *
 * 三条必须守住的行为：
 *   1. **只动没排时间的任务** —— 已经有时间的原封不动
 *   2. 排进去的不能和任何已有安排重叠
 *   3. 时间不够就放弃那一条，**不硬塞**
 *
 * 起点不是死板的 08:00：**排今天**时取「此刻」与 08:00 里较晚的那个（[nowMinute]）。
 * 否则下午点「AI 自动排」会把任务全排到已经过去的时段，今日页紧接着就把它们归进
 * 「已过时」——看起来像凭空多出一堆逾期任务。
 */
object AutoScheduler {

    /** 默认排期窗口 */
    const val DEFAULT_START_HOUR = 8
    const val DEFAULT_END_HOUR = 21

    /** 排期时任务至少占用多久。太短的话排出来会很碎 */
    const val MIN_SLOT_MINUTES = 30

    /**
     * 排期。
     *
     * @param unscheduled 今天没排时间的待办任务
     * @param busy 今天已经被占用的时间段（有时间段的那些任务）
     * @param startHour 排期窗口起点，默认 08:00
     * @param endHour 排期窗口终点，默认 21:00
     * @param nowMinute 「此刻」是当天的第几分钟。**只在排今天时传** —— 传了就从它和窗口
     *   起点里取较晚的那个开始找空档，免得把任务排到已经过去的时段。排未来日期时传 null，
     *   仍从 08:00 找起。用参数注入而不是读系统时钟，纯函数性质与可测性就不破。
     */
    fun schedule(
        unscheduled: List<TaskEntity>,
        busy: List<BusyRange>,
        startHour: Int = DEFAULT_START_HOUR,
        endHour: Int = DEFAULT_END_HOUR,
        nowMinute: Int? = null,
    ): ScheduleResult {
        // 排今天时不能从 08:00 起（那正是 bug）；与「此刻」取较晚者即可。
        // nowMinute 为 null（看非今天）时退化成原来的行为。
        val windowStart = maxOf(startHour * 60, nowMinute ?: 0)
        val windowEnd = endHour * 60
        if (windowStart >= windowEnd) return ScheduleResult(emptyList(), unscheduled)

        // 已占用的区间：裁到窗口内、排好序、合并重叠的
        val occupied = busy
            .map { BusyRange(it.startMinute.coerceAtLeast(windowStart), it.endMinute.coerceAtMost(windowEnd)) }
            .filter { it.startMinute < it.endMinute }
            .sortedBy { it.startMinute }
            .fold(mutableListOf<BusyRange>()) { acc, range ->
                val last = acc.lastOrNull()
                if (last != null && range.startMinute <= last.endMinute) {
                    acc[acc.lastIndex] = BusyRange(last.startMinute, maxOf(last.endMinute, range.endMinute))
                } else {
                    acc.add(range)
                }
                acc
            }
            .toMutableList()

        // 排序：重要的先排，然后按创建时间
        val queue = unscheduled.sortedWith(
            compareByDescending<TaskEntity> { it.priority == Priority.HIGH.value }
                .thenBy { it.createdAt }
        )

        val placed = mutableListOf<ScheduledSlot>()
        val skipped = mutableListOf<TaskEntity>()

        queue.forEach { task ->
            val duration = maxOf(task.durationMinutes ?: TimeUtils.MIN_DURATION, MIN_SLOT_MINUTES)
            val slot = findSlot(occupied, windowStart, windowEnd, duration)
            if (slot == null) {
                skipped.add(task)
            } else {
                occupied.add(BusyRange(slot, slot + duration))
                occupied.sortBy { it.startMinute }
                placed.add(ScheduledSlot(task, slot, duration))
            }
        }

        return ScheduleResult(placed, skipped)
    }

    /**
     * 在已占用区间里找第一个能放下 `duration` 的空档。
     */
    private fun findSlot(
        occupied: List<BusyRange>,
        windowStart: Int,
        windowEnd: Int,
        duration: Int,
    ): Int? {
        var cursor = windowStart
        for (range in occupied.sortedBy { it.startMinute }) {
            if (range.endMinute <= cursor) continue
            // 当前空档从左游标到这一段开始
            if (range.startMinute - cursor >= duration) return cursor
            cursor = maxOf(cursor, range.endMinute)
        }
        return if (windowEnd - cursor >= duration) cursor else null
    }

    /**
     * 从当天的任务里分出「已排时间」和「没排时间」的。
     */
    fun split(tasks: List<TaskEntity>): Pair<List<BusyRange>, List<TaskEntity>> {
        val alive = tasks.filter { it.deletedAt == null }
        val busy = alive.filter { it.hasTime }.mapNotNull { task ->
            val start = TimeUtils.parseHHmm(task.time) ?: return@mapNotNull null
            val dur = TimeUtils.clampDuration(task.durationMinutes ?: TimeUtils.MIN_DURATION)
            BusyRange(start, start + dur)
        }
        val unscheduled = alive.filter { !it.hasTime && !it.isDone }
        return busy to unscheduled
    }
}
