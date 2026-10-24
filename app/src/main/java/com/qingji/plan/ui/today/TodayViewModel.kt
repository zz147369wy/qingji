package com.qingji.plan.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.qingji.plan.data.db.entity.TaskEntity
import com.qingji.plan.data.repo.CourseRepository
import com.qingji.plan.data.repo.LongTermPlanRepository
import com.qingji.plan.data.repo.ReviewRepository
import com.qingji.plan.data.repo.SettingsRepository
import com.qingji.plan.data.repo.TaskRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.qingji.plan.util.DateUtils
import java.time.LocalDate
import java.time.LocalTime

/**
 * 今日页的状态持有者。
 *
 * 真正的计算都在 [TodayStateCalculator] 里（纯函数、可测），
 * 这里只负责把「当前日期 + 筛选条件 + 数据库里的任务」三股数据流合并起来。
 *
 * 用 flatMapLatest 是为了：切换日期时**重新订阅**那一天的任务流，
 * 否则滑到前一天界面不会更新。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TodayViewModel(
    private val tasks: TaskRepository,
    private val settings: SettingsRepository,
    private val courses: CourseRepository,
    private val plans: LongTermPlanRepository,
    private val reviews: ReviewRepository,
    private val todayProvider: () -> LocalDate = { LocalDate.now() },
    private val hourProvider: () -> Int = { LocalTime.now().hour },
) : ViewModel() {

    private val _date = MutableStateFlow(todayProvider())
    val date: StateFlow<LocalDate> = _date

    private val _filter = MutableStateFlow(TaskFilter.ALL)
    val filter: StateFlow<TaskFilter> = _filter

    val state: StateFlow<TodayUiState> =
        combine(_date, _filter) { date, filter -> date to filter }
            .flatMapLatest { (date, filter) ->
                // ★ 先把这一天的长期计划任务生成出来，再看任务列表。
                //
                // 两个触发点，缺一不可：
                //   1. 日期变了（切到别的天）→ 外层 flatMapLatest 重跑
                //   2. **计划列表变了**（刚新建/删了一条长期计划）→ 内层 flatMapLatest 重跑
                //
                // 第 2 条是必须的：用户建完计划是**停在今日页**的，外层不会重跑，
                // 少了它就表现为「计划建好了但今日页什么都不出现」。
                //
                // generateFor 是幂等的（同一天 + 同一个 planUid 不会重复生成），
                // 所以重跑几次也不会多出任务。
                plans.observeAll().flatMapLatest {
                    flow {
                        plans.generateFor(date)
                        emitAll(tasks.observeByDate(date))
                    }
                }.map { list ->
                    TodayStateCalculator.compute(
                        date = date,
                        today = todayProvider(),
                        tasks = list,
                        filter = filter,
                        hour = hourProvider(),
                    )
                }
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = TodayStateCalculator.compute(
                    date = todayProvider(),
                    today = todayProvider(),
                    tasks = emptyList(),
                    filter = TaskFilter.ALL,
                    hour = hourProvider(),
                ),
            )

    /**
     * 当前日期所在那一周（周一到周日）每天有几件事。一周横条每格下面的小圆点用它。
     *
     * 长期计划生成的任务要等那天被打开才落库，所以没打开过的日子只算手动建的那些 ——
     * 圆点只是「这天大概有事」的提示，不追求和当天页面的计数逐一相等。
     */
    val weekCounts: StateFlow<Map<LocalDate, Int>> =
        _date.map { DateUtils.mondayOf(it) }
            .distinctUntilChanged()
            .flatMapLatest { monday ->
                tasks.observeBetween(monday, monday.plusDays(6)).map { list ->
                    list.mapNotNull { DateUtils.fromKey(it.date) }.groupingBy { it }.eachCount()
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /**
     * 拿某一天的快照。**给左右邻页用**。
     *
     * 邻页只在滑动过程中可见，取一次就够，不需要实时跟着数据库变——
     * 所以这里是一次性查询，不是 Flow。（当天那一页仍然走 [state]，是实时的。）
     */
    suspend fun snapshotFor(date: LocalDate): TodayUiState {
        // 跟 state 那条路一样，先确保这天的长期计划任务生成出来
        plans.generateFor(date)
        return TodayStateCalculator.compute(
            date = date,
            today = todayProvider(),
            tasks = tasks.getByDate(date),
            filter = _filter.value,
            hour = hourProvider(),
        )
    }

    // ---------- 操作 ----------

    fun selectFilter(f: TaskFilter) {
        _filter.value = f
    }

    fun goToDate(date: LocalDate) {
        _date.value = date
    }

    fun backToToday() {
        _date.value = todayProvider()
    }

    /** 勾选 / 取消勾选 */
    fun toggleDone(task: TaskEntity) {
        viewModelScope.launch { tasks.toggleDone(task) }
    }

    /** 删除任务 */
    fun delete(task: TaskEntity) {
        viewModelScope.launch { tasks.delete(task) }
    }

    /** 保存编辑弹层的内容（新建或更新） */
    fun saveTask(edit: TaskEditState) {
        viewModelScope.launch {
            tasks.save(edit.toEntity(), isNew = edit.isNew)
        }
    }

    /** 生成今天的结算并存档 */
    suspend fun settleToday(): com.qingji.plan.util.SettleResult {
        val date = _date.value
        val list = tasks.getByDate(date)
        val result = com.qingji.plan.util.SettleCalculator.calculate(
            list, com.qingji.plan.util.DateUtils.toKey(date),
        )
        reviews.save(date, result)
        return result
    }

    suspend fun reviewFor(date: LocalDate) = reviews.getByDate(date)

    /** 批量录入：一次把解析好的多行写进库 */
    suspend fun applyBatch(lines: List<com.qingji.plan.service.ParsedLine>, asCourse: Boolean) {
        lines.forEach { line ->
            when {
                asCourse -> courses.create(
                    title = line.title,
                    weekday = line.date.dayOfWeek.value - 1,
                    time = line.time,
                    durationMinutes = line.durationMinutes ?: CourseRepository.DEFAULT_DURATION,
                    category = if (line.category == "other") "study" else line.category,
                )
                line.isPlan -> plans.create(
                    title = line.title,
                    cadence = line.cadence!!,
                    weekdays = line.weekdays,
                    dayOfMonth = line.dayOfMonth,
                    time = line.time,
                    durationMinutes = line.durationMinutes,
                    category = line.category,
                )
                else -> tasks.create(
                    title = line.title,
                    date = line.date,
                    time = line.time,
                    durationMinutes = line.durationMinutes,
                    category = line.category,
                    priority = line.priority,
                )
            }
        }
    }

    /**
     * 新建或修改一条长期计划。
     *
     * 两种走同一条路 —— 原站的「新建」和「编辑」本来就是同一个弹层
     * （`Lx({open, plan})`），界面也就不用分成两套回调。
     * 新建还是编辑由 [plan] 的 `id` 是不是 0 决定（Room 自增主键的默认值）。
     */
    fun savePlan(plan: com.qingji.plan.data.db.entity.LongTermPlanEntity) {
        viewModelScope.launch { plans.save(plan) }
    }

    fun deletePlan(plan: com.qingji.plan.data.db.entity.LongTermPlanEntity) {
        viewModelScope.launch { plans.delete(plan) }
    }

    /** 自动排期的结果写回数据库 */
    fun applySchedule(slots: List<Pair<TaskEntity, Pair<String, Int>>>) {
        viewModelScope.launch {
            slots.forEach { (task, wd) -> tasks.schedule(task, wd.first, wd.second) }
        }
    }

    /** 从编辑弹层删除任务 */
    fun deleteByEditState(edit: TaskEditState) {
        viewModelScope.launch {
            val existing = tasks.getById(edit.id)
            if (existing != null) tasks.delete(existing)
        }
    }

    class Factory(
        private val tasks: TaskRepository,
        private val settings: SettingsRepository,
        private val courses: CourseRepository,
        private val plans: LongTermPlanRepository,
        private val reviews: ReviewRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            TodayViewModel(tasks, settings, courses, plans, reviews) as T
    }
}
