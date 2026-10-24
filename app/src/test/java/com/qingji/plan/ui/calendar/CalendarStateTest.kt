package com.qingji.plan.ui.calendar

import com.qingji.plan.data.db.entity.CourseEntity
import com.qingji.plan.data.db.entity.TaskEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * 日历页的计算测试。
 *
 * 锚点仍然用原站真实显示过的数据：2026-09-26 是周六，2026-09-01 是周二，
 * 所以 9 月的网格从 08-31（周一）开始。
 */
class CalendarStateTest {

    private val today = LocalDate.of(2026, 9, 26)
    private val now = 1_800_000_000_000L

    private fun task(
        title: String,
        date: LocalDate,
        time: String? = null,
        done: Boolean = false,
        category: String = "work",
        deletedAt: Long? = null,
    ) = TaskEntity(
        uid = "u_$title", date = com.qingji.plan.util.DateUtils.toKey(date), title = title,
        category = category, time = time,
        durationMinutes = if (time != null) 60 else null,
        status = if (done) "done" else "todo",
        createdAt = now, updatedAt = now, deletedAt = deletedAt,
    )

    private fun course(title: String, weekday: Int, time: String? = "08:00", uid: String = title) =
        CourseEntity(
            uid = uid, title = title, weekday = weekday, time = time,
            durationMinutes = 45, createdAt = now, updatedAt = now,
        )

    private fun compute(
        year: Int = 2026,
        month: Int = 9,
        view: CalendarView = CalendarView.MONTH,
        selected: LocalDate = today,
        filter: String? = null,
        tasks: List<TaskEntity> = emptyList(),
        courses: List<CourseEntity> = emptyList(),
    ) = CalendarStateCalculator.compute(
        year, month, view, selected, today, filter, tasks, courses,
    )

    // ---------- 网格结构 ----------

    @Test
    fun `网格固定四十二格`() {
        assertEquals(42, compute().cells.size)
    }

    @Test
    fun `网格从当月第一天所在周的周一开始`() {
        val cells = compute().cells
        assertEquals(LocalDate.of(2026, 8, 31), cells.first().date)
        // 8/31 不是 9 月，所以要淡化
        assertFalse(cells.first().inCurrentMonth)
    }

    @Test
    fun `本月之外的格子标记为非本月`() {
        val cells = compute().cells
        assertFalse(cells.first().inCurrentMonth)          // 8/31
        assertTrue(cells.first { it.date == LocalDate.of(2026, 9, 1) }.inCurrentMonth)
        assertTrue(cells.first { it.date == LocalDate.of(2026, 9, 30) }.inCurrentMonth)
        assertFalse(cells.last().inCurrentMonth)           // 10 月的日子
    }

    @Test
    fun `今天的格子被标记`() {
        val cells = compute().cells
        val todayCell = cells.single { it.isToday }
        assertEquals(today, todayCell.date)
    }

    // ---------- 任务统计 ----------

    @Test
    fun `格子上的任务数不含已删除的`() {
        val cells = compute(
            tasks = listOf(
                task("正常的", today),
                task("删掉的", today, deletedAt = now),
            )
        ).cells
        assertEquals(1, cells.single { it.isToday }.taskCount)
    }

    @Test
    fun `格子能报出已完成的数量`() {
        val cells = compute(
            tasks = listOf(
                task("完成了", today, done = true),
                task("没完成", today),
            )
        ).cells
        val cell = cells.single { it.isToday }
        assertEquals(2, cell.taskCount)
        assertEquals(1, cell.doneCount)
    }

    @Test
    fun `格子里的标签最多三条`() {
        // 用 9/24（周四）—— 这天不是节假日，走的是「平常」那条上限。
        // 节假日的格子另有一条更严的上限，见下面「有节假日的格子」那条测试。
        val day = LocalDate.of(2026, 9, 24)
        val cells = compute(
            tasks = (1..6).map { task("任务$it", day, time = "0$it:00") }
        ).cells
        assertEquals(3, cells.single { it.date == day }.chips.size)
    }

