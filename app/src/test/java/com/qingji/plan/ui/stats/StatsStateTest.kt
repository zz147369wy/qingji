package com.qingji.plan.ui.stats

import com.qingji.plan.data.db.entity.LongTermPlanEntity
import com.qingji.plan.data.db.entity.ReviewEntity
import com.qingji.plan.data.db.entity.TaskEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * 统计页计算测试。
 */
class StatsStateTest {

    private val today = LocalDate.of(2026, 9, 26)
    private val now = 1_800_000_000_000L

    private fun task(
        title: String,
        date: LocalDate = today,
        done: Boolean = false,
        category: String = "work",
        deletedAt: Long? = null,
    ) = TaskEntity(
        uid = "u_$title",
        date = com.qingji.plan.util.DateUtils.toKey(date),
        title = title,
        category = category,
        status = if (done) "done" else "todo",
        createdAt = now, updatedAt = now, deletedAt = deletedAt,
    )

    private fun review(date: String, score: Int) = ReviewEntity(
        uid = "r_$date", date = date, score = score,
        doneCount = 1, totalCount = 1, onTimeCount = 1,
        summary = "完成 1/1 项", feedback = "f", improvement = "i",
        createdAt = now, updatedAt = now,
    )

    private fun compute(
        tasks: List<TaskEntity> = emptyList(),
        reviews: List<ReviewEntity> = emptyList(),
        plans: List<LongTermPlanEntity> = emptyList(),
    ) = StatsCalculator.compute(today, tasks, reviews, plans)

    // ---------- 四个指标 ----------

    @Test
    fun `累计完成数只算已完成的`() {
        val s = compute(
            tasks = listOf(
                task("A", done = true), task("B", done = true), task("C", done = false),
            ),
        )
        assertEquals(2, s.totalDone)
        assertEquals(3, s.totalTasks)
    }

    @Test
    fun `完成率四舍五入到整数`() {
        // 1/3 = 33.33% → 33
        assertEquals(33, compute(tasks = listOf(task("A", done = true), task("B"), task("C"))).completionRate)
    }

    @Test
    fun `没有任务时完成率是零而不是崩溃`() {
        val s = compute()
        assertEquals(0, s.completionRate)
        assertEquals(0, s.totalTasks)
    }

    @Test
    fun `平均分是结算记录的平均值`() {
        val s = compute(reviews = listOf(review("2026-09-24", 80), review("2026-09-25", 60)))
        assertEquals(70.0, s.averageScore!!, 0.001)
        assertEquals(2, s.settleDays)
    }

    @Test
    fun `没有结算记录时平均分是空`() {
        assertNull(compute().averageScore)
    }

    @Test
    fun `平均分显示文本——整数不带小数点`() {
        assertEquals("70", compute(reviews = listOf(review("2026-09-24", 80), review("2026-09-25", 60))).averageScoreText)
    }

    @Test
    fun `平均分显示文本——有小数时保留一位`() {
        assertEquals("75.5", compute(reviews = listOf(review("2026-09-24", 80), review("2026-09-25", 71))).averageScoreText)
    }

    @Test
    fun `没有结算记录时平均分显示破折号`() {
        assertEquals("—", compute().averageScoreText)
    }

    @Test
    fun `结算天数等于记录条数`() {
        val s = compute(reviews = listOf(review("2026-09-24", 80), review("2026-09-25", 60), review("2026-09-26", 90)))
        assertEquals(3, s.settleDays)
    }

    // ---------- 软删除 ----------

    @Test
    fun `已删除的任务不计入统计`() {
        val s = compute(
            tasks = listOf(
                task("正常的", done = true),
                task("删掉的", done = true, deletedAt = now),
            ),
        )
        assertEquals(1, s.totalTasks)
        assertEquals(1, s.totalDone)
    }

    // ---------- 近 14 天趋势 ----------

    @Test
    fun `趋势固定十四天`() {
        assertEquals(14, compute().trend.size)
    }

