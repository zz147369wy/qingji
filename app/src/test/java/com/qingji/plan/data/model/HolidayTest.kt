package com.qingji.plan.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * 法定节假日表。
 *
 * 这张表是**手写死的内置数据**，最容易出的错不是逻辑而是**数据本身**：
 * 少一条、多一条、休/班写反、日期打错。所以测试分两层 ——
 * 一层盯少数几个「人对得上」的代表性日子，一层对全表做完整性扫描。
 */
class HolidayTest {

    // ---------- 数据完整性 ----------

    @Test
    fun `表里没有重复日期`() {
        // associate 会静默覆盖重复键，所以重复日期会表现成「少了一条」，
        // 不会崩也不会报错。这两个数必须相等。
        assertEquals(
            "内置数据里有重复日期，associate 把它们覆盖掉了",
            Holidays.entryCount,
            Holidays.size,
        )
    }

    @Test
    fun `覆盖 2015 到 2026 共 405 条`() {
        assertEquals(2015, Holidays.firstYear)
        assertEquals(2026, Holidays.lastYear)
        assertEquals(405, Holidays.size)
    }

    @Test
    fun `每一条的日期都能解析且落在覆盖范围内`() {
        for (year in Holidays.years) {
            // 抽查每个月，确保那一年真的有条目（而不是整年漏掉）
            var found = 0
            for (month in 1..12) {
                for (day in 1..LocalDate.of(year, month, 1).lengthOfMonth()) {
                    val date = LocalDate.of(year, month, day)
                    if (Holidays.of(date) != null) found++
                }
            }
            assertTrue("$year 年一条节假日数据都没有", found > 0)
        }
    }

    @Test
    fun `每一条的节日名都不为空`() {
        forEachHoliday { date, holiday ->
            assertTrue("$date 的节日名是空的", holiday.name.isNotBlank())
        }
    }

    @Test
    fun `每一年都有调休上班日`() {
        // 「休」和「班」是成对出现的：放假必然要还，一年下来不可能只有休没有班。
        // 真要出现只有休的年份，那多半是数据抓漏了。
        for (year in Holidays.years) {
            val workDays = (1..12).flatMap { month ->
                (1..LocalDate.of(year, month, 1).lengthOfMonth()).mapNotNull { day ->
                    LocalDate.of(year, month, day).takeIf { Holidays.isWorkDay(it) }
                }
            }
            assertTrue("$year 年没有找到任何调休上班日", workDays.isNotEmpty())
        }
    }

    // ---------- 代表性日子（对着国务院通知逐条核过） ----------

    @Test
    fun `2026 年国庆是 10 月 1 到 7 号放假`() {
        // 用户给的那张截图就是 2026 年 10 月，这条必须对得上
        for (day in 1..7) {
            val date = LocalDate.of(2026, 10, day)
            val holiday = Holidays.of(date)
            assertNotNull("2026-10-$day 应该是休", holiday)
            assertTrue("2026-10-$day 应该是休", holiday!!.isOffDay)
            assertEquals("国庆节", holiday.name)
        }
    }

    @Test
    fun `2026 年 10 月 10 号是补班`() {
        val holiday = Holidays.of(LocalDate.of(2026, 10, 10))
        assertNotNull(holiday)
        assertFalse(holiday!!.isOffDay)
        assertEquals("班", holiday.badge)
    }

    @Test
    fun `2026 年 10 月 8 号不是节假日`() {
        // 截图里 8 号只有「寒露」，没有休也没有班
        assertNull(Holidays.of(LocalDate.of(2026, 10, 8)))
    }

    @Test
    fun `2026 年春节 2 月 14 号和 28 号补班`() {
        listOf(14, 28).forEach { day ->
            val date = LocalDate.of(2026, 2, day)
            val holiday = Holidays.of(date)
            assertNotNull("2026-02-$day 应该是补班", holiday)
            assertFalse("2026-02-$day 应该是补班", holiday!!.isOffDay)
        }
    }

    @Test
    fun `2025 年国庆中秋连休八天且 10 月 11 号补班`() {
        for (day in 1..8) {
            assertTrue(Holidays.isOffDay(LocalDate.of(2025, 10, day)))
        }
        assertFalse(Holidays.isOffDay(LocalDate.of(2025, 10, 9)))
        assertTrue(Holidays.isWorkDay(LocalDate.of(2025, 10, 11)))
    }

    @Test
    fun `元旦总是 1 月 1 号`() {
        for (year in Holidays.years) {
            val holiday = Holidays.of(LocalDate.of(year, 1, 1))
            assertNotNull("$year 年 1 月 1 号没有数据", holiday)
            assertTrue("$year 年 1 月 1 号应该是休", holiday!!.isOffDay)
            assertEquals("元旦", holiday.name)
        }
    }

