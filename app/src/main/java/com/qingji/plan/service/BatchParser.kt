package com.qingji.plan.service

import com.qingji.plan.data.model.Cadence
import com.qingji.plan.data.model.Categories
import com.qingji.plan.data.model.Category
import com.qingji.plan.data.model.Priority
import com.qingji.plan.util.TimeUtils
import java.time.LocalDate

/**
 * 批量录入的一行解析结果。
 */
data class ParsedLine(
    /** 原始那一行，出错时好回显 */
    val raw: String,
    /** 剩下的就是标题 */
    val title: String,
    /** 没写日期就用默认日期 */
    val date: LocalDate,
    val time: String? = null,
    val durationMinutes: Int? = null,
    val category: String = "other",
    val priority: Priority = Priority.MID,
    /** 非空表示这是个长期计划，不是普通任务 */
    val cadence: Cadence? = null,
    val weekdays: List<Int> = emptyList(),
    val dayOfMonth: Int? = null,
    /** 教室，课程用 */
    val room: String? = null,
    /** 解析不了的原因，非空表示这行有问题 */
    val error: String? = null,
) {
    val isPlan: Boolean get() = cadence != null
    val isOk: Boolean get() = error == null && title.isNotBlank()
}

/**
 * 批量录入解析器。
 *
 * 认这些东西（原站设置页里列出的语法）：
 * ```
 * 9/1 9:00 开会 @工作        → 9月1日 09:00，分类=工作
 * 每天 背单词 20 个            → 每天重复的长期计划
 * 每周五 写周报                → 每周五重复
 * 周一 14:30 健身 重要         → 高优先级，带「重要」标记
 * 30分钟 / 1小时               → 时长
 * 明天 / 今天 / 后天 / 周X      → 日期
 * ```
 *
 * **按行独立解析**：一行解析失败不影响其它行，失败的那行标上 error 回显给用户。
 */
object BatchParser {

    // ---------- 各种模式 ----------

    /** `9:00` / `14:30` */
    private val TIME = Regex("(?<!\\d)(\\d{1,2}):(\\d{2})(?!\\d)")

    /** `30分钟` `45 分钟` */
    private val DURATION_MIN = Regex("(\\d{1,3})\\s*分钟")

    /** `1小时` `1.5小时` `2 小时` */
    private val DURATION_HOUR = Regex("(\\d{1,2}(?:\\.\\d)?)\\s*(?:个)?小时")

    /** `9/1` `08/25` */
    private val DATE_SLASH = Regex("(?<!\\d)(\\d{1,2})/(\\d{1,2})(?!\\d)")

    /** `8月25日` `8月25号` */
    private val DATE_CN = Regex("(\\d{1,2})\\s*月\\s*(\\d{1,2})\\s*[日号]")

    /** `周一`…`周日` / `周天` */
    private val WEEKDAY = Regex("周([一二三四五六日天])")

    /** `@工作` `@学习` */
    private val CATEGORY = Regex("@([^\\s@]+)")

    /** `每月15日` `每月15号` */
    private val MONTHLY = Regex("每月\\s*(\\d{1,2})\\s*[日号]")

    /** 单字 → 周几索引（原站约定 0 = 周一） */
    private val WEEKDAY_INDEX = mapOf(
        "一" to 0, "二" to 1, "三" to 2, "四" to 3, "五" to 4, "六" to 5, "日" to 6, "天" to 6,
    )

    /**
     * 解析整段文本。
     *
     * @param text 用户粘贴的多行文本
     * @param defaultDate 没写日期的行用哪一天
     * @param customs 自定义分类（用来认 `@自定义分类名`）
     */
    fun parse(
        text: String,
        defaultDate: LocalDate,
        customs: List<Category> = emptyList(),
    ): List<ParsedLine> = text.lines()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .map { parseLine(it, defaultDate, customs) }

