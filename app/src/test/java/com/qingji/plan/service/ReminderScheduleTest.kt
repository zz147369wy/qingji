package com.qingji.plan.service

import com.qingji.plan.data.db.entity.CourseEntity
import com.qingji.plan.data.db.entity.TaskEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * 提醒调度的测试。
 *
 * 全是时间边界，所以把「现在」做成参数，测起来能精确控制。
 */
class ReminderScheduleTest {

    /** 2026-09-27（周日）下午 2 点 */
    private val now = LocalDateTime.of(2026, 9, 27, 14, 0)
    private val today = LocalDate.of(2026, 9, 27)
    private val zone: ZoneId = ZoneId.systemDefault()
    private val stamp = 1_800_000_000_000L

    private fun task(
        title: String,
        date: LocalDate = today,
        time: String? = "15:00",
        done: Boolean = false,
        deletedAt: Long? = null,
    ) = TaskEntity(
        uid = "u_$title",
        date = com.qingji.plan.util.DateUtils.toKey(date),
        title = title,
        time = time,
        durationMinutes = if (time != null) 30 else null,
        status = if (done) "done" else "todo",
        createdAt = stamp, updatedAt = stamp, deletedAt = deletedAt,
    )

    private fun course(title: String, weekday: Int, time: String = "08:00") = CourseEntity(
        uid = "c_$title", title = title, weekday = weekday, time = time,
        durationMinutes = 45, createdAt = stamp, updatedAt = stamp,
    )

    private fun run(
        tasks: List<TaskEntity> = emptyList(),
        courses: List<CourseEntity> = emptyList(),
        settleTime: String = "21:00",
        settleEnabled: Boolean = true,
        horizon: Int = 7,
    ) = ReminderSchedule.upcoming(now, tasks, courses, settleTime, settleEnabled, zone, horizon)

    // ---------- 任务提醒 ----------

    @Test
    fun `今天稍后的任务会排上提醒`() {
        val list = run(tasks = listOf(task("开会", time = "15:00")))
            .filter { it.kind == Reminder.Kind.TASK }
        assertEquals(1, list.size)
        assertEquals("开会", list[0].title)
        assertEquals("15:00-15:30 开始", list[0].body)
    }

    @Test
    fun `已经过去的任务不排提醒`() {
        // 现在是 14:00，13:00 的任务已经过了
        val list = run(tasks = listOf(task("早会", time = "13:00")))
            .filter { it.kind == Reminder.Kind.TASK }
        assertTrue("过去的任务不该提醒", list.isEmpty())
    }

    @Test
    fun `已完成的任务不提醒`() {
        val list = run(tasks = listOf(task("做完了", time = "15:00", done = true)))
            .filter { it.kind == Reminder.Kind.TASK }
        assertTrue(list.isEmpty())
    }

    @Test
    fun `已删除的任务不提醒`() {
        val list = run(tasks = listOf(task("删掉的", time = "15:00", deletedAt = stamp)))
            .filter { it.kind == Reminder.Kind.TASK }
        assertTrue(list.isEmpty())
    }

    @Test
    fun `没有时间的任务不提醒——它躺在随时区`() {
        val list = run(tasks = listOf(task("买菜", time = null)))
            .filter { it.kind == Reminder.Kind.TASK }
        assertTrue(list.isEmpty())
    }

    @Test
    fun `明天的任务会排上`() {
        val list = run(tasks = listOf(task("明天开会", date = today.plusDays(1), time = "10:00")))
            .filter { it.kind == Reminder.Kind.TASK }
        assertEquals(1, list.size)
    }

    @Test
    fun `超出排期范围的任务不排`() {
        val far = today.plusDays(30)
        val list = run(tasks = listOf(task("很久以后", date = far, time = "10:00")), horizon = 7)
            .filter { it.kind == Reminder.Kind.TASK }
        assertTrue(list.isEmpty())
    }

    // ---------- 课程提醒（每周展开）----------

    @Test
    fun `课程会按周展开到排期范围内的每一天`() {
        // 2026-09-27 是周日，weekday 6。往后 7 天里只有 9/27 那一天是周日
        val list = run(courses = listOf(course("答疑", weekday = 6, time = "16:00")), horizon = 7)
            .filter { it.kind == Reminder.Kind.COURSE }
        assertEquals("一周只上一次，应该只排 1 条", 1, list.size)
    }

    @Test
    fun `课程连续两周会排两条`() {
        val list = run(courses = listOf(course("答疑", weekday = 6, time = "16:00")), horizon = 14)
            .filter { it.kind == Reminder.Kind.COURSE }
        assertEquals(2, list.size)
    }

    @Test
    fun `今天的课如果已经过了就不排`() {
        // 周日 08:00 的课，现在 14:00
        val list = run(courses = listOf(course("早课", weekday = 6, time = "08:00")), horizon = 1)
            .filter { it.kind == Reminder.Kind.COURSE }
        assertTrue(list.isEmpty())
    }

    @Test
    fun `课程的提醒带上教室`() {
        val c = course("高等数学", weekday = 0, time = "10:00").copy(room = "A301")
        val list = run(courses = listOf(c)).filter { it.kind == Reminder.Kind.COURSE }
        assertTrue(list[0].body.contains("A301"))
    }

