package com.qingji.plan.ui.ai

import androidx.compose.ui.text.font.FontWeight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * AI 回复的 Markdown 渲染测试。
 *
 * 这是**唯一一处刻意与原站不同**的地方：原站把 `**加粗**` 原样显示成星号，
 * 我们按用户要求渲染成真加粗。
 */
class AiMarkdownTest {

    @Test
    fun `加粗会被去掉星号`() {
        val result = renderMarkdown("你今天的计划只有 1 项：\n\n- **开会** 15:00")
        assertTrue("不该再有星号", !result.text.contains("**"))
        assertTrue(result.text.contains("开会"))
    }

    @Test
    fun `加粗部分真的变粗了`() {
        val result = renderMarkdown("**开会**")
        val bold = result.spanStyles.firstOrNull()
        assertEquals(FontWeight.Bold, bold?.item?.fontWeight)
        assertEquals("开会", result.text.substring(bold!!.start, bold.end))
    }

    @Test
    fun `没有加粗时原样返回`() {
        val result = renderMarkdown("今天天气不错")
        assertEquals("今天天气不错", result.text)
        assertTrue(result.spanStyles.isEmpty())
    }

    @Test
    fun `多处加粗都能识别`() {
        val result = renderMarkdown("**第一** 和 **第二**")
        assertEquals("第一 和 第二", result.text)
        assertEquals(2, result.spanStyles.size)
    }

    @Test
    fun `换行会保留`() {
        val result = renderMarkdown("第一行\n第二行")
        assertTrue(result.text.contains("\n"))
        assertTrue(result.text.contains("第一行"))
        assertTrue(result.text.contains("第二行"))
    }

    @Test
    fun `单个星号不动它`() {
        // 一个星号可能是有意的，别乱改
        val result = renderMarkdown("3 * 4 = 12")
        assertEquals("3 * 4 = 12", result.text)
    }

    @Test
    fun `空字符串不崩`() {
        assertEquals("", renderMarkdown("").text)
    }

    @Test
    fun `原站会显示成星号的真实例子`() {
        // 这是用户截图里原站的真实显示：`- **开会** 15:00`
        // 原站输出： "- **开会** 15:00"
        // 我们的输出： "- 开会 15:00"（开会加粗）
        val result = renderMarkdown("- **开会** 15:00")
        assertEquals("- 开会 15:00", result.text)
    }
}