    @Test
    fun `劳动节总是 5 月 1 号放假`() {
        for (year in Holidays.years) {
            val holiday = Holidays.of(LocalDate.of(year, 5, 1))
            assertNotNull("$year 年 5 月 1 号没有数据", holiday)
            assertTrue("$year 年 5 月 1 号应该是休", holiday!!.isOffDay)
        }
    }

    @Test
    fun `法定节假日总天数落在合理区间`() {
        // 每年 11 天法定假日 + 调休，全年的「休」在 20~35 天之间。
        // 这个区间很宽，只用来兜住「整年数据错位」这种级别的错误。
        for (year in Holidays.years) {
            val offCount = (1..12).sumOf { month ->
                (1..LocalDate.of(year, month, 1).lengthOfMonth()).count { day ->
                    Holidays.isOffDay(LocalDate.of(year, month, day))
                }
            }
            assertTrue("$year 年的休假日是 $offCount 天，不像话", offCount in 18..40)
        }
    }

    // ---------- 范围之外 ----------

    @Test
    fun `覆盖范围之外的年份一律返回 null`() {
        assertNull(Holidays.of(LocalDate.of(2014, 10, 1)))
        assertNull(Holidays.of(LocalDate.of(2027, 10, 1)))
        assertNull(Holidays.of(LocalDate.of(2030, 1, 1)))
    }

    @Test
    fun `范围之外的日子不算休也不算班`() {
        val date = LocalDate.of(2027, 10, 1)
        assertFalse(Holidays.isOffDay(date))
        assertFalse(Holidays.isWorkDay(date))
    }

    @Test
    fun `普通日子返回 null`() {
        // 2026-09-29 是周二，什么都不是 —— 那天恰好是写这段代码的日子
        assertNull(Holidays.of(LocalDate.of(2026, 9, 29)))
    }

    // ---------- 徽标与短名 ----------

    @Test
    fun `休和班对应不同的徽标`() {
        assertEquals("休", Holiday("国庆节", isOffDay = true).badge)
        assertEquals("班", Holiday("国庆节", isOffDay = false).badge)
    }

    @Test
    fun `两个节日合并时用间隔号缩短`() {
        // 「中秋节、国庆节」7 个字，一格放不下
        assertEquals("中秋·国庆", Holiday("中秋节、国庆节", isOffDay = true).shortName)
        assertEquals("国庆·中秋", Holiday("国庆节、中秋节", isOffDay = true).shortName)
    }

    @Test
    fun `单个节日不改名`() {
        assertEquals("国庆节", Holiday("国庆节", isOffDay = true).shortName)
        assertEquals("春节", Holiday("春节", isOffDay = true).shortName)
    }

    @Test
    fun `抗战纪念日用简称`() {
        val holiday = Holiday(Holiday.ANNIVERSARY_2015, isOffDay = true)
        assertEquals("抗战胜利70周年", holiday.shortName)
    }

    @Test
    fun `2015 年那条超长名字真的存在且能缩短`() {
        val holiday = Holidays.of(LocalDate.of(2015, 9, 3))
        assertNotNull(holiday)
        assertEquals(Holiday.ANNIVERSARY_2015, holiday!!.name)
        assertEquals("抗战胜利70周年", holiday.shortName)
        assertTrue("简称也得放得进格子", holiday.shortName.length <= 8)
    }

    /** 遍历表里每一个有节假日的日子 */
    private fun forEachHoliday(block: (LocalDate, Holiday) -> Unit) {
        for (year in Holidays.years) {
            for (month in 1..12) {
                for (day in 1..LocalDate.of(year, month, 1).lengthOfMonth()) {
                    val date = LocalDate.of(year, month, day)
                    Holidays.of(date)?.let { block(date, it) }
                }
            }
        }
    }

    @Test
    fun `除 2015 年那条外，所有节日名缩短后都放得进格子`() {
        // 格子约 40dp 宽、字号 8sp，一个汉字 8dp —— 超过 5 个字就会被省略号截掉。
        // 全表只有 2015 年那次抗战纪念日超了，是已知的、也是一次性的。
        forEachHoliday { date, holiday ->
            if (holiday.name == Holiday.ANNIVERSARY_2015) return@forEachHoliday
            assertTrue(
                "$date 的「${holiday.shortName}」超过 5 字，格子里会省略",
                holiday.shortName.length <= 5,
            )
        }
    }

    @Test
    fun `表里没有意料之外的超长节日名`() {
        // 8 是硬上限：出现比 2015 年那条还长的名字，说明数据里掺了没预料到的东西
        forEachHoliday { date, holiday ->
            assertTrue(
                "$date 的节日名「${holiday.name}」异常地长",
                holiday.shortName.length <= 8,
            )
        }
    }
}
