package com.qingji.plan.ui.ai

import com.qingji.plan.data.remote.AiAction

/**
 * 待确认的操作。
 *
 * 这是整个 AI 功能**最关键的状态**：AI 返回操作后**先不执行**，
 * 必须等用户回复「确认」。状态必须显式保存，不能靠"上一条消息长什么样"去猜。
 */
sealed interface PendingState {
    /** 没有待确认的东西，正常聊天 */
    data object None : PendingState

    /** 有 N 项操作等着用户点头 */
    data class Awaiting(val actions: List<AiAction>) : PendingState {
        val count: Int get() = actions.size
    }
}

/** 用户输入之后该干什么 */
sealed interface ChatDecision {
    /** 执行挂起的那些操作 */
    data object Execute : ChatDecision

    /**
     * 用户明确说了「取消」→ 丢掉挂起的操作，**回一句就停**，别再去问 AI。
     *
     * 原站就是这么分的：它有三条分支，取消那条 `return` 掉，只有「说了别的」才继续走聊天。
     * 之前这里只有 Execute / PlainChat 两条，于是「算了」被当成普通聊天——
     * 取消了挂起操作不算，还会**再问一次 AI**，AI 要是把刚才那份操作重吐一遍，
     * 用户就会看到确认框又弹回来。
     */
    data object Cancel : ChatDecision

    /** 用户说了别的 → 丢掉挂起的操作，把这句话当普通聊天 */
    data object PlainChat : ChatDecision
}

/**
 * 聊天的状态机。纯逻辑，可以直接测。
 *
 * 规则（照抄原站的提示语文案）：
 *   > 回复「确认」即表示你同意并执行；回复其他内容取消。
 */
object AiChatLogic {

    /** 算作「同意」的回复。只认这两个词，避免误触发 */
    val CONFIRM_WORDS = listOf("确认", "确定")

    /**
     * 算作「取消」的回复。取原站正则 `/取消|算了|不要|不用|放弃|no/` 里的词。
     *
     * 注意这里跟原站有个取舍：原站用 `test()`（子串命中），所以「不要忘记买牛奶」
     * 也会被判成取消。这里要求**整句等于**其中某个词，宁可漏判也不误伤——
     * 漏判的后果只是退化成「当普通聊天」，误判的后果是用户正常说话被当成取消。
     */
    val CANCEL_WORDS = listOf("取消", "算了", "不要", "不用", "放弃", "no")

    fun isConfirm(text: String): Boolean {
        val t = text.trim()
        return CONFIRM_WORDS.any { it == t }
    }

    fun isCancel(text: String): Boolean {
        val t = text.trim().lowercase()
        return CANCEL_WORDS.any { it == t }
    }

    /**
     * 根据当前挂起状态和用户输入，决定下一步。
     *
     * - 没有挂起 → 普通聊天
     * - 有挂起 + 回复「确认」→ 执行
     * - 有挂起 + 回复「取消 / 算了 / …」→ 只回一句「已取消」，停下
     * - 有挂起 + 回复别的 → 丢掉挂起的操作，把这句话当普通聊天
     */
    fun decide(pending: PendingState, input: String): ChatDecision = when (pending) {
        PendingState.None -> ChatDecision.PlainChat
        is PendingState.Awaiting -> when {
            isConfirm(input) -> ChatDecision.Execute
            isCancel(input) -> ChatDecision.Cancel
            else -> ChatDecision.PlainChat
        }
    }

    /**
     * 用户还没确认时，给界面的提示文案。原文照抄原站。
     */
    fun proposeMessage(actions: List<AiAction>, modifyAllowed: Boolean): String = buildString {
        if (!modifyAllowed) {
            append("你关掉了「允许 AI 修改计划与课表」，所以这次我不能动手。")
            append("可以到「设置 → AI 助手」里打开。\n\n")
        } else {
            append("我拥有修改你计划与课表的权限（设置中可关闭）。")
        }
        append("我准备执行以下 ${actions.size} 项操作：\n")
        actions.forEach { append("· ").append(it.describe).append('\n') }
        append('\n')
        if (modifyAllowed) {
            // 现在气泡下面有按钮了，打字那条路仍然留着
            append("点下面的按钮执行，或回复「确认」；回复其他内容取消。")
        }
    }

    /**
     * 拼给 AI 的数据快照。原站会把现有数据一起发过去，
     * 这样 AI 才能拿"真实名称"去匹配。
     */
    fun snapshot(
        tasks: List<Pair<String, String>>,
        plans: List<String>,
        courses: List<String>,
    ): String = buildString {
        append(if (tasks.isEmpty()) "（无）" else tasks.joinToString("；") { (title, time) ->
            if (time.isBlank()) title else "$title $time"
        })
        append('\n')
        append(if (plans.isEmpty()) "（无）" else plans.joinToString("；"))
        append('\n')
        append(if (courses.isEmpty()) "（无）" else courses.joinToString("；"))
    }
}
