package com.qingji.plan.syscal

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.CalendarContract
import android.util.Log
import androidx.core.content.ContextCompat
import com.qingji.plan.data.db.entity.CourseEntity
import com.qingji.plan.data.db.entity.TaskEntity
import com.qingji.plan.util.TimeUtils
import java.time.LocalDate
import java.time.ZoneId

/** 用户可选的日历 */
data class WritableCalendar(
    val id: Long,
    val name: String,
    val accountName: String?,
)

/**
 * 把 App 里的日程写进手机自带的日历。
 *
 * ## 三条硬事实（调研在真机上实测出来的）
 *
 * 1. **普通 App 不能创建自己的日历** —— 写 `account_name` 会被 provider 拒
 *    （`IllegalArgumentException`），那是 sync adapter 才有的权限。
 *    所以只能在用户**已有的日历**里挑一个写。一个都没有就无处可写。
 *
 * 2. **标记自己写的事件用官方字段** `customAppPackage` / `customAppUri`。
 *    `sync_data1` / `_sync_id` / `DELETED` 都属于 sync adapter 专属，普通 App 写会被拒。
 *
 * 3. **递归课用单条 RRULE 更合适** —— 每周三的高数插一条带 `RRULE:FREQ=WEEKLY` 的事件，
 *    系统会自己展开成 instances。比每周插一条要干净得多（改一次全改）。
 *
 * ## 为什么不用数据库存 eventId
 *
 * 靠 `customAppUri = qingji://task/<uid>` 反查就行 —— 查 `customAppPackage = 'com.qingji.plan'`
 * 拿到全部自己的事件，再按 uri 里的 uid 对号入座。
 * **这样不用给 TaskEntity/CourseEntity 加字段，避免了 Room 1→2 迁移的风险。**
 */
class SystemCalendarSync(private val context: Context) {

    private val resolver get() = context.contentResolver

