package com.qingji.plan.ui.course

import com.qingji.plan.data.db.entity.CourseEntity
import com.qingji.plan.data.model.Categories
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 课程编辑与课表分组的规则测试。
 */
class CourseEditStateTest {

    private val now = 1_800_000_000_000L

    private fun course(
        title: String,
        weekday: Int,
        time: String? = "08:00",
        duration: Int = 45,
        uid: String = title,
        id: Long = 0,
    ) = CourseEntity(
        id = id, uid = uid, title = title, weekday = weekday, time = time,
        durationMinutes = duration, createdAt = now, updatedAt = now,
    )

    // ---------- 默认值 ----------

    @Test
    fun `课程默认时长是四十五分钟`() {
        assertEquals(45, CourseEditState.create().durationMinutes)
        assertEquals(CourseEditState.DEFAULT_DURATION, CourseEditState.create().durationMinutes)
    }

    @Test
    fun `课程默认分类是学习`() {
        assertEquals(Categories.STUDY.id, CourseEditState.create().category)
    }

    @Test
    fun `新建时默认是周一`() {
        assertEquals(0, CourseEditState.create().weekday)
    }

    // ---------- 校验 ----------

    @Test
    fun `课程名称为空不能保存`() {
        assertFalse(CourseEditState.create().canSave)
        assertFalse(CourseEditState.create().copy(title = "  ").canSave)
    }

    @Test
    fun `填了名称就能保存`() {
        assertTrue(CourseEditState.create().copy(title = "高等数学").canSave)
    }

    // ---------- 周几 ----------

    @Test
    fun `周几的中文标签与原站一致`() {
        assertEquals(
            listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日"),
            CourseEditState.WEEKDAY_LABELS,
        )
    }

    @Test
    fun `零表示周一`() {
        assertEquals("周一", CourseEditState.create(0).weekdayLabel)
        assertEquals("周日", CourseEditState.create(6).weekdayLabel)
    }

    @Test
    fun `周几会被限制在零到六之间`() {
        assertEquals(0, CourseEditState.create(-5).weekday)
        assertEquals(6, CourseEditState.create(99).weekday)
        assertEquals(6, CourseEditState.create().copy(weekday = 99).toEntity(now).weekday)
    }

    // ---------- 时间 ----------

    @Test
    fun `有时刻的课会算出结束时间`() {
        val s = CourseEditState.create().copy(title = "数学", time = "08:00", durationMinutes = 45)
        assertEquals("08:45", s.endTime)
        assertEquals("08:00-08:45", s.timeLabel)
    }

    @Test
    fun `没排固定时间的课不铺到时间轴上`() {
        val s = CourseEditState.create().copy(title = "答疑", hasTime = false)
        assertNull(s.endTime)
        assertEquals("未排固定时间", s.timeLabel)
        assertNull(s.toEntity(now).time)
    }

    // ---------- 教室 ----------

    @Test
    fun `有教室时课表标签带教室`() {
        val s = CourseEditState.create().copy(title = "高等数学", room = "A301")
        assertEquals("高等数学 ·A301", s.displayLabel)
    }

    @Test
    fun `没有教室时标签不带多余的符号`() {
        assertEquals("体育", CourseEditState.create().copy(title = "体育", room = "").displayLabel)
        assertEquals("体育", CourseEditState.create().copy(title = "体育", room = "  ").displayLabel)
    }

    @Test
    fun `空白教室会存成空值`() {
        assertNull(CourseEditState.create().copy(title = "A", room = "   ").toEntity(now).room)
    }

    // ---------- 转实体 ----------

    @Test
    fun `课程名称两边的空格会被去掉`() {
        assertEquals("高等数学", CourseEditState.create().copy(title = "  高等数学 ").toEntity(now).title)
    }

    @Test
    fun `新建时自动分配 uid`() {
        assertTrue(CourseEditState.create().copy(title = "A").toEntity(now).uid.isNotBlank())
    }

    @Test
    fun `从已有课程打开时字段都能带过来`() {
        val existing = course("高等数学", 2, "10:00", 90, uid = "c9", id = 7)
        val s = CourseEditState.from(existing)

        assertEquals(7L, s.id)
        assertEquals("c9", s.uid)
        assertEquals("高等数学", s.title)
        assertEquals(2, s.weekday)
        assertEquals("10:00", s.time)
        assertEquals(90, s.durationMinutes)
        assertFalse("有 id 就不是新建", s.isNew)
    }

    // ---------- 课表分组 ----------

    @Test
    fun `按周几分组且按时间排序`() {
        val courses = listOf(
            course("第三节", 0, "14:00", uid = "a"),
            course("第一节", 0, "08:00", uid = "b"),
            course("周二课", 1, "10:00", uid = "c"),
        )
        val grouped = CourseEditLogic.groupByWeekday(courses)

        assertEquals(2, grouped.size)
        assertEquals(listOf("第一节", "第三节"), grouped[0]!!.map { it.title })
        assertEquals(listOf("周二课"), grouped[1]!!.map { it.title })
    }

    @Test
    fun `没有固定时间的课不参与分组`() {
        val courses = listOf(
            course("正常课", 0, "08:00", uid = "a"),
            course("答疑", 0, null, uid = "b"),
        )
        val grouped = CourseEditLogic.groupByWeekday(courses)
        assertEquals(1, grouped[0]!!.size)
        assertEquals(listOf("答疑"), CourseEditLogic.withoutTime(courses).map { it.title })
    }

    @Test
    fun `已删除的课不出现在课表上`() {
        val deleted = course("删掉的", 0).copy(deletedAt = now)
        assertTrue(CourseEditLogic.groupByWeekday(listOf(deleted)).isEmpty())
        assertTrue(CourseEditLogic.withoutTime(listOf(deleted)).isEmpty())
    }

    // ---------- 课表显示的小时范围 ----------

    @Test
    fun `没有课时默认显示八点到二十一点`() {
        assertEquals(8..21, CourseEditLogic.hourRange(emptyList()))
    }

    @Test
    fun `有早课时小时范围会自动往上扩`() {
        val courses = listOf(course("早课", 0, "06:30", 45))
        assertEquals(6, CourseEditLogic.hourRange(courses).first)
    }

    @Test
    fun `有晚课时小时范围会自动往下扩`() {
        val courses = listOf(course("晚课", 0, "20:00", 120))
        assertEquals(22, CourseEditLogic.hourRange(courses).last)
    }

    @Test
    fun `正常时间的课不会缩小默认范围`() {
        val courses = listOf(course("普通课", 0, "10:00", 45))
        assertEquals(8..21, CourseEditLogic.hourRange(courses))
    }

    @Test
    fun `只有没排时间的课时用默认范围`() {
        val courses = listOf(course("答疑", 0, null))
        assertEquals(8..21, CourseEditLogic.hourRange(courses))
    }
}
