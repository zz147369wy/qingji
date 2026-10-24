package com.qingji.plan.ui.settings

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.IntOffset
import com.qingji.plan.ui.components.AppIcons
import com.qingji.plan.ui.components.QingJiIconButton
import com.qingji.plan.ui.theme.AppMotion
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qingji.plan.data.model.AiConfig
import com.qingji.plan.data.model.Categories
import com.qingji.plan.data.model.Category
import com.qingji.plan.data.model.PeriodSchedules
import com.qingji.plan.data.model.SchoolPeriod
import com.qingji.plan.ui.components.PageHeader
import com.qingji.plan.ui.components.QingJiCard
import com.qingji.plan.ui.components.QingJiChip
import com.qingji.plan.ui.components.QingJiIconButtonBox
import com.qingji.plan.ui.components.dimIndication
import com.qingji.plan.ui.haptics.HapticCue
import com.qingji.plan.ui.haptics.LocalHaptics
import com.qingji.plan.ui.theme.AppText
import com.qingji.plan.ui.theme.Dimens
import com.qingji.plan.ui.theme.LocalPalette
import com.qingji.plan.ui.components.sticker
import com.qingji.plan.ui.pet.petDock
import androidx.compose.ui.draw.drawBehind
import androidx.compose.material.icons.filled.Check

/**
 * 设置页里点进去的二级页。
 *
 * 【2026-10 鲸鱼主题】主页面是设计稿那样的**分组列表**（空空 / 计划 / AI 与系统），
 * 一行一项、右边是当前值和 ›；要填一堆东西的（作息表、AI 接口、分类……）点进二级页再编辑。
 */
private enum class SettingsPage(val title: String, val subtitle: String) {
    CATEGORIES("分类管理", "内置 5 个；也可以加自己的，比如「面试」「副业」"),
    PERIODS("作息时间表", "课表左边那根轴按你学校的作息排，改完立刻生效"),
    SETTLE("每日结算时间", "到点自动弹出「每日结算」"),
    AI("AI 助手", "兼容 OpenAI 接口格式，默认走 DeepSeek"),
    NOTIFY("提醒与通知", "到点提醒你该做哪件事了"),
    BACKUP("数据备份 / 恢复", "数据只存在这台手机上"),
}

