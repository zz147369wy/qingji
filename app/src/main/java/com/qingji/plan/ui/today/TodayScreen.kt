package com.qingji.plan.ui.today

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.sp
import com.qingji.plan.ui.components.Segment
import com.qingji.plan.ui.components.SegmentedControl
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.EventRepeat
import androidx.compose.material.icons.outlined.FactCheck
import androidx.compose.material.icons.outlined.PlaylistAdd
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.qingji.plan.data.db.entity.TaskEntity
import com.qingji.plan.data.model.Category
import com.qingji.plan.data.model.Holidays
import com.qingji.plan.ui.components.QingJiChip
import com.qingji.plan.ui.components.QingJiFab
import com.qingji.plan.ui.components.dimIndication
import com.qingji.plan.ui.components.rememberSwipeListState
import com.qingji.plan.ui.components.sticker
import com.qingji.plan.ui.components.stickerClickable
import com.qingji.plan.ui.haptics.HapticCue
import com.qingji.plan.ui.haptics.LocalHaptics
import com.qingji.plan.ui.pet.PetDockRegistry
import com.qingji.plan.ui.pet.petDock
import com.qingji.plan.ui.theme.AppText
import com.qingji.plan.ui.theme.Dimens
import com.qingji.plan.ui.theme.LocalPalette
import kotlinx.coroutines.launch

/** 日期 Pager 的起始页号。取中间值，前后都留够天数可以翻 */
private const val START_PAGE = 1000

/** 页数。用不完，只是给「往后翻」留出足够的空间 */
private const val PAGE_COUNT = 4000

/** 列表左右内边距 */
private val SidePadding = 18.dp

/**
 * 今日页。整个 App 的主战场。
 *
 * 【2026-10 鲸鱼主题】从上到下：
 *   1. 问候语 + 日期副标题
 *   2. **舷窗卡**：左边一只舷窗（水位 = 完成率），右边是鲸鱼空空的窝
 *   3. **工具行**：批量录入 / 长期计划 / 每日结算 / AI 排期（带文字的贴纸钮）
 *   4. **一周横条**：点哪天去哪天，今天挂一条金色小鲸尾
 *   5. 筛选胶囊：全部 / 待办 / 已完成（**现在真的会过滤列表**）
 *   6. 按海水深度分层的任务列表
 * 外加右下角悬浮 ＋ 气泡按钮。
 */
