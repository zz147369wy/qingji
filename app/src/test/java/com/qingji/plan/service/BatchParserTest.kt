package com.qingji.plan.service

import com.qingji.plan.data.model.Cadence
import com.qingji.plan.data.model.Categories
import com.qingji.plan.data.model.Category
import com.qingji.plan.data.model.Priority
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * 批量录入解析器的测试。
 *
 * 用的例子全部来自原站设置页里写的那行语法说明：
 * > 每行一条，自动识别 时间/日期/分类/重要/重复。示例：9/1 9:00 开会 @工作；
 * > 每天 背单词 20 个；每周五 写周报；周一 14:30 健身 重要
 */
class BatchParserTest {

    private val today = LocalDate.of(2026, 9, 26)   // 周六

    private fun line(text: String) = BatchParser.parseLine(text, today)

    // ---------- 原站给的四个示例 ----------

    @Test
    fun `示例一——日期时间分类`() {
        val p = line("9/1 9:00 开会 @工作")
        assertEquals("开会", p.title)
        assertEquals(LocalDate.of(2026, 9, 1), p.date)
        assertEquals("09:00", p.time)
        assertEquals("work", p.category)
        assertTrue(p.isOk)
    }

    @Test
    fun `示例二——每天重复`() {
        val p = line("每天 背单词 20 个")
        assertEquals("背单词 20 个", p.title)
        assertEquals(Cadence.DAILY, p.cadence)
        assertTrue("每天重复是长期计划", p.isPlan)
    }

    @Test
    fun `示例三——每周重复`() {
        val p = line("每周五 写周报")
        assertEquals("写周报", p.title)
        assertEquals(Cadence.WEEKLY, p.cadence)
        assertEquals(listOf(4), p.weekdays)   // 0 = 周一，所以周五是 4
    }

    @Test
    fun `示例四——周几时间重要`() {
        val p = line("周一 14:30 健身 重要")
        assertEquals("健身", p.title)
        assertEquals("14:30", p.time)
        assertEquals(Priority.HIGH, p.priority)
        assertEquals(LocalDate.of(2026, 9, 28), p.date)   // 下一个周一
    }

    // ---------- 时间 ----------

    @Test
    fun `个位数小时也能认`() {
        assertEquals("09:00", line("9:00 开会").time)
    }

    @Test
    fun `非法时间不会被当成时间`() {
        assertNull(line("25:00 开会").time)
        assertNull(line("9:99 开会").time)
    }

    // ---------- 时长 ----------

    @Test
    fun `分钟时长`() {
        assertEquals(30, line("30分钟 跑步").durationMinutes)
        assertEquals(45, line("45 分钟 跑步").durationMinutes)
    }

    @Test
    fun `小时时长`() {
        assertEquals(60, line("1小时 跑步").durationMinutes)
        assertEquals(90, line("1.5小时 跑步").durationMinutes)
    }

    // ---------- 日期 ----------

    @Test
    fun `斜杠日期`() {
        assertEquals(LocalDate.of(2026, 8, 25), line("8/25 开会").date)
    }

    @Test
    fun `中文日期`() {
        assertEquals(LocalDate.of(2026, 8, 25), line("8月25日 开会").date)
        assertEquals(LocalDate.of(2026, 8, 25), line("8月25号 开会").date)
    }

    @Test
    fun `今天明天后天`() {
        assertEquals(today, line("今天 开会").date)
        assertEquals(today.plusDays(1), line("明天 开会").date)
        assertEquals(today.plusDays(2), line("后天 开会").date)
    }

    @Test
    fun `没写日期就用默认日期`() {
        assertEquals(today, line("开会").date)
    }

    @Test
    fun `周几会算到下一个那天`() {
        // 今天是周六（9/26），下一个周三是 9/30
        assertEquals(LocalDate.of(2026, 9, 30), line("周三 开会").date)
    }

    // ---------- 分类 ----------

    @Test
    fun `at 符号后面的中文分类名能认`() {
        assertEquals("work", line("开会 @工作").category)
        assertEquals("study", line("复习 @学习").category)
        assertEquals("health", line("跑步 @健康").category)
    }

    @Test
    fun `自定义分类名也能认`() {
        val custom = Category("c_x", "健身", Categories.customColorPool[0], Categories.customColorPool[0], Categories.customColorPool[0])
        assertEquals("c_x", BatchParser.parseLine("跑步 @健身", today, listOf(custom)).category)
    }

    @Test
    fun `认不出的分类名保持其他`() {
        assertEquals("other", line("开会 @不存在的分类").category)
    }

    // ---------- 重要 ----------

    @Test
    fun `带重要两个字就是高优先级`() {
        assertEquals(Priority.HIGH, line("开会 重要").priority)
        assertEquals(Priority.MID, line("开会").priority)
    }

    @Test
    fun `重要不会被留在标题里`() {
        assertEquals("开会", line("开会 重要").title)
    }

    // ---------- 每月重复 ----------

    @Test
    fun `每月X日`() {
        val p = line("每月15日 交房租")
        assertEquals(Cadence.MONTHLY, p.cadence)
        assertEquals(15, p.dayOfMonth)
        assertEquals("交房租", p.title)
    }

    @Test
    fun `每周多个日子`() {
        val p = line("每周一三五 健身")
        assertEquals(Cadence.WEEKLY, p.cadence)
        assertEquals(listOf(0, 2, 4), p.weekdays)
    }

    // ---------- 多行 ----------

    @Test
    fun `多行会逐行解析`() {
        val text = """
            9/1 9:00 开会 @工作
            每天 背单词
            每周五 写周报
        """.trimIndent()
        val list = BatchParser.parse(text, today)
        assertEquals(3, list.size)
        assertEquals("开会", list[0].title)
        assertEquals("背单词", list[1].title)
        assertEquals("写周报", list[2].title)
    }

    @Test
    fun `空行会被跳过`() {
        val list = BatchParser.parse("\n\n开会\n\n  \n复习\n\n", today)
        assertEquals(2, list.size)
    }

    @Test
    fun `一行全是符号时标为解析失败`() {
        val p = line("@工作 重要")
        assertFalse(p.isOk)
        assertEquals("没有认出任何内容", p.error)
    }

    @Test
    fun `一行失败不影响其它行`() {
        val list = BatchParser.parse("开会\n@工作\n复习", today)
        assertEquals(3, list.size)
        assertTrue(list[0].isOk)
        assertFalse(list[1].isOk)
        assertTrue(list[2].isOk)
    }

    // ---------- 标题清理 ----------

    @Test
    fun `标题里的多余空格会被清掉`() {
        assertEquals("开会", line("  9:00   开会   @工作  ").title)
    }

    @Test
    fun `标题前后的横杠和顿号会被去掉`() {
        assertEquals("开会", line("- 开会").title)
        assertEquals("开会", line("· 开会").title)
    }

    // ---------- 组合 ----------

    @Test
    fun `一堆东西混在一起也能解析`() {
        val p = line("明天 14:30 和客户开会 1小时 @工作 重要")
        assertEquals("和客户开会", p.title)
        assertEquals(today.plusDays(1), p.date)
        assertEquals("14:30", p.time)
        assertEquals(60, p.durationMinutes)
        assertEquals("work", p.category)
        assertEquals(Priority.HIGH, p.priority)
    }

    @Test
    fun `复制 AI 提示词里带上默认日期`() {
        val prompt = BatchParser.aiHelperPrompt(today)
        assertTrue(prompt.contains("2026-09-26"))
        assertTrue(prompt.contains("@工作"))
        assertTrue(prompt.contains("每天"))
    }
}