    @Test
    fun `趋势最后一天是今天`() {
        val trend = compute().trend
        assertEquals(today, trend.last().date)
        assertTrue(trend.last().isToday)
        assertTrue("只有最后一天是今天", trend.dropLast(1).none { it.isToday })
    }

    @Test
    fun `趋势第一天是十三天前`() {
        assertEquals(today.minusDays(13), compute().trend.first().date)
    }

    @Test
    fun `趋势按日期统计完成数`() {
        val s = compute(
            tasks = listOf(
                task("今天完成的", today, done = true),
                task("今天完成的2", today, done = true),
                task("昨天完成的", today.minusDays(1), done = true),
                task("今天没完成", today, done = false),
            ),
        )
        assertEquals(2, s.trend.last().doneCount)
        assertEquals(1, s.trend[12].doneCount)
        // 未完成的不会算进去
        assertEquals(0, s.trend[5].doneCount)
    }

    @Test
    fun `十四天之外的完成不算进趋势`() {
        val s = compute(tasks = listOf(task("很久以前", today.minusDays(20), done = true)))
        assertEquals(0, s.trend.sumOf { it.doneCount })
    }

    @Test
    fun `趋势纵轴上限至少为一`() {
        assertEquals(1, compute().trendMax)
    }

    @Test
    fun `趋势纵轴上限取当天最大值`() {
        val s = compute(
            tasks = listOf(
                task("A", today, done = true), task("B", today, done = true),
                task("C", today, done = true),
            ),
        )
        assertEquals(3, s.trendMax)
    }

    @Test
    fun `柱子上显示的是几号`() {
        val trend = compute().trend
        assertEquals(26, trend.last().dayOfMonth)
    }

    // ---------- 类别占比 ----------

    @Test
    fun `没有任务时没有类别占比`() {
        assertTrue(compute().categoryShares.isEmpty())
    }

    @Test
    fun `类别占比按数量从多到少排`() {
        val s = compute(
            tasks = listOf(
                task("w1", category = "work"), task("w2", category = "work"),
                task("w3", category = "work"), task("s1", category = "study"),
            ),
        )
        assertEquals(listOf("工作", "学习"), s.categoryShares.map { it.label })
        assertEquals(listOf(3, 1), s.categoryShares.map { it.count })
    }

    @Test
    fun `类别占比的百分比四舍五入`() {
        val s = compute(
            tasks = listOf(
                task("w1", category = "work"), task("w2", category = "work"),
                task("w3", category = "work"), task("s1", category = "study"),
            ),
        )
        assertEquals(75, s.categoryShares[0].percent)
        assertEquals(25, s.categoryShares[1].percent)
    }

    @Test
    fun `未知分类归到其他`() {
        val s = compute(tasks = listOf(task("x", category = "不存在的分类")))
        assertEquals(listOf("其他"), s.categoryShares.map { it.label })
    }

    // ---------- 最近结算记录 ----------

    @Test
    fun `结算记录按日期从新到旧`() {
        val s = compute(
            reviews = listOf(review("2026-09-20", 50), review("2026-09-25", 90), review("2026-09-22", 70)),
        )
        assertEquals(listOf("2026-09-25", "2026-09-22", "2026-09-20"), s.recentReviews.map { it.date })
    }

    @Test
    fun `没有结算记录时标记为空`() {
        assertTrue(!compute().hasReviews)
        assertTrue(compute(reviews = listOf(review("2026-09-25", 90))).hasReviews)
    }

    // ---------- 长期计划计数 ----------

    @Test
    fun `长期计划数不计已删除的`() {
        val s = compute(
            plans = listOf(
                LongTermPlanEntity(uid = "p1", title = "喝水", cadence = "daily", createdAt = now, updatedAt = now),
                LongTermPlanEntity(uid = "p2", title = "删掉的", cadence = "daily", createdAt = now, updatedAt = now, deletedAt = now),
            ),
        )
        assertEquals(1, s.totalPlans)
    }
}