    @Test
    fun `有节假日的格子比平常少放一条标签`() {
        // 格子里要多画一行节日名（约 11dp），格子总共才约 60dp 高，
        // 不砍一条的话最底下那枚小条会被切掉半个字。
        val holiday = LocalDate.of(2026, 9, 26)   // 中秋节
        val ordinary = LocalDate.of(2026, 9, 24)  // 普通周四
        val tasks = (1..6).flatMap { i ->
            listOf(
                task("假期$i", holiday, time = "0$i:00"),
                task("平常$i", ordinary, time = "0$i:00"),
            )
        }
        val cells = compute(tasks = tasks).cells
        assertEquals(2, cells.single { it.date == holiday }.chips.size)
        assertEquals(3, cells.single { it.date == ordinary }.chips.size)
    }

    // ---------- 节假日 ----------

    @Test
    fun `中秋节那格带休字标记`() {
        // 2026-09-26 既是测试的「今天」，也是 2026 年的中秋节
        val holiday = compute().cells.single { it.isToday }.holiday
        assertNotNull(holiday)
        assertEquals("中秋节", holiday!!.name)
        assertTrue(holiday.isOffDay)
        assertEquals("休", holiday.badge)
    }

    @Test
    fun `调休上班日带班字标记`() {
        // 2026-09-20 是国庆节的调休上班日（周日要上班）
        val cell = compute().cells.single { it.date == LocalDate.of(2026, 9, 20) }
        assertNotNull(cell.holiday)
        assertFalse(cell.holiday!!.isOffDay)
        assertEquals("班", cell.holiday!!.badge)
    }

    @Test
    fun `普通日子没有节假日标记`() {
        val cell = compute().cells.single { it.date == LocalDate.of(2026, 9, 24) }
        assertNull(cell.holiday)
    }

    @Test
    fun `十月国庆假期在网格里逐天标出来`() {
        // 用户给的那张参考截图就是 2026 年 10 月
        val cells = compute(month = 10).cells

        for (day in 1..7) {
            val cell = cells.single { it.date == LocalDate.of(2026, 10, day) }
            assertEquals("2026-10-$day 应该是休", "休", cell.holiday?.badge)
        }
        assertEquals(
            "2026-10-10 是补班",
            "班",
            cells.single { it.date == LocalDate.of(2026, 10, 10) }.holiday?.badge,
        )
        // 10/8 是普通上班日（参考截图里只有「寒露」），不该有标记
        assertNull(cells.single { it.date == LocalDate.of(2026, 10, 8) }.holiday)
    }

    @Test
    fun `合并放假的节日名缩短后进格子`() {
        // 2026-10 那几天是「国庆节」，2025-10 才是「国庆节、中秋节」
        val cells = compute(year = 2025, month = 10).cells
        val cell = cells.single { it.date == LocalDate.of(2025, 10, 1) }
        assertEquals("国庆·中秋", cell.holiday?.shortName)
    }

    @Test
    fun `节假日不影响任务数和完成数`() {
        val cells = compute(
            tasks = listOf(
                task("甲", today, done = true),
                task("乙", today),
            )
        ).cells
        val cell = cells.single { it.isToday }
        assertEquals(2, cell.taskCount)
        assertEquals(1, cell.doneCount)
    }

    @Test
    fun `没有任务也没有课的格子标记为空`() {
        val cells = compute(tasks = listOf(task("有事的", today))).cells
        assertTrue(cells.single { it.isToday }.isEmpty.not())
        assertTrue(cells.first { it.date == LocalDate.of(2026, 9, 10) }.isEmpty)
    }

    // ---------- 有课的标记 ----------

    @Test
    fun `有课的那天会被标记`() {
        // 2026-09-26 是周六 → weekday 索引 5
        val cells = compute(courses = listOf(course("高等数学", 5))).cells
        assertTrue(cells.single { it.isToday }.hasCourse)
    }