@Composable
fun TodayScreen(
    state: TodayUiState,
    modifier: Modifier = Modifier,
    customs: List<Category> = emptyList(),
    onFilterChange: (TaskFilter) -> Unit = {},
    onBackToToday: () -> Unit = {},
    onToggleTask: (TaskEntity) -> Unit = {},
    onTaskClick: (TaskEntity) -> Unit = {},
    onAddTask: () -> Unit = {},
    /** 左滑删除、以及多选里的批量删除都走它 */
    onDeleteTask: (TaskEntity) -> Unit = {},
    /**
     * 拿某一天的快照，给左右邻页用。**只在滑动时才调**，所以可以是一次性查询。
     */
    stateFor: suspend (java.time.LocalDate) -> TodayUiState = { state },
    /** 划到别的日期了 */
    onGoToDate: (java.time.LocalDate) -> Unit = {},
    /** 当前小时。用来判「已过时」——注入是为了可测 */
    hourProvider: () -> Int = { java.time.LocalTime.now().hour },
    onOpenBatch: () -> Unit = {},
    onOpenPlans: () -> Unit = {},
    onOpenSettle: () -> Unit = {},
    onOpenAutoSchedule: () -> Unit = {},
    /** 完成一件 / 全部完成时通知外面（让空空跳一下、转个圈） */
    onCelebrate: (allDone: Boolean) -> Unit = {},
    /** 当前这一周每天有几件事（一周横条的小圆点） */
    weekCounts: Map<java.time.LocalDate, Int> = emptyMap(),
) {
    // 折叠了哪些分组。纯界面状态，不落库
    var collapsedGroups by remember { mutableStateOf(emptySet<TodayGroup>()) }

    // 多选中的任务 uid。非空即处于多选模式
    var selection by remember { mutableStateOf(emptySet<String>()) }

    // 整份列表共享「哪一行滑开着」。放在这一层是为了跨重组存活
    val swipeState = rememberSwipeListState()

    // 完成庆祝的气泡。只有「待办 → 已完成」才冒，取消完成不冒
    var burst by remember { mutableStateOf<BurstTrigger?>(null) }
    var burstSeq by remember { mutableIntStateOf(0) }

    /**
     * 刚完成、还要再留一会儿的任务。
     *
     * 在「待办」筛选下勾掉一条，如果立刻把它从列表里撤走，气泡会跟着一起消失 ——
     * 所以先让它留在原位，气泡飘完（约 1.6 秒）再放它走。
     */
    var lingering by remember { mutableStateOf(emptySet<String>()) }

    // 气泡层的根坐标原点。勾选框报上来的是根坐标，得减掉这个原点
    var overlayOrigin by remember { mutableStateOf(Offset.Zero) }

    // 换日期时清掉多选和滞留 —— 它们可能根本不在新的一天里
    LaunchedEffect(state.date) {
        selection = emptySet()
        lingering = emptySet()
    }

    // 进多选就把已经滑开的行合上
    LaunchedEffect(selection.isNotEmpty()) {
        if (selection.isNotEmpty()) swipeState.close()
    }

    // 日期 Pager 的锚点：建好之后就不再变，这样「页号 ↔ 日期」的换算才是稳定的
    val anchor = remember { state.date }
    fun dateOfPage(page: Int) = anchor.plusDays((page - START_PAGE).toLong())

    val pagerState = rememberPagerState(initialPage = START_PAGE) { PAGE_COUNT }
    val scope = rememberCoroutineScope()

    LaunchedEffect(state.date) {
        val target = START_PAGE + java.time.temporal.ChronoUnit.DAYS
            .between(anchor, state.date).toInt()
        if (target in 0 until PAGE_COUNT && pagerState.currentPage != target) {
            pagerState.animateScrollToPage(target)
        }
    }

    Box(
        modifier
            .fillMaxSize()
            .onGloballyPositioned { overlayOrigin = it.positionInRoot() }
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            pageSpacing = 0.dp,
            userScrollEnabled = false,
        ) { page ->
            val pageDate = dateOfPage(page)
            val isLive = pageDate == state.date

            val fetched by produceState<TodayUiState?>(null, pageDate, isLive) {
                value = if (isLive) null else stateFor(pageDate)
            }
            val ps: TodayUiState? = if (isLive) state else fetched

            if (ps != null) {
                val listState = rememberLazyListState()
                // 整页只有**一个** LazyColumn —— 页头是它的第一个 item。
                // 不能写成 Column(verticalScroll) { ...; LazyColumn { ... } }：会抛
                // 「Vertically scrollable component was measured with an infinity maximum
                // height constraints」。这个坑真踩过一次。
                //
                // 横向内边距**下放**到各个 item 里：海水色带要铺满整宽，不能跟着一起缩进。
                LazyColumn(
                    Modifier
                        .fillMaxSize()
                        .depthBands(listState)
                        .testTag("today_screen"),
                    state = listState,
                    contentPadding = PaddingValues(top = 8.dp, bottom = 30.dp),
                ) {
                    item(key = "header") {
                        Column(Modifier.padding(horizontal = SidePadding)) {
                            TodayTopBar(ps)
                            Spacer(Modifier.height(16.dp))
                            PortholeCard(ps)
                            Spacer(Modifier.height(16.dp))
                            TodayTools(
                                onOpenBatch = onOpenBatch,
                                onOpenPlans = onOpenPlans,
                                onOpenSettle = onOpenSettle,
                                onOpenAutoSchedule = onOpenAutoSchedule,
                            )
                            Spacer(Modifier.height(20.dp))
                            WeekStrip(ps, weekCounts, onGoToDate = onGoToDate, onBackToToday = onBackToToday)
                            Spacer(Modifier.height(18.dp))
                            FilterChips(ps, onFilterChange)
                            Spacer(Modifier.height(18.dp))
                        }
                    }

                    todayGroups(
                        groups = TodayGrouping.group(
                            timed = ps.shownTimed(lingering),
                            anytime = ps.shownAnytime(lingering),
                            longTerm = ps.shownLongTerm(lingering),
                            // 「已过时」只在看今天时有意义 —— 翻到别的日期时传 null 就不判它
                            nowMinute = if (ps.isToday) hourProvider() * 60 else null,
                        ),
                        customs = customs,
                        collapsed = collapsedGroups,
                        selection = selection,
                        selectionMode = selection.isNotEmpty(),
                        onToggleCollapse = { group ->
                            collapsedGroups = if (group in collapsedGroups) {
                                collapsedGroups - group
                            } else {
                                collapsedGroups + group
                            }
                        },
                        onToggle = { task, pos ->
                            // pos 是 Offset.Zero 表示「从滑动动作触发、拿不到勾选框位置」——
                            // 那种情况不冒气泡，否则会从屏幕左上角冒出来
                            val becomingDone = !task.isDone
                            if (becomingDone && pos != Offset.Zero) {
                                burstSeq += 1
                                burst = BurstTrigger(burstSeq.toLong(), pos)
                                // 先留在原位让气泡飘完，再真正离开列表
                                val uid = task.uid
                                lingering = lingering + uid
                                scope.launch {
                                    kotlinx.coroutines.delay(1600)
                                    lingering = lingering - uid
                                }
                            }
                            onToggleTask(task)
                            if (becomingDone) {
                                onCelebrate(ps.doneCount + 1 >= ps.totalCount && ps.totalCount > 0)
                            }
                        },
                        onClick = { task ->
                            // 多选状态下点行 = 勾选，不打开编辑
                            if (selection.isNotEmpty()) {
                                selection = if (task.uid in selection) {
                                    selection - task.uid
                                } else {
                                    selection + task.uid
                                }
                            } else {
                                onTaskClick(task)
                            }
                        },
                        onLongClick = { task ->
                            selection = if (task.uid in selection) {
                                selection - task.uid
                            } else {
                                selection + task.uid
                            }
                        },
                        onDelete = onDeleteTask,
                        swipeState = swipeState,
                        emptyHint = when {
                            ps.totalCount == 0 -> null
                            ps.filter == TaskFilter.TODO -> "待办都清空啦，去海里游一圈吧"
                            ps.filter == TaskFilter.DONE -> "还没有完成的，先戳一个泡泡？"
                            else -> null
                        },
                    )

                    // 列表末尾再铺一段最深的颜色，滚动到底时不会突然露出页面底色
                    item(key = "deep_tail", contentType = BandType(5)) {
                        Box(Modifier.fillMaxWidth().height(90.dp))
                    }
                }
            }
        }

        // ---------- 多选操作条 ----------
        if (selection.isNotEmpty()) {
            SelectionBar(
                count = selection.size,
                onToggleAll = {
                    val all = state.visibleTasks.map { it.uid }.toSet()
                    selection = if (selection.size == all.size) emptySet() else all
                },
                // 勾选/删除都按 uid 在 allTasks 里找，不按 visibleTasks ——
                // 否则筛选条件下选中的任务会被静默漏掉
                onComplete = {
                    state.allTasks.filter { it.uid in selection }.forEach(onToggleTask)
                    selection = emptySet()
                },
                onDelete = {
                    state.allTasks.filter { it.uid in selection }.forEach(onDeleteTask)
                    selection = emptySet()
                },
                onCancel = { selection = emptySet() },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(end = SidePadding, bottom = 22.dp),
            )
        }

        // 右下角悬浮 ＋。多选时让位给操作条
        if (selection.isEmpty()) {
            QingJiFab(
                onClick = onAddTask,
                contentDescription = "新建任务",
                testTag = "add_fab",
                hapticCue = HapticCue.Commit,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = SidePadding, bottom = 22.dp),
            )
        }

        // 气泡层盖在最上面。没在播的时候它什么都不画
        CompletionBurstOverlay(
            trigger = burst?.let { BurstTrigger(it.id, it.offset - overlayOrigin) },
        )
    }
}

