package com.qingji.plan.util

import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * 日期工具。原站用 `YYYY-MM-DD` 字符串当任务的日期主键，这里保持一致——
 * 备份文件和原站对得上，也方便直接排序比较。
 */
object DateUtils {

    private val KEY_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    private val WEEKDAY_LABELS = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

    /** LocalDate → `"2026-09-26"` */
    fun toKey(date: LocalDate): String = date.format(KEY_FORMATTER)

    /** `"2026-09-26"` → LocalDate；格式不对返回 null */
    fun fromKey(key: String?): LocalDate? {
        if (key.isNullOrBlank()) return null
        return runCatching { LocalDate.parse(key.trim(), KEY_FORMATTER) }.getOrNull()
    }

    /** 周几的中文标签，周一 = `"周一"` */
    fun weekdayLabel(date: LocalDate): String = WEEKDAY_LABELS[date.dayOfWeek.value - 1]

    /**
     * 这一天所在周的**周一**。
     *
     * 课表是以周一为第一列的（原站也一样），所以不能直接用 `with(DayOfWeek.MONDAY)` 之外
     * 的任何「周首日」约定——ISO 的周一就是我们要的。
     */
    fun mondayOf(date: LocalDate): LocalDate =
        date.minusDays((date.dayOfWeek.value - 1).toLong())

    /** 这一天所在周的 7 天，从周一到周日 */
    fun weekOf(date: LocalDate): List<LocalDate> {
        val monday = mondayOf(date)
        return (0..6).map { monday.plusDays(it.toLong()) }
    }

    /** `"9月26日 周六"` */
    fun dateTitle(date: LocalDate): String = "${date.monthValue}月${date.dayOfMonth}日 ${weekdayLabel(date)}"

    /** `"9月26日 周六（今天）"`；不是今天时不带括号后缀 */
    fun dateTitleWithToday(date: LocalDate, today: LocalDate): String =
        if (date == today) "${dateTitle(date)}（今天）" else dateTitle(date)

    /** `"9月26日"` */
    fun monthDayTitle(date: LocalDate): String = "${date.monthValue}月${date.dayOfMonth}日"

    /**
     * 时间轴上方的问候语。原站按当前小时切换。
     * 09:01–10:00 → 上午好，其余按早/中/下午/晚上分段。
     */
    fun greeting(hour: Int): String = when (hour) {
        in 5..8 -> "早上好"
        in 9..10 -> "上午好"
        in 11..12 -> "中午好"
        in 13..17 -> "下午好"
        else -> "晚上好"
    }

    /** 顶部大标题：`"晚上好，今天辛苦了"` */
    fun greetingTitle(hour: Int): String = "${greeting(hour)}，今天辛苦了"

    /** 顶部副标题：`"9月26日 周六 · 今天 · 6 项计划"` */
    fun todaySubtitle(date: LocalDate, today: LocalDate, taskCount: Int): String {
        val parts = mutableListOf(dateTitle(date))
        if (date == today) parts.add("今天")
        parts.add("$taskCount 项计划")
        return parts.joinToString(" · ")
    }

    /**
     * 某个月在日历上要显示的 42 天（6 行 × 7 列）。
     * 从该月第一天所在的周一开始，共 42 天。
     */
    fun monthGrid(year: Int, month: Int): List<LocalDate> {
        val first = LocalDate.of(year, month, 1)
        val start = first.minusDays((first.dayOfWeek.value - 1).toLong())
        return (0 until 42).map { start.plusDays(it.toLong()) }
    }

    /** 该月第一天 */
    fun firstDayOf(year: Int, month: Int): LocalDate = LocalDate.of(year, month, 1)

    /** 该月最后一天 */
    fun lastDayOf(year: Int, month: Int): LocalDate =
        LocalDate.of(year, month, 1).withDayOfMonth(LocalDate.of(year, month, 1).lengthOfMonth())
}
