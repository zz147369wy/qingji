package com.qingji.plan.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qingji.plan.data.model.Categories
import com.qingji.plan.data.model.Category
import com.qingji.plan.data.model.Priority
import com.qingji.plan.ui.components.PageHeader
import com.qingji.plan.ui.components.QingJiCard
import com.qingji.plan.ui.components.QingJiFab
import com.qingji.plan.ui.components.QingJiIconButton
import com.qingji.plan.ui.components.Segment
import com.qingji.plan.ui.components.SegmentedControl
import com.qingji.plan.ui.components.sticker
import com.qingji.plan.ui.components.stickerClickable
import com.qingji.plan.ui.today.TaskRow
import com.qingji.plan.ui.haptics.HapticCue
import com.qingji.plan.ui.haptics.LocalHaptics
import com.qingji.plan.ui.theme.AppText
import com.qingji.plan.ui.theme.Dimens
import com.qingji.plan.ui.theme.LocalPalette
import com.qingji.plan.util.TimeUtils
import java.time.LocalDate

/**
 * 日历页。两个视图：月视图 / 课程表。
 *
 * 【2026-10 鲸鱼主题】月视图每一格是独立的圆角小格；今天 = 主色实心格，
 * 「休 / 班」= 小角标，有课 = 右下角金色小圆点（设计稿 `设计稿/鲸鱼主题` 第 3 屏）。
 */
