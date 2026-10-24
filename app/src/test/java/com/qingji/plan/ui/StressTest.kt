package com.qingji.plan.ui

import com.qingji.plan.data.db.entity.CourseEntity
import com.qingji.plan.data.db.entity.TaskEntity
import com.qingji.plan.ui.calendar.CalendarStateCalculator
import com.qingji.plan.ui.calendar.CalendarView
import com.qingji.plan.ui.stats.StatsCalculator
import com.qingji.plan.ui.today.TodayStateCalculator
import com.qingji.plan.util.DateUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * 千条数据的压测（对应开发计划 P10 的 10.4 条）。
 *
 * 背景：`开发计划.md` 的风险清单 R5 是「1000 条任务时卡顿」。
 * 这里不去测「跑了多少毫秒」——那在 JVM 上毫无意义，而且会变成随机失败的假测试。
 * 真正要守住的是两件事：
 *   1. **正确性**：一千条数据下聚合出来的数字还得是对的（别因为优化漏算）
 *   2. **没有平方级行为**：三个页面计算器各跑一遍，总耗时给一个宽松上限，
 *      只用来兜住「不小心写成 O(n²)」这种结构性退化
 *
 * 真机上的滑动手感只能真机验，这里证明不了。
 */
class StressTest {

    private val today = LocalDate.of(2026, 9, 27)
    private val now = 1_800_000_000_000L

    /** 造 N 条任务，散在最近 100 天里 */
    private fun manyTasks(count: Int): List<TaskEntity> = (0 until count).map { i ->
        val date = today.minusDays((i % 100).toLong())
        TaskEntity(
            uid = "u$i",
            date = DateUtils.toKey(date),
            title = "任务 $i",
            category = listOf("work", "study", "life", "health", "other")[i % 5],
            time = if (i % 3 == 0) null else "%02d:%02d".format(6 + i % 16, (i * 7) % 60),
            durationMinutes = if (i % 3 == 0) null else 15 + (i % 8) * 15,
            priority = if (i % 4 == 0) "high" else "mid",
            status = if (i % 5 < 2) "done" else "todo",
            createdAt = now,
            updatedAt = now,
        )
    }

    private fun manyCourses(count: Int): List<CourseEntity> = (0 until count).map { i ->
        CourseEntity(
            uid = "c$i",
            title = "课程 $i",
            weekday = i % 7,
            time = "%02d:%02d".format(8 + i % 12, (i * 11) % 60),
            durationMinutes = 45,
            room = if (i % 2 == 0) "教室 $i" else null,
            category = listOf("work", "study", "life")[i % 3],
            createdAt = now,
            updatedAt = now,
        )
    }

    @Test
    fun `一千条任务下今日页的聚合仍然正确`() {
        val tasks = manyTasks(1000)
        // 只看今天那一天
        val todays = tasks.filter { it.date == DateUtils.toKey(today) }

        val state = TodayStateCalculator.compute(
            date = today,
            today = today,
            tasks = todays,
        )

        val total = todays.size
        val done = todays.count { it.isDone }
        assertEquals(total, state.totalCount)
        assertEquals(done, state.doneCount)
        assertEquals(total - done, state.todoCount)
        // 完成率不能是 NaN，且必须落在 0..100
        assertTrue("完成率越界：${state.percent}", state.percent in 0..100)
        // 有时间 / 没时间必须被正确分开，一条都不能漏
        assertEquals(todays.size, state.timedTasks.size + state.anytimeTasks.size)
    }

    @Test
    fun `一千条任务下日历页的聚合仍然正确`() {
        val tasks = manyTasks(1000)

        val state = CalendarStateCalculator.compute(
            year = today.year,
            month = today.monthValue,
            view = CalendarView.MONTH,
            selected = today,
            today = today,
            categoryFilter = null,
            tasks = tasks,
            courses = manyCourses(50),
        )

        // 42 格一个不多一个不少
        assertEquals(42, state.cells.size)
        // 格子上的计数加起来，不能超过总数（分组不能凭空多出任务）
        val counted = state.cells.sumOf { it.taskCount }
        assertTrue("格子里的任务计数比总任务还多：$counted > ${tasks.size}", counted <= tasks.size)
        // 每个格子的 taskCount 必须 ≥ 实际显示出来的小条数
        assertTrue(
            "有格子的任务数比它显示的小条还少",
            state.cells.all { it.taskCount >= it.chips.size },
        )
        // 只选今天那一天时，列表长度要跟今天真实的任务数对上
        assertEquals(tasks.count { it.date == DateUtils.toKey(today) }, state.selectedTasks.size)
    }

    @Test
    fun `一千条任务下统计页的聚合仍然正确`() {
        val tasks = manyTasks(1000)

        val state = StatsCalculator.compute(
            today = today,
            tasks = tasks,
            reviews = emptyList(),
        )

        val done = tasks.count { it.isDone }
        assertEquals(tasks.size, state.totalTasks)
        assertEquals(done, state.totalDone)
        assertEquals((done * 100.0 / tasks.size).toInt(), state.completionRate)
        // 趋势图固定 14 根柱子
        assertEquals(StatsCalculator.TREND_DAYS, state.trend.size)
        // 5 个内置分类都要出现在占比里
        assertEquals(5, state.categoryShares.size)
        // 占比加起来应该是 100（四舍五入允许 1 的误差），不能因为数据多就漏项
        val sum = state.categoryShares.sumOf { it.percent }
        assertTrue("分类占比加起来是 $sum，应该是 100 上下", sum in 99..101)
    }

    @Test
    fun `三个页面串起来跑一遍不退化到平方级`() {
        val tasks = manyTasks(1000)
        val courses = manyCourses(50)

        // 热一遍，避免把类加载和 JIT 计入
        repeat(3) {
            TodayStateCalculator.compute(today, today, tasks)
            CalendarStateCalculator.compute(
                today.year, today.monthValue, CalendarView.MONTH,
                today, today, null, tasks, courses,
            )
            StatsCalculator.compute(today, tasks, emptyList())
        }

        val startedAt = System.nanoTime()
        repeat(20) {
            TodayStateCalculator.compute(today, today, tasks)
            CalendarStateCalculator.compute(
                today.year, today.monthValue, CalendarView.MONTH,
                today, today, null, tasks, courses,
            )
            StatsCalculator.compute(today, tasks, emptyList())
        }
        val elapsedMs = (System.nanoTime() - startedAt) / 1_000_000

        // 20 轮 × 3 个计算器 × 1000 条。线性实现下这在 JVM 上是几十毫秒级别；
        // 给 5 秒的宽松上限，只用来兜住「不小心写成 O(n²)」的结构性退化，不做性能断言。
        assertTrue(
            "20 轮千条聚合用了 ${elapsedMs}ms，疑似出现了平方级行为",
            elapsedMs < 5_000,
        )
    }
}