    @Test
    fun `没排固定时间的课不提醒`() {
        val list = run(courses = listOf(course("答疑", 0).copy(time = null)))
            .filter { it.kind == Reminder.Kind.COURSE }
        assertTrue(list.isEmpty())
    }

    // ---------- 每日结算 ----------

    @Test
    fun `每日结算每天一条——含今天共七天`() {
        val list = run(settleTime = "21:00", horizon = 7).filter { it.kind == Reminder.Kind.SETTLE }
        // horizon=7 表示「含今天共 7 天」，所以是 7 条
        assertEquals(7, list.size)
    }

    @Test
    fun `结算时间已经过了就从明天开始`() {
        // 现在 14:00，结算时间改成 13:00
        val list = run(settleTime = "13:00", horizon = 3).filter { it.kind == Reminder.Kind.SETTLE }
        assertEquals("今天那条已经过了，只排明天和后天", 2, list.size)
    }

    @Test
    fun `关掉结算提醒就一条都不排`() {
        val list = run(settleEnabled = false).filter { it.kind == Reminder.Kind.SETTLE }
        assertTrue(list.isEmpty())
    }

    @Test
    fun `结算时间非法时跳过而不是崩`() {
        val list = run(settleTime = "不是时间").filter { it.kind == Reminder.Kind.SETTLE }
        assertTrue(list.isEmpty())
    }

    // ---------- 整体 ----------

    @Test
    fun `所有提醒按时间从早到晚排`() {
        val list = run(
            tasks = listOf(task("晚上的事", time = "20:00"), task("下午的事", time = "15:00")),
            settleTime = "21:00",
        )
        val times = list.map { it.atMillis }
        assertEquals("应该按时间升序", times.sorted(), times)
    }

    @Test
    fun `每条提醒的 key 唯一`() {
        val list = run(
            tasks = listOf(task("A", time = "15:00"), task("B", time = "16:00")),
            courses = listOf(course("数学", 1)),
        )
        assertEquals(list.size, list.map { it.key }.toSet().size)
    }

    @Test
    fun `提醒的时刻一定在未来`() {
        val list = run(
            tasks = listOf(task("A", time = "15:00")),
            courses = listOf(course("数学", 1)),
        )
        val nowMillis = now.atZone(zone).toInstant().toEpochMilli()
        list.forEach {
            assertTrue("提醒 ${it.key} 的时刻不在未来", it.atMillis > nowMillis)
        }
    }

    @Test
    fun `只保留最近几条`() {
        val list = run(settleTime = "21:00", horizon = 30)
        val nearest = ReminderSchedule.takeNearest(list, 5)
        assertEquals(5, nearest.size)
        assertEquals(list.take(5).map { it.key }, nearest.map { it.key })
    }

    @Test
    fun `什么都没有时不崩`() {
        val list = run(settleEnabled = false)
        assertTrue(list.isEmpty())
    }

    // ---------- 课程提前 30 分钟（用户要求）----------

    @Test
    fun `课程的提醒是提前 30 分钟，不是上课那一刻`() {
        // 下周日 16:00 的课（今天已是周日 14:00，所以取下周日）
        val list = run(courses = listOf(course("答疑", weekday = 6, time = "16:00")), horizon = 8)
            .filter { it.kind == Reminder.Kind.COURSE }

        val first = list.first()
        val at = java.time.Instant.ofEpochMilli(first.atMillis).atZone(zone).toLocalDateTime()

        assertEquals("应该是 15:30 响，不是 16:00", java.time.LocalTime.of(15, 30), at.toLocalTime())
    }

    @Test
    fun `提前量来自常量，改常量就变`() {
        assertEquals(30, ReminderSchedule.COURSE_LEAD_MINUTES)
    }

    @Test
    fun `任务不提前，还是到点才响`() {
        // 今天 15:00 的任务
        val list = run(tasks = listOf(task("开会", time = "15:00")))
            .filter { it.kind == Reminder.Kind.TASK }

        val at = java.time.Instant.ofEpochMilli(list.first().atMillis).atZone(zone).toLocalDateTime()
        assertEquals(java.time.LocalTime.of(15, 0), at.toLocalTime())
    }

    @Test
    fun `提前那一刻已经过去了就不再排这条`() {
        // now 是周日 14:00；排一门周日 14:10 的课，提前 30 分钟 = 13:40，已经过了
        val list = run(courses = listOf(course("刚上过", weekday = 6, time = "14:10")), horizon = 1)
            .filter { it.kind == Reminder.Kind.COURSE }

        assertTrue("提前提醒的时刻已经过去，不该排一条会立刻炸的提醒", list.isEmpty())
    }

    @Test
    fun `课程的正文还是课名和上课时间，不是提醒时间`() {
        val c = course("高等数学", weekday = 0, time = "10:00").copy(room = "A301")
        val list = run(courses = listOf(c), horizon = 8).filter { it.kind == Reminder.Kind.COURSE }

        val first = list.first()
        assertEquals("高等数学", first.title)
        assertEquals("10:00 · A301", first.body)
    }
}
