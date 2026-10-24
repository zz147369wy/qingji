package com.qingji.plan.util

import com.qingji.plan.data.db.entity.TaskEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * 结算算法的验收测试。对应 PRD §10.3 的 S1–S5。
 *
 * 公式：`分数 = 四舍五入(完成率 × 70 + 守时率 × 30)`
 * 这些用例是照着原站 `$w()` 函数的行为一条条写的。
 */
class SettleCalculatorTest {

    private val dateKey = "2026-09-26"
    private val date: LocalDate = LocalDate.of(2026, 9, 26)
    private val now = 1_800_000_000_000L

    /** 造一个完成时刻的时间戳 */
    private fun at(hour: Int, minute: Int, day: LocalDate = date): Long =
        LocalDateTime.of(day, java.time.LocalTime.of(hour, minute))
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

    private fun task(
        title: String,
        time: String? = null,
        durationMinutes: Int? = null,
        endTime: String? = null,
        done: Boolean = false,
        completedAt: Long? = null,
        date: String = dateKey,
        deletedAt: Long? = null,
    ) = TaskEntity(
        uid = "u_$title",
        date = date,
        title = title,
        time = time,
        endTime = endTime ?: if (time != null && durationMinutes != null) {
            TimeUtils.endTimeOf(time, durationMinutes)
        } else null,
        durationMinutes = durationMinutes,
        status = if (done) "done" else "todo",
        completedAt = completedAt,
        createdAt = now,
        updatedAt = now,
        deletedAt = deletedAt,
    )

    // ---------- S4：一条任务都没有 ----------

    @Test
    fun `一条任务都没有时分数为零`() {
        val r = SettleCalculator.calculate(emptyList(), dateKey)
        assertEquals(0, r.score)
        assertEquals(0, r.totalCount)
        assertEquals("完成 0/0 项", r.summary)
    }

    @Test
    fun `一条任务都没有时的评语与原文一致`() {
        val r = SettleCalculator.calculate(emptyList(), dateKey)
        assertEquals("今天还没有安排计划，明天可以列 2-3 件最重要的事。", r.feedback)
    }

    // ---------- S1：核心公式 ----------

    @Test
    fun `完成率七成守时率三成——四任务完成三其中两守时应得七十三分`() {
        val tasks = listOf(
            // A：09:00-10:00，09:30 完成 → 守时
            task("A", time = "09:00", durationMinutes = 60, done = true, completedAt = at(9, 30)),
            // B：10:00-11:00，11:30 才完成 → 迟到
            task("B", time = "10:00", durationMinutes = 60, done = true, completedAt = at(11, 30)),
            // C：11:00-12:00，10:30 完成（提前做完）→ 守时
            task("C", time = "11:00", durationMinutes = 60, done = true, completedAt = at(10, 30)),
            // D：未完成
            task("D", time = "14:00", durationMinutes = 60, done = false),
        )
        val r = SettleCalculator.calculate(tasks, dateKey)

        assertEquals(4, r.totalCount)
        assertEquals(3, r.doneCount)
        assertEquals(2, r.onTimeCount)
        // 完成率 3/4 = 0.75 → 0.75 × 70 = 52.5
        // 守时率 2/3 ≈ 0.667 → × 30 = 20
        // 合计 72.5 → 四舍五入 73
        assertEquals(73, r.score)
        assertEquals("完成 3/4 项，守时 2/3 项", r.summary)
    }

    @Test
    fun `全部按时完成是一百分`() {
        val tasks = listOf(
            task("A", time = "09:00", durationMinutes = 60, done = true, completedAt = at(9, 30)),
            task("B", time = "10:00", durationMinutes = 60, done = true, completedAt = at(10, 5)),
        )
        val r = SettleCalculator.calculate(tasks, dateKey)
        assertEquals(100, r.score)
        assertEquals("太棒了！完成率与守时都很好，今天的节奏非常棒，继续保持。", r.feedback)
    }