    @Test
    fun `没有课的周几不会被标记`() {
        val cells = compute(courses = listOf(course("高等数学", 0))).cells
        assertFalse(cells.single { it.isToday }.hasCourse)
    }

    @Test
    fun `没排固定时间的课不参与有课标记`() {
        val cells = compute(courses = listOf(course("答疑", 5, time = null))).cells
        assertFalse(cells.single { it.isToday }.hasCourse)
    }

    // ---------- 选中的那天 ----------

    @Test
    fun `选中那天的任务按时间排序没时间的排最后`() {
        val s = compute(
            selected = today,
            tasks = listOf(
                task("下午", today, time = "15:00"),
                task("随时", today, time = null),
                task("早上", today, time = "09:00"),
            ),
        )
        assertEquals(listOf("早上", "下午", "随时"), s.selectedTasks.map { it.title })
    }

    @Test
    fun `选中那天的标题格式与原站一致`() {
        val s = compute(selected = today, tasks = listOf(task("开会", today)))
        assertEquals("9月26日的计划 · 1 项", s.selectedTitle)
    }

    @Test
    fun `没有计划时标题显示零项`() {
        assertEquals("9月26日的计划 · 0 项", compute().selectedTitle)
    }

    @Test
    fun `只显示选中那天自己的任务`() {
        val s = compute(
            selected = today,
            tasks = listOf(
                task("今天的", today),
                task("明天的", today.plusDays(1)),
            ),
        )
        assertEquals(listOf("今天的"), s.selectedTasks.map { it.title })
    }

    // ---------- 分类筛选 ----------

    @Test
    fun `筛选某个分类后只统计该分类`() {
        val s = compute(
            filter = "work",
            tasks = listOf(
                task("工作的事", today, category = "work"),
                task("学习的事", today, category = "study"),
            ),
        )
        assertEquals(1, s.cells.single { it.isToday }.taskCount)
        assertEquals(listOf("工作的事"), s.selectedTasks.map { it.title })
    }

    @Test
    fun `筛选为空表示全部分类`() {
        val s = compute(
            filter = null,
            tasks = listOf(
                task("工作的事", today, category = "work"),
                task("学习的事", today, category = "study"),
            ),
        )
        assertEquals(2, s.cells.single { it.isToday }.taskCount)
    }

    @Test
    fun `筛选选项第一项是全部分类`() {
        val options = CalendarStateCalculator.filterOptions()
        assertEquals(null, options.first().first)
        assertEquals("全部分类", options.first().second)
        assertEquals(6, options.size)   // 全部分类 + 5 个内置
    }

    // ---------- 月份导航 ----------

    @Test
    fun `上个月能正确跨年`() {
        assertEquals(2025 to 12, CalendarStateCalculator.previousMonth(2026, 1))
    }

    @Test
    fun `下个月能正确跨年`() {
        assertEquals(2027 to 1, CalendarStateCalculator.nextMonth(2026, 12))
    }

    @Test
    fun `月份标签格式是点号分隔`() {
        assertEquals("2026.09", compute().monthLabel)
        assertEquals("2026.12", compute(month = 12).monthLabel)
    }

    // ---------- 视图与文案 ----------

    @Test
    fun `两个视图的名称与原站一致`() {
        assertEquals(listOf("月视图", "课程表"), CalendarView.entries.map { it.label })
    }

    @Test
    fun `副标题照设计稿`() {
        assertEquals("点日期看当天 · 金点 = 这天有课", compute().subtitle)
    }

    @Test
    fun `当日计划卡标题不带条数`() {
        assertEquals("9月26日的计划", compute().selectedHeading)
    }

    @Test
    fun `课程表视图下也能拿到课程列表`() {
        val s = compute(view = CalendarView.COURSE, courses = listOf(course("数学", 0)))
        assertEquals(1, s.courses.size)
    }

    @Test
    fun `已删除的课不出现在课程表里`() {
        val deleted = course("删掉的", 0).copy(deletedAt = now)
        assertTrue(compute(view = CalendarView.COURSE, courses = listOf(deleted)).courses.isEmpty())
    }
}
