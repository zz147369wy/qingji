package com.qingji.plan.data.repo

import com.qingji.plan.data.db.dao.SettingDao
import com.qingji.plan.data.db.entity.SettingEntity
import com.qingji.plan.data.model.AiConfig
import com.qingji.plan.data.model.PeriodSchedules
import com.qingji.plan.data.model.SchoolPeriod
import com.qingji.plan.data.model.Category
import com.qingji.plan.ui.pet.DEFAULT_WHALE_SCHEME
import com.qingji.plan.ui.theme.ThemeMode
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 设置仓库。键值对形式，键名与原站保持一致，备份文件可以直接互通。
 */
class SettingsRepository(private val dao: SettingDao) {

    object Keys {
        const val THEME = "theme"
        const val SETTLE_TIME = "settleTime"
        const val TIMELINE_NO_OVERLAP = "timelineNoOverlap"
        const val COURSE_COLOR = "courseColor"
        const val AI_MODIFY_ALLOWED = "aiModifyAllowed"
        const val AI_CONFIG = "aiConfig"
        const val CUSTOM_CATEGORIES = "customCategories"
        /** 作息时间表：课表左边那根按「节次」排的时间轴。存 JSON 数组 */
        const val PERIODS = "periods"

        // ---- P9 原生能力 ----
        /** 提醒总开关 */
        const val NOTIFY_ENABLED = "notifyEnabled"
        /** 已经排进系统的闹钟 key 集合（用 | 分隔），重排前要先撤掉这些 */
        const val ARMED_ALARMS = "armedAlarms"
        /** 有没有申请过通知权限。用来区分「没问过」和「被永久拒绝」 */
        const val NOTIFY_PERMISSION_ASKED = "notifyPermissionAsked"
        /** 写入系统日历时选中的日历 id */
        const val CALENDAR_ID = "calendarId"
        /** 鲸鱼空空的配色 id，见 ui/pet/WhaleScheme.kt。同时也是 App 主题 */
        const val WHALE_SCHEME = "whaleScheme"
        /** 明暗模式：light / dark / system，见 ThemeMode */
        const val THEME_MODE = "themeMode"
        /** 空空对操作做反应（完成跳一下、删除摇头……） */
        const val PET_REACT = "petReact"
        /** 空空偶尔离开窝出去逛一圈 */
        const val PET_ROAM = "petRoam"
    }

    // ---------- 通用 ----------

    suspend fun getString(key: String): String? = dao.get(key)?.value

    /** 读一个布尔设置。缺省或格式不对时用 [default] */
    suspend fun getBoolean(key: String, default: Boolean): Boolean =
        getString(key)?.toBooleanStrictOrNull() ?: default

    suspend fun putBoolean(key: String, value: Boolean) =
        putString(key, value.toString())

    suspend fun putString(key: String, value: String) =
        dao.put(SettingEntity(key, value))

    suspend fun getAll(): List<SettingEntity> = dao.getAll()

    suspend fun clear() = dao.clear()

    fun observeString(key: String): Flow<String?> = dao.observe(key).map { it?.value }

    // ---------- 主题 ----------
    //
    // 【2026-10 鲸鱼主题】主题 = 鲸鱼配色（[Keys.WHALE_SCHEME]）× 明暗模式（[Keys.THEME_MODE]）。
    // 旧的 [Keys.THEME]（paper/night/…）不再读写；留着键名是为了老备份文件导入时不报错。

    suspend fun themeMode(): ThemeMode = ThemeMode.of(getString(Keys.THEME_MODE))

    fun observeThemeMode(): Flow<ThemeMode> = observeString(Keys.THEME_MODE).map { ThemeMode.of(it) }

    suspend fun setThemeMode(mode: ThemeMode) = putString(Keys.THEME_MODE, mode.key)

    // ---------- 每日结算时间 ----------

    /** 默认 21:00，与原站一致 */
    suspend fun settleTime(): String = getString(Keys.SETTLE_TIME) ?: DEFAULT_SETTLE_TIME

    suspend fun setSettleTime(hhmm: String) = putString(Keys.SETTLE_TIME, hhmm)

    // ---------- 时间轴强制不重合 ----------

    /** 默认关闭，与原站一致 */
    suspend fun timelineNoOverlap(): Boolean =
        getString(Keys.TIMELINE_NO_OVERLAP)?.toBooleanStrictOrNull() ?: false

    suspend fun setTimelineNoOverlap(enabled: Boolean) =
        putString(Keys.TIMELINE_NO_OVERLAP, enabled.toString())

    // ---------- 课程颜色 ----------

    /** 默认 #8B5CF6，与原站一致 */
    suspend fun courseColor(): String = getString(Keys.COURSE_COLOR) ?: DEFAULT_COURSE_COLOR