// ---------------------------------------------------------------- 页头

@Composable
private fun TodayTopBar(state: TodayUiState) {
    Column(Modifier.padding(vertical = 6.dp)) {
        Text(
            text = state.greetingTitle,
            style = AppText.title,
            color = LocalPalette.current.text,
            modifier = Modifier.testTag("today_greeting"),
        )
        Text(
            text = state.subtitle,
            style = AppText.sub,
            color = LocalPalette.current.text2,
            modifier = Modifier
                .padding(top = 2.dp)
                .testTag("today_subtitle"),
        )
    }
}

/**
 * 舷窗卡：左边一只舷窗（水位 = 完成率），右边是完成数和一句话。
 * 鲸鱼空空的「窝」登记在这张卡上（[PetDockRegistry.TODAY]），她坐在卡片右侧。
 *
 * 底色是从卡片色到次级底色的竖向渐变（设计稿 `.hero`）。
 */
@Composable
private fun PortholeCard(state: TodayUiState) {
    val palette = LocalPalette.current
    val shape = RoundedCornerShape(18.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 128.dp)
            .sticker(shape = shape, fill = Color.Transparent, drop = 3.dp)
            .background(Brush.verticalGradient(listOf(palette.surface, palette.surface2)))
            .petDock(PetDockRegistry.TODAY)
            .testTag("progress_card"),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(
            // 右边 112dp 留给鲸鱼的窝
            Modifier.padding(start = 14.dp, top = 14.dp, bottom = 14.dp, end = 112.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Porthole(percent = state.percent)
            Column(Modifier.padding(start = 14.dp)) {
                Text(
                    text = state.ringTitle,
                    style = AppText.body.copy(fontSize = 17.sp, fontWeight = FontWeight.ExtraBold),
                    color = palette.text,
                    modifier = Modifier.testTag("ring_title"),
                )
                Text(
                    text = state.ringHint,
                    style = AppText.sub.copy(lineHeight = 19.5.sp),
                    color = palette.text2,
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .testTag("ring_hint"),
                )
            }
        }
    }
}

