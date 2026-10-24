package com.qingji.plan.data.db

import androidx.test.core.app.ApplicationProvider
import com.qingji.plan.data.db.entity.ChatLogEntity
import com.qingji.plan.data.db.entity.CourseEntity
import com.qingji.plan.data.db.entity.LongTermPlanEntity
import com.qingji.plan.data.db.entity.ReviewEntity
import com.qingji.plan.data.db.entity.SettingEntity
import com.qingji.plan.data.db.entity.TaskEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * 数据层测试。用内存数据库，不碰真机文件。
 *
 * 重点验证三件事：
 *   1. 六张表都能正常读写
 *   2. **软删除**的行为正确（删除后查不到，但数据还在）
 *   3. 长期计划「周几」这个 list 字段的转换没丢数据
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppDatabaseTest {

    private lateinit var db: AppDatabase

    private val now = 1_800_000_000_000L
    private val date = "2026-09-26"

    @Before
    fun setUp() {
        db = AppDatabase.inMemory(ApplicationProvider.getApplicationContext())
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun task(
        uid: String = "t1",
        title: String = "写周报",
        date: String = this.date,
        time: String? = "09:00",
        status: String = "todo",
        category: String = "work",
    ) = TaskEntity(
        uid = uid,
        date = date,
        title = title,
        category = category,
        time = time,
        endTime = time?.let { "10:00" },
        durationMinutes = 60,
        status = status,
        createdAt = now,
        updatedAt = now,
    )

    // ---------- 任务表 ----------

    @Test
    fun `任务能存能取`() = runTest {
        db.taskDao().insert(task())
        val loaded = db.taskDao().getByDate(date)
        assertEquals(1, loaded.size)
        assertEquals("写周报", loaded[0].title)
        assertEquals("09:00", loaded[0].time)
    }

    @Test
    fun `软删除之后查不到但数据还在`() = runTest {
        val id = db.taskDao().insert(task())
        assertEquals(1, db.taskDao().getByDate(date).size)

        db.taskDao().softDelete(id, now + 1)

        assertEquals("软删除后不该被查到", 0, db.taskDao().getByDate(date).size)
        assertEquals(0, db.taskDao().countAll())

        // 记录本身还在，只是打了标记
        val raw = db.taskDao().getById(id)
        assertNotNull("记录不该被真删", raw)
        assertEquals(now + 1, raw!!.deletedAt)
    }

    @Test
    fun `没有时间的任务排在前面`() = runTest {
        db.taskDao().insert(task(uid = "a", title = "开会", time = "15:00"))
        db.taskDao().insert(task(uid = "b", title = "买菜", time = null))
        db.taskDao().insert(task(uid = "c", title = "写周报", time = "09:00"))

        val list = db.taskDao().getByDate(date)
        assertEquals("买菜", list[0].title)
        assertEquals("写周报", list[1].title)
        assertEquals("开会", list[2].title)
    }

    @Test
    fun `按日期区间能查到整月的任务`() = runTest {
        db.taskDao().insert(task(uid = "a", date = "2026-09-01"))
        db.taskDao().insert(task(uid = "b", date = "2026-09-15"))
        db.taskDao().insert(task(uid = "c", date = "2026-09-30"))
        db.taskDao().insert(task(uid = "d", date = "2026-10-01"))

        val sept = db.taskDao().getBetween("2026-09-01", "2026-09-30")
        assertEquals(3, sept.size)
    }

    @Test
    fun `观察某天的任务能收到数据`() = runTest {
        db.taskDao().insert(task())
        val list = db.taskDao().observeByDate(date).first()
        assertEquals(1, list.size)
    }

    @Test
    fun `完成状态的计数正确`() = runTest {
        db.taskDao().insert(task(uid = "a", status = "done"))
        db.taskDao().insert(task(uid = "b", status = "todo"))
        db.taskDao().insert(task(uid = "c", status = "done"))

        assertEquals(3, db.taskDao().countAll())
        assertEquals(2, db.taskDao().countDone())
        assertEquals(3, db.taskDao().countOn(date))
    }

    @Test
    fun `按标题模糊查找任务`() = runTest {
        db.taskDao().insert(task(uid = "a", title = "写周报"))
        db.taskDao().insert(task(uid = "b", title = "开会讨论季度预算"))

        assertNotNull(db.taskDao().findByTitleOnDate(date, "周报"))
        assertNotNull(db.taskDao().findByTitleOnDate(date, "季度预算"))
        assertNull(db.taskDao().findByTitleOnDate(date, "不存在的任务"))
    }

    @Test
    fun `清理某天之后的全部任务`() = runTest {
        db.taskDao().insert(task(uid = "a", date = "2026-09-25"))
        db.taskDao().insert(task(uid = "b", date = "2026-09-26"))
        db.taskDao().insert(task(uid = "c", date = "2026-09-27"))

        val affected = db.taskDao().softDeleteFrom("2026-09-26", now + 1)

        assertEquals(2, affected)
        assertEquals(1, db.taskDao().countAll())
    }

    // ---------- 长期计划：list 字段转换 ----------

    @Test
    fun `长期计划的周几列表能原样存取`() = runTest {
        val plan = LongTermPlanEntity(
            uid = "p1",
            title = "健身",
            cadence = "weekly",
            weekdays = listOf(0, 2, 4),
            time = "19:00",
            durationMinutes = 60,
            category = "health",
            createdAt = now,
            updatedAt = now,
        )
        db.longTermPlanDao().insert(plan)

        val loaded = db.longTermPlanDao().getAll().single()
        assertEquals(listOf(0, 2, 4), loaded.weekdays)
    }

    @Test
    fun `空的周几列表也不会丢`() = runTest {
        val plan = LongTermPlanEntity(
            uid = "p2",
            title = "喝水",
            cadence = "daily",
            weekdays = emptyList(),
            createdAt = now,
            updatedAt = now,
        )
        db.longTermPlanDao().insert(plan)
        assertEquals(emptyList<Int>(), db.longTermPlanDao().getAll().single().weekdays)
    }

    @Test
    fun `每月计划按几号判断是否命中`() {
        val plan = LongTermPlanEntity(
            uid = "p3", title = "交房租", cadence = "monthly", dayOfMonth = 15,
            createdAt = now, updatedAt = now,
        )
        assertTrue(plan.matches(LocalDate.of(2026, 9, 15)))
        assertTrue(!plan.matches(LocalDate.of(2026, 9, 16)))
    }

    @Test
    fun `每周计划按周几判断是否命中`() {
        // 2026-09-26 是周六，对应 weekday 索引 5
        val plan = LongTermPlanEntity(
            uid = "p4", title = "健身", cadence = "weekly", weekdays = listOf(5),
            createdAt = now, updatedAt = now,
        )
        assertTrue(plan.matches(LocalDate.of(2026, 9, 26)))
        assertTrue(!plan.matches(LocalDate.of(2026, 9, 25)))
    }

    @Test
    fun `每天计划每天都能命中`() {
        val plan = LongTermPlanEntity(
            uid = "p5", title = "喝水", cadence = "daily",
            createdAt = now, updatedAt = now,
        )
        assertTrue(plan.matches(LocalDate.of(2026, 1, 1)))
        assertTrue(plan.matches(LocalDate.of(2026, 12, 31)))
    }

    // ---------- 课程表 ----------

    @Test
    fun `课程能存能取且默认四十五分钟`() = runTest {
        db.courseDao().insert(
            CourseEntity(
                uid = "c1", title = "高等数学", weekday = 0,
                time = "08:00", room = "A301", createdAt = now, updatedAt = now,
            )
        )
        val loaded = db.courseDao().getByWeekday(0).single()
        assertEquals("高等数学", loaded.title)
        assertEquals(45, loaded.durationMinutes)
        assertEquals("高等数学 ·A301", loaded.displayLabel)
    }

    @Test
    fun `没有教室时课程标签不带多余的符号`() {
        val c = CourseEntity(
            uid = "c2", title = "体育", weekday = 2, createdAt = now, updatedAt = now,
        )
        assertEquals("体育", c.displayLabel)
    }

    // ---------- 结算记录 ----------

    @Test
    fun `结算记录能存能取且能算平均分`() = runTest {
        db.reviewDao().insert(
            ReviewEntity(
                uid = "r1", date = "2026-09-25", score = 80,
                doneCount = 4, totalCount = 5, onTimeCount = 3,
                summary = "完成 4/5 项", feedback = "不错", improvement = "再早点",
                createdAt = now, updatedAt = now,
            )
        )
        db.reviewDao().insert(
            ReviewEntity(
                uid = "r2", date = "2026-09-26", score = 60,
                doneCount = 3, totalCount = 5, onTimeCount = 2,
                summary = "完成 3/5 项", feedback = "一般", improvement = "加油",
                createdAt = now, updatedAt = now,
            )
        )

        assertEquals(2, db.reviewDao().count())
        assertEquals(70.0, db.reviewDao().averageScore()!!, 0.001)
        assertNotNull(db.reviewDao().getByDate("2026-09-26"))
        assertEquals(2, db.reviewDao().getRecent(10).size)
        assertEquals("2026-09-26", db.reviewDao().getRecent(1).single().date)
    }

    // ---------- 设置表 ----------

    @Test
    fun `设置项能存能改能删`() = runTest {
        db.settingDao().put(SettingEntity("theme", "paper", now))
        assertEquals("paper", db.settingDao().get("theme")?.value)

        db.settingDao().put(SettingEntity("theme", "night", now + 1))
        assertEquals("night", db.settingDao().get("theme")?.value)

        db.settingDao().delete("theme")
        assertNull(db.settingDao().get("theme"))
    }

    @Test
    fun `设置项能整表取出和清空——备份恢复要用`() = runTest {
        db.settingDao().put(SettingEntity("theme", "paper", now))
        db.settingDao().put(SettingEntity("settleTime", "21:00", now))
        assertEquals(2, db.settingDao().getAll().size)

        db.settingDao().clear()
        assertEquals(0, db.settingDao().getAll().size)
    }

    // ---------- AI 对话：待确认状态 ----------

    @Test
    fun `待确认的 AI 操作能被查出来`() = runTest {
        assertNull("一开始不该有待确认操作", db.chatLogDao().getPending())

        db.chatLogDao().insert(
            ChatLogEntity(
                uid = "m1", role = "assistant", content = "我准备执行以下 1 项操作",
                actionsJson = """{"actions":[{"op":"addTask"}]}""",
                actionState = "pending", createdAt = now,
            )
        )
        assertNotNull(db.chatLogDao().getPending())

        val pending = db.chatLogDao().getPending()!!
        db.chatLogDao().updateActionState(pending.id, "done")

        assertNull("确认执行后就不该再有待确认操作", db.chatLogDao().getPending())
    }

    @Test
    fun `取消时会把所有待确认操作一起取消`() = runTest {
        db.chatLogDao().insert(
            ChatLogEntity(
                uid = "m2", role = "assistant", content = "操作一",
                actionState = "pending", createdAt = now,
            )
        )
        db.chatLogDao().insert(
            ChatLogEntity(
                uid = "m3", role = "assistant", content = "操作二",
                actionState = "pending", createdAt = now + 1,
            )
        )

        db.chatLogDao().cancelAllPending()
        assertNull(db.chatLogDao().getPending())
        assertEquals(2, db.chatLogDao().getAll().size)
    }

    @Test
    fun `群发插入不报错`() = runTest {
        db.taskDao().insertAll(listOf(task(uid = "x1"), task(uid = "x2"), task(uid = "x3")))
        assertEquals(3, db.taskDao().countAll())
        db.taskDao().deleteAll()
        assertEquals(0, db.taskDao().countAll())
    }
}
