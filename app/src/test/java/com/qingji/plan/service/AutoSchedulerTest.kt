package com.qingji.plan.service

import com.qingji.plan.data.db.entity.TaskEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * AI 自动排期测试。
 *
 * 对应 PRD 的 P9 / P10 验收项：
 *   - 3 条未排时间的任务 → 排进 08:00–21:00 空档，**互不重叠**
 *   - 已有 14:00 开会 → 自动排期后 **14:00 那条时间不能变**
 */
class AutoSchedulerTest {

    private val now = 1_800_000_000_000L

    private fun task(
        title: String,
        time: String? = null,
        duration: Int? = null,
        priority: String = "mid",
        done: Boolean = false,
        createdAt: Long = now,
        deletedAt: Long? = null,
    ) = TaskEntity(
        uid = "u_$title",
        date = "2026-09-26",
        title = title,
        time = time,
        durationMinutes = duration,
        priority = priority,
        status = if (done) "done" else "todo",
        createdAt = createdAt,
        updatedAt = now,
        deletedAt = deletedAt,
    )

    // ---------- 基本排期 ----------

    @Test
    fun `一条任务会被排到窗口开头`() {
        val r = AutoScheduler.schedule(listOf(task("写周报")), emptyList())
        assertEquals(1, r.placed.size)
        assertEquals(8 * 60, r.placed[0].startMinute)
        assertEquals("08:00", r.placed[0].startHHmm)
    }

    @Test
    fun `多条任务依次往后排不重叠`() {
        val tasks = listOf(
            task("A", createdAt = now),
            task("B", createdAt = now + 1),
            task("C", createdAt = now + 2),
        )
        val r = AutoScheduler.schedule(tasks, emptyList())

        assertEquals(3, r.placed.size)
        // 依次排开，互相不重叠
        val sorted = r.placed.sortedBy { it.startMinute }
        sorted.zipWithNext().forEach { (a, b) ->
            assertTrue("${a.task.title} 和 ${b.task.title} 重叠了", a.endMinute <= b.startMinute)
        }
    }

    @Test
    fun `所有排进去的都不重叠`() {
        val tasks = (1..8).map { task("任务$it", duration = 60, createdAt = now + it) }
        val busy = listOf(BusyRange(10 * 60, 11 * 60), BusyRange(15 * 60, 16 * 60))

        val r = AutoScheduler.schedule(tasks, busy)

        val all = r.placed.map { BusyRange(it.startMinute, it.endMinute) } + busy
        val sorted = all.sortedBy { it.startMinute }
        sorted.zipWithNext().forEach { (a, b) ->
            assertTrue("排期结果和已有安排重叠了：$a 与 $b", a.endMinute <= b.startMinute)
        }
    }

    // ---------- 不碰已排时间的任务 ----------

    @Test
    fun `已经有时间的任务不会被排期结果包含`() {
        val scheduled = task("开会", time = "14:00", duration = 60)
        val free = task("写周报")

        val (busy, unscheduled) = AutoScheduler.split(listOf(scheduled, free))
        assertEquals(1, busy.size)
        assertEquals(1, unscheduled.size)
        assertEquals("写周报", unscheduled[0].title)

        val r = AutoScheduler.schedule(unscheduled, busy)
        assertTrue("已排时间的任务不该出现在结果里", r.placed.none { it.task.title == "开会" })
    }

    @Test
    fun `排期会绕过已有安排`() {
        // 窗口 10:00-12:00，其中 10:00-11:00 已被占用，只剩 11:00-12:00 这 60 分钟。
        // 三条 60 分钟的任务只能放下第一条，其余两条因为没有空档被跳过。
        val busy = listOf(BusyRange(10 * 60, 11 * 60))
        val tasks = listOf(
            task("A", duration = 60, createdAt = now),
            task("B", duration = 60, createdAt = now + 1),
            task("C", duration = 60, createdAt = now + 2),
        )

        val r = AutoScheduler.schedule(tasks, busy, startHour = 10, endHour = 12)
        assertEquals(1, r.placed.size)
        assertEquals(11 * 60, r.placed[0].startMinute)
        assertEquals(2, r.skipped.size)
    }

    // ---------- 装不下就跳过 ----------

    @Test
    fun `时间不够的任务会被跳过而不是硬塞`() {
        // 窗口 08:00-09:00 只有 60 分钟，两条 60 分钟的任务只能放下一条
        val tasks = listOf(
            task("A", duration = 60, createdAt = now),
            task("B", duration = 60, createdAt = now + 1),
        )
        val r = AutoScheduler.schedule(tasks, emptyList(), startHour = 8, endHour = 9)

        assertEquals(1, r.placed.size)
        assertEquals(1, r.skipped.size)
        assertEquals("A", r.placed[0].task.title)
    }

    @Test
    fun `窗口被完全占满时一条都排不进去`() {
        val busy = listOf(BusyRange(8 * 60, 21 * 60))
        val r = AutoScheduler.schedule(listOf(task("A")), busy)
        assertTrue(r.placed.isEmpty())
        assertEquals(1, r.skipped.size)
        assertFalse(r.hasAnything)
    }

    // ---------- 优先级 ----------

