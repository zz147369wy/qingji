package com.qingji.plan.ui.plans

import com.qingji.plan.data.db.entity.LongTermPlanEntity
import com.qingji.plan.data.model.Cadence
import com.qingji.plan.data.model.Categories
import com.qingji.plan.data.model.Category
import com.qingji.plan.util.Ids
import com.qingji.plan.util.TimeUtils

/**
 * 重复方式的人话。**逐字对齐原站的 `Ix()`**：
 *
 * ```js
 * daily   → "每天"
 * weekly  → "每周" + 周几.map("一二三四五六日"[d]).join("/")   ← 用斜杠连
 * monthly → "每月" + dayOfMonth + "日"
 * ```
 *
 * ⚠️ 周几之间是 **`/`**，不是顿号也不是直接拼。原站写的是
 * `weekly:"每周"+(e.weekdays??[]).map(...).join("/")`，
 * 所以「周一三五」在原站是 `每周一/三/五`。别改成 `每周一三五`。
 */
fun cadenceText(plan: LongTermPlanEntity): String = when (plan.cadenceEnum) {
    Cadence.DAILY -> "每天"
    Cadence.WEEKLY -> {
        val days = plan.weekdays.sorted().mapNotNull { "一二三四五六日".getOrNull(it) }
        if (days.isEmpty()) "每周" else "每周" + days.joinToString("/")
    }
    Cadence.MONTHLY -> "每月${plan.dayOfMonth ?: 1}日"
}

/**
 * 长期计划行上那组小标签的内容，跟原站 `plan-row` 的 `plan-meta` 逐个对应。
 *
 * | 这里 | 原站 |
 * |---|---|
 * | [cadenceText] | `.tag-longterm`（前面还带个循环图标） |
 * | [category] | `.cat-tag`（底色/字色取 `cat.soft` / `cat.deep`，带 -1.5° 倾斜） |
 * | [timeText] | `.time-chip`，`ny(time, endTime, durationMinutes)` |
 * | [durationText] | `.dur-tag`，`rd(durationMinutes)` |
 * | [startDateText] | `.muted.small`，`{startDate}起` |
 */
data class PlanMeta(
    val cadenceText: String,
    val category: Category,
    /** 没排时间就是 null —— 那一整个 `.time-chip` 都不画 */
    val timeText: String?,
    /** 原站的判断是「有时间**且**有时长」才画 `.dur-tag` */
    val durationText: String?,
    val startDateText: String,
)

fun planMetaOf(plan: LongTermPlanEntity, customs: List<Category> = emptyList()): PlanMeta {
    // 原站给 ny 传了 endTime，但我们的长期计划表没有这一列 —— 原站那个 endTime
    // 也是保存时由 time + duration 算出来的（`endTime: _ && F ? lr(_,F) : void 0`），
    // 所以这里传 null 让它按同一个公式算，结果一样。
    val timeText = plan.time?.let { TimeUtils.chipTimeLabel(it, null, plan.durationMinutes) }
    return PlanMeta(
        cadenceText = cadenceText(plan),
        category = Categories.of(plan.category, customs),
        timeText = timeText,
        durationText = timeText?.let { plan.durationMinutes?.let(TimeUtils::formatDuration) },
        startDateText = "${plan.startDate}起",
    )
}

/**
 * 长期计划编辑器里填出来的一份数据。
 *
 * 新建和编辑**共用**这一个类 —— 原站也是同一个弹层两用
 * （`Lx({open, plan})`：`plan` 有值就是编辑，没有就是新建）。
 */
data class PlanDraft(
    val title: String,
    val cadence: Cadence,
    val weekdays: List<Int>,
    val dayOfMonth: Int?,
    val time: String?,
    val durationMinutes: Int?,
    val category: String,
) {

    /** 周重复必须至少挑一天，其余只要有名字就能存 */
    val canSave: Boolean
        get() = title.isNotBlank() && (cadence != Cadence.WEEKLY || weekdays.isNotEmpty())

    /**
     * 折成实体。[existing] 为 null 表示新建 —— 这时 `id` 留 0，
     * 由 [com.qingji.plan.data.repo.LongTermPlanRepository.save] 认出来走 insert。
     */
    fun toEntity(existing: LongTermPlanEntity?, now: Long): LongTermPlanEntity = LongTermPlanEntity(
        id = existing?.id ?: 0L,
        // uid 必须沿用旧的：生成出来的任务靠 planUid 关联，换了 uid 就断了
        uid = existing?.uid ?: Ids.uid(),
        title = title.trim(),
        cadence = cadence.value,
        weekdays = if (cadence == Cadence.WEEKLY) {
            weekdays.filter { it in 0..6 }.distinct().sorted()
        } else {
            emptyList()
        },
        dayOfMonth = if (cadence == Cadence.MONTHLY) dayOfMonth else null,
        time = time,
        // 原站：`durationMinutes: x ? N||15 : undefined` —— 没排时间就没有时长
        durationMinutes = if (time != null) durationMinutes ?: TimeUtils.MIN_DURATION else null,
        category = category,
        // createdAt 原样保留 —— startDate 是从它推出来的，改了就动了「从哪天起」
        createdAt = existing?.createdAt ?: now,
        updatedAt = now,
        deletedAt = existing?.deletedAt,
    )

    companion object {

        /** 原站新建时的默认值：每天、15 分、生活分类 */
        val DEFAULT_CATEGORY: String = "life"

        /**
         * 拿一条已有计划铺进编辑器；[plan] 为 null 就是新建的默认值。
         *
         * ⚠️ 新建时**时间默认留空**（进「随时」区），这跟原站不一样 ——
         * 原站默认填当前时刻。留空是用户 2026-09-28 明确要的
         * （「没指定时间的长期计划单独一栏放在时间轴上方」），别改回去。
         */
        fun from(plan: LongTermPlanEntity?): PlanDraft = if (plan == null) {
            PlanDraft(
                title = "",
                cadence = Cadence.DAILY,
                weekdays = emptyList(),
                dayOfMonth = 1,
                time = null,
                durationMinutes = TimeUtils.MIN_DURATION,
                category = DEFAULT_CATEGORY,
            )
        } else {
            PlanDraft(
                title = plan.title,
                cadence = plan.cadenceEnum,
                weekdays = plan.weekdays,
                dayOfMonth = plan.dayOfMonth ?: 1,
                time = plan.time,
                durationMinutes = plan.durationMinutes ?: TimeUtils.MIN_DURATION,
                category = plan.category,
            )
        }
    }
}
