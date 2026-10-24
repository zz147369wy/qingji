package com.qingji.plan.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalTime

/**
 * 时间换算与文案的验收测试。
 *
 * 文案相关的数字来自原站源码（如时长档位 `tn = 15`、列表行时间 `ny()`），
 * 错一个显示就不对，所以测得比较细。
 */
class TimeUtilsTest {

    // ---------- HH:mm 解析 ----------

    @Test
    fun `正常时间能解析`() {
        assertEquals(570, TimeUtils.parseHHmm("09:30"))
        assertEquals(0, TimeUtils.parseHHmm("00:00"))
        assertEquals(1439, TimeUtils.parseHHmm("23:59"))
    }

    @Test
    fun `个位数小时也能解析`() {
        assertEquals(570, TimeUtils.parseHHmm("9:30"))
    }

    @Test
    fun `非法时间一律返回空`() {
        assertNull(TimeUtils.parseHHmm(null))
        assertNull(TimeUtils.parseHHmm(""))
        assertNull(TimeUtils.parseHHmm("  "))
        assertNull(TimeUtils.parseHHmm("abc"))
        assertNull(TimeUtils.parseHHmm("24:00"))
        assertNull(TimeUtils.parseHHmm("09:60"))
        assertNull(TimeUtils.parseHHmm("-1:00"))
        assertNull(TimeUtils.parseHHmm("09"))
        assertNull(TimeUtils.parseHHmm("09:30:00"))
    }

    @Test
    fun `格式化会把小时补零`() {
        assertEquals("00:00", TimeUtils.formatHHmm(0))
        assertEquals("06:05", TimeUtils.formatHHmm(6 * 60 + 5))
        assertEquals("23:59", TimeUtils.formatHHmm(1439))
    }

    @Test
    fun `格式化会自动处理跨天`() {
        assertEquals("00:30", TimeUtils.formatHHmm(24 * 60 + 30))
        assertEquals("06:00", TimeUtils.formatHHmm(-18 * 60))
    }

    // ---------- 结束时间 ----------

    @Test
    fun `结束时间等于开始加时长`() {
        assertEquals("10:00", TimeUtils.endTimeOf("09:00", 60))
        assertEquals("09:15", TimeUtils.endTimeOf("09:00", 15))
    }

    @Test
    fun `结束时间能跨过午夜`() {
        assertEquals("00:30", TimeUtils.endTimeOf("23:30", 60))
    }

    @Test
    fun `开始时间非法时算不出结束时间`() {
        assertNull(TimeUtils.endTimeOf("啊啊啊", 60))
    }

    @Test
    fun `时长会被规整到不小于十五分钟`() {
        assertEquals(15, TimeUtils.clampDuration(0))
        assertEquals(15, TimeUtils.clampDuration(5))
        assertEquals(15, TimeUtils.clampDuration(15))
        assertEquals(30, TimeUtils.clampDuration(30))
    }

    // ---------- 显示文案 ----------

    @Test
    fun `时长文案跟原站的 rd 一模一样`() {
        // 逐字对齐 `扒站/app.js` 的 rd()：不足一小时说「分」，整小时说「时」，
        // 两小时以内正好一位小数的说「1.5时」，其余拆成「N时M分」。
        // 别改成「15分钟 / 1小时」那种写法 —— 原站从不那么写。
        assertEquals("15分", TimeUtils.formatDuration(15))
        assertEquals("30分", TimeUtils.formatDuration(30))
        assertEquals("45分", TimeUtils.formatDuration(45))
        assertEquals("1时", TimeUtils.formatDuration(60))
        assertEquals("1.5时", TimeUtils.formatDuration(90))
        assertEquals("2时", TimeUtils.formatDuration(120))
        assertEquals("3时", TimeUtils.formatDuration(180))
        assertEquals("4时", TimeUtils.formatDuration(240))
    }

