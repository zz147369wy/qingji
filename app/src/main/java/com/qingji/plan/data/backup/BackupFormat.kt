package com.qingji.plan.data.backup

import com.qingji.plan.data.db.entity.ChatLogEntity
import com.qingji.plan.data.db.entity.CourseEntity
import com.qingji.plan.data.db.entity.LongTermPlanEntity
import com.qingji.plan.data.db.entity.ReviewEntity
import com.qingji.plan.data.db.entity.SettingEntity
import com.qingji.plan.data.db.entity.TaskEntity
import kotlinx.serialization.Serializable

/**
 * 备份文件的格式。
 *
 * **整个 App 的全部数据**打包成一个 JSON：
 *   任务 / 长期计划 / 课程 / 结算记录 / 设置 / AI 对话
 *
 * `version` 字段是给未来留的后路 —— 以后格式变了，
 * 导入时能认出旧文件并给出明确提示，而不是静默失败。
 */
@Serializable
data class BackupFile(
    /** 格式版本。以后改结构要 +1 */
    val version: Int = CURRENT_VERSION,
    /** 导出时刻（毫秒时间戳） */
    val exportedAt: Long,
    /** 导出时 App 的版本名，仅供人看 */
    val appVersion: String = "",
    /** 各类数据的条数。导入前预览用，也方便人肉检查 */
    val counts: BackupCounts = BackupCounts(),
    val tasks: List<TaskEntity> = emptyList(),
    val plans: List<LongTermPlanEntity> = emptyList(),
    val courses: List<CourseEntity> = emptyList(),
    val reviews: List<ReviewEntity> = emptyList(),
    val settings: List<SettingEntity> = emptyList(),
    val chatLog: List<ChatLogEntity> = emptyList(),
) {
    companion object {
        /** 当前格式版本 */
        const val CURRENT_VERSION = 1
    }
}

/**
 * 各类数据的条数。
 *
 * 导入前先给用户看清楚「这个备份里有什么」，再让他决定要不要覆盖。
 */
@Serializable
data class BackupCounts(
    val tasks: Int = 0,
    val plans: Int = 0,
    val courses: Int = 0,
    val reviews: Int = 0,
    val settings: Int = 0,
    val chatLog: Int = 0,
) {
    val total: Int get() = tasks + plans + courses + reviews + settings + chatLog

    /** 「任务 12 条 · 课程 5 条 · 结算 30 条 …」 */
    fun describe(): String = buildList {
        if (tasks > 0) add("任务 $tasks 条")
        if (plans > 0) add("长期计划 $plans 条")
        if (courses > 0) add("课程 $courses 条")
        if (reviews > 0) add("结算记录 $reviews 条")
        if (settings > 0) add("设置 $settings 项")
        if (chatLog > 0) add("AI 对话 $chatLog 条")
    }.joinToString(" · ").ifEmpty { "空备份" }
}

/**
 * 导入时可能出的问题。
 *
 * 每一种都要给人话，不能只说「导入失败」。
 */
sealed class BackupError(message: String) : Exception(message) {
    /** 文件读不出来 */
    class Unreadable(detail: String) : BackupError("文件读不出来：$detail")

    /** 不是合法 JSON */
    class NotJson(detail: String) : BackupError("这个文件不是「鲸记」的备份（内容不是合法 JSON）：$detail")

    /** 版本不认识 */
    class UnknownVersion(found: Int) : BackupError(
        "这个备份的格式版本是 $found，当前 App 只认到 ${BackupFile.CURRENT_VERSION}。" +
            "可能是用更新版本的 App 导出的，请先把 App 更新到最新版。"
    )

    /** 内容为空 */
    class Empty : BackupError("这个备份里一条数据都没有，可能导出时出了问题")
}
