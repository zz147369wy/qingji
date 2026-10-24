package com.qingji.plan.ui.settings

import com.qingji.plan.data.model.AiConfig
import com.qingji.plan.data.model.Categories
import com.qingji.plan.data.model.Category
import com.qingji.plan.ui.pet.WHALE_SCHEMES
import com.qingji.plan.ui.pet.WhaleScheme
import com.qingji.plan.ui.theme.ThemeMode

/**
 * 设置页的全部状态。
 */
data class SettingsUiState(
    /** 明暗模式。配色见 [whaleScheme]（鲸鱼配色 = App 主题） */
    val themeMode: ThemeMode = ThemeMode.LIGHT,
    val customCategories: List<Category> = emptyList(),
    /** 课程在课表里的标记色 */
    val courseColor: String = DEFAULT_COURSE_COLOR,
    /** 时间轴上的任务是否强制不重叠 */
    val timelineNoOverlap: Boolean = false,
    /** 每日结算提醒时间 `HH:mm` */
    val settleTime: String = DEFAULT_SETTLE_TIME,
    val aiConfig: AiConfig = AiConfig(),
    /** 是否允许 AI 直接修改计划与课表（关掉后它只能聊） */
    val aiModifyAllowed: Boolean = true,
    /** 鲸鱼空空的配色 id，见 ui/pet/WhaleScheme.kt。同时决定整套 App 配色 */
    val whaleScheme: String = com.qingji.plan.ui.pet.DEFAULT_WHALE_SCHEME,
    /** 空空对操作做反应 */
    val petReact: Boolean = true,
    /** 空空偶尔出门逛逛 */
    val petRoam: Boolean = true,
) {
    val schemeOptions: List<WhaleScheme> get() = WHALE_SCHEMES

    val aiEnabled: Boolean get() = aiConfig.isConfigured

    /** 分类管理里显示的所有分类 = 内置 + 自定义 */
    val allCategories: List<Category> get() = Categories.all(customCategories)

    companion object {
        const val DEFAULT_SETTLE_TIME = "21:00"
        const val DEFAULT_COURSE_COLOR = "#8B5CF6"

        /**
         * 课程标记色可选项。
         *
         * 原站 `wx` 数组**只有 6 个**，顺序照抄（`扒站/app.js`）：
         * `["#8B5CF6","#C97B4A","#7E9BB5","#D9A441","#CA8F94","#6FA06F"]`
         *
         * 之前这里凑了 10 个——原站那 6 个都在，但多出 4 个、顺序也不对。已改回 6 个。
         *
         * 原站这块的 DOM 是「6 个 `.swatch` + 1 个 `.swatch.custom`」，
         * custom 是个 `<input type="color">`（调系统取色器）。
         * **安卓版有意不做那个自定义取色器**（用户 2026-09-27 确认），只保留这 6 个预设色——
         * 这是刻意留的差异，不是没做完。
         */
        val COURSE_COLOR_OPTIONS = listOf(
            "#8B5CF6", "#C97B4A", "#7E9BB5", "#D9A441", "#CA8F94", "#6FA06F",
        )

        /** 清理任务的预设范围 */
        val CLEAR_RANGES = listOf("今天起", "今天", "未来7天", "未来30天", "自定义")

        /** 自定义分类可选的配色，与原站一致 */
        val CUSTOM_COLOR_POOL = Categories.customColorPool
    }
}

/**
 * 设置页里跟界面无关的规则。
 */
object SettingsLogic {

    /**
     * 结算时间是否合法。格式必须是 `HH:mm` 且小时在 0..23。
     */
    fun isValidSettleTime(value: String): Boolean =
        com.qingji.plan.util.TimeUtils.parseHHmm(value) != null

    /**
     * 新建自定义分类。名称为空或者重名都不允许。
     */
    fun canAddCategory(name: String, existing: List<Category>): Boolean {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return false
        // 内置分类名也不允许重复，避免出现两个「工作」
        return existing.none { it.label == trimmed } &&
            Categories.builtIn.none { it.label == trimmed }
    }

    /**
     * 生成一个新分类。id 用 `c_` 前缀，跟内置的英文 id 区分开。
     */
    fun newCategory(name: String, colorIndex: Int): Category {
        val color = SettingsUiState.CUSTOM_COLOR_POOL[
            colorIndex.mod(SettingsUiState.CUSTOM_COLOR_POOL.size)
        ]
        return Category(
            id = com.qingji.plan.util.Ids.uid(),
            label = name.trim(),
            color = color,
            soft = Categories.deriveSoft(color),
            deep = Categories.deriveDeep(color),
        )
    }

    /** 内置分类不允许删除 */
    fun canDelete(category: Category): Boolean = !Categories.builtInIds.contains(category.id)

    /**
     * 「清理任务」按范围算出起始日期。返回 null 表示需要用户自己选日期。
     */
    fun clearFromDate(
        range: String,
        today: java.time.LocalDate,
    ): java.time.LocalDate? = when (range) {
        "今天起" -> today
        "今天" -> today
        "未来7天" -> today.plusDays(7)
        "未来30天" -> today.plusDays(30)
        else -> null
    }
}