// ---------------------------------------------------------------- 工具行

/** 页头 4 个看不懂的图标 → 带文字的贴纸钮 */
@Composable
private fun TodayTools(
    onOpenBatch: () -> Unit,
    onOpenPlans: () -> Unit,
    onOpenSettle: () -> Unit,
    onOpenAutoSchedule: () -> Unit,
) {
    val haptics = LocalHaptics.current
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ToolButton("批量录入", Icons.Outlined.PlaylistAdd, Modifier.weight(1f)) {
            haptics.perform(HapticCue.Tap); onOpenBatch()
        }
        ToolButton("长期计划", Icons.Outlined.EventRepeat, Modifier.weight(1f)) {
            haptics.perform(HapticCue.Tap); onOpenPlans()
        }
        ToolButton("每日结算", Icons.Outlined.FactCheck, Modifier.weight(1f)) {
            haptics.perform(HapticCue.Tap); onOpenSettle()
        }
        // AI 排期用胸针金单独点出来 —— 全 App 只有它和「今天」用金色
        ToolButton("AI 排期", Icons.Outlined.AutoFixHigh, Modifier.weight(1f), gold = true) {
            haptics.perform(HapticCue.Tap); onOpenAutoSchedule()
        }
    }
}

@Composable
private fun ToolButton(
    label: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    gold: Boolean = false,
    onClick: () -> Unit,
) {
    val palette = LocalPalette.current
    Column(
        modifier = modifier
            .height(62.dp)
            .stickerClickable(
                shape = RoundedCornerShape(16.dp),
                fill = if (gold) palette.goldSoft else palette.surface,
                onClick = onClick,
            )
            .testTag("top_btn_$label"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            // 金色钮：图标是金色，字仍是正文色（设计稿 `.tool.ai`）
            tint = if (gold) palette.goldDeep else palette.primaryDeep,
            modifier = Modifier.size(22.dp),
        )
        Text(
            text = label,
            style = AppText.label.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold),
            color = palette.text,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

// ---------------------------------------------------------------- 一周横条

/**
 * 一周七天横条。点哪天去哪天 —— 比一天一天点箭头快得多。
 *
 * 标题是「2026.10 第 41 周」（ISO 周）。没选中的日子没有底，只有选中那天是一颗主色贴纸；
 * 每格下面的小圆点 = 那天有几件事（最多 3 颗）；休息日的**数字**是珊瑚红；
 * 今天那格下面挂一条金色小鲸尾。
 */
@Composable
private fun WeekStrip(
    state: TodayUiState,
    weekCounts: Map<java.time.LocalDate, Int>,
    onGoToDate: (java.time.LocalDate) -> Unit,
    onBackToToday: () -> Unit,
) {
    val palette = LocalPalette.current
    val haptics = LocalHaptics.current
    val monday = remember(state.date) { com.qingji.plan.util.DateUtils.mondayOf(state.date) }
    val days = remember(monday) { (0..6).map { monday.plusDays(it.toLong()) } }
    val today = remember(state.date) { java.time.LocalDate.now() }
    val weekNo = remember(monday) { monday.get(java.time.temporal.IsoFields.WEEK_OF_WEEK_BASED_YEAR) }

    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 2.dp, end = 2.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "%d.%02d".format(state.date.year, state.date.monthValue),
                style = AppText.numMedium.copy(fontWeight = FontWeight.Bold),
                color = palette.text,
                modifier = Modifier.testTag("week_title"),
            )
            Text(
                text = "第 $weekNo 周",
                style = AppText.label.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
                color = palette.text3,
                modifier = Modifier.padding(start = 6.dp),
            )
            Spacer(Modifier.weight(1f))
            // 不在本周时给一颗「回今天」
            if (today !in days) {
                QingJiChip(
                    label = "回今天",
                    selected = true,
                    onClick = {
                        haptics.perform(HapticCue.Tap)
                        onBackToToday()
                    },
                    compact = true,
                    testTag = "back_to_today",
                )
                Spacer(Modifier.width(6.dp))
            }
            StripArrow(Icons.Outlined.ChevronLeft, "上一周", "prev_week") {
                haptics.perform(HapticCue.Tap)
                onGoToDate(monday.minusWeeks(1))
            }
            Spacer(Modifier.width(6.dp))
            StripArrow(Icons.Outlined.ChevronRight, "下一周", "next_week") {
                haptics.perform(HapticCue.Tap)
                onGoToDate(monday.plusWeeks(1))
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            days.forEach { day ->
                DayCell(
                    date = day,
                    selected = day == state.date,
                    isToday = day == today,
                    count = weekCounts[day] ?: 0,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        haptics.perform(HapticCue.Tap)
                        onGoToDate(day)
                    },
                )
            }
        }
    }
}