/**
 * 设置页。
 *
 * 【2026-10 鲸鱼主题】照设计稿第 5 屏：
 *   1. 配色卡：空空的小舞台 + 8 个色点 + 明暗三选一（鲸鱼穿什么颜色，整个 App 就是什么颜色）
 *   2. 分组列表：空空 / 计划 / AI 与系统。开关直接在行上拨，要编辑的点进二级页
 *
 * 「写入系统日历」暂时不显示（写入路径还没接上，开了也不会写；代码留着，以后接线了再放出来）。
 * 「课程标记颜色」删了 —— 日历里「有课」统一是金色小圆点。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    modifier: Modifier = Modifier,
    onThemeModeChange: (com.qingji.plan.ui.theme.ThemeMode) -> Unit = {},
    onThemeTapAt: (androidx.compose.ui.geometry.Offset) -> Unit = {},
    onPetReactChange: (Boolean) -> Unit = {},
    onPetRoamChange: (Boolean) -> Unit = {},
    onAddCategory: (String, Int) -> Unit = { _, _ -> },
    onDeleteCategory: (Category) -> Unit = {},
    /** 作息时间表。课表左边那根按「节次」排的轴 */
    periods: List<com.qingji.plan.data.model.SchoolPeriod> =
        com.qingji.plan.data.model.PeriodSchedules.DEFAULT,
    onPeriodsChange: (List<com.qingji.plan.data.model.SchoolPeriod>) -> Unit = {},
    onTimelineNoOverlapChange: (Boolean) -> Unit = {},
    onSettleTimeChange: (String) -> Unit = {},
    onAiConfigChange: (AiConfig) -> Unit = {},
    onAiModifyAllowedChange: (Boolean) -> Unit = {},
    onClearTasks: (String) -> Unit = {},
    onBackup: () -> Unit = {},
    onRestore: () -> Unit = {},
    onTestAi: (AiConfig, (Result<String>) -> Unit) -> Unit = { _, _ -> },
    // ---- P9 ----
    notifyEnabled: Boolean = true,
    onNotifyEnabledChange: (Boolean) -> Unit = {},
    notifyStateText: String = "提醒已开启",
    notifyActionLabel: String = "",
    onNotifyAction: () -> Unit = {},
    onWhaleSchemeChange: (String) -> Unit = {},
) {
    val haptics = LocalHaptics.current

    var page by rememberSaveable { mutableStateOf<SettingsPage?>(null) }
    // 列表的滚动位置放在外面：从二级页回来时停在原来那一行，而不是跳回顶上
    val homeScroll = rememberScrollState()
    var showClearDialog by remember { mutableStateOf(false) }
    var showAddCategory by remember { mutableStateOf(false) }
    var clearResult by remember { mutableStateOf<String?>(null) }

    // 二级页开着时，返回键先回到列表
    androidx.activity.compose.BackHandler(enabled = page != null) { page = null }

    fun open(p: SettingsPage) {
        haptics.perform(HapticCue.Tap)
        page = p
    }

    AnimatedContent(
        targetState = page,
        transitionSpec = {
            // 进二级页：从右边推进来；回列表：反过来。位移有一点弹性，透明度不回弹
            val forward = targetState != null
            val move = AppMotion.springOf<IntOffset>(durationMillis = 320, bounce = 0.0)
            (slideInHorizontally(move) { w -> if (forward) w / 3 else -w / 3 } + fadeIn(AppMotion.Springs.effects))
                .togetherWith(slideOutHorizontally(move) { w -> if (forward) -w / 3 else w / 3 } + fadeOut(AppMotion.Springs.effectsFast))
        },
        modifier = modifier.fillMaxSize(),
        label = "settings_page",
    ) { current ->
        if (current == null) {
            SettingsHome(
                state = state,
                scroll = homeScroll,
                periods = periods,
                notifyEnabled = notifyEnabled,
                onThemeModeChange = onThemeModeChange,
                onThemeTapAt = onThemeTapAt,
                onWhaleSchemeChange = onWhaleSchemeChange,
                onPetReactChange = onPetReactChange,
                onPetRoamChange = onPetRoamChange,
                onTimelineNoOverlapChange = onTimelineNoOverlapChange,
                onAiModifyAllowedChange = onAiModifyAllowedChange,
                onOpen = ::open,
                onClear = {
                    haptics.perform(HapticCue.Tap)
                    showClearDialog = true
                },
                clearResult = clearResult,
            )
        } else {
            SettingsSubPage(page = current, onBack = { page = null }) {
                when (current) {
                    SettingsPage.CATEGORIES -> CategoriesPage(
                        state = state,
                        onDeleteCategory = onDeleteCategory,
                        onAdd = { showAddCategory = true },
                    )
                    SettingsPage.PERIODS -> PeriodsCard(periods = periods, onChange = onPeriodsChange)
                    SettingsPage.SETTLE -> SettleTimePage(state.settleTime, onSettleTimeChange)
                    SettingsPage.AI -> AiSettingsCard(
                        config = state.aiConfig,
                        onChange = onAiConfigChange,
                        onTest = onTestAi,
                    )
                    SettingsPage.NOTIFY -> NotifyPage(
                        enabled = notifyEnabled,
                        stateText = notifyStateText,
                        actionLabel = notifyActionLabel,
                        onEnabledChange = onNotifyEnabledChange,
                        onAction = onNotifyAction,
                    )
                    SettingsPage.BACKUP -> BackupPage(onBackup = onBackup, onRestore = onRestore)
                }
            }
        }
    }

    // ---------- 清理确认弹窗 ----------
    if (showClearDialog) {
        val palette = LocalPalette.current
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("清理任务") },
            text = { Text("只清理「今天及以后」的计划，今天以前的记录和统计会保留。确定吗？") },
            confirmButton = {
                TextButton(onClick = {
                    showClearDialog = false
                    onClearTasks("今天起")
                    clearResult = "已清理今天及以后的计划"
                }) { Text("清理今天起", color = palette.danger) }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) { Text("取消") }
            },
        )
    }

    // ---------- 添加分类弹窗 ----------
    if (showAddCategory) {
        AddCategoryDialog(
            existing = state.allCategories,
            onDismiss = { showAddCategory = false },
            onConfirm = { name, colorIndex ->
                haptics.perform(HapticCue.Commit)
                onAddCategory(name, colorIndex)
                showAddCategory = false
            },
        )
    }
}

// ---------------------------------------------------------------- 主页面

