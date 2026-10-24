package com.qingji.plan.ui.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.qingji.plan.data.repo.CourseRepository
import com.qingji.plan.data.repo.TaskRepository
import com.qingji.plan.util.DateUtils
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * 日历页的状态持有者。
 *
 * 只订阅**当前月历网格覆盖的那 42 天**，不是全表 —— 数据多了才不会卡。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CalendarViewModel(
    private val tasks: TaskRepository,
    private val courses: CourseRepository,
    private val todayProvider: () -> LocalDate = { LocalDate.now() },
) : ViewModel() {

    private val today = todayProvider()

    private val _yearMonth = MutableStateFlow(today.year to today.monthValue)
    private val _view = MutableStateFlow(CalendarView.MONTH)
    private val _selected = MutableStateFlow(today)
    private val _filter = MutableStateFlow<String?>(null)

    /** 当前网格覆盖的日期范围 */
    private val gridRange: kotlinx.coroutines.flow.Flow<Pair<LocalDate, LocalDate>> =
        _yearMonth.map { (y, m) ->
            val grid = DateUtils.monthGrid(y, m)
            grid.first() to grid.last()
        }

    private val taskFlow = gridRange.flatMapLatest { (from, to) ->
        tasks.observeBetween(from, to)
    }

    private val courseFlow = courses.observeAll()

    val state: StateFlow<CalendarUiState> = combine(
        _yearMonth, _view, _selected, _filter,
    ) { ym, view, selected, filter ->
        Params(ym.first, ym.second, view, selected, filter)
    }.flatMapLatest { p ->
        combine(taskFlow, courseFlow) { dayTasks, allCourses ->
            CalendarStateCalculator.compute(
                year = p.year,
                month = p.month,
                view = p.view,
                selected = p.selected,
                today = todayProvider(),
                categoryFilter = p.filter,
                tasks = dayTasks,
                courses = allCourses,
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = CalendarStateCalculator.compute(
            year = today.year,
            month = today.monthValue,
            view = CalendarView.MONTH,
            selected = today,
            today = today,
            categoryFilter = null,
            tasks = emptyList(),
            courses = emptyList(),
        ),
    )

    private data class Params(
        val year: Int,
        val month: Int,
        val view: CalendarView,
        val selected: LocalDate,
        val filter: String?,
    )

    // ---------- 操作 ----------

    fun setView(view: CalendarView) {
        _view.value = view
    }

    fun previousMonth() {
        _yearMonth.value = CalendarStateCalculator.previousMonth(_yearMonth.value.first, _yearMonth.value.second)
    }

    fun nextMonth() {
        _yearMonth.value = CalendarStateCalculator.nextMonth(_yearMonth.value.first, _yearMonth.value.second)
    }

    fun selectDate(date: LocalDate) {
        _selected.value = date
        // 点到别的月份的日子时，跟着翻月
        if (date.year != _yearMonth.value.first || date.monthValue != _yearMonth.value.second) {
            _yearMonth.value = date.year to date.monthValue
        }
    }

    fun setFilter(categoryId: String?) {
        _filter.value = categoryId
    }

    /** 从日历点某天 → 跳到今日页看那天的计划 */
    fun selectedDate(): LocalDate = _selected.value

    /** 保存课程编辑弹层的内容 */
    fun saveCourse(edit: com.qingji.plan.ui.course.CourseEditState) {
        viewModelScope.launch {
            courses.save(edit.toEntity(), isNew = edit.isNew)
        }
    }

    /** 从编辑弹层删除课程 */
    fun deleteCourse(edit: com.qingji.plan.ui.course.CourseEditState) {
        viewModelScope.launch {
            courses.getById(edit.id)?.let { courses.delete(it) }
        }
    }

    class Factory(
        private val tasks: TaskRepository,
        private val courses: CourseRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            CalendarViewModel(tasks, courses) as T
    }
}
