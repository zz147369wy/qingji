package com.qingji.plan.data.model

/**
 * 任务状态。原站用字符串 `"todo"` / `"done"` 存。
 */
enum class TaskStatus(val value: String) {
    TODO("todo"),
    DONE("done");

    companion object {
        fun from(value: String?): TaskStatus =
            entries.firstOrNull { it.value == value } ?: TODO
    }
}

/**
 * 优先级。原站只有两档：`"mid"`（默认）/ `"high"`（显示红色「重要」标签）。
 */
enum class Priority(val value: String) {
    MID("mid"),
    HIGH("high");

    companion object {
        fun from(value: String?): Priority =
            entries.firstOrNull { it.value == value } ?: MID
    }
}

/**
 * 长期计划的重复方式。
 * 原站定义：`cadence(daily/weekly/monthly)`。
 */
enum class Cadence(val value: String) {
    DAILY("daily"),
    WEEKLY("weekly"),
    MONTHLY("monthly");

    val label: String
        get() = when (this) {
            DAILY -> "每天"
            WEEKLY -> "每周"
            MONTHLY -> "每月"
        }

    companion object {
        fun from(value: String?): Cadence =
            entries.firstOrNull { it.value == value } ?: DAILY
    }
}

/**
 * 任务来源。原站给 AI 建的任务打 `"ai"` 标记。
 */
enum class TaskSource(val value: String) {
    MANUAL("manual"),
    AI("ai");

    companion object {
        fun from(value: String?): TaskSource =
            entries.firstOrNull { it.value == value } ?: MANUAL
    }
}