@Composable
private fun SettingsHome(
    state: SettingsUiState,
    scroll: androidx.compose.foundation.ScrollState,
    periods: List<SchoolPeriod>,
    notifyEnabled: Boolean,
    onThemeModeChange: (com.qingji.plan.ui.theme.ThemeMode) -> Unit,
    onThemeTapAt: (androidx.compose.ui.geometry.Offset) -> Unit,
    onWhaleSchemeChange: (String) -> Unit,
    onPetReactChange: (Boolean) -> Unit,
    onPetRoamChange: (Boolean) -> Unit,
    onTimelineNoOverlapChange: (Boolean) -> Unit,
    onAiModifyAllowedChange: (Boolean) -> Unit,
    onOpen: (SettingsPage) -> Unit,
    onClear: () -> Unit,
    clearResult: String?,
) {
    val palette = LocalPalette.current
    val haptics = LocalHaptics.current
    fun toggle(on: Boolean, f: (Boolean) -> Unit) {
        haptics.perform(if (on) HapticCue.ToggleOn else HapticCue.ToggleOff)
        f(on)
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(horizontal = 18.dp)
            .padding(top = 16.dp, bottom = 30.dp)
            .testTag("settings_screen"),
    ) {
        PageHeader(title = "设置", subtitle = "空空穿什么颜色，整个 App 就是什么颜色")

        Spacer(Modifier.height(16.dp))

        // ---------- 配色 = 主题 ----------
        QingJiCard(modifier = Modifier.fillMaxWidth()) {
            val current = com.qingji.plan.ui.pet.whaleSchemeOf(state.whaleScheme)
            // 舞台：一截海水（浅滩 → 暮光层），空空站在正中（设计稿 `.scheme-stage`）
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .sticker(shape = RoundedCornerShape(14.dp), fill = Color.Transparent, drop = 0.dp)
                    .background(Brush.verticalGradient(listOf(palette.depths[0], palette.depths[2])))
                    .testTag("settings_scheme_stage"),
            ) {
                Column(Modifier.padding(start = 12.dp, top = 10.dp)) {
                    Text("当前配色", style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold), color = palette.text2)
                    Text(current.label, style = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.ExtraBold), color = palette.text)
                }
                // 空空的窝：她站在舞台正中，脚踩舞台底
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .size(width = 118.dp, height = 150.dp)
                        .petDock(com.qingji.plan.ui.pet.PetDockRegistry.SETTINGS),
                )
            }
            Spacer(Modifier.height(6.dp))
            // 4 × 2 的色点。点哪个，圆形换肤就从哪扩散开
            state.schemeOptions.chunked(4).forEachIndexed { i, row ->
                Row(Modifier.fillMaxWidth().padding(top = if (i == 0) 0.dp else 12.dp)) {
                    row.forEach { s ->
                        SchemeSwatch(
                            label = s.label,
                            color = Color(s.appAccent),
                            active = s.id == state.whaleScheme,
                            onClick = {
                                haptics.perform(HapticCue.Tap)
                                onWhaleSchemeChange(s.id)
                            },
                            onPositioned = onThemeTapAt,
                            modifier = Modifier.weight(1f).testTag("scheme_${s.id}"),
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            com.qingji.plan.ui.components.SegmentedControl(
                segments = com.qingji.plan.ui.theme.ThemeMode.entries.map {
                    com.qingji.plan.ui.components.Segment(it.label, testTag = "mode_${it.key}")
                },
                selectedIndex = state.themeMode.ordinal,
                onSelect = {
                    haptics.perform(HapticCue.Tap)
                    onThemeModeChange(com.qingji.plan.ui.theme.ThemeMode.entries[it])
                },
            )
        }

        // ---------- 空空 ----------
        SetGroup("空空") {
            SetItem(AppIcons.Heart, "对我的操作做反应", first = true, testTag = "pet_react_row") {
                StickerSwitch(state.petReact, { toggle(it, onPetReactChange) }, testTag = "pet_react")
            }
            SetItem(AppIcons.Fish, "偶尔出门逛逛", testTag = "pet_roam_row") {
                StickerSwitch(state.petRoam, { toggle(it, onPetRoamChange) }, testTag = "pet_roam")
            }
        }

        // ---------- 计划 ----------
        SetGroup("计划") {
            SetItem(
                AppIcons.Tag, "分类管理", first = true,
                onClick = { onOpen(SettingsPage.CATEGORIES) }, testTag = "settings_categories",
            ) { SetValue("${state.allCategories.size} 个") }
            SetItem(
                AppIcons.Table, "作息时间表",
                onClick = { onOpen(SettingsPage.PERIODS) }, testTag = "settings_periods",
            ) { SetValue("${periods.size} 节") }
            SetItem(AppIcons.Layers, "时间不重合", testTag = "settings_no_overlap_row") {
                StickerSwitch(state.timelineNoOverlap, { toggle(it, onTimelineNoOverlapChange) }, testTag = "settings_no_overlap")
            }
            SetItem(
                AppIcons.Settle, "每日结算时间",
                onClick = { onOpen(SettingsPage.SETTLE) }, testTag = "settings_settle",
            ) { SetValue(state.settleTime, number = true) }
            SetItem(AppIcons.Broom, "清理今天起的计划", onClick = onClear, testTag = "settings_clear") {
                SetValue(clearResult?.let { "已清理" } ?: "")
            }
        }

        // ---------- AI 与系统 ----------
        SetGroup("AI 与系统") {
            SetItem(
                AppIcons.Bot, "AI 助手", first = true,
                onClick = { onOpen(SettingsPage.AI) }, testTag = "settings_ai",
            ) { SetValue(if (state.aiEnabled) "已配置" else "未配置") }
            SetItem(AppIcons.Shield, "允许 AI 修改计划", testTag = "settings_ai_modify_row") {
                StickerSwitch(state.aiModifyAllowed, { toggle(it, onAiModifyAllowedChange) }, testTag = "settings_ai_modify")
            }
            SetItem(
                AppIcons.Bell, "提醒与通知",
                onClick = { onOpen(SettingsPage.NOTIFY) }, testTag = "settings_notify_row",
            ) { SetValue(if (notifyEnabled) "已开启" else "已关闭") }
            SetItem(
                AppIcons.Box, "数据备份 / 恢复",
                onClick = { onOpen(SettingsPage.BACKUP) }, testTag = "settings_backup_row",
            ) { SetValue("") }
        }
    }
}

// ---------------------------------------------------------------- 分组列表的零件

/** 一组设置：组名 + 一张贴纸卡（设计稿 `.set-group`） */
@Composable
private fun SetGroup(title: String, content: @Composable () -> Unit) {
    val palette = LocalPalette.current
    Column(Modifier.padding(top = 18.dp)) {
        Text(
            title,
            style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp),
            color = palette.text3,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
        )
        QingJiCard(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
        ) { content() }
    }
}

