package com.qingji.plan.ui.today

import com.qingji.plan.data.model.Categories
import com.qingji.plan.data.model.Priority
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * 任务编辑的规则测试。
 *
 * 重点在「没有时间的任务不该有结束时间」「标题为空不能保存」这类
 * 一旦做错就会污染数据的地方。
 */
class TaskEditStateTest {

    private val date = LocalDate.of(2026, 9, 26)
    private val now = 1_800_000_000_000L

    // ---------- 保存校验 ----------

    @Test
    fun `标题为空时不能保存`() {
        assertFalse(TaskEditState.create(date).canSave)
        assertFalse(TaskEditState.create(date).copy(title = "   ").canSave)
    }

    @Test
    fun `填了标题就能保存`() {
        assertTrue(TaskEditState.create(date).copy(title = "写周报").canSave)
    }

    // ---------- 有没有时间 ----------

    @Test
    fun `新建时不给时间就落在随时区`() {
        val s = TaskEditState.create(date, time = null)
        assertFalse(s.hasTime)
        assertNull("没有时间的任务不该有结束时间", s.endTime)
        assertEquals("", s.timeLabel)
    }

    @Test
    fun `给了时间就排在时间轴上`() {
        val s = TaskEditState.create(date, time = "09:00", durationMinutes = 60)
        assertTrue(s.hasTime)
        assertEquals("10:00", s.endTime)
        assertEquals("09:00-10:00", s.timeLabel)
    }

    @Test
    fun `关掉时间开关后结束时间跟着消失`() {
        val s = TaskEditState.create(date, time = "09:00", durationMinutes = 60)
        val off = TaskEditLogic.toggleHasTime(s, false)
        assertFalse(off.hasTime)
        assertNull(off.endTime)
    }

    @Test
    fun `没有时间的任务在界面上显示为随时`() {
        assertEquals("随时", TaskEditLogic.labelFor(TaskEditState.create(date, time = null)))
        assertEquals(
            "09:00-09:15",
            TaskEditLogic.labelFor(TaskEditState.create(date, "09:00", 15)),
        )
    }

    // ---------- 时长 ----------

    @Test
    fun `时长选项覆盖原站的常用档位`() {
        assertEquals(listOf(15, 30, 45, 60, 90, 120), TaskEditState.DURATION_OPTIONS)
    }

    @Test
    fun `时长会被规整到不小于十五分钟`() {
        val s = TaskEditState.create(date, "09:00", 5)
        assertEquals(15, s.durationMinutes)
    }

    @Test
    fun `时长文案跟原站的 rd 一致`() {
        // 编辑弹层那排「持续」档位用的就是这套写法，见 TimeUtils.formatDuration
        assertEquals("30分", TaskEditState.create(date, "09:00", 30).durationLabel)
        assertEquals("1时", TaskEditState.create(date, "09:00", 60).durationLabel)
        assertEquals("1.5时", TaskEditState.create(date, "09:00", 90).durationLabel)
    }

    // ---------- 转成实体 ----------

    @Test
    fun `有时刻的任务会算出结束时间和时长`() {
        val s = TaskEditState.create(date, "09:00", 60).copy(title = "写周报")
        val e = s.toEntity(now)

        assertEquals("2026-09-26", e.date)
        assertEquals("写周报", e.title)
        assertEquals("09:00", e.time)
        assertEquals("10:00", e.endTime)
        assertEquals(60, e.durationMinutes)
        assertEquals("todo", e.status)
    }

    @Test
    fun `无时刻的任务时长和结束时间都是空`() {
        val s = TaskEditState.create(date, time = null).copy(title = "买菜")
        val e = s.toEntity(now)

        assertNull(e.time)
        assertNull(e.endTime)
        assertNull("没有时间的任务不该有时长", e.durationMinutes)
        assertFalse(e.hasTime)
    }

    @Test
    fun `标题两边的空格会被去掉`() {
        val e = TaskEditState.create(date).copy(title = "  写周报  ").toEntity(now)
        assertEquals("写周报", e.title)
    }

    @Test
    fun `新建时自动分配一个 uid`() {
        val e = TaskEditState.create(date).copy(title = "A").toEntity(now)
        assertTrue(e.uid.isNotBlank())
    }

    @Test
    fun `编辑已有任务时保留原来的 uid`() {
        val s = TaskEditState.create(date).copy(title = "A", uid = "keep-me", id = 7)
        val e = s.toEntity(now)
        assertEquals("keep-me", e.uid)
        assertEquals(7L, e.id)
    }

    @Test
    fun `重要标记会写进优先级`() {
        val s = TaskEditState.create(date, "09:00", 15)
            .copy(title = "交房租", priority = Priority.HIGH)
        assertEquals("high", s.toEntity(now).priority)
    }

    // ---------- 从已有任务打开 ----------

    @Test
    fun `从已有任务打开时字段都能带过来`() {
        val task = com.qingji.plan.data.db.entity.TaskEntity(
            id = 3, uid = "u3", date = "2026-09-26", title = "开会",
            category = "work", time = "15:00", endTime = "15:15", durationMinutes = 15,
            priority = "high", status = "done", completedAt = now,
            createdAt = now, updatedAt = now,
        )
        val s = TaskEditState.from(task)

        assertEquals(3L, s.id)
        assertEquals("u3", s.uid)
        assertEquals("开会", s.title)
        assertEquals(date, s.date)
        assertTrue(s.hasTime)
        assertEquals("15:00", s.time)
        assertEquals(15, s.durationMinutes)
        assertEquals("work", s.category)
        assertEquals(Priority.HIGH, s.priority)
        assertTrue("已完成状态要保留", s.done)
        assertEquals(now, s.completedAt)
        assertFalse("有 id 就不是新建", s.isNew)
    }

    @Test
    fun `完成状态不会因为编辑而丢失`() {
        val s = TaskEditState.create(date, "09:00", 15)
            .copy(title = "A", done = true, completedAt = now)
        val e = s.toEntity(now + 1)
        assertEquals("done", e.status)
        assertEquals(now, e.completedAt)
    }

    // ---------- 分类 ----------

    @Test
    fun `分类选项包含内置和自定义`() {
        val custom = com.qingji.plan.data.model.Category(
            "c_1", "阅读",
            androidx.compose.ui.graphics.Color.Blue,
            androidx.compose.ui.graphics.Color.Blue,
            androidx.compose.ui.graphics.Color.Blue,
        )
        assertEquals(5, TaskEditLogic.categoryOptions(emptyList()).size)
        assertEquals(6, TaskEditLogic.categoryOptions(listOf(custom)).size)
    }

    @Test
    fun `默认分类是其他`() {
        assertEquals(Categories.OTHER.id, TaskEditState.create(date).category)
    }

    @Test
    fun `新建时标记为新建`() {
        assertTrue(TaskEditState.create(date).isNew)
    }
}