@Composable
fun CalendarScreen(
    state: CalendarUiState,
    modifier: Modifier = Modifier,
    customs: List<Category> = emptyList(),
    /** 作息时间表。课表左边那根按「节次」排的轴 */
    periods: List<com.qingji.plan.data.model.SchoolPeriod> =
        com.qingji.plan.data.model.PeriodSchedules.DEFAULT,
    onViewChange: (CalendarView) -> Unit = {},
    onPreviousMonth: () -> Unit = {},
    onNextMonth: () -> Unit = {},
    onSelectDate: (LocalDate) -> Unit = {},
    onFilterChange: (String?) -> Unit = {},
    onTaskClick: (com.qingji.plan.data.db.entity.TaskEntity) -> Unit = {},
    onCourseClick: (com.qingji.plan.data.db.entity.CourseEntity) -> Unit = {},
    onAddCourse: () -> Unit = {},
    onAddTask: () -> Unit = {},
    onTaskToggle: (com.qingji.plan.data.db.entity.TaskEntity) -> Unit = {},
) {
    val palette = LocalPalette.current
    val haptics = LocalHaptics.current

    val pagerState = rememberPagerState(initialPage = state.view.ordinal) {
        CalendarView.entries.size
    }

    // ⚠️ 必须用 rememberUpdatedState 拿最新的 state.view。
    //
    // 下面那个 LaunchedEffect(pagerState) 的 key 永远不变，所以**从不重启**，
    // 它捕获的 `state` 是首次组合那一刻的旧值。直接读 `state.view` 会得到：
    //   初始 state.view = 月视图 → 效果捕获「月视图」
    //   第一次划到课程表：target(课程) ≠ 捕获的(月) → 成立，更新 ✓
    //   state.view 变成课程，捕获的仍是「月」→ **第二次划回月视图时
    //   target(月) == 捕获的(月)，判断不成立，什么都不做** ✗
    // 症状就是「第一次划正常，之后越划越对不上」。
    val currentView by rememberUpdatedState(state.view)

    // 划动过程中**实时**把当前页写回状态 —— 顶部的「月视图 / 课程表」切换器
    // 必须跟手一起变，不能等松手停下才更新。
    //
    // 用 currentPage（离吸附点最近的那一页）而不是 settledPage：
    // currentPage 在拖过一半时就翻页，切换器正好在那一刻跟着跳；
    // settledPage 要等回弹动画停稳，会明显滞后一拍。
    //
    // （这一行曾经写成 `if (scrolling) return@collect`，那样子划动全程切换器都不动，
    //   看起来就是「内容切过去了、上面还停在旧的」。）
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }
            .collect { page ->
                val target = CalendarView.entries[page]
                if (target != currentView) {
                    haptics.perform(HapticCue.PageSwipe)
                    onViewChange(target)
                }
            }
    }

    // 点顶部的切换器 → 让 pager 滚过去。
    // 这一侧按 state.view 做 key，每次变化都会重启，所以读到的就是新值，没有陈旧问题。
    //
    // 和上面那条不会打架：实时同步把 state.view 写成 currentPage 之后，
    // 这里读到的 target 就等于 currentPage，条件不成立、不会重复滚动。
    LaunchedEffect(state.view) {
        val target = state.view.ordinal
        if (pagerState.currentPage != target) {
            pagerState.animateScrollToPage(target)
        }
    }

    androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()) {
    Column(
        Modifier
            .fillMaxSize()
            // 头部固定、不跟着竖向滚动 —— 这是套 Pager 的前提（Pager 需要一个确定的高度），
            // 也正好跟用户给的参考视频一致：那边标题栏和周表头也是钉住不动的
            .padding(horizontal = 18.dp)
            .padding(top = 16.dp)
            .testTag("calendar_screen"),
    ) {
        PageHeader(title = "日历", subtitle = state.subtitle)

        Spacer(Modifier.height(14.dp))

        // ---------- 月视图 / 课程表 ----------
        SegmentedControl(
            segments = CalendarView.entries.map {
                Segment(
                    it.label,
                    "calendar_view_${it.name}",
                    if (it == CalendarView.MONTH) Icons.Outlined.CalendarMonth else Icons.Outlined.TableChart,
                )
            },
            selectedIndex = state.view.ordinal,
            onSelect = { index ->
                val target = CalendarView.entries[index]
                if (target != state.view) {
                    haptics.perform(HapticCue.Tap)
                    onViewChange(target)
                }
            },
            testTag = "calendar_view_switcher",
        )

        Spacer(Modifier.height(14.dp))

        // ---------- 月份导航 ----------
        MonthNav(
            label = state.monthLabel,
            filterLabel = CalendarStateCalculator
                .filterOptions(customs)
                .firstOrNull { it.first == state.categoryFilter }?.second ?: "全部分类",
            filterOptions = CalendarStateCalculator.filterOptions(customs),
            onPrevious = {
                haptics.perform(HapticCue.Tap)
                onPreviousMonth()
            },
            onNext = {
                haptics.perform(HapticCue.Tap)
                onNextMonth()
            },
            onFilter = {
                haptics.perform(HapticCue.Tap)
                onFilterChange(it)
            },
        )

        Spacer(Modifier.height(12.dp))

        // 左右翻页切「月视图 / 课程表」。
        //
        // 用真正的 Pager（跟手拖、两页并排互相推挤、松手吸附到整页），
        // 而不是「切换后补个滑入动画」—— 用户给的参考视频里就是这个交互：
        // 拖动时内容 1:1 跟着手指走、两页同速并排、没有淡入没有视差。
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f),
            // 两页之间零间隙，跟参考视频一致
            pageSpacing = 0.dp,
        ) { page ->
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 90.dp),
            ) {
                if (CalendarView.entries[page] == CalendarView.MONTH) {
                    MonthGrid(
                        state = state,
                        customs = customs,
                        onSelect = { date ->
                            haptics.perform(HapticCue.Tap)
                            onSelectDate(date)
                        },
                    )

                    Spacer(Modifier.height(12.dp))

                    SelectedDayCard(
                        state = state,
                        customs = customs,
                        onTaskClick = onTaskClick,
                        onTaskToggle = onTaskToggle,
                    )
                } else {
                    CourseTable(
                        courses = state.courses,
                        periods = periods,
                        onCourseClick = onCourseClick,
                        onAddCourse = onAddCourse,
                    )
                }
            }
        }
    }

        // 右下角悬浮 ＋。月视图下建任务，课程表下建课程
        QingJiFab(
            onClick = {
                if (state.view == CalendarView.COURSE) onAddCourse() else onAddTask()
            },
            contentDescription = "新建",
            testTag = "calendar_add_fab",
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = Dimens.pagePadding.dp, bottom = 22.dp),
        )
    }
}

// ---------------------------------------------------------------- 视图切换

// ---------------------------------------------------------------- 月份导航

