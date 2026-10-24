package com.qingji.plan.ui.calendar

import com.qingji.plan.data.db.entity.CourseEntity
import com.qingji.plan.data.db.entity.TaskEntity
import com.qingji.plan.data.model.Categories
import com.qingji.plan.data.model.Category
import com.qingji.plan.data.model.Holiday
import com.qingji.plan.data.model.Holidays
import com.qingji.plan.util.DateUtils
import java.time.LocalDate
import java.time.YearMonth

/** 日历页的两个视图 */
enum class CalendarView(val label: String) {
    MONTH("月视图"),
    COURSE("课程表"),
}

/**
 * 月历上的一格。
 */
data class DayCell(
    val date: LocalDate,
    /** 是不是当前显示的月份。不是的话画得淡一些 */
    val inCurrentMonth: Boolean,
    val isToday: Boolean,
    val isSelected: Boolean,
    /** 这一天有几个任务（不含已删除的） */
    val taskCount: Int,
    /** 有多少个已完成 */
    val doneCount: Int,
    /** 当天要上的课（只用于判断「这天有课」） */
    val hasCourse: Boolean,
    /** 格子里的任务小条，最多显示前几条 */
    val chips: List<TaskChip>,
    /**
     * 法定节假日 / 调休上班日。不是就为 null。
     *
     * 这是**原站没有的**东西（原站是个纯排程工具，日历上没有节日概念），
     * 照用户手机自带日历的样子加的。见 [Holidays]。
     */
    val holiday: Holiday? = null,
) {
    val isEmpty: Boolean get() = taskCount == 0 && !hasCourse
}

/** 月历格子里的一枚小标签 */
data class TaskChip(
    val title: String,
    val done: Boolean,
    val categoryId: String,
)

/**
 * 日历页的全部展示状态。
 */
data class CalendarUiState(
    val year: Int,
    val month: Int,
    val view: CalendarView,
    /** 当前选中的日期 */
    val selected: LocalDate,
    val today: LocalDate,
    /** 分类筛选，null 表示「全部分类」 */
    val categoryFilter: String?,
    /** 42 格 */
    val cells: List<DayCell>,
    /** 选中那天的任务 */
    val selectedTasks: List<TaskEntity>,
    /** 所有课程（课程表用） */
    val courses: List<CourseEntity>,
) {
    /** `2026.09` */
    val monthLabel: String get() = "%d.%02d".format(year, month)

    /** `9月26日的计划 · 0 项` */
    val selectedTitle: String
        get() = "${DateUtils.monthDayTitle(selected)}的计划 · ${selectedTasks.size} 项"

    /** 当日计划卡的标题（不带条数，条数在右边的小徽章里）：`9月26日的计划` */
    val selectedHeading: String get() = "${DateUtils.monthDayTitle(selected)}的计划"

    /** 顶栏副标题（设计稿第 3 屏） */
    val subtitle: String get() = "点日期看当天 · 金点 = 这天有课"
}

/**
 * 日历页的计算。纯函数，不碰数据库也不碰安卓，所以能直接测。
 */
object CalendarStateCalculator {

    /** 月历格子里最多显示几条任务，多了折叠 */
    const val MAX_CHIPS = 3

    /**
     * 有节假日的格子里最多显示几条任务。
     *
     * 比平常少一条 —— 格子里要多画一行节日名（约 11dp），
     * 而格子总共只有约 60dp 高。不砍一条的话最底下那枚小条会被切掉半个字，
     * 比少显示一条更难看。
     */
    const val MAX_CHIPS_WITH_HOLIDAY = 2

    fun compute(
        year: Int,
        month: Int,
        view: CalendarView,
        selected: LocalDate,
        today: LocalDate,
        categoryFilter: String?,
        tasks: List<TaskEntity>,
        courses: List<CourseEntity>,
        customs: List<Category> = emptyList(),
    ): CalendarUiState {
        val alive = tasks.filter { it.deletedAt == null }
        val aliveCourses = courses.filter { it.deletedAt == null }

        // 分类筛选
        val filtered = if (categoryFilter == null) alive else alive.filter { it.category == categoryFilter }

        val byDate = filtered.groupBy { it.date }

        // 有课的周几（只算排了固定时间的课）
        val courseWeekdays = aliveCourses
            .filter { it.hasTime }
            .map { it.weekday }
            .toSet()

        val grid = DateUtils.monthGrid(year, month)
        val cells = grid.map { date ->
            val key = DateUtils.toKey(date)
            val dayTasks = byDate[key].orEmpty()
            val holiday = Holidays.of(date)
            DayCell(
                date = date,
                inCurrentMonth = date.monthValue == month && date.year == year,
                isToday = date == today,
                isSelected = date == selected,
                taskCount = dayTasks.size,
                doneCount = dayTasks.count { it.isDone },
                // 原站约定 0 = 周一
                hasCourse = (date.dayOfWeek.value - 1) in courseWeekdays,
                chips = dayTasks
                    .sortedBy { it.time ?: "" }
                    .take(if (holiday == null) MAX_CHIPS else MAX_CHIPS_WITH_HOLIDAY)
                    .map { TaskChip(it.title, it.isDone, it.category) },
                holiday = holiday,
            )
        }

        val selectedKey = DateUtils.toKey(selected)
        val selectedTasks = filtered
            .filter { it.date == selectedKey }
            .sortedWith(
                compareBy(
                    { it.time == null || it.time.isBlank() },
                    { it.time ?: "" },
                    { it.createdAt },
                )
            )

        return CalendarUiState(
            year = year,
            month = month,
            view = view,
            selected = selected,
            today = today,
            categoryFilter = categoryFilter,
            cells = cells,
            selectedTasks = selectedTasks,
            courses = aliveCourses,
        )
    }

    /** 上一个月 */
    fun previousMonth(year: Int, month: Int): Pair<Int, Int> {
        val ym = YearMonth.of(year, month).minusMonths(1)
        return ym.year to ym.monthValue
    }

    /** 下一个月 */
    fun nextMonth(year: Int, month: Int): Pair<Int, Int> {
        val ym = YearMonth.of(year, month).plusMonths(1)
        return ym.year to ym.monthValue
    }

    /**
     * 筛选下拉的选项：全部分类 + 每个分类。
     */
    fun filterOptions(customs: List<Category> = emptyList()): List<Pair<String?, String>> =
        listOf(null to "全部分类") + Categories.all(customs).map { it.id to it.label }
}