    @Test
    fun `不是整小时的时长拆成几时几分`() {
        assertEquals("2时15分", TimeUtils.formatDuration(135))
        assertEquals("1时45分", TimeUtils.formatDuration(105))
        // 75 分钟 = 1.25 时，小数不是「正好一位」，原站也拆开
        assertEquals("1时15分", TimeUtils.formatDuration(75))
        assertEquals("1时1分", TimeUtils.formatDuration(61))
    }

    @Test
    fun `零和负数时长没有文案`() {
        // 原站 rd() 对 <=0 返回空串，不是「0分钟」
        assertEquals("", TimeUtils.formatDuration(0))
        assertEquals("", TimeUtils.formatDuration(-5))
    }

    @Test
    fun `编辑弹层的持续档位文案跟原站那排一致`() {
        // 原站 zw 数组：15分 30分 45分 1时 1.5时 2时 3时 4时
        val options = listOf(15, 30, 45, 60, 90, 120, 180, 240)
        assertEquals(
            listOf("15分", "30分", "45分", "1时", "1.5时", "2时", "3时", "4时"),
            options.map { TimeUtils.formatDuration(it) },
        )
    }

    @Test
    fun `时间标签优先用起止时间`() {
        assertEquals("09:00-10:00", TimeUtils.timeLabel("09:00", "10:00", null))
    }

    // ---------- 列表行的时间小标签（原站的 ny） ----------

    @Test
    fun `列表行的起止时间用空格加短横连接`() {
        // ⚠️ 中间是 en dash（U+2013），两边有空格；跟任务块上的 "09:00-10:00" 故意不同
        assertEquals("09:00 – 10:00", TimeUtils.chipTimeLabel("09:00", "10:00", null))
    }

    @Test
    fun `列表行没有结束时间时用时长算`() {
        assertEquals("09:00 – 10:30", TimeUtils.chipTimeLabel("09:00", null, 90))
    }

    @Test
    fun `列表行只有开始时间就只显示开始`() {
        assertEquals("09:00", TimeUtils.chipTimeLabel("09:00", null, null))
    }

    @Test
    fun `列表行没有时间就显示随时`() {
        // 原站 ny() 的兜底分支就是这个字面量
        assertEquals("随时", TimeUtils.chipTimeLabel(null, null, null))
        assertEquals("随时", TimeUtils.chipTimeLabel("", "10:00", 60))
    }

    @Test
    fun `跨零点的列表行时间也对`() {
        assertEquals("23:30 – 00:30", TimeUtils.chipTimeLabel("23:30", null, 60))
    }

    @Test
    fun `没有结束时间时用时长算`() {
        assertEquals("09:00-10:30", TimeUtils.timeLabel("09:00", null, 90))
    }

    @Test
    fun `只有开始时间就只显示开始时间`() {
        assertEquals("09:00", TimeUtils.timeLabel("09:00", null, null))
    }

    @Test
    fun `没有开始时间就没有标签`() {
        assertEquals("", TimeUtils.timeLabel(null, "10:00", 60))
        assertEquals("", TimeUtils.timeLabel("", null, null))
    }

    // ---------- nowHHmm：新建弹层的默认时间 ----------

    @Test
    fun `nowHHmm 补足两位并对上当前时刻`() {
        // 原站新建时把此刻填进时间字段，9 点 5 分要显示成 09:05 而不是 9:5
        assertEquals("09:05", TimeUtils.nowHHmm(LocalTime.of(9, 5)))
        assertEquals("00:00", TimeUtils.nowHHmm(LocalTime.of(0, 0)))
        assertEquals("23:59", TimeUtils.nowHHmm(LocalTime.of(23, 59)))
    }

    @Test
    fun `nowHHmm 的结果能被 parseHHmm 原样吃回去`() {
        val now = TimeUtils.nowHHmm(LocalTime.of(7, 8))
        assertEquals(7 * 60 + 8, TimeUtils.parseHHmm(now))
    }

    // ---------- 常量 ----------

    @Test
    fun `关键常量与原站一致`() {
        assertEquals(15, TimeUtils.MIN_DURATION)
        assertEquals(1440, TimeUtils.MINUTES_PER_DAY)
    }
}