    // ---------- 权限 ----------

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) ==
            PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR) ==
            PackageManager.PERMISSION_GRANTED

    // ---------- 日历列表 ----------

    /**
     * 列出**可写**的日历。
     *
     * 筛选条件 `calendar_access_level >= 500`（500 = CAL_ACCESS_CONTRIBUTOR）。
     * 用户一个可写日历都没有时返回空列表，界面要引导他去系统日历里建一个。
     */
    fun writableCalendars(): List<WritableCalendar> {
        if (!hasPermission()) return emptyList()
        val result = mutableListOf<WritableCalendar>()
        runCatching {
            resolver.query(
                CalendarContract.Calendars.CONTENT_URI,
                arrayOf(
                    CalendarContract.Calendars._ID,
                    CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
                    CalendarContract.Calendars.ACCOUNT_NAME,
                ),
                "${CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL} >= ?",
                arrayOf(CAL_ACCESS_CONTRIBUTOR.toString()),
                null,
            )?.use { c ->
                while (c.moveToNext()) {
                    result.add(
                        WritableCalendar(
                            id = c.getLong(0),
                            name = c.getString(1) ?: "未命名日历",
                            accountName = c.getString(2),
                        )
                    )
                }
            }
        }.onFailure { Log.w(TAG, "查日历失败：${it.message}") }
        return result
    }

    /**
     * 校验一个日历 id 是不是还存在。
     *
     * **恢复备份/换机后必须校验** —— 备份里的 calendarId 指向的是老手机上的日历，
     * 换台手机那个 id 要么不存在、要么指向别人的日历。
     */
    fun isValidCalendar(calendarId: Long): Boolean =
        writableCalendars().any { it.id == calendarId }

    // ---------- 写入 ----------

    /**
     * 把某一天的任务写进系统日历。
     *
     * 只写**有时间**的任务（没时间的写进去没意义）。
     * 返回成功写入的条数。
     */
    fun syncTasks(calendarId: Long, tasks: List<TaskEntity>): Int {
        if (!hasPermission() || !isValidCalendar(calendarId)) return 0
        var count = 0
        tasks.filter { it.deletedAt == null && it.hasTime }.forEach { task ->
            runCatching {
                val date = com.qingji.plan.util.DateUtils.fromKey(task.date) ?: return@runCatching
                val startMinute = TimeUtils.parseHHmm(task.time) ?: return@runCatching
                val duration = TimeUtils.clampDuration(task.durationMinutes ?: TimeUtils.MIN_DURATION)

                upsertEvent(
                    calendarId = calendarId,
                    uid = task.uid,
                    title = task.title,
                    date = date,
                    startMinute = startMinute,
                    durationMinutes = duration,
                    description = "来自「鲸记」",
                )
                count++
            }.onFailure { Log.w(TAG, "写任务 ${task.title} 失败：${it.message}") }
        }
        return count
    }

    /**
     * 把课程写进系统日历。
     *
     * 课程是「每周固定」的，所以用**一条 RRULE 递归事件**，
     * 而不是每周插一条 —— 改一次全改，删一次全删。
     *
     * 起点取「从今天起的下一个该上课的日子」。
     */
    fun syncCourses(calendarId: Long, courses: List<CourseEntity>, today: LocalDate): Int {
        if (!hasPermission() || !isValidCalendar(calendarId)) return 0
        var count = 0
        courses.filter { it.deletedAt == null && it.hasTime }.forEach { course ->
            runCatching {
                val startMinute = TimeUtils.parseHHmm(course.time) ?: return@runCatching
                // 找到从今天起的第一个该上课的那天
                val first = firstOccurrence(today, course.weekday)

                upsertEvent(
                    calendarId = calendarId,
                    uid = course.uid,
                    title = course.title,
                    date = first,
                    startMinute = startMinute,
                    durationMinutes = course.durationMinutes,
                    description = buildString {
                        append("来自「鲸记」的课程")
                        course.room?.let { append(" · ").append(it) }
                    },
                    // 每周重复。UNTIL 留空表示一直重复
                    rrule = "FREQ=WEEKLY;BYDAY=${rruleDay(course.weekday)}",
                )
                count++
            }.onFailure { Log.w(TAG, "写课程 ${course.title} 失败：${it.message}") }
        }
        return count
    }

    /**
     * 插入或更新一条事件。
     *
     * 靠 `customAppUri` 里的 uid 找回自己以前写的那条，有就改、没有就插，
     * 所以反复同步不会产生重复事件。
     */
    private fun upsertEvent(
        calendarId: Long,
        uid: String,
        title: String,
        date: LocalDate,
        startMinute: Int,
        durationMinutes: Int,
        description: String,
        rrule: String? = null,
    ) {
        val zone = ZoneId.systemDefault()
        val start = date.atStartOfDay(zone)
            .plusMinutes(startMinute.toLong())
        val startMillis = start.toInstant().toEpochMilli()
        val endMillis = start.plusMinutes(durationMinutes.toLong()).toInstant().toEpochMilli()

        val values = ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, calendarId)
            put(CalendarContract.Events.TITLE, title)
            put(CalendarContract.Events.DESCRIPTION, description)
            put(CalendarContract.Events.DTSTART, startMillis)
            put(CalendarContract.Events.DTEND, endMillis)
            put(CalendarContract.Events.EVENT_TIMEZONE, zone.id)
            // ★ 官方给第三方 App 用的「这条是我写的」标记
            put(CalendarContract.Events.CUSTOM_APP_PACKAGE, context.packageName)
            put(CalendarContract.Events.CUSTOM_APP_URI, "$URI_SCHEME://${kindOf(rrule)}/$uid")
            if (rrule != null) put(CalendarContract.Events.RRULE, rrule)
        }

        val existing = findEventIdByUid(uid)
        if (existing != null) {
            resolver.update(
                ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, existing),
                values.apply { remove(CalendarContract.Events.CALENDAR_ID) },
                null, null,
            )
        } else {
            resolver.insert(CalendarContract.Events.CONTENT_URI, values)
        }
    }

    /**
     * 按 uid 找回我们写的那条事件的 id。
     *
     * 查 `customAppPackage = 自己` 拿到全部自己的事件，再按 uri 末段匹配 uid。
     */
    fun findEventIdByUid(uid: String): Long? {
        if (!hasPermission()) return null
        return runCatching {
            resolver.query(
                CalendarContract.Events.CONTENT_URI,
                arrayOf(
                    CalendarContract.Events._ID,
                    CalendarContract.Events.CUSTOM_APP_URI,
                ),
                "${CalendarContract.Events.CUSTOM_APP_PACKAGE} = ?",
                arrayOf(context.packageName),
                null,
            )?.use { c ->
                while (c.moveToNext()) {
                    val uri = c.getString(1)
                    if (uri != null && uri.endsWith("/$uid")) return@use c.getLong(0)
                }
                null
            }
        }.getOrNull()
    }

    /**
     * 删掉某条 App 写进去的事件。
     */
    fun deleteEventByUid(uid: String): Boolean {
        val id = findEventIdByUid(uid) ?: return false
        return runCatching {
            resolver.delete(
                ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, id),
                null, null,
            ) > 0
        }.getOrDefault(false)
    }

    /**
     * 清空所有由本 App 写进系统日历的事件。
     *
     * 关闭同步开关时用，或者用户想「重新来一遍」时用。
     */
    fun deleteAllOurEvents(): Int {
        if (!hasPermission()) return 0
        return runCatching {
            resolver.delete(
                CalendarContract.Events.CONTENT_URI,
                "${CalendarContract.Events.CUSTOM_APP_PACKAGE} = ?",
                arrayOf(context.packageName),
            )
        }.getOrDefault(0)
    }

    // ---------- 工具 ----------

    /** 从今天起第一个该上课的日子 */
    private fun firstOccurrence(today: LocalDate, weekday: Int): LocalDate {
        val target = weekday + 1   // java.time：1 = 周一
        var delta = target - today.dayOfWeek.value
        if (delta < 0) delta += 7
        return today.plusDays(delta.toLong())
    }

    /** 原站约定 0 = 周一 → RRULE 的 BYDAY */
    private fun rruleDay(weekday: Int): String =
        listOf("MO", "TU", "WE", "TH", "FR", "SA", "SU").getOrElse(weekday) { "MO" }

    private fun kindOf(rrule: String?): String = if (rrule == null) "task" else "course"

    companion object {
        private const val TAG = "SystemCalendarSync"

        /** CalendarContract 里 CAL_ACCESS_CONTRIBUTOR = 500，可写的最低级别 */
        const val CAL_ACCESS_CONTRIBUTOR = 500

        const val URI_SCHEME = "qingji"
    }
}
