package com.qingji.plan.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * AI 返回内容的解析测试。
 *
 * 这一组对应 PRD §10.4 的 A2/A3/A7 —— 整个 AI 改数据链路的入口。
 * 关键点：**模型不听话是常态，App 绝不能崩**。
 */
class AiActionParserTest {

    // ---------- 三道防线 ----------

    @Test
    fun `干净的 JSON 能解析`() {
        val raw = """{"actions":[{"op":"addTask","task":{"title":"开会","time":"15:00"}}]}"""
        val actions = AiActionParser.parse(raw)
        assertEquals(1, actions.size)
        assertEquals("addTask", actions[0].op)
        assertEquals("开会", actions[0].task?.title)
        assertEquals("15:00", actions[0].task?.time)
    }

    @Test
    fun `裹在代码块里的 JSON 也能解析`() {
        val raw = """
            好的，我这就安排：
            ```json
            {"actions":[{"op":"addTask","task":{"title":"开会"}}]}
            ```
        """.trimIndent()
        assertEquals(1, AiActionParser.parse(raw).size)
    }

    @Test
    fun `没有 json 标记的代码块也能解析`() {
        val raw = "```\n{\"actions\":[{\"op\":\"deleteTask\",\"match\":{\"title\":\"开会\"}}]}\n```"
        assertEquals(1, AiActionParser.parse(raw).size)
    }

    @Test
    fun `夹在一堆废话里的 JSON 也能抠出来`() {
        val raw = "根据你的要求，我准备这样做：{\"actions\":[{\"op\":\"addTask\",\"task\":{\"title\":\"开会\"}}]} 你看行吗？"
        assertEquals(1, AiActionParser.parse(raw).size)
    }

    @Test
    fun `完全不是 JSON 时返回空清单而不是崩溃`() {
        assertTrue(AiActionParser.parse("今天天气不错").isEmpty())
        assertTrue(AiActionParser.parse("").isEmpty())
        assertTrue(AiActionParser.parse(null).isEmpty())
        assertTrue(AiActionParser.parse("{{{ 坏掉的 json").isEmpty())
    }

    @Test
    fun `JSON 合法但没有 actions 字段时返回空`() {
        assertTrue(AiActionParser.parse("""{"foo":"bar"}""").isEmpty())
        assertTrue(AiActionParser.parse("""{"actions":"不是数组"}""").isEmpty())
    }

    // ---------- 白名单 ----------

    @Test
    fun `不认识的 op 会被丢掉`() {
        val raw = """{"actions":[
            {"op":"addTask","task":{"title":"开会"}},
            {"op":"deleteEverything","task":{"title":"删库"}},
            {"op":"rm -rf","task":{"title":"危险"}}
        ]}"""
        val actions = AiActionParser.parse(raw)
        assertEquals(1, actions.size)
        assertEquals("addTask", actions[0].op)
    }

    @Test
    fun `八种合法 op 都能通过`() {
        val raw = """{"actions":[
            {"op":"addTask","task":{"title":"A"}},
            {"op":"editTask","match":{"title":"A"},"task":{"title":"B"}},
            {"op":"deleteTask","match":{"title":"A"}},
            {"op":"addPlan","plan":{"title":"P"}},
            {"op":"deletePlan","match":{"title":"P"}},
            {"op":"addCourse","course":{"title":"C"}},
            {"op":"editCourse","match":{"title":"C"},"course":{"title":"D"}},
            {"op":"deleteCourse","match":{"title":"C"}}
        ]}"""
        assertEquals(8, AiActionParser.parse(raw).size)
    }

    @Test
    fun `缺少必需字段的条目会被丢掉`() {
        val raw = """{"actions":[
            {"op":"addTask","task":{"title":"开会"}},
            {"op":"addTask","task":{}},
            {"op":"addTask","task":{"title":"   "}},
            {"op":"editTask","match":{"title":"开会"}},
            {"op":"deleteTask","match":{}}
        ]}"""
        // 只有第一条是完整的
        assertEquals(1, AiActionParser.parse(raw).size)
    }

    @Test
    fun `多余字段不会导致解析失败`() {
        val raw = """{"actions":[{"op":"addTask","task":{"title":"开会"},"备注":"模型爱加没用的字段"}]}"""
        assertEquals(1, AiActionParser.parse(raw).size)
    }

    // ---------- 操作描述 ----------

    @Test
    fun `操作的描述文案与原站一致`() {
        val add = AiAction("addTask", task = TaskSpec(title = "开会"))
        val edit = AiAction("editTask", match = MatchSpec(title = "开会"), task = TaskSpec())
        val del = AiAction("deleteTask", match = MatchSpec(title = "开会"))
        val course = AiAction("addCourse", course = CourseSpec(title = "数学"))

        assertEquals("➕ 新增任务：开会", add.describe)
        assertEquals("✏️ 修改任务：开会", edit.describe)
        assertEquals("➖ 删除任务：开会", del.describe)
        assertEquals("➕ 新增课程：数学", course.describe)
    }

    // ---------- 标题匹配（原站规则）----------

    @Test
    fun `标题完全相同算命中`() {
        assertTrue(AiActionParser.titleMatches("开会", "开会"))
    }

    @Test
    fun `已有的标题包含用户说的算命中`() {
        assertTrue(AiActionParser.titleMatches("开会讨论季度预算", "季度预算"))
    }

    @Test
    fun `用户说的包含已有标题也算命中`() {
        assertTrue(AiActionParser.titleMatches("周报", "把这周的周报删掉"))
    }

    @Test
    fun `单字符的子串不算命中——避免误伤`() {
        // 「会」只有一个字，不应该匹配到「开会」
        assertFalse(AiActionParser.titleMatches("开会", "会"))
    }

    @Test
    fun `毫不相干的标题不命中`() {
        assertFalse(AiActionParser.titleMatches("开会", "健身"))
    }

    @Test
    fun `空标题不命中`() {
        assertFalse(AiActionParser.titleMatches("", "开会"))
        assertFalse(AiActionParser.titleMatches("开会", ""))
        assertFalse(AiActionParser.titleMatches(null, "开会"))
        assertFalse(AiActionParser.titleMatches("开会", null))
    }

    @Test
    fun `两边空格会被忽略`() {
        assertTrue(AiActionParser.titleMatches("  开会  ", "开会"))
    }

    // ---------- 回执 ----------

    @Test
    fun `全部成功时的回执格式`() {
        val r = AiExecResult(listOf("➕ 任务：开会", "✏️ 任务：周报"), emptyList())
        val text = r.receipt()
        assertTrue(text.startsWith("✅ 已执行 2 项："))
        assertTrue(text.contains("➕ 任务：开会"))
        assertEquals(2, r.total)
        assertTrue(r.allOk)
    }

    @Test
    fun `部分失败时两边都列出来`() {
        val r = AiExecResult(listOf("➕ 任务：开会"), listOf("➖ 任务：不存在 —— 未找到任务：不存在"))
        val text = r.receipt()
        assertTrue(text.contains("✅ 已执行 1 项"))
        assertTrue(text.contains("⚠️ 1 项没做成"))
        assertFalse(r.allOk)
        assertEquals(2, r.total)
    }
}
