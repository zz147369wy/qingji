package com.qingji.plan.data.model

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 分类的色值与名称全部对照 `扒站/app.js` 里的 `Wa` 常量。
 */
class CategoryTest {

    @Test
    fun `一共五个内置分类`() {
        assertEquals(5, Categories.builtIn.size)
    }

    @Test
    fun `内置分类的 id 和名称与原站一致`() {
        assertEquals(
            listOf("work", "study", "life", "health", "other"),
            Categories.builtIn.map { it.id },
        )
        assertEquals(
            listOf("工作", "学习", "生活", "健康", "其他"),
            Categories.builtIn.map { it.label },
        )
    }

    // 【2026-10 鲸鱼主题】原站那套大地色放进蓝色的海里发闷，五个内置分类换成清亮的一组。
    // 界面上标签的底/字改由 ThemePalette.tagBg/tagFg 按明暗推导（见 ThemePaletteTest），
    // 这里的 soft/deep 只作为给备份文件和旧调用方用的兜底值。

    @Test
    fun `五个内置分类的主色与设计稿一致`() {
        assertEquals(Color(0xFFFF8A5B), Categories.WORK.color)
        assertEquals(Color(0xFF3D9BFF), Categories.STUDY.color)
        assertEquals(Color(0xFFF7B731), Categories.LIFE.color)
        assertEquals(Color(0xFFFF7FA8), Categories.HEALTH.color)
        assertEquals(Color(0xFF33BE8F), Categories.OTHER.color)
    }

    @Test
    fun `内置分类的 soft 和 deep 都是从主色派生的`() {
        Categories.builtIn.forEach { c ->
            assertEquals("${c.label} 的 soft 应该是主色向白混合", Categories.deriveSoft(c.color), c.soft)
            assertEquals("${c.label} 的 deep 应该是主色压暗", Categories.deriveDeep(c.color), c.deep)
        }
    }

    fun `全部可选分类等于内置加自定义`() {
        val custom = Category("c_1", "阅读", Color.Blue, Color.Blue, Color.Blue)
        assertEquals(6, Categories.all(listOf(custom)).size)
        assertEquals(5, Categories.all().size)
    }

    @Test
    fun `自定义配色池有六种颜色，且不与内置分类撞色`() {
        assertEquals(6, Categories.customColorPool.size)
        assertEquals("颜色不能重复", 6, Categories.customColorPool.toSet().size)
        val builtin = Categories.builtIn.map { it.color }.toSet()
        Categories.customColorPool.forEach { c ->
            assertFalse("自建分类的颜色不该和内置分类一样，否则分不清", c in builtin)
        }
    }

    @Test
    fun `浅色底比原色更亮`() {
        val base = Color(0xFF8B5CF6)
        val soft = Categories.deriveSoft(base)
        assertTrue("浅底应该更亮", soft.red > base.red && soft.green > base.green && soft.blue > base.blue)
    }

    @Test
    fun `深色字比原色更暗`() {
        val base = Color(0xFF8B5CF6)
        val deep = Categories.deriveDeep(base)
        assertTrue("深字应该更暗", deep.red < base.red && deep.green < base.green && deep.blue < base.blue)
    }

    @Test
    fun `内置分类的 id 集合正确`() {
        assertEquals(setOf("work", "study", "life", "health", "other"), Categories.builtInIds)
    }
}
