package com.qingji.plan.ui.ai

import com.qingji.plan.data.remote.AiAction
import com.qingji.plan.data.remote.MatchSpec
import com.qingji.plan.data.remote.TaskSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * AI 确认状态机的测试。
 *
 * **对应 PRD 的 A3 验收项：「让 AI 改计划后故意不回复确认，去今日页看——不能有任何变化」。**
 * 这个状态机就是保证那件事的地方。
 */
class AiChatLogicTest {

    private val addTask = AiAction("addTask", task = TaskSpec(title = "开会", time = "15:00"))
    private val deleteTask = AiAction("deleteTask", match = MatchSpec(title = "买菜"))
    private val awaiting = PendingState.Awaiting(listOf(addTask, deleteTask))

    // ---------- 「确认」的判定 ----------

    @Test
    fun `回复确认算同意`() {
        assertTrue(AiChatLogic.isConfirm("确认"))
    }

    @Test
    fun `回复确定也算同意`() {
        assertTrue(AiChatLogic.isConfirm("确定"))
    }

    @Test
    fun `两边有空格也算`() {
        assertTrue(AiChatLogic.isConfirm("  确认  "))
    }

    @Test
    fun `其它任何回复都不算同意`() {
        listOf("好", "可以", "OK", "yes", "确认吧", "不确认", "算了", "", "再想想").forEach {
            assertFalse("「$it」不该被当成确认", AiChatLogic.isConfirm(it))
        }
    }

    // ---------- 核心：不确认就不执行 ----------

    @Test
    fun `没有挂起操作时是普通聊天`() {
        assertEquals(ChatDecision.PlainChat, AiChatLogic.decide(PendingState.None, "你好"))
        assertEquals(ChatDecision.PlainChat, AiChatLogic.decide(PendingState.None, "确认"))
    }

    @Test
    fun `有挂起操作且回复确认时才执行`() {
        assertEquals(ChatDecision.Execute, AiChatLogic.decide(awaiting, "确认"))
    }

    @Test
    fun `有挂起操作但回复别的内容就不执行`() {
        // 这是 A3 的核心：不回复「确认」，什么都别做
        listOf("等等", "先别改", "我再看看", "你好", "").forEach { input ->
            assertEquals(
                "「$input」不该触发执行",
                ChatDecision.PlainChat,
                AiChatLogic.decide(awaiting, input),
            )
        }
    }

    @Test
    fun `回复取消词走 Cancel 而不是当普通聊天`() {
        // 原站把「取消」单独分了一条分支：回一句「好的，已取消刚才的操作。」就**停**，
        // 不会再去问 AI——否则 AI 可能把同一份操作再吐一遍，确认框又弹回来
        listOf("取消", "算了", "不要", "不用", "放弃", "no", "NO", " No ").forEach { input ->
            assertEquals(
                "「$input」该走 Cancel",
                ChatDecision.Cancel,
                AiChatLogic.decide(awaiting, input),
            )
        }
    }

    @Test
    fun `取消词只认整句，不误伤正常说话`() {
        // 原站用子串匹配，所以「不要忘记买牛奶」也会被它判成取消。
        // 这里故意收严成整句相等：漏判只是退化成普通聊天，误判会让用户正常说话被当成取消
        assertEquals(
            ChatDecision.PlainChat,
            AiChatLogic.decide(awaiting, "不要忘记买牛奶"),
        )
    }

    @Test
    fun `没有挂起操作时，取消词也只是普通聊天`() {
        assertEquals(ChatDecision.PlainChat, AiChatLogic.decide(PendingState.None, "取消"))
    }

    // ---------- 提示文案 ----------

    @Test
    fun `提示文案与截图里的一致`() {
        val text = AiChatLogic.proposeMessage(listOf(addTask), modifyAllowed = true)
        assertTrue(text.contains("我拥有修改你计划与课表的权限（设置中可关闭）。"))
        assertTrue(text.contains("我准备执行以下 1 项操作："))
        assertTrue(text.contains("· ➕ 新增任务：开会"))
        assertTrue(text.contains("点下面的按钮执行，或回复「确认」；回复其他内容取消。"))
    }

    @Test
    fun `多项操作会逐条列出`() {
        val text = AiChatLogic.proposeMessage(listOf(addTask, deleteTask), modifyAllowed = true)
        assertTrue(text.contains("我准备执行以下 2 项操作："))
        assertTrue(text.contains("➕ 新增任务：开会"))
        assertTrue(text.contains("➖ 删除任务：买菜"))
    }

    @Test
    fun `关掉修改权限时明确说明不会执行`() {
        val text = AiChatLogic.proposeMessage(listOf(addTask), modifyAllowed = false)
        assertTrue(text.contains("你关掉了「允许 AI 修改计划与课表」"))
        assertFalse("关掉权限后不该再出现「回复确认」的引导", text.contains("回复「确认」即表示"))
    }

    // ---------- 数据快照 ----------

    @Test
    fun `没有数据时快照显示无`() {
        val s = AiChatLogic.snapshot(emptyList(), emptyList(), emptyList())
        assertEquals("（无）\n（无）\n（无）", s)
    }

    @Test
    fun `有数据时快照带上时间和名称`() {
        val s = AiChatLogic.snapshot(
            tasks = listOf("开会" to "15:00", "买菜" to ""),
            plans = listOf("每天喝水"),
            courses = listOf("周三 08:00 高等数学"),
        )
        assertTrue(s.contains("开会 15:00"))
        assertTrue(s.contains("买菜"))
        assertTrue(s.contains("每天喝水"))
        assertTrue(s.contains("周三 08:00 高等数学"))
    }

    // ---------- 挂起状态 ----------

    @Test
    fun `挂起状态能报出条数`() {
        assertEquals(2, awaiting.count)
        assertEquals(1, PendingState.Awaiting(listOf(addTask)).count)
    }
}
