package com.qingji.plan.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 「选择时间」面板里那两段纯算法。
 *
 * 规则全部照抄原站 `dy` 组件，尤其是**时长反推**那一段——它有两条容易写错的边界。
 */
class TimePickerSheetTest {

    // ---------- initialDuration：从已有的起止时间反推「持续」档位 ----------

    @Test
    fun `正常区间能反推出时长`() {
        assertEquals(15, initialDuration("11:02", "11:17"))
        assertEquals(45, initialDuration("08:00", "08:45"))
        assertEquals(90, initialDuration("10:00", "11:30"))
    }

    @Test
    fun `跨零点的区间按取模算`() {
        // 23:50 → 00:20 是 30 分钟，不是负数
        assertEquals(30, initialDuration("23:50", "00:20"))
        assertEquals(15, initialDuration("23:45", "00:00"))
    }

    @Test
    fun `没有结束时间就回落 15 分钟`() {
        assertEquals(15, initialDuration("11:02", null))
        assertEquals(15, initialDuration("11:02", ""))
    }

    @Test
    fun `开始时间没填就回落 15 分钟`() {
        assertEquals(15, initialDuration("", "11:17"))
    }

    @Test
    fun `超过 240 分钟的区间不采信，回落 15 分钟`() {
        // 原站写的是 N>0&&N<=240，241 分钟就被丢掉了
        assertEquals(15, initialDuration("09:00", "13:01"))
        assertEquals(15, initialDuration("09:00", "20:00"))
    }

    @Test
    fun `正好 240 分钟是采信的`() {
        assertEquals(240, initialDuration("09:00", "13:00"))
    }

    @Test
    fun `起止相同视为非法，回落 15 分钟`() {
        // 差值 0，不满足 N>0
        assertEquals(15, initialDuration("09:00", "09:00"))
    }

    @Test
    fun `格式不合法不崩，回落 15 分钟`() {
        assertEquals(15, initialDuration("九点", "11:17"))
        assertEquals(15, initialDuration("11:02", "十一点"))
        assertEquals(15, initialDuration("11", "11:17"))
    }

    // ---------- endTimeOf：开始时间 + 时长 → 结束时间 ----------

    @Test
    fun `结束时间等于开始加时长`() {
        assertEquals("11:17", endTimeOf("11:02", 15))
        assertEquals("08:45", endTimeOf("08:00", 45))
        assertEquals("12:00", endTimeOf("10:00", 120))
    }

    @Test
    fun `结束时间跨零点会绕回来`() {
        assertEquals("00:20", endTimeOf("23:50", 30))
        assertEquals("00:00", endTimeOf("23:45", 15))
    }

    // ---------- 「持续」档位表 ----------

    @Test
    fun `持续档位和原站 zw 数组一致`() {
        assertEquals(listOf(15, 30, 45, 60, 90, 120, 180, 240), durationPresetMinutes)
    }
}
