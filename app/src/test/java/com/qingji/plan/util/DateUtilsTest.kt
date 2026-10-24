package com.qingji.plan.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * 日期工具测试。
 *
 * 锚点用的是原站真实显示过的数据：你截图里 2026-09-26 显示的是「9月26日 周六」，
 * 所以这条可以直接拿来验证星期换算对不对。
 */
class DateUtilsTest {

    private val sep26 = LocalDate.of(2026, 9, 26)

    @Test
    fun `日期主键格式是年月日`() {
        assertEquals("2026-09-26", DateUtils.toKey(sep26))
        assertEquals("2026-01-05", DateUtils.toKey(LocalDate.of(2026, 1, 5)))
    }

    @Test
    fun `日期主键能解析回来`() {
        assertEquals(sep26, DateUtils.fromKey("2026-09-26"))
    }

    @Test
    fun `非法日期主键返回空`() {
        assertNull(DateUtils.fromKey(null))
        assertNull(DateUtils.fromKey(""))
        assertNull(DateUtils.fromKey("不是日期"))
        assertNull(DateUtils.fromKey("2026/09/26"))
    }

    // ---------- 用原站真实显示过的日期当锚点 ----------

    @Test
    fun `二零二六年九月二十六日是周六`() {
        assertEquals("周六", DateUtils.weekdayLabel(sep26))
    }

    @Test
    fun `日期标题与截图里的显示一致`() {
        assertEquals("9月26日 周六", DateUtils.dateTitle(sep26))
        assertEquals("9月26日 周六（今天）", DateUtils.dateTitleWithToday(sep26, sep26))
    }

    @Test
    fun `不是今天时不加今天二字`() {
        assertEquals("9月26日 周六", DateUtils.dateTitleWithToday(sep26, LocalDate.of(2026, 9, 27)))
    }

    @Test
    fun `周一到周日的标签都对`() {
        // 2026-09-21 是周一，往后推一周
        val monday = LocalDate.of(2026, 9, 21)
        val expected = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
        expected.forEachIndexed { offset, label ->
            assertEquals(label, DateUtils.weekdayLabel(monday.plusDays(offset.toLong())))
        }
    }

    @Test
    fun `月日标题不带星期`() {
        assertEquals("9月26日", DateUtils.monthDayTitle(sep26))
    }

    // ---------- 顶部副标题 ----------

    @Test
    fun `今天时副标题带今天二字`() {
        assertEquals(
            "9月26日 周六 · 今天 · 6 项计划",
            DateUtils.todaySubtitle(sep26, sep26, 6),
        )
    }

    @Test
    fun `不是今天时副标题不带今天二字`() {
        assertEquals(
            "9月24日 周四 · 3 项计划",
            DateUtils.todaySubtitle(LocalDate.of(2026, 9, 24), sep26, 3),
        )
    }

    @Test
    fun `没有任务时显示零项计划`() {
        assertEquals(
            "9月26日 周六 · 今天 · 0 项计划",
            DateUtils.todaySubtitle(sep26, sep26, 0),
        )
    }

    // ---------- 问候语 ----------

    @Test
    fun `问候语按小时分段`() {
        assertEquals("早上好", DateUtils.greeting(6))
        assertEquals("上午好", DateUtils.greeting(9))
        assertEquals("中午好", DateUtils.greeting(12))
        assertEquals("下午好", DateUtils.greeting(15))
        assertEquals("晚上好", DateUtils.greeting(20))
        assertEquals("晚上好", DateUtils.greeting(2))
    }

    @Test
    fun `问候语标题与截图一致`() {
        assertEquals("晚上好，今天辛苦了", DateUtils.greetingTitle(21))
    }

    // ---------- 日历网格 ----------

    @Test
    fun `日历网格固定四十二天`() {
        assertEquals(42, DateUtils.monthGrid(2026, 9).size)
    }

    @Test
    fun `日历网格从当月第一天所在周的周一开始`() {
        // 2026-09-01 是周二，所以网格从 08-31（周一）起算
        val grid = DateUtils.monthGrid(2026, 9)
        assertEquals(LocalDate.of(2026, 8, 31), grid.first())
        assertEquals("周一", DateUtils.weekdayLabel(grid.first()))
    }

    @Test
    fun `日历网格包含当月全部日期`() {
        val grid = DateUtils.monthGrid(2026, 9)
        for (day in 1..30) {
            assertTrue("缺少 9月$day 日", grid.contains(LocalDate.of(2026, 9, day)))
        }
    }

    @Test
    fun `日历网格每行都是周一到周日`() {
        val grid = DateUtils.monthGrid(2026, 9)
        grid.chunked(7).forEach { week ->
            assertEquals("周一", DateUtils.weekdayLabel(week.first()))
            assertEquals("周日", DateUtils.weekdayLabel(week.last()))
        }
    }

    @Test
    fun `月份首末日期正确`() {
        assertEquals(LocalDate.of(2026, 9, 1), DateUtils.firstDayOf(2026, 9))
        assertEquals(LocalDate.of(2026, 9, 30), DateUtils.lastDayOf(2026, 9))
        assertEquals(LocalDate.of(2026, 2, 28), DateUtils.lastDayOf(2026, 2))
    }

    // ---------- 本周（课表日期行用） ----------

    @Test
    fun `周一算出来的就是它自己`() {
        // 2026-09-28 是周一
        val monday = LocalDate.of(2026, 9, 28)
        assertEquals(monday, DateUtils.mondayOf(monday))
    }

    @Test
    fun `周日算出来的是往前那个周一，不是往后`() {
        // 2026-09-27 是周日。一周以周一为第一天，所以它属于 9-21 那一周
        val sunday = LocalDate.of(2026, 9, 27)
        assertEquals(LocalDate.of(2026, 9, 21), DateUtils.mondayOf(sunday))
    }

    @Test
    fun `一周七天，从周一到周日`() {
        val week = DateUtils.weekOf(LocalDate.of(2026, 9, 27))

        assertEquals(7, week.size)
        assertEquals(LocalDate.of(2026, 9, 21), week.first())
        assertEquals(LocalDate.of(2026, 9, 27), week.last())
        // 全是周一开头
        week.forEach { assertEquals(DayOfWeek.MONDAY, it.dayOfWeek.minus((it.dayOfWeek.value - 1).toLong())) }
    }

    @Test
    fun `跨月的那一周也能算对`() {
        // 2026-10-01 是周四，它那一周从 9-28 开始
        val week = DateUtils.weekOf(LocalDate.of(2026, 10, 1))

        assertEquals(LocalDate.of(2026, 9, 28), week.first())
        assertEquals(LocalDate.of(2026, 10, 4), week.last())
    }

    @Test
    fun `同一周里随便哪天算出来都是同一周`() {
        val expected = DateUtils.weekOf(LocalDate.of(2026, 9, 23))
        (21..27).forEach { d ->
            assertEquals(
                "9月$d 日算出来的周不对",
                expected,
                DateUtils.weekOf(LocalDate.of(2026, 9, d)),
            )
        }
    }
}
