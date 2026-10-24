package com.qingji.plan.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.qingji.plan.data.repo.LongTermPlanRepository
import com.qingji.plan.data.repo.ReviewRepository
import com.qingji.plan.data.repo.TaskRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

/**
 * 统计页的状态持有者。
 *
 * 三股数据流（任务 / 结算记录 / 长期计划）合并成一份展示状态。
 */
class StatsViewModel(
    tasks: TaskRepository,
    reviews: ReviewRepository,
    plans: LongTermPlanRepository,
    private val todayProvider: () -> LocalDate = { LocalDate.now() },
) : ViewModel() {

    val state: StateFlow<StatsUiState> = combine(
        tasks.observeAll(),
        reviews.observeRecent(365),
        plans.observeAll(),
    ) { taskList, reviewList, planList ->
        StatsCalculator.compute(
            today = todayProvider(),
            tasks = taskList,
            reviews = reviewList,
            plans = planList,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = StatsCalculator.compute(
            today = todayProvider(),
            tasks = emptyList(),
            reviews = emptyList(),
            plans = emptyList(),
        ),
    )

    class Factory(
        private val tasks: TaskRepository,
        private val reviews: ReviewRepository,
        private val plans: LongTermPlanRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            StatsViewModel(tasks, reviews, plans) as T
    }
}
