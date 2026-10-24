package com.qingji.plan.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.qingji.plan.data.model.AiConfig
import com.qingji.plan.data.model.Category
import com.qingji.plan.data.repo.SettingsRepository
import com.qingji.plan.data.repo.TaskRepository
import com.qingji.plan.ui.theme.ThemeMode
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * 设置页的状态持有者。
 */
class SettingsViewModel(
    private val settings: SettingsRepository,
    private val tasks: TaskRepository,
    private val todayProvider: () -> LocalDate = { LocalDate.now() },
) : ViewModel() {

    val state: StateFlow<SettingsUiState> = combine(
        settings.observeThemeMode(),
        settings.observeCustomCategories(),
        settings.observeString(SettingsRepository.Keys.COURSE_COLOR),
        settings.observeString(SettingsRepository.Keys.TIMELINE_NO_OVERLAP),
        settings.observeString(SettingsRepository.Keys.SETTLE_TIME),
        settings.observeAiConfig(),
        settings.observeString(SettingsRepository.Keys.AI_MODIFY_ALLOWED),
        settings.observeWhaleScheme(),
        settings.observePetReact(),
        settings.observePetRoam(),
    ) { values ->
        SettingsUiState(
            themeMode = values[0] as ThemeMode,
            customCategories = values[1] as List<Category>,
            courseColor = (values[2] as String?) ?: SettingsUiState.DEFAULT_COURSE_COLOR,
            // 原站存的是字符串 "true"/"false"
            timelineNoOverlap = (values[3] as String?)?.toBooleanStrictOrNull() ?: false,
            settleTime = (values[4] as String?) ?: SettingsUiState.DEFAULT_SETTLE_TIME,
            aiConfig = values[5] as AiConfig,
            aiModifyAllowed = (values[6] as String?)?.toBooleanStrictOrNull() ?: true,
            whaleScheme = values[7] as String,
            petReact = values[8] as Boolean,
            petRoam = values[9] as Boolean,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SettingsUiState(),
    )

    // ---------- 主题 ----------

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settings.setThemeMode(mode) }
    }

    // ---------- 空空 ----------

    fun setPetReact(on: Boolean) {
        viewModelScope.launch { settings.setPetReact(on) }
    }

    fun setPetRoam(on: Boolean) {
        viewModelScope.launch { settings.setPetRoam(on) }
    }

    // ---------- 分类 ----------

    fun addCategory(name: String, colorIndex: Int) {
        viewModelScope.launch {
            val current = settings.customCategories()
            if (!SettingsLogic.canAddCategory(name, current)) return@launch
            settings.setCustomCategories(current + SettingsLogic.newCategory(name, colorIndex))
        }
    }

    fun deleteCategory(category: Category) {
        viewModelScope.launch {
            if (!SettingsLogic.canDelete(category)) return@launch
            val current = settings.customCategories()
            settings.setCustomCategories(current.filterNot { it.id == category.id })
        }
    }

    // ---------- 课程颜色 ----------

    fun setCourseColor(hex: String) {
        viewModelScope.launch { settings.setCourseColor(hex) }
    }

    // ---------- 时间轴 ----------

    fun setTimelineNoOverlap(enabled: Boolean) {
        viewModelScope.launch { settings.setTimelineNoOverlap(enabled) }
    }

    // ---------- 结算时间 ----------

    fun setSettleTime(hhmm: String) {
        if (!SettingsLogic.isValidSettleTime(hhmm)) return
        viewModelScope.launch { settings.setSettleTime(hhmm) }
    }

    // ---------- AI ----------

    fun setAiConfig(config: AiConfig) {
        viewModelScope.launch { settings.setAiConfig(config) }
    }

    fun setAiModifyAllowed(allowed: Boolean) {
        viewModelScope.launch { settings.setAiModifyAllowed(allowed) }
    }

    // ---------- 鲸鱼配色 ----------

    fun setWhaleScheme(id: String) {
        viewModelScope.launch { settings.setWhaleScheme(id) }
    }

    // ---------- 清理任务 ----------

    /** 返回被清掉的任务条数 */
    fun clearTasks(range: String, onDone: (Int) -> Unit = {}) {
        viewModelScope.launch {
            val today = todayProvider()
            val count = when (range) {
                "今天" -> tasks.clearOn(today)
                else -> SettingsLogic.clearFromDate(range, today)?.let { tasks.clearFrom(it) } ?: 0
            }
            onDone(count)
        }
    }

    class Factory(
        private val settings: SettingsRepository,
        private val tasks: TaskRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            SettingsViewModel(settings, tasks) as T
    }
}
