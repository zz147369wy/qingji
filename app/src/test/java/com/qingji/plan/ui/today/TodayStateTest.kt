package com.qingji.plan.ui.today

import com.qingji.plan.data.db.entity.TaskEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * 今日页状态计算的测试。
 *
 * 三态文案、百分比、筛选计数全部对照原站源码写。
 */
class TodayStateTest {

    private val today: LocalDate = LocalDate.of(2026, 9, 26)
    private val dateKey = "2026-09-26"
    private val now = 1_800_000_000_000L

    private fun task(
        title: String,
        time: String? = null,
        done: Boolean = false,
        category: String = "work",
        deletedAt: Long? = null,
        isLongTerm: Boolean = false,
    ) = TaskEntity(
        uid = "u_$title",
        date = dateKey,
        title = title,
        category = category,
        time = time,
        durationMinutes = if (time != null) 60 else null,
        status = if (done) "done" else "todo",
        isLongTerm = isLongTerm,
        createdAt = now,
        updatedAt = now,
        deletedAt = deletedAt,
    )

    private fun compute(
        tasks: List<TaskEntity>,
        filter: TaskFilter = TaskFilter.ALL,
        date: LocalDate = today,
        hour: Int = 21,
    ) = TodayStateCalculator.compute(date, today, tasks, filter, hour)

    // ---------- 进度环百分比 ----------

    @Test
    fun `六项完成两项是百分之三十三`() {
        val tasks = listOf(
            task("A"), task("B"), task("C"), task("D"),
            task("E", done = true), task("F", done = true),
        )
        // 环中心显示的是百分比数字，不是「2/6」
        assertEquals(33, compute(tasks).percent)
    }

    @Test
    fun `没有任务时百分比为零`() {
        assertEquals(0, compute(emptyList()).percent)
    }

    @Test
    fun `全部完成是百分之百`() {
        assertEquals(100, compute(listOf(task("A", done = true), task("B", done = true))).percent)
    }

    // ---------- 进度卡主文案三态 ----------

    @Test
    fun `一项都没完成时显示开始今天的第一件事吧`() {
        assertEquals("开始今天的第一件事吧", compute(emptyList()).ringTitle)
        assertEquals("开始今天的第一件事吧", compute(listOf(task("A"))).ringTitle)
    }

    @Test
    fun `全部完成时显示太棒了全部完成`() {
        val tasks = listOf(task("A", done = true), task("B", done = true))
        assertEquals("太棒了，全部完成！", compute(tasks).ringTitle)
    }

    @Test
    fun `完成一部分时显示已完成几分之几`() {
        val tasks = listOf(task("A", done = true), task("B"), task("C"))
        assertEquals("已完成 1 / 3", compute(tasks).ringTitle)
    }

    // ---------- 进度卡副文案三档 ----------

    @Test
    fun `不到一半时显示慢慢来`() {
        assertEquals("慢慢来，完成比完美重要", compute(emptyList()).ringHint)
        assertEquals("慢慢来，完成比完美重要", compute(listOf(task("A", done = true), task("B"), task("C"))).ringHint)
    }

    @Test
    fun `刚好过半时显示进度过半`() {
        val tasks = listOf(task("A", done = true), task("B", done = true), task("C"), task("D"))
        assertEquals("进度过半，保持节奏", compute(tasks).ringHint)
    }

    @Test
    fun `全部完成时显示今天圆满收官`() {
        assertEquals("今天圆满收官，去海里游一圈吧", compute(listOf(task("A", done = true))).ringHint)
    }

    // ---------- 顶部文案 ----------

    @Test
    fun `问候语按小时给出`() {
        assertEquals("晚上好，今天辛苦了", compute(emptyList(), hour = 21).greetingTitle)
        assertEquals("早上好，今天辛苦了", compute(emptyList(), hour = 7).greetingTitle)
    }

    @Test
    fun `副标题显示日期与计划条数`() {
        val tasks = listOf(task("A"), task("B"), task("C"))
        assertEquals("9月26日 周六 · 今天 · 3 项计划", compute(tasks).subtitle)
    }

    @Test
    fun `不是今天时副标题不带今天二字且标记为非今天`() {
        val state = compute(emptyList(), date = LocalDate.of(2026, 9, 24))
        assertFalse(state.isToday)
        assertEquals("9月24日 周四 · 0 项计划", state.subtitle)
    }

    @Test
    fun `今天时标记为今天`() {
        assertTrue(compute(emptyList()).isToday)
    }

    // ---------- 筛选 ----------

    @Test
    fun `筛选全部时显示所有任务`() {
        val tasks = listOf(task("A"), task("B", done = true), task("C"))
        val s = compute(tasks, TaskFilter.ALL)
        assertEquals(3, s.visibleTasks.size)
        assertEquals(3, s.countFor(TaskFilter.ALL))
    }

    @Test
    fun `筛选待办只显示未完成的`() {
        val tasks = listOf(task("A"), task("B", done = true), task("C"))
        val s = compute(tasks, TaskFilter.TODO)
        assertEquals(2, s.visibleTasks.size)
        assertTrue(s.visibleTasks.none { it.isDone })
        assertEquals(2, s.countFor(TaskFilter.TODO))
    }

    @Test
    fun `筛选已完成只显示已完成的`() {
        val tasks = listOf(task("A"), task("B", done = true), task("C"))
        val s = compute(tasks, TaskFilter.DONE)
        assertEquals(1, s.visibleTasks.size)
        assertTrue(s.visibleTasks.all { it.isDone })
        assertEquals(1, s.countFor(TaskFilter.DONE))
    }

    @Test
    fun `筛选按钮的文案与原站一致`() {
        assertEquals(listOf("全部", "待办", "已完成"), TaskFilter.entries.map { it.label })
    }