/**
 * 一行设置：`[图标] 名称 ………… 值 ›` 或 `………… [开关]`。
 * 行与行之间一道虚线（第一行没有）。传了 [onClick] 整行可点（点进二级页）。
 */
@Composable
private fun SetItem(
    icon: ImageVector,
    label: String,
    first: Boolean = false,
    onClick: (() -> Unit)? = null,
    testTag: String? = null,
    trailing: @Composable () -> Unit,
) {
    val palette = LocalPalette.current
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 54.dp)
            .then(
                if (first) {
                    Modifier
                } else {
                    Modifier.drawBehind {
                        val w = 1.5.dp.toPx()
                        drawLine(
                            palette.line, Offset(0f, w / 2), Offset(size.width, w / 2), strokeWidth = w,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())),
                        )
                    }
                }
            )
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = interactionSource,
                        indication = dimIndication(RoundedCornerShape(8.dp)),
                        onClick = onClick,
                    )
                } else {
                    Modifier
                }
            )
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(32.dp)
                .sticker(shape = RoundedCornerShape(10.dp), fill = palette.primarySoft, drop = 0.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = palette.primaryDeep, modifier = Modifier.size(17.dp))
        }
        Text(
            label,
            style = TextStyle(fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold),
            color = palette.text,
            modifier = Modifier
                .padding(start = 12.dp)
                .weight(1f),
        )
        trailing()
    }
}

/** 行右边的「当前值 ›」 */
@Composable
private fun SetValue(text: String, number: Boolean = false) {
    val palette = LocalPalette.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (text.isNotEmpty()) {
            Text(
                text,
                style = if (number) AppText.numSmall else TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
                color = palette.text3,
            )
        }
        Icon(
            Icons.Outlined.ChevronRight,
            contentDescription = null,
            tint = palette.text3,
            modifier = Modifier.padding(start = 4.dp).size(16.dp),
        )
    }
}

/**
 * 贴纸开关（设计稿 `.switch`）：50×30 的轨道 + 一颗白色贴纸圆钮。
 * 开 = 轨道主色、圆钮滑到右边；圆钮滑动走弹簧（位移允许回弹），轨道颜色不回弹。
 */