@Composable
private fun DayCell(
    date: java.time.LocalDate,
    selected: Boolean,
    isToday: Boolean,
    count: Int,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val palette = LocalPalette.current
    val off = Holidays.isOffDay(date)
    val shape = RoundedCornerShape(15.dp)
    val onSel = palette.onPrimary.copy(alpha = 0.9f)

    Box(
        modifier
            // 今天：格子下沿挂一条金色小鲸尾（画在格子外面，不占布局；设计稿 `.day.today::after`）
            .then(if (isToday) Modifier.drawWithContent { drawContent(); drawWhaleTail(palette.gold, palette.ink) } else Modifier),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(66.dp)
                .stickerClickable(
                    shape = shape,
                    fill = if (selected) palette.primary else Color.Transparent,
                    drop = if (selected) 3.dp else 0.dp,
                    stroke = if (selected) palette.ink else Color.Transparent,
                    onClick = onClick,
                )
                .testTag("day_${date.dayOfMonth}"),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = if (off) "休" else com.qingji.plan.util.DateUtils.weekdayLabel(date).removePrefix("周"),
                style = AppText.label.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold),
                color = if (selected) onSel else palette.text3,
            )
            Text(
                text = date.dayOfMonth.toString(),
                style = AppText.numMedium.copy(fontSize = 18.sp, lineHeight = 20.sp),
                color = when {
                    selected -> onSel
                    off -> palette.coral
                    else -> palette.text
                },
                modifier = Modifier.padding(top = 2.dp),
            )
            // 这天有几件事：最多三颗小圆点
            Row(
                Modifier
                    .padding(top = 2.dp)
                    .height(5.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                repeat(count.coerceAtMost(3)) {
                    Box(
                        Modifier
                            .size(5.dp)
                            .background(
                                if (selected) onSel else palette.primary.copy(alpha = 0.55f),
                                CircleShape,
                            ),
                    )
                }
            }
        }
    }
}

