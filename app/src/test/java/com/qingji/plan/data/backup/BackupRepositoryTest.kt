package com.qingji.plan.data.backup

import androidx.test.core.app.ApplicationProvider
import com.qingji.plan.data.db.AppDatabase
import com.qingji.plan.data.db.entity.CourseEntity
import com.qingji.plan.data.db.entity.SettingEntity
import com.qingji.plan.data.db.entity.TaskEntity
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

/**
 * 备份与恢复测试。对应 PRD §10.5 的 D2/D3/D4/D5。
 *
 * 重点验证三件事：
 *   1. 导出 → 清空 → 导入 → 数据**完全回来**（含设置）
 *   2. 恢复前自动存的安全网确实存了
 *   3. 各种坏文件都给得出**人话**的错误，不是「导入失败」
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BackupRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var backup: BackupRepository
    private val now = 1_800_000_000_000L

    @Before
    fun setUp() {
        db = AppDatabase.inMemory(ApplicationProvider.getApplicationContext())
        backup = BackupRepository(db, appVersion = "test")
    }

    @After
    fun tearDown() = db.close()

    private suspend fun seed() {
        db.taskDao().insert(
            TaskEntity(
                uid = "t1", date = "2026-09-27", title = "开会",
                category = "work", time = "15:00", durationMinutes = 60,
                createdAt = now, updatedAt = now,
            )
        )
        db.taskDao().insert(
            TaskEntity(
                uid = "t2", date = "2026-09-28", title = "健身",
                category = "health", createdAt = now, updatedAt = now,
            )
        )
        db.courseDao().insert(
            CourseEntity(
                uid = "c1", title = "高等数学", weekday = 2, time = "08:00",
                durationMinutes = 45, room = "A301", createdAt = now, updatedAt = now,
            )
        )
        db.settingDao().put(SettingEntity("theme", "night", now))
        db.settingDao().put(SettingEntity("settleTime", "22:00", now))
    }

    // ---------- 导出 ----------

    @Test
    fun `导出能拿到全部数据`() = runTest {
        seed()
        val text = backup.export()
        val file = backup.preview(text)

        assertEquals(2, file.counts.tasks)
        assertEquals(1, file.counts.courses)
        assertEquals(2, file.counts.settings)
    }

    @Test
    fun `导出的文件里有版本号和导出时间`() = runTest {
        seed()
        val file = backup.preview(backup.export())
        assertEquals(BackupFile.CURRENT_VERSION, file.version)
        assertTrue("导出时间应该是合理的毫秒时间戳", file.exportedAt > 0)
    }

    @Test
    fun `导出的文件名带日期`() {
        val name = backup.suggestedFileName(java.time.LocalDate.of(2026, 9, 27))
        assertEquals("鲸记备份_2026-09-27.json", name)
    }

    @Test
    fun `导出的内容人能看懂`() = runTest {
        seed()
        val text = backup.export()
        assertTrue("应该是格式化过的 JSON", text.contains("\n"))
        assertTrue(text.contains("开会"))
    }

    // ---------- 恢复：覆盖 ----------

    @Test
    fun `清空之后导入数据能完全回来`() = runTest {
        seed()
        val text = backup.export()

        // 清空
        db.taskDao().deleteAll()
        db.courseDao().deleteAll()
        db.settingDao().clear()
        assertEquals(0, db.taskDao().countAll())
        assertNull(db.settingDao().get("theme"))

        // 恢复
        val file = backup.preview(text)
        backup.restore(file)

        assertEquals(2, db.taskDao().countAll())
        assertEquals(1, db.courseDao().getAll().size)
        assertEquals("night", db.settingDao().get("theme")?.value)
        assertEquals("22:00", db.settingDao().get("settleTime")?.value)
    }

    @Test
    fun `恢复是覆盖不是合并`() = runTest {
        seed()
        val text = backup.export()

        // 再塞一条「不在备份里」的数据
        db.taskDao().insert(
            TaskEntity(
                uid = "extra", date = "2026-10-01", title = "备份之后加的",
                createdAt = now, updatedAt = now,
            )
        )
        assertEquals(3, db.taskDao().countAll())

        backup.restore(backup.preview(text))

        // 恢复后那条额外的应该没了
        assertEquals(2, db.taskDao().countAll())
        assertNull(db.taskDao().getByUid("extra"))
    }

    @Test
    fun `恢复后任务的字段一个不少`() = runTest {
        seed()
        val text = backup.export()
        backup.restore(backup.preview(text))

        val task = db.taskDao().getByUid("t1")
        assertNotNull(task)
        assertEquals("开会", task!!.title)
        assertEquals("2026-09-27", task.date)
        assertEquals("15:00", task.time)
        assertEquals(60, task.durationMinutes)
        assertEquals("work", task.category)
    }

    @Test
    fun `恢复后课程也带教室`() = runTest {
        seed()
        val text = backup.export()
        backup.restore(backup.preview(text))

        val course = db.courseDao().getAll().single()
        assertEquals("高等数学", course.title)
        assertEquals("A301", course.room)
        assertEquals(45, course.durationMinutes)
    }

    @Test
    fun `空数据库也能导出`() = runTest {
        val text = backup.export()
        assertTrue(text.isNotBlank())
    }

    // ---------- 安全网 ----------

    @Test
    fun `恢复前的安全网能存下当前数据`() = runTest {
        seed()
        val snapshot = backup.snapshotForSafety()
        val file = backup.preview(snapshot)
        assertEquals(2, file.counts.tasks)
    }

    // ---------- 错误处理：每一种都要说人话 ----------

    @Test
    fun `空文件报错说清楚`() {
        val e = runCatching { backup.preview("") }.exceptionOrNull()
        assertTrue(e is BackupError.Unreadable)
        assertTrue(e!!.message!!.contains("空"))
    }

    @Test
    fun `不是 JSON 的文件报错说清楚`() {
        val e = runCatching { backup.preview("这不是备份文件，是一段普通文字") }.exceptionOrNull()
        assertTrue(e is BackupError.NotJson)
        assertTrue(e!!.message!!.contains("不是合法 JSON"))
    }

    @Test
    fun `JSON 但结构不对时也报错而不是崩溃`() {
        val e = runCatching { backup.preview("""{"hello":"world"}""") }.exceptionOrNull()
        assertTrue("应该抛 BackupError 的子类，实际是 $e", e is BackupError)
    }

    @Test
    fun `版本太新时给出明确提示`() {
        val future = """{"version":999,"exportedAt":1,"counts":{"tasks":1},"tasks":[{"uid":"a","date":"2026-01-01","title":"x","createdAt":1,"updatedAt":1}]}"""
        val e = runCatching { backup.preview(future) }.exceptionOrNull()
        assertTrue(e is BackupError.UnknownVersion)
        assertTrue(e!!.message!!.contains("999"))
        assertTrue(e.message!!.contains("更新"))
    }

    @Test
    fun `空备份会被识别出来`() {
        val empty = """{"version":1,"exportedAt":1,"counts":{}}"""
        val e = runCatching { backup.preview(empty) }.exceptionOrNull()
        assertTrue(e is BackupError.Empty)
    }

    @Test
    fun `多余的字段不会导致导入失败`() = runTest {
        seed()
        val text = backup.export()
        // 模拟「更新版本的 App 加了新字段」
        val withExtra = text.replaceFirst("{", """{"未来的新字段":123,""")
        val file = backup.preview(withExtra)
        assertEquals(2, file.counts.tasks)
    }

    // ---------- 条数描述 ----------

    @Test
    fun `条数描述是人话`() {
        val counts = BackupCounts(tasks = 12, courses = 5, reviews = 30)
        val text = counts.describe()
        assertTrue(text.contains("任务 12 条"))
        assertTrue(text.contains("课程 5 条"))
        assertTrue(text.contains("结算记录 30 条"))
    }

    @Test
    fun `空备份的条数描述`() {
        assertEquals("空备份", BackupCounts().describe())
    }
}
