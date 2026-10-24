package com.qingji.plan.ui.settings

import com.qingji.plan.data.model.Categories
import com.qingji.plan.data.model.Category
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * 设置页规则测试。重点是那些「手滑就会做错」的边界：
 * 重名分类、删内置分类、清理范围换算、结算时间格式。
 */
class SettingsStateTest {

    private val today = LocalDate.of(2026, 9, 26)

    // ---------- 结算时间 ----------

    @Test
    fun `合法的时间能被接受`() {
        assertTrue(SettingsLogic.isValidSettleTime("21:00"))
        assertTrue(SettingsLogic.isValidSettleTime("00:00"))
        assertTrue(SettingsLogic.isValidSettleTime("23:59"))
    }

    @Test
    fun `非法的时间会被拒绝`() {
        assertFalse(SettingsLogic.isValidSettleTime("24:00"))
        assertFalse(SettingsLogic.isValidSettleTime("21:60"))
        assertFalse(SettingsLogic.isValidSettleTime("晚上九点"))
        assertFalse(SettingsLogic.isValidSettleTime(""))
    }

    @Test
    fun `默认结算时间是二十一点`() {
        assertEquals("21:00", SettingsUiState.DEFAULT_SETTLE_TIME)
    }

    // ---------- 分类 ----------

    @Test
    fun `名称为空不能添加`() {
        assertFalse(SettingsLogic.canAddCategory("", emptyList()))
        assertFalse(SettingsLogic.canAddCategory("   ", emptyList()))
    }

    @Test
    fun `和内置分类重名不能添加`() {
        assertFalse(SettingsLogic.canAddCategory("工作", emptyList()))
        assertFalse(SettingsLogic.canAddCategory("学习", emptyList()))
    }

    @Test
    fun `和已有自定义分类重名不能添加`() {
        val custom = Category("c1", "健身", Categories.customColorPool[0], Categories.customColorPool[0], Categories.customColorPool[0])
        assertFalse(SettingsLogic.canAddCategory("健身", listOf(custom)))
        assertTrue(SettingsLogic.canAddCategory("阅读", listOf(custom)))
    }

    @Test
    fun `新分类的 id 不与内置冲突`() {
        val cat = SettingsLogic.newCategory("健身", 0)
        assertFalse(Categories.builtInIds.contains(cat.id))
        assertNotNull(cat.id)
    }

    @Test
    fun `新分类的浅底和深字是从主色推出来的`() {
        val cat = SettingsLogic.newCategory("健身", 0)
        assertTrue("浅底应该比主色亮", cat.soft.red > cat.color.red)
        assertTrue("深字应该比主色暗", cat.deep.red < cat.color.red)
    }

    @Test
    fun `配色下标会自动绕回来`() {
        val n = SettingsUiState.CUSTOM_COLOR_POOL.size
        assertEquals(
            SettingsLogic.newCategory("A", 0).color,
            SettingsLogic.newCategory("A", n).color,
        )
        assertEquals(
            SettingsLogic.newCategory("A", 1).color,
            SettingsLogic.newCategory("A", n + 1).color,
        )
    }

    @Test
    fun `内置分类不允许删除`() {
        Categories.builtIn.forEach { assertFalse("${it.label} 不该能删", SettingsLogic.canDelete(it)) }
    }

    @Test
    fun `自定义分类允许删除`() {
        assertTrue(SettingsLogic.canDelete(SettingsLogic.newCategory("健身", 0)))
    }

    @Test
    fun `配色池就原站那六种，别自己加`() {
        // 原站 wx 数组只有 6 个。之前这里写成了 10 个并断言 10——那是把错误的实现锁死了
        assertEquals(6, SettingsUiState.COURSE_COLOR_OPTIONS.size)
    }

    @Test
    fun `配色池顺序和取值逐项与原站 wx 一致`() {
        assertEquals(
            listOf("#8B5CF6", "#C97B4A", "#7E9BB5", "#D9A441", "#CA8F94", "#6FA06F"),
            SettingsUiState.COURSE_COLOR_OPTIONS,
        )
    }

    @Test
    fun `默认课程色在配色池里`() {
        assertTrue(
            SettingsUiState.DEFAULT_COURSE_COLOR in SettingsUiState.COURSE_COLOR_OPTIONS,
        )
    }

    // ---------- 清理任务的范围换算 ----------

    @Test
    fun `今天起从今天算`() {
        assertEquals(today, SettingsLogic.clearFromDate("今天起", today))
    }

    @Test
    fun `未来七天是今天往后推七天`() {
        assertEquals(today.plusDays(7), SettingsLogic.clearFromDate("未来7天", today))
    }

    @Test
    fun `未来三十天是今天往后推三十天`() {
        assertEquals(today.plusDays(30), SettingsLogic.clearFromDate("未来30天", today))
    }

    @Test
    fun `自定义需要用户自己选日期`() {
        assertNull(SettingsLogic.clearFromDate("自定义", today))
    }

    @Test
    fun `清理范围的选项与原站一致`() {
        assertEquals(
            listOf("今天起", "今天", "未来7天", "未来30天", "自定义"),
            SettingsUiState.CLEAR_RANGES,
        )
    }

    // ---------- 初始状态 ----------

    @Test
    fun `初始状态用的是默认值`() {
        val s = SettingsUiState()
        assertEquals("21:00", s.settleTime)
        assertEquals("#8B5CF6", s.courseColor)
        assertFalse(s.timelineNoOverlap)
        assertTrue("默认允许 AI 改，但每次都要确认", s.aiModifyAllowed)
        assertFalse("没填 Key 就是没配置", s.aiEnabled)
    }

    @Test
    fun `没填 Key 时 AI 未启用`() {
        assertFalse(SettingsUiState().aiEnabled)
        assertTrue(SettingsUiState(aiConfig = com.qingji.plan.data.model.AiConfig(apiKey = "sk-x")).aiEnabled)
    }

    @Test
    fun `配色选项是八套鲸鱼配色，默认原版蓝`() {
        assertEquals(8, SettingsUiState().schemeOptions.size)
        assertEquals("deepseek", SettingsUiState().whaleScheme)
    }

    @Test
    fun `明暗模式三选一，默认浅色海面`() {
        assertEquals(3, com.qingji.plan.ui.theme.ThemeMode.entries.size)
        assertEquals(com.qingji.plan.ui.theme.ThemeMode.LIGHT, SettingsUiState().themeMode)
        assertEquals("light", com.qingji.plan.ui.theme.ThemeMode.LIGHT.key)
        assertEquals("跟随系统", com.qingji.plan.ui.theme.ThemeMode.SYSTEM.label)
    }

    @Test
    fun `全部可选分类等于内置加自定义`() {
        val custom = SettingsLogic.newCategory("健身", 0)
        assertEquals(6, SettingsUiState(customCategories = listOf(custom)).allCategories.size)
        assertEquals(5, SettingsUiState().allCategories.size)
    }
}