    /**
     * 解析一行。
     *
     * 做法：把认出来的片段一个个从行里**抠掉**，剩下的是标题。
     */
    fun parseLine(raw: String, defaultDate: LocalDate, customs: List<Category> = emptyList()): ParsedLine {
        var work = raw

        // 1. 重要
        var priority = Priority.MID
        if (work.contains("重要")) {
            priority = Priority.HIGH
            work = work.replace("重要", " ")
        }

        // 2. @分类
        var category = "other"
        CATEGORY.find(work)?.let { m ->
            resolveCategory(m.groupValues[1], customs)?.let { category = it }
            work = work.replace(m.value, " ")
        }

        // 3. 时长（分钟优先，因为「1小时30分」这种情况原站也没处理，保持简单）
        var duration: Int? = null
        DURATION_HOUR.find(work)?.let { m ->
            duration = (m.groupValues[1].toDoubleOrNull()?.times(60))?.toInt()
            work = work.replace(m.value, " ")
        }
        if (duration == null) {
            DURATION_MIN.find(work)?.let { m ->
                duration = m.groupValues[1].toIntOrNull()
                work = work.replace(m.value, " ")
            }
        }

        // 4. 重复（要放在日期之前，因为「每周五」里也有个「周五」）
        var cadence: Cadence? = null
        var weekdays = listOf<Int>()
        var dayOfMonth: Int? = null

        MONTHLY.find(work)?.let { m ->
            cadence = Cadence.MONTHLY
            dayOfMonth = m.groupValues[1].toIntOrNull()?.coerceIn(1, 31)
            work = work.replace(m.value, " ")
        }
        if (cadence == null && work.contains("每天")) {
            cadence = Cadence.DAILY
            work = work.replace("每天", " ")
        }
        if (cadence == null) {
            // 「每周五」或「每周一三五」
            val weekly = Regex("每周([一二三四五六日天]+)").find(work)
            if (weekly != null) {
                cadence = Cadence.WEEKLY
                weekdays = weekly.groupValues[1].mapNotNull { WEEKDAY_INDEX[it.toString()] }
                    .distinct().sorted()
                work = work.replace(weekly.value, " ")
            }
        }

        // 5. 时间
        var time: String? = null
        TIME.find(work)?.let { m ->
            val h = m.groupValues[1].toIntOrNull()
            val min = m.groupValues[2].toIntOrNull()
            if (h != null && min != null && h in 0..23 && min in 0..59) {
                time = "%02d:%02d".format(h, min)
                work = work.replace(m.value, " ")
            }
        }

        // 6. 日期
        var date = defaultDate
        var dateFound = false
        DATE_CN.find(work)?.let { m ->
            val mo = m.groupValues[1].toIntOrNull()
            val d = m.groupValues[2].toIntOrNull()
            dateFrom(mo, d)?.let { date = it; dateFound = true }
            work = work.replace(m.value, " ")
        }
        if (!dateFound) {
            DATE_SLASH.find(work)?.let { m ->
                val mo = m.groupValues[1].toIntOrNull()
                val d = m.groupValues[2].toIntOrNull()
                dateFrom(mo, d)?.let { date = it; dateFound = true }
                work = work.replace(m.value, " ")
            }
        }
        if (!dateFound) {
            when {
                work.contains("后天") -> { date = defaultDate.plusDays(2); dateFound = true; work = work.replace("后天", " ") }
                work.contains("明天") -> { date = defaultDate.plusDays(1); dateFound = true; work = work.replace("明天", " ") }
                work.contains("今天") -> { date = defaultDate; dateFound = true; work = work.replace("今天", " ") }
            }
        }
        // 单独的「周一」这种，当天计划
        if (!dateFound && cadence == null) {
            WEEKDAY.find(work)?.let { m ->
                val idx = WEEKDAY_INDEX[m.groupValues[1]]
                if (idx != null) {
                    date = nextWeekday(defaultDate, idx)
                    work = work.replace(m.value, " ")
                }
            }
        }

        // 7. 剩下的就是标题
        val title = work.replace(Regex("\\s+"), " ").trim().trim('-', '·', '、').trim()

        val error = when {
            title.isBlank() -> "没有认出任何内容"
            else -> null
        }

        return ParsedLine(
            raw = raw,
            title = title,
            date = date,
            time = time,
            durationMinutes = duration?.let { TimeUtils.clampDuration(it) },
            category = category,
            priority = priority,
            cadence = cadence,
            weekdays = weekdays,
            dayOfMonth = dayOfMonth,
            error = error,
        )
    }

    /**
     * 从今天往后找最近的某个周几。
     */
    fun nextWeekday(today: LocalDate, weekdayIndex: Int): LocalDate {
        val target = weekdayIndex + 1              // java.time：1 = 周一
        val current = today.dayOfWeek.value
        var delta = target - current
        if (delta < 0) delta += 7
        return today.plusDays(delta.toLong())
    }

    private fun dateFrom(month: Int?, day: Int?): LocalDate? {
        if (month == null || day == null) return null
        if (month !in 1..12 || day !in 1..31) return null
        val year = LocalDate.now().year
        return runCatching { LocalDate.of(year, month, day) }.getOrNull()
    }

    /**
     * 把 `@工作` 里的名字解析成分类 id。
     * 先找内置分类名，再找自定义分类名。认不出来就保持「其他」。
     */
    private fun resolveCategory(name: String, customs: List<Category>): String? {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return null
        Categories.builtIn.firstOrNull { it.label == trimmed || it.id == trimmed }?.let { return it.id }
        customs.firstOrNull { it.label == trimmed || it.id == trimmed }?.let { return it.id }
        return null
    }

    /**
     * 给「复制 AI 提示词」按钮用的文本。
     * 复制走粘给别的 AI，让它帮你按格式生成批量内容。
     */
    fun aiHelperPrompt(defaultDate: LocalDate): String = """
        请把下面的内容整理成「鲸记」App 能识别的批量录入格式，每行一条，只输出整理后的文本，不要解释。
        支持的写法：
        - 时间：HH:mm，例如 9:00
        - 时长：30分钟 / 1小时
        - 日期：9/1 或 8月25日，也可以写 今天 / 明天 / 后天 / 周X
        - 分类：@工作 @学习 @生活 @健康 @其他
        - 重要：行尾加「重要」
        - 重复：每天 / 每周五 / 每月15日
        示例：
        9/1 9:00 开会 @工作
        每天 背单词 20 个
        每周五 写周报 @工作
        周一 14:30 健身 重要
        默认日期是 $defaultDate，没写日期的行会归到这一天。
    """.trimIndent()
}
