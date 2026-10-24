package com.qingji.plan.service

import com.qingji.plan.data.db.entity.CourseEntity
import com.qingji.plan.data.db.entity.TaskEntity
import com.qingji.plan.util.DateUtils
import com.qingji.plan.util.TimeUtils
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** 一条要提醒的事项 */
data class Reminder(
    /** 稳定标识，用来取消/覆盖同一条提醒 */
    val key: String,
    /** 弹通知的时刻 */
    val atMillis: Long,
    val title: String,
    val body: String,
    val kind: Kind,
) {
    enum class Kind { TASK, COURSE, SETTLE }
}

/**
 * 提醒调度。
 *
 * **纯逻辑**：只算「什么时候该响」，不碰 AlarmManager / NotificationManager。
 * 这样能在 JVM 里把时间边界测干净，真正调系统 API 的部分薄薄一层。
 *
 * 三条规则：
 *   1. 一次只给**未来**的提醒排期，已经过去的跳过
 *   2. 已完成的任务不提醒
 *   3. 每日结算每天只有一条，到点才响
 */
object ReminderSchedule {

    /**
     * **课程**提前多久提醒。用户要求提前 30 分钟，好有时间从宿舍走到教室。
     *
     * 任务不提前：任务本来就是「现在该做这件事」，提前半小时提醒反而怪。
     *
     * （原来这里有个 `LEAD_MINUTES = 0`，但**全工程没人用**——是个死常量，
     *   课程实际是排在开始那一刻的。现在换成这个真的接上了。）
     */
    const val COURSE_LEAD_MINUTES = 30

    /**
     * 最多往前排多久。太远的没必要现在排，免得占一堆闹钟。
     *
     * 语义是「**含今天在内**共 N 天」—— 所以 HORIZON_DAYS = 7 覆盖的是今天到第 6 天。
     */
    const val HORIZON_DAYS = 7

    /**
     * 算出从现在到 [HORIZON_DAYS] 天内所有该响的提醒。
     *
     * @param now 当前时刻（测试可注入）
     * @param zone 时区
     */
    fun upcoming(
        now: LocalDateTime,
        tasks: List<TaskEntity>,
        courses: List<CourseEntity>,
        settleTime: String,
        settleEnabled: Boolean = true,
        zone: ZoneId = ZoneId.systemDefault(),
        horizonDays: Int = HORIZON_DAYS,
    ): List<Reminder> {
        val result = mutableListOf<Reminder>()
        val today = now.toLocalDate()
        // 含今天在内共 horizonDays 天
        val horizonEnd = today.plusDays((horizonDays - 1).toLong())

        // ---------- 任务 ----------
        tasks.filter { it.deletedAt == null && !it.isDone && it.hasTime }
            .forEach { task ->
                val date = DateUtils.fromKey(task.date) ?: return@forEach
                if (date.isBefore(today) || date.isAfter(horizonEnd)) return@forEach
                val minute = TimeUtils.parseHHmm(task.time) ?: return@forEach
                val at = atMinute(date, minute, zone)
                if (!at.isAfter(now)) return@forEach   // 已经过去了

                result.add(
                    Reminder(
                        key = "task_${task.uid}",
                        atMillis = at.atZone(zone).toInstant().toEpochMilli(),
                        title = task.title,
                        body = "${task.timeLabel} 开始",
                        kind = Reminder.Kind.TASK,
                    )
                )
            }

        // ---------- 课程 ----------
        // 课程是「每周固定」，所以要按周展开到 horizon 里的每一天
        courses.filter { it.deletedAt == null && it.hasTime }
            .forEach { course ->
                var date = today
                while (!date.isAfter(horizonEnd)) {
                    if (date.dayOfWeek.value - 1 == course.weekday) {
                        val minute = TimeUtils.parseHHmm(course.time)
                        if (minute != null) {
                            // 提前 COURSE_LEAD_MINUTES 分钟响。
                            // 算出来已经过去了就跳过（比如 8:20 才打开 App，8:30 的课提前提醒是 8:00）
                            val at = atMinute(date, minute, zone)
                                .minusMinutes(COURSE_LEAD_MINUTES.toLong())
                            if (at.isAfter(now)) {
                                result.add(
                                    Reminder(
                                        key = "course_${course.uid}_${DateUtils.toKey(date)}",
                                        atMillis = at.atZone(zone).toInstant().toEpochMilli(),
                                        title = course.title,
                                        body = buildString {
                                            append(course.time)
                                            course.room?.let { append(" · ").append(it) }
                                        },
                                        kind = Reminder.Kind.COURSE,
                                    )
                                )
                            }
                        }
                    }
                    date = date.plusDays(1)
                }
            }

        // ---------- 每日结算 ----------
        if (settleEnabled) {
            val minute = TimeUtils.parseHHmm(settleTime)
            if (minute != null) {
                var date = today
                while (!date.isAfter(horizonEnd)) {
                    val at = atMinute(date, minute, zone)
                    if (at.isAfter(now)) {
                        result.add(
                            Reminder(
                                key = "settle_${DateUtils.toKey(date)}",
                                atMillis = at.atZone(zone).toInstant().toEpochMilli(),
                                title = "该做今日结算了",
                                body = "给今天的表现打个分吧",
                                kind = Reminder.Kind.SETTLE,
                            )
                        )
                    }
                    date = date.plusDays(1)
                }
            }
        }

        return result.sortedBy { it.atMillis }
    }

    /**
     * 某天的某个分钟数 → LocalDateTime。
     *
     * 时间轴上的「凌晨 0-6 点」在概念上属于**前一天的深夜**，
     * 但提醒是绝对时刻，所以这里按自然日处理：`00:30` 就是当天凌晨 0:30。
     */
    fun atMinute(date: LocalDate, minute: Int, @Suppress("UNUSED_PARAMETER") zone: ZoneId): LocalDateTime =
        LocalDateTime.of(date, java.time.LocalTime.of(minute / 60 % 24, minute % 60))

    /**
     * 只留下最近的 N 条。
     *
     * 系统对同时注册的闹钟数量有限制，排太多还可能被系统丢掉。
     */
    fun takeNearest(reminders: List<Reminder>, limit: Int): List<Reminder> =
        reminders.sortedBy { it.atMillis }.take(limit)
}