@Composable
fun StickerSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    testTag: String? = null,
) {
    val palette = LocalPalette.current
    val knob by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = AppMotion.springOf(durationMillis = 300, bounce = 0.45),
        label = "switch_knob",
    )
    val track by androidx.compose.animation.animateColorAsState(
        targetValue = if (checked) palette.primary else palette.surface2,
        animationSpec = AppMotion.springOf(durationMillis = 200, bounce = 0.0),
        label = "switch_track",
    )
    Box(
        modifier
            .size(width = 50.dp, height = 30.dp)
            .sticker(shape = CircleShape, fill = track, drop = 0.dp)
            .toggleable(
                value = checked,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = androidx.compose.ui.semantics.Role.Switch,
                onValueChange = onCheckedChange,
            )
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier),
    ) {
        Box(
            Modifier
                .padding(start = 2.5.dp, top = 2.dp)
                .graphicsLayer { translationX = 20.dp.toPx() * knob }
                .size(23.dp)
                .sticker(shape = CircleShape, fill = Color.White, drop = 2.dp, clipContent = false),
        )
    }
}

// ---------------------------------------------------------------- 二级页

/** 二级页骨架：`[‹] 标题 / 副标题`，下面是可滚动的内容 */
@Composable
private fun SettingsSubPage(
    page: SettingsPage,
    onBack: () -> Unit,
    content: @Composable () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp)
            .padding(top = 16.dp, bottom = 30.dp)
            .testTag("settings_page_${page.name.lowercase()}"),
    ) {
        PageHeader(
            title = page.title,
            subtitle = page.subtitle,
            leading = {
                QingJiIconButton(
                    icon = AppIcons.Back,
                    contentDescription = "返回",
                    onClick = onBack,
                    testTag = "settings_back",
                )
            },
        )
        Spacer(Modifier.height(16.dp))
        content()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoriesPage(
    state: SettingsUiState,
    onDeleteCategory: (Category) -> Unit,
    onAdd: () -> Unit,
) {
    val palette = LocalPalette.current
    val haptics = LocalHaptics.current
    QingJiCard(modifier = Modifier.fillMaxWidth()) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            state.allCategories.forEach { cat ->
                val deletable = SettingsLogic.canDelete(cat)
                QingJiChip(
                    // 可删的分类在标签后带个「×」，提示「点了就删」
                    label = if (deletable) "${cat.label} ×" else cat.label,
                    onClick = if (deletable) {
                        {
                            haptics.perform(HapticCue.Tap)
                            onDeleteCategory(cat)
                        }
                    } else {
                        null
                    },
                    container = palette.tagBg(cat.color),
                    contentColor = palette.tagFg(cat.color),
                    dotColor = cat.color,
                )
            }
            QingJiChip(
                label = "＋ 添加分类",
                onClick = {
                    haptics.perform(HapticCue.Tap)
                    onAdd()
                },
                testTag = "settings_add_category",
            )
        }
        Text(
            "内置：工作 / 学习 / 生活 / 健康 / 其他。自己加的分类点一下就能删",
            style = AppText.sub.copy(fontSize = 12.sp),
            color = palette.text3,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SettleTimePage(current: String, onChange: (String) -> Unit) {
    val palette = LocalPalette.current
    val haptics = LocalHaptics.current
    QingJiCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            current,
            style = AppText.numLarge,
            color = palette.text,
        )
        Spacer(Modifier.height(12.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("20:00", "21:00", "22:00", "23:00").forEach { hhmm ->
                QingJiChip(
                    label = hhmm,
                    selected = current == hhmm,
                    onClick = {
                        haptics.perform(HapticCue.Tap)
                        onChange(hhmm)
                    },
                    testTag = "settle_$hhmm",
                )
            }
        }
        Text(
            "到点自动弹出「每日结算」，给今天打个分",
            style = AppText.sub.copy(fontSize = 12.sp),
            color = palette.text3,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}

@Composable
private fun NotifyPage(
    enabled: Boolean,
    stateText: String,
    actionLabel: String,
    onEnabledChange: (Boolean) -> Unit,
    onAction: () -> Unit,
) {
    val palette = LocalPalette.current
    val haptics = LocalHaptics.current
    QingJiCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("到点提醒", style = TextStyle(fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold), color = palette.text)
                Text(stateText, style = AppText.sub, color = palette.text2)
            }
            StickerSwitch(
                checked = enabled,
                onCheckedChange = {
                    haptics.perform(if (it) HapticCue.ToggleOn else HapticCue.ToggleOff)
                    onEnabledChange(it)
                },
                testTag = "settings_notify",
            )
        }
        if (actionLabel.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            QingJiChip(
                label = actionLabel,
                selected = true,
                onClick = {
                    haptics.perform(HapticCue.Tap)
                    onAction()
                },
                testTag = "settings_notify_action",
            )
        }
    }
    Spacer(Modifier.height(14.dp))
    // 只写亲测机型（iQOO / vivo）。不加这三步，定时提醒会被系统冻掉。
    QingJiCard(modifier = Modifier.fillMaxWidth(), container = palette.surface2, drop = 0.dp) {
        Text("想让提醒准点收到，在 iQOO / vivo 上做这三步", style = AppText.cardTitle, color = palette.text)
        listOf(
            "设置 → 应用与权限 → 权限管理 → 自启动 → 允许",
            "设置 → 电池 → 后台高耗电 → 允许",
            "设置 → 通知与状态栏 → 应用通知管理 → 鲸记 → 锁屏通知选「显示」",
        ).forEachIndexed { i, step ->
            Row(Modifier.padding(top = 8.dp)) {
                Text("${i + 1}.", style = AppText.numSmall, color = palette.text2, modifier = Modifier.width(18.dp))
                Text(step, style = AppText.sub, color = palette.text2)
            }
        }
        Text(
            "不做的话，到点提醒可能被系统延迟或静默拦掉 —— 这不是 App 的问题。",
            style = AppText.sub.copy(fontSize = 12.sp),
            color = palette.text3,
            modifier = Modifier.padding(top = 10.dp),
        )
    }
}

