package com.qingji.plan.ui.calendar

import com.qingji.plan.data.db.entity.CourseEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 课表里每门课的颜色。
 *
 * 这是**有意偏离原站**的一处：原站按「分类」上色（同分类的课一个色），
 * 用户要求每门课颜色不同、方便区分。所以这里守的是「稳定 + 分得开」。
 */
class CourseColorsTest {

    private fun course(title: String, uid: String = "u_$title") = CourseEntity(
        uid = uid, title = title, weekday = 0,
        time = "08:00", durationMinutes = 85, createdAt = 0, updatedAt = 0,
    )

    @Test
    fun `同一门课每次都是同一个颜色`() {
        val c = course("高等数学")
        assertEquals(CourseColors.colorFor(c), CourseColors.colorFor(c))
        // 重新造一个同样的课（uid 变了、课名没变）也要同色 —— 用户心里「同一门课」看的是课名
        assertEquals(
            CourseColors.colorFor(c),
            CourseColors.colorFor(course("高等数学", uid = "u_别的")),
        )
    }

    @Test
    fun `不同课一般不同色`() {
        // 用户实际那几门课
        val names = listOf(
            "Java程序设计", "云网络技术应用", "云数据中心算力设备安装与维护",
            "算力中心基础设施运维", "虚拟化技术基础", "容器云服务架构与运维",
        )
        val colors = names.map { CourseColors.colorFor(course(it)) }

        // 6 门课、8 个颜色的调色板，不该全挤成一样的
        assertTrue("6 门课撞得只剩 ${colors.toSet().size} 种颜色", colors.toSet().size >= 4)
    }

    @Test
    fun `颜色一律从调色板里取`() {
        listOf("数学", "英语", "物理", "", " ", "很长很长的一门课名字啊啊啊").forEach {
            assertTrue(CourseColors.colorFor(course(it)) in CourseColors.PALETTE)
        }
    }

    @Test
    fun `课名为空时不崩，退回用 uid 取色`() {
        val a = course("", uid = "aaa")
        val b = course("   ", uid = "bbb")
        assertTrue(CourseColors.colorFor(a) in CourseColors.PALETTE)
        assertTrue(CourseColors.colorFor(b) in CourseColors.PALETTE)
    }

    // ---------- 哈希 ----------

    @Test
    fun `哈希是稳定的，不依赖 JVM 的 String_hashCode`() {
        // 同一个字符串算多少次都一样
        assertEquals(CourseColors.stableHash("高等数学"), CourseColors.stableHash("高等数学"))
        // 而且是正数（要对调色板取模，负数会越界）
        listOf("", "a", "高等数学", "Java程序设计", "🎓").forEach {
            assertTrue("「$it」的哈希是负的", CourseColors.stableHash(it) >= 0)
        }
    }

    @Test
    fun `哈希能把不同的字串分开`() {
        val a = CourseColors.stableHash("高等数学")
        val b = CourseColors.stableHash("大学英语")
        assertNotEquals(a, b)
    }

    @Test
    fun `调色板里每个颜色都是浅底深字`() {
        CourseColors.PALETTE.forEach { color ->
            // 底色要比字色亮（明度更高），不然深底深字看不清
            assertTrue(
                "底色比字色还暗：$color",
                color.soft.luminance() > color.deep.luminance(),
            )
        }
    }

    @Test
    fun `调色板够大，能应付一个学期的课`() {
        assertTrue("调色板只有 ${CourseColors.PALETTE.size} 个色", CourseColors.PALETTE.size >= 6)
    }

    private fun androidx.compose.ui.graphics.Color.luminance(): Float =
        0.299f * red + 0.587f * green + 0.114f * blue
}