    @Test
    fun `一项都没完成时分数为零且没有守时统计`() {
        val tasks = listOf(
            task("A", time = "09:00", durationMinutes = 60),
            task("B", time = "10:00", durationMinutes = 60),
        )
        val r = SettleCalculator.calculate(tasks, dateKey)
        assertEquals(0, r.score)
        assertEquals("完成 0/2 项", r.summary)
    }

    // ---------- S2：没有时间的任务直接算守时 ----------

    @Test
    fun `没有时间的任务勾选后直接算守时`() {
        val tasks = listOf(
            task("买菜", time = null, done = true, completedAt = at(23, 50)),
            task("写周报", time = null, done = true, completedAt = at(23, 59)),
        )
        val r = SettleCalculator.calculate(tasks, dateKey)
        assertEquals(2, r.onTimeCount)
        assertEquals(100, r.score)
    }

    @Test
    fun `混合场景——有时间的算迟到无时间的算守时`() {
        val tasks = listOf(
            task("没时间的", time = null, done = true, completedAt = at(23, 0)),
            task("迟到的", time = "09:00", durationMinutes = 60, done = true, completedAt = at(15, 0)),
        )
        val r = SettleCalculator.calculate(tasks, dateKey)
        assertEquals(2, r.doneCount)
        assertEquals(1, r.onTimeCount)
        // 完成率 1.0 × 70 = 70，守时率 0.5 × 30 = 15，合计 85
        assertEquals(85, r.score)
    }

    // ---------- S3：迟到判定 ----------

    @Test
    fun `完成时间超过计划结束时间不算守时`() {
        val t = task("开会", time = "09:00", durationMinutes = 60, done = true, completedAt = at(10, 1))
        assertFalse(SettleCalculator.isOnTime(t, dateKey))
    }

    @Test
    fun `正好卡在结束时间上算守时`() {
        val t = task("开会", time = "09:00", durationMinutes = 60, done = true, completedAt = at(10, 0))
        assertTrue(SettleCalculator.isOnTime(t, dateKey))
    }

    @Test
    fun `提前完成算守时`() {
        val t = task("开会", time = "09:00", durationMinutes = 60, done = true, completedAt = at(9, 30))
        assertTrue(SettleCalculator.isOnTime(t, dateKey))
    }

    @Test
    fun `没有填结束时间时用开始时间加时长算`() {
        // 09:00 + 30 分钟 = 09:30
        val ok = task("A", time = "09:00", durationMinutes = 30, done = true, completedAt = at(9, 29))
        val late = task("B", time = "09:00", durationMinutes = 30, done = true, completedAt = at(9, 31))
        assertTrue(SettleCalculator.isOnTime(ok, dateKey))
        assertFalse(SettleCalculator.isOnTime(late, dateKey))
    }

    @Test
    fun `完成日期不是结算那天不算守时`() {
        val t = task(
            "跨天完成的", time = "09:00", durationMinutes = 60, done = true,
            completedAt = at(9, 30, day = date.plusDays(1)),
        )
        assertFalse(SettleCalculator.isOnTime(t, dateKey))
    }

    @Test
    fun `没有完成时刻时按守时处理——与原站行为一致`() {
        val t = task("A", time = "09:00", durationMinutes = 60, done = true, completedAt = null)
        assertTrue(SettleCalculator.isOnTime(t, dateKey))
    }

    @Test
    fun `没勾选完成的任务不参与守时判定`() {
        val tasks = listOf(task("A", time = "09:00", durationMinutes = 60, done = false))
        assertEquals(0, SettleCalculator.calculate(tasks, dateKey).onTimeCount)
    }

    // ---------- S5：五档评语 ----------

    @Test
    fun `八十五分以上是最高档评语`() {
        val tasks = listOf(
            task("A", time = "09:00", durationMinutes = 60, done = true, completedAt = at(9, 30)),
            task("B", time = "10:00", durationMinutes = 60, done = true, completedAt = at(10, 30)),
        )
        val r = SettleCalculator.calculate(tasks, dateKey)
        assertEquals(100, r.score)
        assertTrue(r.feedback.startsWith("太棒了"))
    }

