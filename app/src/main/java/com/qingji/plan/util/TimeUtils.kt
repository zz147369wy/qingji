package com.qingji.plan.util

import java.time.LocalTime
import kotlin.math.roundToInt

/**
 * 时间换算与文案工具。
 *
 * 纯函数、与 UI 无关：`HH:mm` 与「当天第几分钟」互转、时长规整，以及各处显示文案。
 * 文案逐字对齐原站（见各函数上的说明），改动前先确认原站写法。
 */
object TimeUtils {

    const val MINUTES_PER_DAY = 24 * 60

    /** 最小时长（分钟），原站 `tn = 15` */
    const val MIN_DURATION = 15

    // ---------- HH:mm 与分钟数互转 ----------

    /** `"09:30"` → `570`。格式不合法返回 null */
    fun parseHHmm(text: String?): Int? {
        if (text.isNullOrBlank()) return null
        val parts = text.trim().split(":")
        if (parts.size != 2) return null
        val h = parts[0].toIntOrNull() ?: return null
        val m = parts[1].toIntOrNull() ?: return null
        if (h !in 0..23 || m !in 0..59) return null
        return h * 60 + m
    }

    /** `570` → `"09:30"`（自动对 1440 取模） */
    fun formatHHmm(minute: Int): String {
        val m = normalizeMinute(minute)
        val h = m / 60
        return "${pad2(h)}:${pad2(m % 60)}"
    }

    /** 把任意分钟数规整到 [0, 1440) */
    fun normalizeMinute(minute: Int): Int = ((minute % MINUTES_PER_DAY) + MINUTES_PER_DAY) % MINUTES_PER_DAY

    /**
     * 此刻的 `HH:mm`。
     *
     * 原站新建任务/课程时，时间字段默认就填当前时刻（`new Date().getHours()`），
     * 所以「添加计划」弹层一打开时间就是满的。参数注入是为了能测。
     */
    fun nowHHmm(now: LocalTime = LocalTime.now()): String = formatHHmm(now.hour * 60 + now.minute)

    private fun pad2(v: Int): String = if (v < 10) "0$v" else v.toString()

    // ---------- 常用计算 ----------

    /** 结束时间 = 开始 + 时长（跨零点自动回绕） */
    fun endTimeOf(startHHmm: String, durationMinutes: Int): String? {
        val start = parseHHmm(startHHmm) ?: return null
        return formatHHmm(start + durationMinutes)
    }

    /** 把时长规整到不小于 [MIN_DURATION] */
    fun clampDuration(minutes: Int): Int = if (minutes < MIN_DURATION) MIN_DURATION else minutes

    // ---------- 显示 ----------

    /**
     * 时长文案（`.dur-tag`、编辑弹层的「持续」档位）。**逐字对齐原站的 `rd()`**：
     *
     * ```
     * 0 或负数 → ""          15分 30分 45分  ← 不足一小时的按分钟
     * 整小时   → "1时" "2时" "3时" "4时"
     * 90       → "1.5时"     ← 两小时以内、且小时数正好一位小数的，用小数
     * 其余     → "2时15分" "1时45分"
     * ```
     *
     * 原站那排「持续」档位就是这套写法的产物：
     * `15分 / 30分 / 45分 / 1时 / 1.5时 / 2时 / 3时 / 4时`。
     *
     * ⚠️ **不是**「15分钟 / 1小时 / 1小时30分」那种写法 —— 原站从不这么写，别改回去。
     */
    fun formatDuration(minutes: Int): String {
        if (minutes <= 0) return ""
        if (minutes < 60) return "${minutes}分"
        if (minutes % 60 == 0) return "${minutes / 60}时"
        val hours = minutes / 60.0
        // 原站是 `Math.round(t*10)/10 === t` —— 只认「正好一位小数」，浮点误差要抹掉
        if (minutes < 120 && (hours * 10).roundToInt() / 10.0 == hours) return "${hours}时"
        return "${minutes / 60}时${minutes % 60}分"
    }

    /**
     * **时间轴任务块**上的时间（原站 `.tg-btime`）。
     * 有起止时间显示 `"09:00-10:30"`，只有开始时间显示 `"09:00"`。
     *
     * ⚠️ 原站这里写的是 `yn(w)+"-"+yn(j)` —— **普通连字符、两边没有空格**。
     * 跟列表行那个 [chipTimeLabel] 不是一回事，别合并。
     */
    fun timeLabel(startHHmm: String?, endHHmm: String?, durationMinutes: Int?): String {
        val start = parseHHmm(startHHmm) ?: return ""
        // 结束时间优先取 endTime，没有就用 开始 + 时长 算，再没有就只显示开始时间
        val end = parseHHmm(endHHmm)
            ?: durationMinutes?.let { start + it }
            ?: return formatHHmm(start)
        return "${formatHHmm(start)}-${formatHHmm(end)}"
    }

    /**
     * **列表行**上的时间小标签 `.time-chip`（日历页任务行、长期计划行）。
     * **逐字对齐原站的 `ny()`**：
     *
     * ```
     * 没有时间   → "随时"
     * 有结束时间 → "11:02 – 11:17"    ← 空格 + 短横(–) + 空格
     * 只有开始   → "11:02"
     * ```
     *
     * 注意中间那个是 **en dash（U+2013）**，不是普通连字符，
     * 跟 [timeLabel] 的写法**故意不一样** —— 原站这两个地方用的就是两套。
     */
    fun chipTimeLabel(startHHmm: String?, endHHmm: String?, durationMinutes: Int?): String {
        val start = parseHHmm(startHHmm) ?: return "随时"
        val end = parseHHmm(endHHmm)
            ?: durationMinutes?.let { start + it }
            ?: return formatHHmm(start)
        return "${formatHHmm(start)} – ${formatHHmm(end)}"
    }
}