@Composable
private fun BackupPage(onBackup: () -> Unit, onRestore: () -> Unit) {
    val palette = LocalPalette.current
    val haptics = LocalHaptics.current
    QingJiCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            "导出一个文件存起来，换手机或重装时导回来。",
            style = AppText.sub,
            color = palette.text2,
        )
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            QingJiChip(
                label = "导出备份",
                selected = true,
                onClick = {
                    haptics.perform(HapticCue.Commit)
                    onBackup()
                },
                testTag = "settings_backup",
            )
            QingJiChip(
                label = "恢复备份",
                onClick = {
                    haptics.perform(HapticCue.Tap)
                    onRestore()
                },
                testTag = "settings_restore",
            )
        }
        Text(
            "恢复会用备份里的数据完全覆盖这台手机上的数据；恢复前会自动存一份临时备份。",
            style = AppText.sub.copy(fontSize = 12.sp),
            color = palette.coral,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}

// ---------------------------------------------------------------- 小组件

/**
 * 配色色点（设计稿 `.sw`）：40dp 带高光的小泡泡 + 墨线；选中时外面一圈卡片色 + 一圈墨线，
 * 右下角一颗金色小勾。色值是数据（每套配色自己的主色），不走主题。
 */
@Composable
private fun SchemeSwatch(
    label: String,
    color: Color,
    active: Boolean,
    onClick: () -> Unit,
    onPositioned: (androidx.compose.ui.geometry.Offset) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalPalette.current
    val interactionSource = remember { MutableInteractionSource() }
    // 记下色点在屏幕上的位置 —— 圆形换肤要从这里开始扩散
    var centerOnScreen by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    Column(
        modifier.clickable(interactionSource = interactionSource, indication = null) {
            onPositioned(centerOnScreen)
            onClick()
        },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(49.dp)
                .onGloballyPositioned { coords ->
                    val b = coords.boundsInWindow()
                    centerOnScreen = androidx.compose.ui.geometry.Offset(b.left + b.width / 2f, b.top + b.height / 2f)
                }
                .then(
                    if (active) {
                        Modifier.drawBehind {
                            val w = Dimens.strokeWidth.dp.toPx()
                            drawCircle(palette.ink, radius = size.minDimension / 2f - w / 2f, style = Stroke(width = w))
                        }
                    } else {
                        Modifier
                    }
                ),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .size(40.dp)
                    .sticker(shape = CircleShape, fill = color, interactionSource = interactionSource, drop = 0.dp)
                    .drawBehind {
                        drawCircle(
                            Color.White.copy(alpha = 0.6f),
                            radius = 4.5.dp.toPx(),
                            center = androidx.compose.ui.geometry.Offset(size.width * 0.32f, size.height * 0.28f),
                        )
                    },
            )
            if (active) {
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .size(16.dp)
                        .sticker(shape = CircleShape, fill = palette.gold, drop = 0.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = "当前",
                        tint = palette.ink,
                        modifier = Modifier.size(10.dp),
                    )
                }
            }
        }
        Text(
            label,
            style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold),
            color = if (active) palette.text else palette.text2,
            modifier = Modifier.padding(top = 3.dp),
        )
    }
}

/**
 * AI 助手二级页：API 地址 / API Key / 模型 + 测试连接。
 */