    // ---------- 时间轴分组 ----------

    @Test
    fun `有时间的进时间轴没时间的进随时区`() {
        val tasks = listOf(
            task("开会", time = "15:00"),
            task("买菜", time = null),
            task("写周报", time = "09:00"),
        )
        val s = compute(tasks)
        assertEquals(2, s.timedTasks.size)
        assertEquals(1, s.anytimeTasks.size)
        assertEquals("买菜", s.anytimeTasks.single().title)
    }

    @Test
    fun `时间轴上的任务按时间排序`() {
        val tasks = listOf(
            task("下午", time = "15:00"),
            task("早上", time = "09:00"),
            task("中午", time = "12:00"),
        )
        assertEquals(listOf("早上", "中午", "下午"), compute(tasks).timedTasks.map { it.title })
    }

    // ---------- 软删除 ----------

    @Test
    fun `已删除的任务不出现在任何地方`() {
        val tasks = listOf(
            task("正常的", time = "09:00"),
            task("删掉的", time = "10:00", deletedAt = now),
        )
        val s = compute(tasks)
        assertEquals(1, s.totalCount)
        assertEquals(1, s.timedTasks.size)
        assertEquals("正常的", s.timedTasks.single().title)
    }

    @Test
    fun `已删除的任务不计入百分比`() {
        val tasks = listOf(
            task("完成的", done = true),
            task("删掉的", deletedAt = now),
        )
        assertEquals(100, compute(tasks).percent)
    }

    // ---------- 杂项 ----------

    @Test
    fun `没有任务时标记为空`() {
        assertTrue(compute(emptyList()).isEmpty)
        assertFalse(compute(listOf(task("A"))).isEmpty)
    }

    @Test
    fun `计数三个相加等于总数`() {
        val tasks = listOf(task("A"), task("B", done = true), task("C"), task("D", done = true))
        val s = compute(tasks)
        assertEquals(s.totalCount, s.doneCount + s.todoCount)
    }

    // ---------- 长期计划生成的「没排时间」任务单独一栏 ----------

    @Test
    fun `没排时间的长期计划任务进长计栏，不进随时栏`() {
        val state = compute(
            listOf(
                task("买菜"),                                   // 普通随时任务
                task("背单词", isLongTerm = true),                // 长期计划生成的
            ),
        )

        assertEquals(listOf("买菜"), state.anytimeTasks.map { it.title })
        assertEquals(listOf("背单词"), state.longTermTasks.map { it.title })
    }

    @Test
    fun `排了时间的长期计划任务照常上时间轴，不进长计栏`() {
        val state = compute(listOf(task("晨跑", time = "07:00", isLongTerm = true)))

        assertEquals(listOf("晨跑"), state.timedTasks.map { it.title })
        assertTrue("排了时间的不该再进长计栏", state.longTermTasks.isEmpty())
    }

    @Test
    fun `两栏互不重复，也都算进总任务数`() {
        val state = compute(
            listOf(
                task("随时A"),
                task("长期B", isLongTerm = true),
                task("时间轴C", time = "09:00"),
            ),
        )

        assertEquals(3, state.totalCount)
        assertEquals(1, state.anytimeTasks.size)
        assertEquals(1, state.longTermTasks.size)
        assertEquals(1, state.timedTasks.size)
        // 三条各归一处，没有哪条被算两遍
        val all = state.anytimeTasks + state.longTermTasks + state.timedTasks
        assertEquals(3, all.map { it.uid }.toSet().size)
    }

    @Test
    fun `没有长期计划时那一栏是空的`() {
        val state = compute(listOf(task("买菜"), task("写周报", time = "09:00")))

        assertTrue(state.longTermTasks.isEmpty())
    }

    // ---------- 筛选（2026-10：筛选以前只改计数，列表纹丝不动，是个 bug） ----------

    @Test
    fun `待办筛选只留下没完成的`() {
        val state = compute(
            listOf(task("A", done = true), task("B"), task("C", time = "09:00")),
            filter = TaskFilter.TODO,
        )
        // 先有时间的那组，再是随时
        assertEquals(listOf("C", "B"), state.shownTimed().map { it.title } + state.shownAnytime().map { it.title })
    }

    @Test
    fun `已完成筛选只留下完成的`() {
        val state = compute(
            listOf(task("A", done = true), task("B"), task("C", time = "09:00", done = true)),
            filter = TaskFilter.DONE,
        )
        assertEquals(listOf("A"), state.shownAnytime().map { it.title })
        assertEquals(listOf("C"), state.shownTimed().map { it.title })
    }

    @Test
    fun `全部筛选一条不落`() {
        val state = compute(listOf(task("A", done = true), task("B"), task("C", time = "09:00")))
        assertEquals(3, state.shownTimed().size + state.shownAnytime().size + state.shownLongTerm().size)
    }

    @Test
    fun `刚完成的任务在待办筛选下还会多留一会儿`() {
        val done = task("A", done = true)
        val state = compute(listOf(done, task("B")), filter = TaskFilter.TODO)

        // 没有滞留集合：它已经离开待办
        assertEquals(listOf("B"), state.shownAnytime().map { it.title })

        // 冒泡期间（uid 进了滞留集合）：它还留在原位，泡泡才有地方冒
        assertEquals(listOf("A", "B"), state.shownAnytime(lingering = setOf(done.uid)).map { it.title })
    }

    @Test
    fun `滞留只影响待办筛选，不影响已完成筛选`() {
        val done = task("A", done = true)
        val state = compute(listOf(done), filter = TaskFilter.DONE)
        assertEquals(listOf("A"), state.shownAnytime(lingering = setOf(done.uid)).map { it.title })
    }
}
