package com.qingji.plan.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 作息时间表。
 *
 * 课表左边那根轴是按「节次」排的（第 1 节 8:30–9:55 …），默认那套来自用户学校。
 * 这是可配置的，所以「存了坏数据怎么办」要守住：宁可退回默认，也不能让课表打不开。
 */
class SchoolPeriodTest {

    // ---------- 默认作息 ----------

    @Test
    fun `默认作息是用户学校那六节`() {
        val periods = PeriodSchedules.DEFAULT

        assertEquals(6, periods.size)
        assertEquals(listOf(1, 2, 3, 4, 5, 6), periods.map { it.index })
        assertEquals("08:30", periods.first().start)
        assertEquals("09:55", periods.first().end)
        assertEquals("19:45", periods.last().start)
        assertEquals("21:10", periods.last().end)
    }

    @Test
    fun `默认作息每节都是 85 分钟`() {
        PeriodSchedules.DEFAULT.forEach {
            assertEquals("第 ${it.index} 节不是 85 分钟", 85, it.durationMinutes)
        }
    }

    @Test
    fun `默认作息的节次之间不重叠`() {
        val sorted = PeriodSchedules.DEFAULT.sortedBy { it.startMinute }
        sorted.zipWithNext { a, b ->
            assertTrue(
                "第 ${a.index} 节和第 ${b.index} 节撞上了",
                b.startMinute >= a.endMinute,
            )
        }
    }

    // ---------- 时间的显示与规整 ----------

    @Test
    fun `起止时间标签去掉了前导零`() {
        val p = SchoolPeriod(1, "08:30", "09:55")
        assertEquals("8:30", p.startLabel)
        assertEquals("9:55", p.endLabel)
    }

    @Test
    fun `十点以后的时间不受影响`() {
        val p = SchoolPeriod(2, "10:15", "11:40")
        assertEquals("10:15", p.startLabel)
        assertEquals("11:40", p.endLabel)
    }

    @Test
    fun `手输的时间能被规整成两位`() {
        assertEquals("08:30", PeriodSchedules.normalizeTime("8:30"))
        assertEquals("08:05", PeriodSchedules.normalizeTime("08:5"))
        assertEquals("21:10", PeriodSchedules.normalizeTime(" 21:10 "))
        // 中文冒号也认
        assertEquals("08:30", PeriodSchedules.normalizeTime("8：30"))
    }

    @Test
    fun `离谱的时间规整不了`() {
        assertNull(PeriodSchedules.normalizeTime("25:00"))
        assertNull(PeriodSchedules.normalizeTime("8:70"))
        assertNull(PeriodSchedules.normalizeTime("八点半"))
        assertNull(PeriodSchedules.normalizeTime("830"))
        assertNull(PeriodSchedules.normalizeTime(""))
    }

    // ---------- 合法性 ----------

    @Test
    fun `结束早于开始是不合法的`() {
        assertFalse(PeriodSchedules.isSane(SchoolPeriod(1, "10:00", "09:00")))
        assertFalse(PeriodSchedules.isSane(SchoolPeriod(1, "10:00", "10:00")))
    }

    @Test
    fun `正常的一节是合法的`() {
        assertTrue(PeriodSchedules.isSane(SchoolPeriod(1, "08:30", "09:55")))
    }

    // ---------- 存取：坏数据必须退回默认 ----------

    @Test
    fun `存了再读回来是一样的`() {
        val periods = PeriodSchedules.DEFAULT
        assertEquals(periods, PeriodSchedules.parse(PeriodSchedules.encode(periods)))
    }

    @Test
    fun `自己改过的作息也能往返`() {
        val mine = listOf(
            SchoolPeriod(1, "09:00", "10:30"),
            SchoolPeriod(2, "10:45", "12:15"),
        )
        assertEquals(mine, PeriodSchedules.parse(PeriodSchedules.encode(mine)))
    }

    @Test
    fun `没存过就用默认`() {
        assertEquals(PeriodSchedules.DEFAULT, PeriodSchedules.parse(null))
        assertEquals(PeriodSchedules.DEFAULT, PeriodSchedules.parse(""))
        assertEquals(PeriodSchedules.DEFAULT, PeriodSchedules.parse("   "))
    }

    @Test
    fun `设置里存了坏数据一律退回默认，不让课表打不开`() {
        // 不是 JSON
        assertEquals(PeriodSchedules.DEFAULT, PeriodSchedules.parse("不是 json"))
        // 是 JSON 但不是数组
        assertEquals(PeriodSchedules.DEFAULT, PeriodSchedules.parse("""{"a":1}"""))
        // 空数组
        assertEquals(PeriodSchedules.DEFAULT, PeriodSchedules.parse("[]"))
        // 有一条时间不合法
        assertEquals(
            PeriodSchedules.DEFAULT,
            PeriodSchedules.parse("""[{"index":1,"start":"25:00","end":"09:00"}]"""),
        )
        // 有一条结束早于开始
        assertEquals(
            PeriodSchedules.DEFAULT,
            PeriodSchedules.parse("""[{"index":1,"start":"10:00","end":"09:00"}]"""),
        )
    }
}