@Composable
private fun AiSettingsCard(
    config: AiConfig,
    onChange: (AiConfig) -> Unit,
    onTest: (AiConfig, (Result<String>) -> Unit) -> Unit,
) {
    val palette = LocalPalette.current
    val haptics = LocalHaptics.current
    var draft by remember(config) { mutableStateOf(config) }
    var testing by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(null) }

    QingJiCard(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = draft.baseUrl,
            onValueChange = { draft = draft.copy(baseUrl = it); onChange(draft) },
            label = { Text("API 地址") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("ai_base_url"),
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = draft.apiKey,
            onValueChange = { draft = draft.copy(apiKey = it); onChange(draft) },
            label = { Text("API Key") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("ai_api_key"),
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = draft.model,
            onValueChange = { draft = draft.copy(model = it); onChange(draft) },
            label = { Text("模型") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("ai_model"),
        )
        Text(
            com.qingji.plan.data.model.AiConfig.VISION_MODEL_HINT,
            style = AppText.sub,
            color = palette.text3,
            fontSize = 11.sp,
            modifier = Modifier.padding(top = 4.dp).testTag("ai_model_hint"),
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // 没配好、或正在测，都不给点 —— 对应原来 clickable(enabled = …)
            val canTest = draft.isConfigured && !testing
            QingJiChip(
                label = if (testing) "测试中…" else "测试连接",
                selected = canTest,
                onClick = if (canTest) {
                    {
                        haptics.perform(HapticCue.Tap)
                        testing = true
                        result = null
                        onTest(draft) { r ->
                            testing = false
                            result = r.fold(
                                onSuccess = { "✅ 连接成功：$it" },
                                onFailure = { "❌ ${it.message?.take(120) ?: "连接失败"}" },
                            )
                        }
                    }
                } else {
                    null
                },
                contentColor = if (draft.isConfigured) palette.scheme.onPrimary else palette.text3,
                testTag = "ai_test",
            )
        }
        result?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, style = AppText.sub, color = palette.text2, fontSize = 11.sp)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "兼容 OpenAI 接口格式。默认走 DeepSeek，也可以换成任何兼容的服务",
            style = AppText.sub,
            color = palette.text3,
            fontSize = 11.sp,
        )
    }
}

/**
 * 添加分类的弹窗。
 */
