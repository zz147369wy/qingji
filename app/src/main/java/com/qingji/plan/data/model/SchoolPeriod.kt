package com.qingji.plan.data.model

import com.qingji.plan.util.TimeUtils
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 一节课的「作息」——第几节、从几点到几点。
 *
 * 学校的课表不是按整点的，是按「节次」排的：第 1 节 8:30–9:55、第 2 节 10:15–11:40……
 * 每节长度也不一定是 60 分钟（用户学校是 85 分钟）。所以课表左边那根时间轴不能是
 * 通用的「08:00 / 09:00 / 10:00」，得按用户自己的作息来。
 *
 * @param index 节次号，从 1 开始
 * @param start `HH:mm`
 * @param end `HH:mm`
 */
@Serializable
data class SchoolPeriod(
    val index: Int,
    val start: String,
    val end: String,
) {
    val startMinute: Int get() = TimeUtils.parseHHmm(start) ?: 0
    val endMinute: Int get() = TimeUtils.parseHHmm(end) ?: 0

    /** 这一节多少分钟。作息表没写对（结束早于开始）时返回 0 */
    val durationMinutes: Int get() = (endMinute - startMinute).coerceAtLeast(0)

    /** 「8:30 / 9:55」这种两行显示用的，不带前导零 */
    val startLabel: String get() = trimLeadingZero(start)
    val endLabel: String get() = trimLeadingZero(end)

    private fun trimLeadingZero(hhmm: String): String =
        if (hhmm.length >= 5 && hhmm[0] == '0') hhmm.substring(1) else hhmm
}

/**
 * 作息表。整张表一起编辑、一起存，所以放在一个对象里。
 */
object PeriodSchedules {

    /**
     * 默认作息。
     *
     * **来自用户提供的视频**（`课表时间/时间.mp4`），是他学校实际用的那套：
     * 一天 6 节，每节 85 分钟。
     */
    val DEFAULT: List<SchoolPeriod> = listOf(
        SchoolPeriod(1, "08:30", "09:55"),
        SchoolPeriod(2, "10:15", "11:40"),
        SchoolPeriod(3, "13:30", "14:55"),
        SchoolPeriod(4, "15:05", "16:30"),
        SchoolPeriod(5, "18:00", "19:25"),
        SchoolPeriod(6, "19:45", "21:10"),
    )

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /**
     * 解析存进设置的 JSON。**任何一步不对都退回默认作息**——
     * 宁可显示默认的，也不能因为设置字段坏了就让整个课表打不开。
     */
    fun parse(raw: String?): List<SchoolPeriod> {
        if (raw.isNullOrBlank()) return DEFAULT
        val list = runCatching { json.decodeFromString<List<SchoolPeriod>>(raw) }
            .getOrNull()
            ?.takeIf { it.isNotEmpty() }
            ?: return DEFAULT
        return list.takeIf { it.all(::isSane) } ?: DEFAULT
    }

    fun encode(periods: List<SchoolPeriod>): String = json.encodeToString(periods)

    /** 起止时间得是合法 `HH:mm`，而且结束要晚于开始 */
    fun isSane(period: SchoolPeriod): Boolean =
        TimeUtils.parseHHmm(period.start) != null &&
            TimeUtils.parseHHmm(period.end) != null &&
            period.durationMinutes > 0

    /** 从「8:30」这种用户手输的写法规整成 `08:30`。不合法返回 null */
    fun normalizeTime(input: String): String? {
        val t = input.trim().replace("：", ":")
        val parts = t.split(":")
        if (parts.size != 2) return null
        val h = parts[0].trim().toIntOrNull() ?: return null
        val m = parts[1].trim().toIntOrNull() ?: return null
        if (h !in 0..23 || m !in 0..59) return null
        return (if (h < 10) "0$h" else "$h") + ":" + (if (m < 10) "0$m" else "$m")
    }
}
