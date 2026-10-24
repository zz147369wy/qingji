package com.qingji.plan.data.remote

import kotlinx.serialization.json.Json

/**
 * 把 AI 返回的文本解析成操作清单。
 *
 * **模型不听话是常态**，所以有三道防线（照抄原站策略）：
 *   1. 先剥掉 ```` ```json ```` 代码块
 *   2. 直接 JSON 解析
 *   3. 失败就用正则从文本里抠出第一个 `{...}` 再试
 *   4. 最后**白名单过滤**，只保留认识的 8 种 op
 *
 * 全失败就返回空清单，**绝不抛异常**——AI 胡说八道不该让 App 崩。
 */
object AiActionParser {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    private val CODE_BLOCK = Regex("```(?:json)?\\s*([\\s\\S]*?)```")
    private val JSON_OBJECT = Regex("\\{[\\s\\S]*\\}")

    /** 解析结果：拿到了哪些操作 */
    fun parse(raw: String?): List<AiAction> {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty()) return emptyList()

        // 第一道：剥代码块
        val stripped = CODE_BLOCK.find(text)?.groupValues?.get(1)?.trim() ?: text

        // 第二道：直接解析
        parseObject(stripped)?.let { return filterKnown(it) }

        // 第三道：从文本里抠 JSON
        JSON_OBJECT.find(stripped)?.value?.let { candidate ->
            parseObject(candidate)?.let { return filterKnown(it) }
        }

        return emptyList()
    }

    private fun parseObject(text: String): List<AiAction>? =
        runCatching { json.decodeFromString<AiActionList>(text).actions }.getOrNull()

    /** 白名单过滤：丢掉不认识的 op、没有必需字段的、以及标题为空的 */
    private fun filterKnown(actions: List<AiAction>): List<AiAction> =
        actions.filter { it.isKnown && it.hasRequiredFields() }

    /**
     * 每条操作都要有它必需的那部分数据，否则执行时会出诡异错误。
     */
    private fun AiAction.hasRequiredFields(): Boolean = when (op) {
        "addTask" -> !task?.title.isNullOrBlank()
        "editTask" -> !match?.title.isNullOrBlank() && task != null
        "deleteTask" -> !match?.title.isNullOrBlank()
        "addPlan" -> !plan?.title.isNullOrBlank()
        "deletePlan" -> !match?.title.isNullOrBlank()
        "addCourse" -> !course?.title.isNullOrBlank()
        "editCourse" -> !match?.title.isNullOrBlank() && course != null
        "deleteCourse" -> !match?.title.isNullOrBlank()
        else -> false
    }

    /**
     * 标题匹配规则，与原站一字不差：
     *   完全相同，或一方包含另一方（且长度 ≥ 2）就算命中。
     */
    fun titleMatches(existing: String?, candidate: String?): Boolean {
        val a = existing?.trim().orEmpty()
        val b = candidate?.trim().orEmpty()
        if (a.isEmpty() || b.isEmpty()) return false
        if (a == b) return true
        if (b.length >= 2 && a.contains(b)) return true
        if (a.length >= 2 && b.contains(a)) return true
        return false
    }
}
