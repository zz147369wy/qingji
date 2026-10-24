package com.qingji.plan.data.db.entity

import androidx.room.Entity
import kotlinx.serialization.Serializable
import androidx.room.Index
import androidx.room.PrimaryKey
import com.qingji.plan.data.model.Cadence
import com.qingji.plan.util.DateUtils
import java.time.LocalDate

/**
 * 长期计划：每天 / 每周 / 每月自动往「今日」生成任务。
 *
 * 对应原站的 longTermPlans 表。`weekdays` 里 0 表示周一（原站约定，和
 * JS 的 Date.getDay() 不同，别搞混）。
 */
@Entity(
    tableName = "long_term_plans",
    indices = [Index("uid"), Index("deletedAt")],
)
@Serializable
data class LongTermPlanEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val uid: String,
    val title: String,
    /** daily / weekly / monthly */
    val cadence: String = "daily",
    /** 周几，0 = 周一。仅 weekly 用 */
    val weekdays: List<Int> = emptyList(),
    /** 每月几号，仅 monthly 用 */
    val dayOfMonth: Int? = null,
    /** 生成的任务排在几点 `HH:mm`，可空 */
    val time: String? = null,
    val durationMinutes: Int? = null,
    val category: String = "other",

    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
) {
    val cadenceEnum: Cadence get() = Cadence.from(cadence)

    /**
     * 计划「从哪天起」生效，`"2026-09-27"`。
     *
     * 原站这是个**真实字段** `startDate`：新建时定死成当天（`startDate: De(Ye())`），
     * 生成任务时拿它当左边界（`if (d < a.startDate) continue`），
     * 而编辑器 `Lx` 里**根本没有让用户改它的输入**。
     *
     * 既然它永远等于「建这条计划的那天」，这里就**从 [createdAt] 推**出来 ——
     * 不额外占一列、不用改表结构、也不用写 Room 迁移。
     */
    val startDate: String
        get() = DateUtils.toKey(
            java.time.Instant.ofEpochMilli(createdAt)
                .atZone(java.time.ZoneId.systemDefault())
                .toLocalDate()
        )

    /**
     * 这一天要不要生成任务？
     */
    fun matches(date: LocalDate): Boolean = when (cadenceEnum) {
        Cadence.DAILY -> true
        // 原站 weekdays 用 0=周一，java.time 的 dayOfWeek.value 是 1=周一
        Cadence.WEEKLY -> weekdays.contains(date.dayOfWeek.value - 1)
        Cadence.MONTHLY -> dayOfMonth == date.dayOfMonth
    }

    /** 规则的中文描述，列表里显示用。如「每周 一、三、五」 */
    fun ruleLabel(): String = when (cadenceEnum) {
        Cadence.DAILY -> "每天"
        Cadence.WEEKLY -> {
            val names = listOf("一", "二", "三", "四", "五", "六", "日")
            val picked = weekdays.sorted().mapNotNull { names.getOrNull(it) }
            if (picked.isEmpty()) "每周" else "每周${picked.joinToString("、")}"
        }
        Cadence.MONTHLY -> "每月 ${dayOfMonth ?: 1} 日"
    }

    fun softDeleted(at: Long = System.currentTimeMillis()): LongTermPlanEntity =
        copy(deletedAt = at, updatedAt = at)

    /** 便于日志与调试 */
    fun describe(): String = "$title · ${ruleLabel()} · ${DateUtils.dateTitle(LocalDate.now())}"
}
