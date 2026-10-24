package com.qingji.plan.ui.today

import com.qingji.plan.data.db.entity.TaskEntity
import com.qingji.plan.util.DateUtils
import java.time.LocalDate
import kotlin.math.roundToInt

/** 任务筛选 */
enum class TaskFilter(val label: String) {
    ALL("全部"),
    TODO("待办"),
    DONE("已完成"),
}

/**
 * 今日页的全部展示状态。
 */
data class TodayUiState(
    val date: LocalDate,
    val isToday: Boolean,

    /** 当天的全部任务（未筛选） */
    val allTasks: List<TaskEntity>,
    /** 按当前筛选条件过滤后的任务 */
    val visibleTasks: List<TaskEntity>,
    val filter: TaskFilter,

    val totalCount: Int,
    val doneCount: Int,
    val todoCount: Int,
    /** 完成百分比 0-100 */
    val percent: Int,

    /** 顶部问候语，如「晚上好，今天辛苦了」 */
    val greetingTitle: String,
    /** 顶部副标题，如「9月26日 周六 · 今天 · 6 项计划」 */
    val subtitle: String,
    /** 进度卡主文案 */
    val ringTitle: String,
    /** 进度卡副文案 */
    val ringHint: String,

    /** 有时间、要排在时间轴上的任务 */
    val timedTasks: List<TaskEntity>,
    /** 没有时间、放在「🕊 随时」区的任务 */
    val anytimeTasks: List<TaskEntity>,
    /**
     * 长期计划生成出来的、**没有排时间**的任务。
     *
     * 跟「随时」区分开显示：随时的多是临时想起来的事，长期计划是每天都要做的固定项，
     * 混在一起分不清。它们单独占时间轴上方的一栏（用户要求）。
     */
    val longTermTasks: List<TaskEntity>,
) {
    val isEmpty: Boolean get() = allTasks.isEmpty()

    /**
     * 按当前筛选过滤后、真正画在列表里的三组任务。
     *
     * 【2026-10】以前列表用的是未筛选的 [timedTasks] 等，筛选胶囊只改了计数 ——
     * 点「待办」列表纹丝不动，是个 bug。现在列表走这三个。
     *
     * [lingering]：刚在「待办」里完成、还要再留一小会儿的任务（让它冒完泡泡再离开），
     * 见 [TodayStateCalculator.matches]。
     */
    fun shownTimed(lingering: Set<String> = emptySet()) =
        timedTasks.filter { TodayStateCalculator.matches(it, filter, lingering) }

    fun shownAnytime(lingering: Set<String> = emptySet()) =
        anytimeTasks.filter { TodayStateCalculator.matches(it, filter, lingering) }

    fun shownLongTerm(lingering: Set<String> = emptySet()) =
        longTermTasks.filter { TodayStateCalculator.matches(it, filter, lingering) }

    /** 筛选按钮上显示的计数 */
    fun countFor(f: TaskFilter): Int = when (f) {
        TaskFilter.ALL -> totalCount
        TaskFilter.TODO -> todoCount
        TaskFilter.DONE -> doneCount
    }
}

/**
 * 今日页状态计算。
 *
 * 全部是纯函数，不碰数据库也不碰安卓——所以能直接在 JVM 上跑测试。
 * 文案三态的判断规则照抄原站源码，一个字都不改。
 */
object TodayStateCalculator {

    fun compute(
        date: LocalDate,
        today: LocalDate,
        tasks: List<TaskEntity>,
        filter: TaskFilter = TaskFilter.ALL,
        hour: Int = 0,
    ): TodayUiState {
        // 已删除的任务不参与任何计算
        val alive = tasks.filter { it.deletedAt == null }

        val total = alive.size
        val done = alive.count { it.isDone }
        val todo = total - done
        val percent = if (total == 0) 0 else (done * 100.0 / total).roundToInt()

        val visible = alive.filter { matches(it, filter) }

        val timed = alive.filter { it.hasTime }
            .sortedBy { it.time }
        // 没时间的分两类：长期计划生成的单独一栏，其余进「随时」
        val anytime = alive.filter { !it.hasTime && !it.isLongTerm }
        val longTerm = alive.filter { !it.hasTime && it.isLongTerm }

        return TodayUiState(
            date = date,
            isToday = date == today,
            allTasks = alive,
            visibleTasks = visible,
            filter = filter,
            totalCount = total,
            doneCount = done,
            todoCount = todo,
            percent = percent,
            greetingTitle = DateUtils.greetingTitle(hour),
            subtitle = DateUtils.todaySubtitle(date, today, total),
            ringTitle = ringTitle(done, total),
            ringHint = ringHint(percent),
            timedTasks = timed,
            anytimeTasks = anytime,
            longTermTasks = longTerm,
        )
    }

    /**
     * 任务在当前筛选下该不该显示。
     *
     * [lingering] 里的 uid 在「待办」筛选下照样显示 —— 刚完成的那一行要先把泡泡冒完、
     * 再从列表里游走，而不是手指一抬就凭空消失。
     */
    fun matches(task: TaskEntity, filter: TaskFilter, lingering: Set<String> = emptySet()): Boolean =
        when (filter) {
            TaskFilter.ALL -> true
            TaskFilter.TODO -> !task.isDone || task.uid in lingering
            TaskFilter.DONE -> task.isDone
        }

    /**
     * 进度卡主文案。原站三态：
     *   完成 0 项            → 开始今天的第一件事吧
     *   完成数 == 总数        → 太棒了，全部完成！
     *   其它                 → 已完成 {done} / {total}
     */
    fun ringTitle(done: Int, total: Int): String = when {
        done == 0 -> "开始今天的第一件事吧"
        done == total -> "太棒了，全部完成！"
        else -> "已完成 $done / $total"
    }

    /**
     * 进度卡副文案。原站按百分比三档。
     */
    fun ringHint(percent: Int): String = when {
        percent >= 100 -> "今天圆满收官，去海里游一圈吧"
        percent >= 50 -> "进度过半，保持节奏"
        else -> "慢慢来，完成比完美重要"
    }
}