    suspend fun setCourseColor(hex: String) = putString(Keys.COURSE_COLOR, hex)

    // ---------- 作息时间表 ----------

    /**
     * 课表左边那根按「节次」排的时间轴。
     *
     * 没存过、或者存坏了，都退回 [PeriodSchedules.DEFAULT]（用户学校那套 6 节 85 分钟）。
     * 宁可显示默认作息，也不能因为一个设置字段坏了就让课表打不开。
     */
    suspend fun periods(): List<SchoolPeriod> = PeriodSchedules.parse(getString(Keys.PERIODS))

    suspend fun setPeriods(periods: List<SchoolPeriod>) =
        putString(Keys.PERIODS, PeriodSchedules.encode(periods))

    // ---------- AI ----------

    /** 默认允许 AI 改数据，但改之前必须用户确认 */
    suspend fun aiModifyAllowed(): Boolean =
        getString(Keys.AI_MODIFY_ALLOWED)?.toBooleanStrictOrNull() ?: true

    suspend fun setAiModifyAllowed(allowed: Boolean) =
        putString(Keys.AI_MODIFY_ALLOWED, allowed.toString())

    // ---------- 自定义分类 ----------

    /** 用户自己加的分类。内置的五个不在里面 */
    suspend fun customCategories(): List<Category> {
        val raw = getString(Keys.CUSTOM_CATEGORIES) ?: return emptyList()
        return runCatching {
            json.decodeFromString<List<CategoryDto>>(raw).map { it.toCategory() }
        }.getOrDefault(emptyList())
    }

    suspend fun setCustomCategories(list: List<Category>) {
        val dto = list.map { CategoryDto(it.id, it.label, it.color.toHex(), it.soft.toHex(), it.deep.toHex()) }
        putString(Keys.CUSTOM_CATEGORIES, json.encodeToString(dto))
    }

    fun observeCustomCategories(): Flow<List<Category>> =
        observeString(Keys.CUSTOM_CATEGORIES).map { raw ->
            if (raw.isNullOrBlank()) emptyList()
            else runCatching {
                json.decodeFromString<List<CategoryDto>>(raw).map { it.toCategory() }
            }.getOrDefault(emptyList())
        }

    // ---------- AI 配置 ----------

    suspend fun aiConfig(): AiConfig {
        val raw = getString(Keys.AI_CONFIG) ?: return AiConfig()
        return runCatching { json.decodeFromString<AiConfig>(raw) }.getOrDefault(AiConfig())
    }

    suspend fun setAiConfig(config: AiConfig) = putString(Keys.AI_CONFIG, json.encodeToString(config))

    fun observeAiConfig(): Flow<AiConfig> =
        observeString(Keys.AI_CONFIG).map { raw ->
            if (raw.isNullOrBlank()) AiConfig()
            else runCatching { json.decodeFromString<AiConfig>(raw) }.getOrDefault(AiConfig())
        }

    /** AI 总开关：有没有填 Key */
    suspend fun isAiEnabled(): Boolean = aiConfig().isConfigured

    // ---------- 鲸鱼配色 ----------

    /** 默认原版蓝，见 ui/pet/WhaleScheme.kt */
    suspend fun whaleScheme(): String = getString(Keys.WHALE_SCHEME) ?: DEFAULT_WHALE_SCHEME

    suspend fun setWhaleScheme(id: String) = putString(Keys.WHALE_SCHEME, id)

    fun observeWhaleScheme(): Flow<String> =
        observeString(Keys.WHALE_SCHEME).map { it ?: DEFAULT_WHALE_SCHEME }

    // ---------- 空空的行为开关（默认都开） ----------

    fun observePetReact(): Flow<Boolean> =
        observeString(Keys.PET_REACT).map { it?.toBooleanStrictOrNull() ?: true }

    fun observePetRoam(): Flow<Boolean> =
        observeString(Keys.PET_ROAM).map { it?.toBooleanStrictOrNull() ?: true }

    suspend fun setPetReact(on: Boolean) = putBoolean(Keys.PET_REACT, on)

    suspend fun setPetRoam(on: Boolean) = putBoolean(Keys.PET_ROAM, on)

    companion object {
        const val DEFAULT_SETTLE_TIME = "21:00"
        const val DEFAULT_COURSE_COLOR = "#8B5CF6"
    }
}


/** 自定义分类的存储形态。Color 不能直接序列化，所以存十六进制字符串 */
@Serializable
private data class CategoryDto(
    val id: String,
    val label: String,
    val color: String,
    val soft: String,
    val deep: String,
) {
    fun toCategory() = Category(id, label, parseHex(color), parseHex(soft), parseHex(deep))
}

private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

private fun parseHex(hex: String): Color =
    runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrDefault(Color.Gray)

private fun Color.toHex(): String = String.format("#%06X", 0xFFFFFF and toArgb())