@Composable
private fun AddCategoryDialog(
    existing: List<Category>,
    onDismiss: () -> Unit,
    onConfirm: (String, Int) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var colorIndex by remember { mutableStateOf(0) }
    val canAdd = SettingsLogic.canAddCategory(name, existing)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加分类") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("分类名称") },
                    singleLine = true,
                    isError = name.isNotBlank() && !canAdd,
                    modifier = Modifier.fillMaxWidth().testTag("new_category_name"),
                )
                if (name.isNotBlank() && !canAdd) {
                    Text("这个名字已经有了", color = LocalPalette.current.danger, fontSize = 11.sp)
                }
                Spacer(Modifier.height(12.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SettingsUiState.CUSTOM_COLOR_POOL.forEachIndexed { index, color ->
                        val interactionSource = remember { MutableInteractionSource() }
                        Box(
                            Modifier
                                .size(26.dp)
                                .clip(CircleShape)
                                .background(color)
                                .then(
                                    if (index == colorIndex) {
                                        Modifier.border(2.dp, LocalPalette.current.text, CircleShape)
                                    } else {
                                        Modifier
                                    }
                                )
                                .clickable(
                                    interactionSource = interactionSource,
                                    indication = dimIndication(CircleShape),
                                ) { colorIndex = index }
                                .testTag("new_category_color_$index"),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = canAdd,
                onClick = { onConfirm(name, colorIndex) },
                modifier = Modifier.testTag("new_category_confirm"),
            ) { Text("添加") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

// ---------------------------------------------------------------- 小工具

private fun parseColor(hex: String): Color =
    runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrDefault(Color.Gray)


/**
 * 作息时间表。
 *
 * 课表左边那根轴是按「节次」排的，每个学校不一样、换学期还会变，所以做成可改的。
 * 默认给的是用户学校那套（6 节 × 85 分钟，见 [PeriodSchedules.DEFAULT]）。
 *
 * 编辑时**本地先留住原始文本**：用户打到一半（「8:」）不能立刻被规整掉、
 * 也不能因为一行没填完就把整张表丢掉。只有整张表都合法了才往上传。
 */
@Composable
private fun PeriodsCard(
    periods: List<SchoolPeriod>,
    onChange: (List<SchoolPeriod>) -> Unit,
) {
    val palette = LocalPalette.current
    val haptics = LocalHaptics.current

    // 草稿。只在外面传进来的值真的变了的时候才重置（避免打字打到一半被冲掉）
    var draft by remember(periods) { mutableStateOf(periods) }
    val allValid = draft.all(PeriodSchedules::isSane) && draft.isNotEmpty()

    fun push(next: List<SchoolPeriod>) {
        draft = next
        if (next.isNotEmpty() && next.all(PeriodSchedules::isSane)) onChange(next)
    }

    QingJiCard(modifier = Modifier.fillMaxWidth()) {

        draft.forEachIndexed { index, period ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "${period.index}",
                    style = AppText.sub.copy(fontWeight = FontWeight.Bold),
                    color = palette.text2,
                    modifier = Modifier.width(20.dp),
                )
                PeriodTimeField(
                    value = period.start,
                    label = "开始",
                    testTag = "period_${period.index}_start",
                    modifier = Modifier.weight(1f),
                    onValueChange = { raw ->
                        val next = draft.toMutableList()
                        next[index] = period.copy(start = raw)
                        push(next)
                    },
                )
                Text("–", color = palette.text3)
                PeriodTimeField(
                    value = period.end,
                    label = "结束",
                    testTag = "period_${period.index}_end",
                    modifier = Modifier.weight(1f),
                    onValueChange = { raw ->
                        val next = draft.toMutableList()
                        next[index] = period.copy(end = raw)
                        push(next)
                    },
                )
                QingJiIconButtonBox(
                    onClick = {
                        haptics.perform(HapticCue.Tap)
                        val next = draft.toMutableList().also { it.removeAt(index) }
                        // 节次号重排，保证一直是 1..n
                        push(next.mapIndexed { i, p -> p.copy(index = i + 1) })
                    },
                    testTag = "period_${period.index}_remove",
                ) {
                    Icon(Icons.Outlined.Close, contentDescription = "删掉这一节", tint = palette.text3, modifier = Modifier.size(16.dp))
                }
            }
        }

        if (!allValid) {
            Text(
                "有格子没填对：时间要写成 8:30 这样（结束要晚于开始）",
                style = AppText.sub.copy(fontSize = 11.sp),
                color = palette.danger,
                modifier = Modifier.padding(bottom = 4.dp).testTag("periods_invalid"),
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            QingJiChip(
                label = "＋ 加一节",
                onClick = {
                    haptics.perform(HapticCue.Tap)
                    // 新一节默认接在最后一节结束之后，时长跟上一节一样
                    val last = draft.lastOrNull()
                    val startMin = last?.endMinute ?: (8 * 60)
                    val duration = last?.durationMinutes?.takeIf { it > 0 } ?: 85
                    push(
                        draft + SchoolPeriod(
                            index = draft.size + 1,
                            start = minuteToHHmm(startMin),
                            end = minuteToHHmm(startMin + duration),
                        )
                    )
                },
                container = palette.primarySoft,
                contentColor = palette.primaryDeep,
                testTag = "periods_add",
            )
            QingJiChip(
                label = "恢复默认",
                onClick = {
                    haptics.perform(HapticCue.Tap)
                    push(PeriodSchedules.DEFAULT)
                },
                // 原来没有底色，直接落在卡片上；给成卡片色才不会多出一块
                container = palette.surface,
                contentColor = palette.text3,
                testTag = "periods_reset",
            )
        }
    }
}

/** 作息表里一个时间格。宽度很窄，所以不用 OutlinedTextField 那一整套 */
@Composable
private fun PeriodTimeField(
    value: String,
    label: String,
    testTag: String,
    modifier: Modifier = Modifier,
    onValueChange: (String) -> Unit,
) {
    val palette = LocalPalette.current
    val shape = RoundedCornerShape(10.dp)
    Column(modifier) {
        Text(label, style = AppText.sub.copy(fontSize = 10.sp), color = palette.text3)
        Box(
            Modifier
                .fillMaxWidth()
                .sticker(shape = shape, fill = palette.surface2, drop = 0.dp)
                .padding(horizontal = 10.dp, vertical = 8.dp),
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = AppText.numSmall.copy(fontSize = 14.sp, color = palette.text),
                cursorBrush = SolidColor(palette.primary),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth().testTag(testTag),
            )
        }
    }
}

private fun minuteToHHmm(minute: Int): String {
    val m = ((minute % 1440) + 1440) % 1440
    val h = m / 60
    return (if (h < 10) "0$h" else "$h") + ":" + (if (m % 60 < 10) "0${m % 60}" else "${m % 60}")
}