    @Test
    fun `七十分到八十四分是第二档评语`() {
        val tasks = listOf(
            task("A", time = "09:00", durationMinutes = 60, done = true, completedAt = at(9, 30)),
            task("B", time = "10:00", durationMinutes = 60, done = true, completedAt = at(10, 30)),
            task("C", time = "11:00", durationMinutes = 60, done = true, completedAt = at(20, 0)),
            task("D", time = "14:00", durationMinutes = 60),
        )
        val r = SettleCalculator.calculate(tasks, dateKey)
        assertEquals(73, r.score)
        assertTrue(r.feedback.startsWith("完成得不错"))
    }

    @Test
    fun `五十分到六十九分是第三档评语`() {
        val tasks = listOf(
            task("A", time = "09:00", durationMinutes = 60, done = true, completedAt = at(9, 30)),
            task("B", time = "10:00", durationMinutes = 60, done = true, completedAt = at(10, 30)),
            task("C", time = "11:00", durationMinutes = 60),
            task("D", time = "14:00", durationMinutes = 60),
        )
        val r = SettleCalculator.calculate(tasks, dateKey)
        assertEquals(65, r.score)
        assertTrue(r.feedback.startsWith("今天完成了一半左右"))
    }

    @Test
    fun `五十分以下是第四档评语`() {
        val tasks = listOf(
            task("A", time = "09:00", durationMinutes = 60, done = true, completedAt = at(20, 0)),
            task("B", time = "10:00", durationMinutes = 60),
            task("C", time = "11:00", durationMinutes = 60),
            task("D", time = "14:00", durationMinutes = 60),
        )
        val r = SettleCalculator.calculate(tasks, dateKey)
        assertEquals(18, r.score)
        assertTrue(r.feedback.startsWith("今天有点赶"))
    }

    // ---------- 改进建议 ----------

    @Test
    fun `有未完成的任务时给出顺延建议`() {
        val tasks = listOf(
            task("A", time = "09:00", durationMinutes = 60, done = true, completedAt = at(9, 30)),
            task("B", time = "10:00", durationMinutes = 60),
            task("C", time = "11:00", durationMinutes = 60),
        )
        val r = SettleCalculator.calculate(tasks, dateKey)
        assertTrue(r.improvement.contains("还有 2 项未完成"))
    }

    @Test
    fun `有迟到任务时给出预留更早时间的建议`() {
        val tasks = listOf(
            task("A", time = "09:00", durationMinutes = 60, done = true, completedAt = at(20, 0)),
        )
        val r = SettleCalculator.calculate(tasks, dateKey)
        assertTrue(r.improvement.contains("完成得比计划晚"))
    }

    @Test
    fun `全部按时完成时给鼓励`() {
        val tasks = listOf(
            task("A", time = "09:00", durationMinutes = 60, done = true, completedAt = at(9, 30)),
        )
        val r = SettleCalculator.calculate(tasks, dateKey)
        assertEquals("今天节奏很好，明天可以适当增加一点难度或安排新计划。", r.improvement)
    }

    @Test
    fun `既没完成又有迟到时两条建议都有`() {
        val tasks = listOf(
            task("A", time = "09:00", durationMinutes = 60, done = true, completedAt = at(20, 0)),
            task("B", time = "10:00", durationMinutes = 60),
        )
        val r = SettleCalculator.calculate(tasks, dateKey)
        assertTrue(r.improvement.contains("还有 1 项未完成"))
        assertTrue(r.improvement.contains("完成得比计划晚"))
    }

    // ---------- 软删除不参与结算 ----------

    @Test
    fun `已删除的任务不参与结算`() {
        val tasks = listOf(
            task("正常的", time = "09:00", durationMinutes = 60, done = true, completedAt = at(9, 30)),
            task("已删除的", time = "10:00", durationMinutes = 60, done = false, deletedAt = now),
        )
        val r = SettleCalculator.calculate(tasks, dateKey)
        assertEquals(1, r.totalCount)
        assertEquals(100, r.score)
    }
}