/**
 * 金色小鲸尾：18×9dp，挂在格子下沿外面。上沿两道内凹的弧、下沿是一整条圆底 ——
 * 和设计稿里用两个圆形遮罩挖出来的形状一致。
 */
private fun DrawScope.drawWhaleTail(fill: Color, ink: Color) {
    val w = 18.dp.toPx()
    val h = 9.dp.toPx()
    val left = (size.width - w) / 2f
    val top = size.height
    val path = Path().apply {
        moveTo(left, top)
        quadraticTo(left + w * 0.25f, top + h * 1.1f, left + w * 0.5f, top)
        quadraticTo(left + w * 0.75f, top + h * 1.1f, left + w, top)
        cubicTo(left + w, top + h * 1.3f, left, top + h * 1.3f, left, top)
        close()
    }
    drawPath(path, fill)
    drawPath(path, ink, style = Stroke(width = 1.5.dp.toPx(), join = androidx.compose.ui.graphics.StrokeJoin.Round))
}

/** 横条右上角翻周的箭头 */
@Composable
private fun StripArrow(
    icon: ImageVector,
    contentDescription: String,
    testTag: String,
    onClick: () -> Unit,
) {
    val palette = LocalPalette.current
    Box(
        Modifier
            .size(32.dp)
            .stickerClickable(shape = RoundedCornerShape(11.dp), fill = palette.surface, onClick = onClick)
            .testTag(testTag),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = palette.text, modifier = Modifier.size(18.dp))
    }
}

// ---------------------------------------------------------------- 筛选

/** 「全部 / 待办 / 已完成」：一条分段控件（设计稿 `.seg.filter`），数字跟在字后面 */
@Composable
private fun FilterChips(state: TodayUiState, onChange: (TaskFilter) -> Unit) {
    val haptics = LocalHaptics.current
    val filters = TaskFilter.entries
    SegmentedControl(
        segments = filters.map { f ->
            Segment(label = f.label, testTag = "chip_${f.name}", count = state.countFor(f).toString())
        },
        selectedIndex = filters.indexOf(state.filter),
        onSelect = { i ->
            val f = filters[i]
            // 点已经选中的那个不震：什么都没变
            if (f != state.filter) haptics.perform(HapticCue.Tap)
            onChange(f)
        },
        testTag = "filter_chips",
    )
}
