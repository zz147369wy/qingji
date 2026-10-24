package com.qingji.plan.ui.calendar

import com.qingji.plan.data.db.entity.CourseEntity
import com.qingji.plan.data.model.PeriodSchedules
import com.qingji.plan.data.model.SchoolPeriod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 课表排布。
 *
 * 改版后**一节一行、行行等高**，不再是连续时间轴。
 * 所以这里守的是「哪门课归到哪一行」——归错了整张课表就是错的。
 */
class CourseTableLayoutTest {

    private fun course(
        title: String,
        time: String?,
        weekday: Int = 0,
        duration: Int = 85,
    ) = CourseEntity(
        uid = "u_$title",
        title = title,
        weekday = weekday,
        time = time,
        durationMinutes = duration,
        createdAt = 0,
        updatedAt = 0,
    )

    private val periods = PeriodSchedules.DEFAULT

    // ---------- 归到哪一节 ----------

    @Test
    fun `开始时间落在哪一节的区间里就归哪一节`() {
        // 第 1 节 8:30–9:55
        assertEquals(1, CourseTableLayout.periodIndexOf(course("早课", "08:30"), periods))
        assertEquals(1, CourseTableLayout.periodIndexOf(course("早课", "09:00"), periods))
        assertEquals(1, CourseTableLayout.periodIndexOf(course("早课", "09:54"), periods))
        // 第 2 节 10:15–11:40
        assertEquals(2, CourseTableLayout.periodIndexOf(course("第二", "10:15"), periods))
        assertEquals(2, CourseTableLayout.periodIndexOf(course("第二", "11:39"), periods))
    }

    @Test
    fun `一节的结束时刻属于下一节，不属于本节`() {
        // 9:55 是第 1 节的结束、第 2 节还没开始 —— 中间是课间，哪节都不算
        assertNull(CourseTableLayout.periodIndexOf(course("课间", "09:55"), periods))
        // 但 11:40 是第 2 节的结束，10:15 是第 2 节的开始
        assertEquals(2, CourseTableLayout.periodIndexOf(course("第二", "10:15"), periods))
    }

    @Test
    fun `课间和午休里的课归不进去`() {
        assertNull(CourseTableLayout.periodIndexOf(course("课间", "10:00"), periods))
        assertNull(CourseTableLayout.periodIndexOf(course("午休", "12:30"), periods))
        assertNull(CourseTableLayout.periodIndexOf(course("课后", "22:00"), periods))
    }

    @Test
    fun `没时间的课归不进去`() {
        assertNull(CourseTableLayout.periodIndexOf(course("随时", null), periods))
    }

    // ---------- 排成行 ----------

    @Test
    fun `一节一行，行数就是节次数`() {
        val rows = CourseTableLayout.rows(emptyList(), periods)

        assertEquals(6, rows.size)
        assertEquals(listOf(1, 2, 3, 4, 5, 6), rows.map { it.period.index })
        rows.forEach { assertEquals("cells 必须是 7 天", 7, it.cells.size) }
    }

    @Test
    fun `课被放进正确的行和列`() {
        val rows = CourseTableLayout.rows(
            listOf(
                course("周一第一节", "08:30", weekday = 0),
                course("周三第二节", "10:30", weekday = 2),
                course("周日第六节", "20:00", weekday = 6),
            ),
            periods,
        )

        assertEquals("周一第一节", rows[0].cells[0].single().title)
        assertEquals("周三第二节", rows[1].cells[2].single().title)
        assertEquals("周日第六节", rows[5].cells[6].single().title)
        // 其它格子都是空的
        assertEquals(0, rows[0].cells[1].size)
        assertEquals(0, rows[1].cells[0].size)
    }

    @Test
    fun `同一格里有几门课就几个，渲染时并排分栏`() {
        val rows = CourseTableLayout.rows(
            listOf(
                course("A课", "08:30", weekday = 0),
                course("B课", "09:00", weekday = 0),
            ),
            periods,
        )

        assertEquals(2, rows[0].cells[0].size)
        assertEquals(2, rows[0].columnCount(0))
        // 同格内按标题排，顺序稳定
        assertEquals(listOf("A课", "B课"), rows[0].cells[0].map { it.title })
    }

    @Test
    fun `没时间的课不占格子`() {
        val rows = CourseTableLayout.rows(listOf(course("随时做", null, weekday = 0)), periods)

        assertTrue(rows.all { row -> row.cells.all { it.isEmpty() } })
    }

    @Test
    fun `作息里有非法行时跳过那一行，不影响别的`() {
        val broken = periods + SchoolPeriod(7, "25:00", "26:00")

        val rows = CourseTableLayout.rows(emptyList(), broken)

        assertEquals("非法那一节不该出行", 6, rows.size)
        assertEquals(listOf(1, 2, 3, 4, 5, 6), rows.map { it.period.index })
    }

    @Test
    fun `星期超出 0-6 的脏数据不会崩`() {
        val rows = CourseTableLayout.rows(
            listOf(course("脏数据", "08:30", weekday = 99)),
            periods,
        )

        assertTrue(rows.all { row -> row.cells.all { it.isEmpty() } })
    }

    // ---------- 落不进作息的课 ----------

    @Test
    fun `对不上作息的课会被单独收起来，不会凭空消失`() {
        val stray = course("早八之前", "07:00", weekday = 0)
        val all = listOf(course("正常", "08:30", weekday = 0), stray)

        val outside = CourseTableLayout.outside(all, periods)

        assertEquals(listOf("早八之前"), outside.map { it.title })
        // 而且它确实不在任何格子里
        val rows = CourseTableLayout.rows(all, periods)
        assertEquals(1, rows[0].cells[0].size)
        assertEquals("正常", rows[0].cells[0].single().title)
    }

    @Test
    fun `没时间的课不算「对不上作息」——它归另一个列表`() {
        val outside = CourseTableLayout.outside(
            listOf(course("随时", null), course("正常", "08:30")),
            periods,
        )

        assertTrue("没时间的课不该进这里", outside.isEmpty())
    }

    @Test
    fun `全都在作息里时，外面那个列表是空的`() {
        val outside = CourseTableLayout.outside(
            listOf(course("A", "08:30"), course("B", "13:30"), course("C", "19:50")),
            periods,
        )

        assertTrue(outside.isEmpty())
    }
}
