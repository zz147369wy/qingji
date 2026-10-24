package com.qingji.plan.data.remote

import kotlinx.serialization.Serializable

/**
 * AI 返回的一条操作。
 *
 * 字段是可选的，用哪个取决于 `op`：
 *   addTask    → task
 *   editTask   → match + task
 *   deleteTask → match
 *   addPlan    → plan
 *   deletePlan → match
 *   addCourse  → course
 *   editCourse → match + course
 *   deleteCourse → match
 */
@Serializable
data class AiAction(
    val op: String,
    val match: MatchSpec? = null,
    val task: TaskSpec? = null,
    val plan: PlanSpec? = null,
    val course: CourseSpec? = null,
) {
    /** 认不认识这个操作 */
    val isKnown: Boolean get() = op in KNOWN_OPS

    /**
     * 给用户看的一行说明，显示在「我准备执行以下 N 项操作」下面。
     * 前缀符号与原站一致：➕ 新增 / ✏️ 修改 / ➖ 删除
     */
    val describe: String
        get() {
            val title = task?.title ?: plan?.title ?: course?.title ?: match?.title ?: "未命名"
            return when (op) {
                "addTask" -> "➕ 新增任务：$title"
                "editTask" -> "✏️ 修改任务：$title"
                "deleteTask" -> "➖ 删除任务：$title"
                "addPlan" -> "➕ 新增长期计划：$title"
                "deletePlan" -> "➖ 删除长期计划：$title"
                "addCourse" -> "➕ 新增课程：$title"
                "editCourse" -> "✏️ 修改课程：$title"
                "deleteCourse" -> "➖ 删除课程：$title"
                else -> "❔ 未知操作：$op"
            }
        }

    companion object {
        /** 白名单。AI 返回的其它 op 一律丢掉 */
        val KNOWN_OPS = listOf(
            "addTask", "editTask", "deleteTask",
            "addPlan", "deletePlan",
            "addCourse", "editCourse", "deleteCourse",
        )
    }
}

/** 用来定位已有数据。原站靠标题匹配，大小写与包含都算命中 */
@Serializable
data class MatchSpec(
    val title: String = "",
    val date: String? = null,
)

/** 任务的可选字段。只填要改的那些 */
@Serializable
data class TaskSpec(
    val title: String? = null,
    val date: String? = null,
    val time: String? = null,
    val endTime: String? = null,
    val durationMinutes: Int? = null,
    val category: String? = null,
    val priority: String? = null,
)

/** 长期计划的可选字段 */
@Serializable
data class PlanSpec(
    val title: String? = null,
    val cadence: String? = null,
    val weekdays: List<Int>? = null,
    val dayOfMonth: Int? = null,
    val time: String? = null,
    val durationMinutes: Int? = null,
    val category: String? = null,
)

/** 课程的可选字段 */
@Serializable
data class CourseSpec(
    val title: String? = null,
    val weekday: Int? = null,
    val time: String? = null,
    val durationMinutes: Int? = null,
    val room: String? = null,
    val category: String? = null,
)

/** AI 一次返回的操作清单 */
@Serializable
data class AiActionList(
    val actions: List<AiAction> = emptyList(),
)
