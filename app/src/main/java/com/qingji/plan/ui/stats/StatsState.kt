package com.qingji.plan.ui.stats

import com.qingji.plan.data.db.entity.LongTermPlanEntity
import com.qingji.plan.data.db.entity.ReviewEntity
import com.qingji.plan.data.db.entity.TaskEntity
import com.qingji.plan.data.model.Categories
import com.qingji.plan.data.model.Category
import com.qingji.plan.util.DateUtils
import java.time.LocalDate
import kotlin.math.roundToInt

/** 趋势图上的一根柱子 */
data class TrendBar(
    val date: LocalDate,
    /** 柱子上方显示的数字，原站显示的是几号 */
    val dayOfMonth: Int,
    val doneCount: Int,
    val isToday: Boolean,
)

/** 类别占比里的一项 */
data class CategoryShare(
    val categoryId: String,
    val label: String,
    val count: Int,
    val percent: Int,
)

/**
 * 统计页的全部展示状态。
 *
 * 四个指标卡的文案和格式都对着原站截图写：
 *   累计完成 / 完成率 / 平均分 / 结算天数
 * 空数据时平均分显示的是一个破折号「—」，不是 0。
 */
data class StatsUiState(
    val totalDone: Int,
    val totalTasks: Int,
    /** 0-100 */
    val completionRate: Int,
    /** 没有结算记录时为 null，界面上显示「—」 */
    val averageScore: Double?,
    val settleDays: Int,
    val trend: List<TrendBar>,
    /** 柱状图的纵轴上限，至少为 1，避免全零时除零 */
    val trendMax: Int,
    val categoryShares: List<CategoryShare>,
    val recentReviews: List<ReviewEntity>,
    val totalPlans: Int,
) {
    val hasTasks: Boolean get() = totalTasks > 0

    val hasReviews: Boolean get() = recentReviews.isNotEmpty()

    /** 平均分显示文本。没有记录时是破折号 */
    val averageScoreText: String
        get() = averageScore?.let { formatScore(it) } ?: "—"

    companion object {
        fun formatScore(value: Double): String {
            val rounded = (value * 10).roundToInt() / 10.0
            return if (rounded % 1.0 == 0.0) rounded.toInt().toString() else rounded.toString()
        }
    }
}

/**
 * 统计页的计算。纯函数，可以直接测。
 */
object StatsCalculator {

    /** 趋势图显示最近多少天。原站是 14 天 */
    const val TREND_DAYS = 14

    fun compute(
        today: LocalDate,
        tasks: List<TaskEntity>,
        reviews: List<ReviewEntity>,
        plans: List<LongTermPlanEntity> = emptyList(),
        customs: List<Category> = emptyList(),
    ): StatsUiState {
        val aliveTasks = tasks.filter { it.deletedAt == null }
        val alivePlans = plans.filter { it.deletedAt == null }

        val total = aliveTasks.size
        val done = aliveTasks.count { it.isDone }

        // 完成率：没有任何任务时是 0，不是 NaN
        val rate = if (total == 0) 0 else (done * 100.0 / total).roundToInt()

        // 平均分只算有记录的
        val scores = reviews.map { it.score }
        val average = if (scores.isEmpty()) null else scores.average()

        val trend = buildTrend(today, aliveTasks)

        return StatsUiState(
            totalDone = done,
            totalTasks = total,
            completionRate = rate,
            averageScore = average,
            settleDays = reviews.size,
            trend = trend,
            trendMax = trend.maxOfOrNull { it.doneCount }?.coerceAtLeast(1) ?: 1,
            categoryShares = buildShares(aliveTasks, customs),
            recentReviews = reviews.sortedByDescending { it.date },
            totalPlans = alivePlans.size,
        )
    }

    /**
     * 最近 14 天的每天完成数。最后一天是今天。
     */
    fun buildTrend(today: LocalDate, tasks: List<TaskEntity>): List<TrendBar> {
        val doneByDate = tasks.filter { it.isDone }
            .groupingBy { it.date }
            .eachCount()

        return (TREND_DAYS - 1 downTo 0).map { back ->
            val date = today.minusDays(back.toLong())
            TrendBar(
                date = date,
                dayOfMonth = date.dayOfMonth,
                doneCount = doneByDate[DateUtils.toKey(date)] ?: 0,
                isToday = date == today,
            )
        }
    }

    /** 纵轴上限。至少 1，避免空数据时除零 */
    fun trendMax(today: LocalDate, tasks: List<TaskEntity>): Int =
        buildTrend(today, tasks).maxOfOrNull { it.doneCount }?.coerceAtLeast(1) ?: 1

    /**
     * 类别占比。按任务数从多到少排，占比是四舍五入的百分比。
     */
    fun buildShares(tasks: List<TaskEntity>, customs: List<Category> = emptyList()): List<CategoryShare> {
        if (tasks.isEmpty()) return emptyList()

        val total = tasks.size
        return tasks.groupingBy { it.category }
            .eachCount()
            .map { (categoryId, count) ->
                CategoryShare(
                    categoryId = categoryId,
                    label = Categories.of(categoryId, customs).label,
                    count = count,
                    percent = (count * 100.0 / total).roundToInt(),
                )
            }
            .sortedWith(compareByDescending<CategoryShare> { it.count }.thenBy { it.label })
    }
}