/**
 * 月份导航：一条贴纸卡（设计稿 `.month-nav`）。
 * `[‹]   2026.10   [›]  [全部分类 ▾]`
 */
@Composable
private fun MonthNav(
    label: String,
    filterLabel: String,
    filterOptions: List<Pair<String?, String>>,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onFilter: (String?) -> Unit,
) {
    val palette = LocalPalette.current
    var expanded by remember { mutableStateOf(false) }

    Row(
        Modifier
            .fillMaxWidth()
            .sticker(shape = RoundedCornerShape(16.dp), fill = palette.surface)
            .padding(8.dp)
            .testTag("calendar_month_nav"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        QingJiIconButton(
            icon = Icons.Outlined.ChevronLeft,
            contentDescription = "上个月",
            onClick = onPrevious,
            size = 32.dp,
            testTag = "cal_prev",
        )
        Text(
            text = label,
            style = AppText.numMedium.copy(fontSize = 18.sp, fontWeight = FontWeight.Bold),
            color = palette.text,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .weight(1f)
                .testTag("cal_month_label"),
        )
        QingJiIconButton(
            icon = Icons.Outlined.ChevronRight,
            contentDescription = "下个月",
            onClick = onNext,
            size = 32.dp,
            testTag = "cal_next",
        )

        // 「全部分类 ▾」：一颗胶囊，箭头是「点开有菜单」的唯一提示，不能省
        Box {
            Row(
                Modifier
                    .height(32.dp)
                    .stickerClickable(
                        shape = RoundedCornerShape(Dimens.shapeFull.dp),
                        fill = palette.surface,
                        drop = 0.dp,
                        onClick = { expanded = true },
                    )
                    .padding(start = 13.dp, end = 9.dp)
                    .testTag("cal_filter"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(filterLabel, style = AppText.chip.copy(fontWeight = FontWeight.SemiBold), color = palette.text)
                Icon(
                    Icons.Outlined.ExpandMore,
                    contentDescription = "筛选分类",
                    tint = palette.text,
                    modifier = Modifier.padding(start = 4.dp).size(14.dp),
                )
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                filterOptions.forEach { (id, name) ->
                    DropdownMenuItem(
                        text = { Text(name, style = AppText.body) },
                        onClick = {
                            expanded = false
                            onFilter(id)
                        },
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------- 月历网格

/** 周一开头。原站的日历第一列是周一 */
private val WEEKDAY_HEADERS = listOf("一", "二", "三", "四", "五", "六", "日")

@Composable
private fun MonthGrid(
    state: CalendarUiState,
    customs: List<Category>,
    onSelect: (LocalDate) -> Unit,
) {
    val palette = LocalPalette.current

    Column(Modifier.testTag("calendar_month_grid")) {
        // 星期表头
        Row(
            Modifier.fillMaxWidth().padding(bottom = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            WEEKDAY_HEADERS.forEach { label ->
                Text(
                    text = label,
                    style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold),
                    color = palette.text3,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        // 6 行 × 7 列
        state.cells.chunked(7).forEach { week ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                week.forEach { cell ->
                    DayCellView(
                        cell = cell,
                        customs = customs,
                        onClick = { onSelect(cell.date) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}

/**
 * 一格（设计稿 `.cell`）：
 *   - 平时是一张卡片色小格，描一圈极淡的墨线 —— 42 格都描重线会很吵
 *   - **今天**：整格主色实底 + 墨线 + 2dp 投影；选中（不是今天）：一圈墨线
 *   - 日期数字右边紧跟「休 / 班」小角标（休 = 珊瑚红，班 = 海藻绿，白字）
 *   - 下面最多三条任务小条，**有课 = 右下角一颗金色小圆点**
 *   - 不是本月的整格淡到 35%
 */
@Composable
private fun DayCellView(
    cell: DayCell,
    customs: List<Category>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalPalette.current
    val shape = RoundedCornerShape(12.dp)
    val interactionSource = remember { MutableInteractionSource() }
    val today = cell.isToday

    Box(
        modifier = modifier
            .height(70.dp)
            .alpha(if (cell.inCurrentMonth) 1f else 0.35f)
            .sticker(
                shape = shape,
                fill = if (today) palette.primary else palette.surface,
                interactionSource = interactionSource,
                drop = if (today) 2.dp else 0.dp,
                stroke = when {
                    today || cell.isSelected -> palette.ink
                    else -> palette.ink.copy(alpha = 0.10f)
                },
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 3.dp, vertical = 4.dp)
            .testTag("cal_day_${cell.date}"),
    ) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(
                Modifier.padding(start = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = cell.date.dayOfMonth.toString(),
                    style = AppText.numSmall.copy(fontSize = 12.sp),
                    color = if (today) palette.onPrimary else palette.text,
                )
                cell.holiday?.let { holiday ->
                    Text(
                        text = holiday.badge,
                        style = TextStyle(fontSize = 8.sp, fontWeight = FontWeight.Bold, lineHeight = 11.sp),
                        color = palette.scheme.onError,
                        modifier = Modifier
                            .padding(start = 3.dp)
                            .background(if (holiday.isOffDay) palette.coral else palette.mint, RoundedCornerShape(4.dp))
                            .padding(horizontal = 2.dp)
                            .testTag("cal_holiday_badge_${cell.date}"),
                    )
                }
            }

            // 任务小条
            cell.chips.forEach { chip ->
                val cat = Categories.of(chip.categoryId, customs)
                Text(
                    text = chip.title,
                    style = TextStyle(fontSize = 8.sp, fontWeight = FontWeight.Bold, lineHeight = 12.sp),
                    color = palette.tagFg(cat.color).let { if (today && palette.isDark) palette.catChipFg(cat.color) else it },
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                    textDecoration = if (chip.done) TextDecoration.LineThrough else null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .alpha(if (chip.done) 0.5f else 1f)
                        .background(
                            // 今天那格是主色实底，小条换成近白底才看得清（设计稿 `.cell.today .bar`）
                            if (today) palette.surface.let { if (palette.isDark) palette.catChipBg(cat.color) else it.copy(alpha = 0.88f) }
                            else palette.tagBg(cat.color),
                            RoundedCornerShape(4.dp),
                        )
                        .padding(horizontal = 3.dp),
                )
            }
        }

        // 有课：右下角一颗金色小圆点
        if (cell.hasCourse) {
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 1.dp, bottom = 0.dp)
                    .size(7.dp)
                    .background(palette.gold, CircleShape)
                    .border(1.dp, palette.ink, CircleShape)
                    .testTag("cal_course_dot_${cell.date}"),
            )
        }
    }
}

// ---------------------------------------------------------------- 选中那天的计划

/**
 * 选中那天的计划（设计稿 `.day-card`）：标题 + 右侧「13 项」小徽章，
 * 下面是和今日页**同一个**单行任务行（点泡泡完成，点行打开编辑）。
 */
@Composable
private fun SelectedDayCard(
    state: CalendarUiState,
    customs: List<Category>,
    onTaskClick: (com.qingji.plan.data.db.entity.TaskEntity) -> Unit,
    onTaskToggle: (com.qingji.plan.data.db.entity.TaskEntity) -> Unit = {},
) {
    val palette = LocalPalette.current

    QingJiCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("calendar_selected_day"),
    ) {
        Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = state.selectedHeading,
                style = AppText.cardTitle.copy(fontWeight = FontWeight.ExtraBold),
                color = palette.text,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "${state.selectedTasks.size} 项",
                style = AppText.numTiny.copy(fontWeight = FontWeight.Bold),
                color = palette.primaryDeep,
                modifier = Modifier
                    .background(palette.primarySoft, RoundedCornerShape(999.dp))
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            )
        }

        if (state.selectedTasks.isEmpty()) {
            Text(
                "这一天还没有计划",
                style = AppText.sub,
                color = palette.text3,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp),
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                state.selectedTasks.forEach { task ->
                    TaskRow(
                        task = task,
                        category = Categories.of(task.category, customs),
                        selectionMode = false,
                        selected = false,
                        onToggle = { onTaskToggle(task) },
                        onClick = { onTaskClick(task) },
                        onLongClick = {},
                        modifier = Modifier.testTag("cal_task_${task.uid}"),
                    )
                }
            }
        }
    }
}