    @Test
    fun `重要的任务先排`() {
        val tasks = listOf(
            task("普通", createdAt = now),
            task("重要的事", priority = "high", createdAt = now + 1),
        )
        val r = AutoScheduler.schedule(tasks, emptyList())
        assertEquals("重要的事", r.placed[0].task.title)
    }

    // ---------- 时长 ----------

    @Test
    fun `排期时至少占三十分钟——太短会很碎`() {
        val r = AutoScheduler.schedule(listOf(task("很短的事", duration = 15)), emptyList())
        assertEquals(AutoScheduler.MIN_SLOT_MINUTES, r.placed[0].durationMinutes)
    }

    @Test
    fun `已有较长时长会保留`() {
        val r = AutoScheduler.schedule(listOf(task("长任务", duration = 90)), emptyList())
        assertEquals(90, r.placed[0].durationMinutes)
    }

    @Test
    fun `排期结果带出可读的时间标签`() {
        val r = AutoScheduler.schedule(listOf(task("A", duration = 60)), emptyList())
        assertEquals("08:00-09:00", r.placed[0].label)
    }

    // ---------- 拆分 ----------

    @Test
    fun `已完成的任务不参与排期`() {
        val (_, unscheduled) = AutoScheduler.split(listOf(task("做完的", done = true), task("没做的")))
        assertEquals(1, unscheduled.size)
        assertEquals("没做的", unscheduled[0].title)
    }

    @Test
    fun `已删除的任务不参与排期`() {
        val (busy, unscheduled) = AutoScheduler.split(
            listOf(task("删掉的", time = "10:00", deletedAt = now), task("删掉的2", deletedAt = now))
        )
        assertTrue(busy.isEmpty())
        assertTrue(unscheduled.isEmpty())
    }

    @Test
    fun `拆分时已排时间的进占用列表`() {
        val (busy, _) = AutoScheduler.split(listOf(task("开会", time = "14:00", duration = 60)))
        assertEquals(1, busy.size)
        assertEquals(14 * 60, busy[0].startMinute)
        assertEquals(15 * 60, busy[0].endMinute)
    }

    @Test
    fun `窗口非法时不做任何事`() {
        val r = AutoScheduler.schedule(listOf(task("A")), emptyList(), startHour = 20, endHour = 8)
        assertTrue(r.placed.isEmpty())
        assertEquals(1, r.skipped.size)
    }

    @Test
    fun `空任务列表不崩`() {
        val r = AutoScheduler.schedule(emptyList(), emptyList())
        assertTrue(r.placed.isEmpty())
        assertTrue(r.skipped.isEmpty())
        assertFalse(r.hasAnything)
    }

    // ---------- 排今天：不能从过去开始 ----------

    @Test
    fun `排今天时从当前时刻起而不是八点`() {
        // 下午 14:30 点「AI 自动排」。以前固定从 08:00 排，排出来的都在过去
        val r = AutoScheduler.schedule(listOf(task("写周报")), emptyList(), nowMinute = 14 * 60 + 30)
        assertEquals(1, r.placed.size)
        assertEquals(14 * 60 + 30, r.placed[0].startMinute)
        assertEquals("14:30", r.placed[0].startHHmm)
    }

    @Test
    fun `当前时刻早于八点时仍从八点起`() {
        // 07:00 排期时 08:00 还没到，起点取较晚的 08:00
        val r = AutoScheduler.schedule(listOf(task("晨跑")), emptyList(), nowMinute = 7 * 60)
        assertEquals(8 * 60, r.placed[0].startMinute)
    }

    @Test
    fun `排今天时不会把任务排到当前时刻之前`() {
        val nowMinute = 10 * 60
        val tasks = listOf(
            task("A", createdAt = now),
            task("B", createdAt = now + 1),
            task("C", createdAt = now + 2),
        )
        val r = AutoScheduler.schedule(tasks, emptyList(), nowMinute = nowMinute)
        assertEquals(3, r.placed.size)
        assertTrue("排出来的任务不该早于此刻", r.placed.all { it.startMinute >= nowMinute })
    }

    @Test
    fun `排今天时会跳过当前时刻之前的占用`() {
        // 09:00-10:00 已占用，但现在 11:00 —— 应从 11:00 起排，而不是被过去的占用卡住
        val busy = listOf(BusyRange(9 * 60, 10 * 60))
        val r = AutoScheduler.schedule(listOf(task("A")), busy, nowMinute = 11 * 60)
        assertEquals(11 * 60, r.placed[0].startMinute)
    }

    @Test
    fun `当前时刻已过窗口终点时一条都排不进去`() {
        // 22:00 点「AI 自动排」，08:00-21:00 的窗口已经过去了，只剩「跳过」
        val r = AutoScheduler.schedule(listOf(task("A")), emptyList(), nowMinute = 22 * 60)
        assertTrue(r.placed.isEmpty())
        assertEquals(1, r.skipped.size)
    }

    @Test
    fun `不传当前时刻时仍从八点起——排未来日期`() {
        val r = AutoScheduler.schedule(listOf(task("A", duration = 60)), emptyList(), nowMinute = null)
        assertEquals(8 * 60, r.placed[0].startMinute)
        assertEquals("08:00-09:00", r.placed[0].label)
    }
}
