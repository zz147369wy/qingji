package com.qingji.plan.util

import com.qingji.plan.data.db.entity.TaskEntity
import java.time.Instant
import java.time.ZoneId
import kotlin.math.roundToInt

/** 一次结算的结果 */
data class SettleResult(
    val score: Int,
    val doneCount: Int,
    val totalCount: Int,
    val onTimeCount: Int,
    val summary: String,
    val feedback: String,
    val improvement: String,
)

/**
 * 每日结算算法。
 *
 * 逐行复刻原站的 `$w()` 函数，公式一个字都不能改：
 *
 * ```
 * 完成率 = 已完成数 ÷ 总任务数
 * 守时率 = 守时完成数 ÷ 已完成数
 * 分数   = 四舍五入(完成率 × 70 + 守时率 × 30)
 * ```
 *
 * **「守时」的判定**（这是最容易做错的地方）：
 *   - 没有 `time` 的任务 → **直接算守时**
 *   - 有 `time` 的任务 → 必须同时满足两个条件才算守时：
 *       1. 完成时刻的**日期**和结算那天一致
 *       2. 完成时刻的**时分** ≤ 计划结束时间（没填 endTime 就用 time + 时长）
 *   - **没有完成时刻**（completedAt 为空）→ 原站算它守时
 */
object SettleCalculator {

    private val ZONE: ZoneId get() = ZoneId.systemDefault()

    fun calculate(tasks: List<TaskEntity>, dateKey: String): SettleResult {
        // 已删除的任务不参与结算
        val alive = tasks.filter { it.deletedAt == null }

        val total = alive.size
        val doneTasks = alive.filter { it.isDone }
        val done = doneTasks.size

        val onTime = doneTasks.count { isOnTime(it, dateKey) }

        val doneRate = if (total == 0) 0.0 else done.toDouble() / total
        val onTimeRate = if (done == 0) 0.0 else onTime.toDouble() / done

        // 一条任务都没有时分数为 0
        val score = if (total == 0) 0 else (doneRate * 70 + onTimeRate * 30).roundToInt()

        return SettleResult(
            score = score,
            doneCount = done,
            totalCount = total,
            onTimeCount = onTime,
            summary = buildSummary(done, total, onTime),
            feedback = buildFeedback(score, total),
            improvement = buildImprovement(alive, doneTasks, done, total, dateKey),
        )
    }

    /**
     * 这个任务算不算「守时」完成。
     */
    fun isOnTime(task: TaskEntity, dateKey: String): Boolean {
        // 没有时间的任务，原站直接算守时
        if (!task.hasTime) return true

        // 没有完成时刻的，原站也算守时
        val completedAt = task.completedAt ?: return true

        val planEnd = planEndMinutes(task) ?: return true

        val completedDate = dateKeyOf(completedAt)
        // 完成日期和结算日期不是同一天 → 不算守时
        if (completedDate != dateKey) return false

        val completedMinute = minuteOfDay(completedAt) ?: return true
        return completedMinute <= planEnd
    }

    /** 计划结束时间（分钟）。没有时间信息就返回 null */
    private fun planEndMinutes(task: TaskEntity): Int? {
        TimeUtils.parseHHmm(task.endTime)?.let { return it }
        val start = TimeUtils.parseHHmm(task.time) ?: return null
        return start + TimeUtils.clampDuration(task.durationMinutes ?: TimeUtils.MIN_DURATION)
    }

    private fun dateKeyOf(timestamp: Long): String =
        Instant.ofEpochMilli(timestamp).atZone(ZONE).toLocalDate().let(DateUtils::toKey)

    private fun minuteOfDay(timestamp: Long): Int? {
        val time = Instant.ofEpochMilli(timestamp).atZone(ZONE).toLocalTime()
        return time.hour * 60 + time.minute
    }

    /**
     * 总结文案：`完成 3/5 项，守时 2/3 项`
     * 一项都没完成时不带「守时」后半句。
     */
    private fun buildSummary(done: Int, total: Int, onTime: Int): String =
        if (done > 0) "完成 $done/$total 项，守时 $onTime/$done 项"
        else "完成 $done/$total 项"

    /**
     * 评语，按分数分五档。文案原文照抄原站。
     */
    private fun buildFeedback(score: Int, total: Int): String = when {
        total == 0 -> "今天还没有安排计划，明天可以列 2-3 件最重要的事。"
        score >= 85 -> "太棒了！完成率与守时都很好，今天的节奏非常棒，继续保持。"
        score >= 70 -> "完成得不错，还有一点可以优化：把重要的事尽量往前排。"
        score >= 50 -> "今天完成了一半左右，明天优先做最重要的一两件，比面面俱到更有效。"
        else -> "今天有点赶，先完成最重要的 1-2 件，其它可以顺延，不必自责。"
    }

    /**
     * 改进建议：先说没做完的，再说做晚了的；都没有就鼓励一句。
     */
    private fun buildImprovement(
        alive: List<TaskEntity>,
        doneTasks: List<TaskEntity>,
        done: Int,
        total: Int,
        dateKey: String,
    ): String {
        val parts = mutableListOf<String>()

        val notDone = total - done
        if (notDone > 0) {
            parts.add("还有 $notDone 项未完成，建议明天排到上午并拆成小步。")
        }

        // 迟到 = 有时间的任务，完成时刻晚于计划结束时间
        val late = doneTasks.count { task ->
            if (!task.hasTime) return@count false
            val completedAt = task.completedAt ?: return@count false
            val planEnd = planEndMinutes(task) ?: return@count false
            (minuteOfDay(completedAt) ?: return@count false) > planEnd
        }
        if (late > 0) {
            parts.add("有 $late 项完成得比计划晚，明天给重要任务预留更早的时间。")
        }

        return if (parts.isEmpty()) {
            "今天节奏很好，明天可以适当增加一点难度或安排新计划。"
        } else {
            parts.joinToString(" ")
        }
    }
}
