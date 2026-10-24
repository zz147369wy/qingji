package com.qingji.plan.ui.edit

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qingji.plan.data.model.Categories
import com.qingji.plan.data.model.Category
import com.qingji.plan.data.model.Priority
import com.qingji.plan.ui.components.TimePickerSheet
import com.qingji.plan.ui.components.TopSheet
import com.qingji.plan.ui.course.CourseEditState
import com.qingji.plan.ui.components.QingJiChip
import com.qingji.plan.ui.components.QingJiIconButton
import com.qingji.plan.ui.components.sticker
import com.qingji.plan.ui.components.stickerClickable
import com.qingji.plan.ui.theme.AppText
import com.qingji.plan.ui.components.Segment
import com.qingji.plan.ui.components.SegmentedControl
import com.qingji.plan.ui.haptics.HapticCue
import com.qingji.plan.ui.haptics.LocalHaptics
import com.qingji.plan.ui.theme.LocalPalette
import com.qingji.plan.ui.today.TaskEditState
import java.time.LocalDate

/**
 * 任务 / 课程的新建与编辑弹层。
 *
 * 这一个弹层取代了原来的 `TaskEditSheet` 和 `CourseEditSheet`。
 * 结构逐行对照原站的 `sd` 组件（`扒站/app.js`），从下往上排：
 *
 * ```
 * 添加计划                                    [✕]     ← .sheet-head
 * ┌─────────────────────────────────────────┐
 * │  📝 计划   │   📚 课程                    │      ← .batch-mode，选中态白底+红棕字
 * └─────────────────────────────────────────┘
 * [ 要做什么？例如：给客户回电话            ]        ← .sheet-input，autoFocus
 * 时间    [ 11:02 – 11:17               ]           ← .sheet-row，label 固定 40 宽
 * 优先级  [普通] [⭐ 重要]                            ← 仅「计划」Tab
 * ── 换成「课程」Tab 时上面两行变成 ──
 * 星期    周一 周二 周三 周四                         ← .sheet-wd 靠 flex-wrap 折成 4+3
 *         周五 周六 周日
 * 时间    [ 11:02                       ]
 * 教室    [ 地点（可选）                 ]
 * ── 两个 Tab 共用 ──
 * [工作] [学习] [生活] [健康] [其他]                   ← .sheet-cats，无 label、无色点
 * [ 删除课程 ]              [   添加课程     ]        ← 编辑时左边才多一个红按钮
 * ```
 *
 * 三处刻意的取舍（都问过用户）：
 *   1. 编辑已有条目时 **Tab 锁死**。原站编辑课程时 Tab 还在，切过去会丢掉正在改的课。
 *   2. 原站没有「指定时间」开关，新建时时间默认就是**当前时刻**。安卓版保留「🕊 随时」区，
 *      所以把清空时间的能力挪进了时间选择器的「清除时间」。
 *   3. 分类胶囊不带色点（原站就没有），选中态靠 `category.soft` 底 + `category.deep` 字。
 */
@Composable
fun EditSheet(
    target: EditTarget,
    onDismiss: () -> Unit,
    onSaveTask: (TaskEditState) -> Unit,
    onSaveCourse: (CourseEditState) -> Unit,
    onDeleteTask: (TaskEditState) -> Unit = {},
    onDeleteCourse: (CourseEditState) -> Unit = {},
    customs: List<Category> = emptyList(),
) {
    val palette = LocalPalette.current
    val haptics = LocalHaptics.current

    // 当前 Tab。编辑时锁死在目标那一类
    var mode by remember(target) { mutableStateOf(target.mode) }
    // 标题/课程名。原站两个 Tab **共用一个** state，切 Tab 文字会带过去，这里照做
    var title by remember(target) {
        mutableStateOf(target.taskState?.title ?: target.courseState?.title ?: "")
    }
    var task by remember(target) {
        mutableStateOf(target.taskState ?: TaskEditState.create(LocalDate.now()))
    }
    var course by remember(target) {
        mutableStateOf(target.courseState ?: CourseEditState.create(CourseEditState.todayWeekday()))
    }
    var pickerOpen by remember(target) { mutableStateOf(false) }

    val isCourse = mode == EditMode.COURSE
    val canSave = title.isNotBlank()

    // 时间字段显示什么，跟原站逐字一致
    val timeText = if (isCourse) {
        if (course.hasTime) course.time else EMPTY_TIME_HINT
    } else {
        if (task.hasTime) {
            task.time + (task.endTime?.let { " – $it" } ?: "")
        } else {
            EMPTY_TIME_HINT
        }
    }
    val timeSelected = if (isCourse) course.hasTime else task.hasTime

    val headTitle = when {
        target is EditTarget.EditCourse -> "修改课程"
        target is EditTarget.EditTask -> "修改任务"
        isCourse -> "添加课程"
        else -> "添加计划"
    }
    val submitText = when {
        target.isEditing -> "保存修改"
        isCourse -> "添加课程"
        else -> "添加计划"
    }

    Box(Modifier.fillMaxWidth()) {

        TopSheet(onDismiss = onDismiss, testTag = "edit_sheet") {
            Column(Modifier.fillMaxWidth()) {

                // ---------- 标题行：左标题 + 右上角 ✕ ----------
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = headTitle,
                        style = TextStyle(fontSize = 19.sp, fontWeight = FontWeight.Black),
                        color = palette.text,
                        modifier = Modifier.testTag("edit_head_title"),
                    )
                    CloseButton(onClick = onDismiss)
                }

                // ---------- 📝 计划 / 📚 课程 ----------
                ModeTabs(
                    current = mode,
                    locked = target.isEditing,
                    onSelect = { next ->
                        haptics.perform(HapticCue.Tap)
                        mode = next
                        // 原站两个 Tab **共用** title / 时间 / 分类 这几项 state，切 Tab 会把它们带过去。
                        // 时长不带：任务默认 15 分钟、课程默认 45 分钟，各留各的更合理。
                        if (next == EditMode.COURSE) {
                            course = course.copy(
                                title = title,
                                hasTime = task.hasTime,
                                time = task.time,
                                category = task.category,
                            )
                        } else {
                            task = task.copy(
                                title = title,
                                hasTime = course.hasTime,
                                time = course.time,
                                category = course.category,
                            )
                        }
                    },
                )

                // ---------- 标题输入框 ----------
                SheetInput(
                    value = title,
                    onValueChange = { title = it.take(120) },
                    placeholder = if (isCourse) {
                        "课程名 / 科目，如：英语"
                    } else {
                        "要做什么？例如：给客户回电话"
                    },
                    autofocus = true,
                    testTag = "edit_title",
                )

                // ---------- 各 Tab 自己的字段 ----------
                if (isCourse) {
                    SheetRow(label = "星期") {
                        // **强制每行 4 个**，排成原站那样的 4+3。
                        // 原站是 `flex-wrap` 自然折行，在它那个 ~360px 的视口里正好断在 4 个；
                        // 安卓这边屏幕更宽，自然折行会变成 5+2，跟原站对不上，所以这里按行切。
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            CourseEditState.WEEKDAY_LABELS
                                .withIndex()
                                .chunked(WEEKDAY_PER_ROW)
                                .forEach { row ->
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        row.forEach { (index, label) ->
                                            SheetChip(
                                                text = label,
                                                active = course.weekday == index,
                                                modifier = Modifier.testTag("edit_weekday_$index"),
                                                onClick = {
                                                    haptics.perform(HapticCue.Tap)
                                                    course = course.copy(weekday = index)
                                                },
                                            )
                                        }
                                    }
                                }
                        }
                    }

                    SheetRow(label = "时间") {
                        TimeField(
                            text = timeText,
                            selected = timeSelected,
                            modifier = Modifier.testTag("edit_time"),
                            onClick = {
                                haptics.perform(HapticCue.Tap)
                                pickerOpen = true
                            },
                        )
                    }

                    SheetRow(label = "教室") {
                        SheetInput(
                            value = course.room,
                            onValueChange = { course = course.copy(room = it.take(40)) },
                            placeholder = "地点（可选）",
                            compact = true,
                            autofocus = false,
                            testTag = "edit_room",
                        )
                    }
                } else {
                    SheetRow(label = "时间") {
                        TimeField(
                            text = timeText,
                            selected = timeSelected,
                            modifier = Modifier.testTag("edit_time"),
                            onClick = {
                                haptics.perform(HapticCue.Tap)
                                pickerOpen = true
                            },
                        )
                    }

                    SheetRow(label = "优先级") {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PriorityPill(
                                text = "普通",
                                active = task.priority == Priority.MID,
                                modifier = Modifier.testTag("edit_prio_mid"),
                                onClick = {
                                    haptics.perform(HapticCue.Tap)
                                    task = task.copy(priority = Priority.MID)
                                },
                            )
                            PriorityPill(
                                text = "重要",
                                star = true,
                                active = task.priority == Priority.HIGH,
                                important = true,
                                modifier = Modifier.testTag("edit_prio_high"),
                                onClick = {
                                    haptics.perform(HapticCue.Tap)
                                    task = task.copy(priority = Priority.HIGH)
                                },
                            )
                        }
                    }
                }

                // ---------- 分类：每颗前面一个分类色小圆点（设计稿 `.chip.cat`） ----------
                SheetRow(label = "分类", alignTop = true) {
                  FlowRow(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                  ) {
                    val current = if (isCourse) course.category else task.category
                    Categories.all(customs).forEach { cat ->
                        SheetChip(
                            text = cat.label,
                            active = current == cat.id,
                            activeFill = palette.catChipBg(cat.color),
                            activeText = palette.catChipFg(cat.color),
                            dotColor = cat.color,
                            modifier = Modifier.testTag("edit_cat_${cat.id}"),
                            onClick = {
                                haptics.perform(HapticCue.Tap)
                                if (isCourse) {
                                    course = course.copy(category = cat.id)
                                } else {
                                    task = task.copy(category = cat.id)
                                }
                            },
                        )
                    }
                  }
                }
                Spacer(Modifier.height(18.dp))

                // ---------- 底部：删除（仅编辑）+ 通栏主按钮 ----------
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (target.isEditing) {
                        Box(
                            Modifier
                                .height(52.dp)
                                .stickerClickable(
                                    shape = RoundedCornerShape(16.dp),
                                    fill = palette.coral,
                                    onClick = {
                                        haptics.perform(HapticCue.Tap)
                                        if (isCourse) onDeleteCourse(course.copy(title = title))
                                        else onDeleteTask(task.copy(title = title))
                                    },
                                )
                                .padding(horizontal = 18.dp)
                                .testTag("edit_delete"),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = if (isCourse) "删除课程" else "删除任务",
                                style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.ExtraBold),
                                color = palette.scheme.onError,
                            )
                        }
                    }

                    // 主按钮：一张主色贴纸，按下整颗沉 3dp、投影消失（设计稿 `.btn-main`）。
                    // 还没写标题时不能点：变成平的次级底，不带投影
                    Box(
                        Modifier
                            .weight(1f)
                            .height(52.dp)
                            .stickerClickable(
                                shape = RoundedCornerShape(16.dp),
                                fill = if (canSave) palette.primary else palette.surface2,
                                drop = if (canSave) 3.dp else 0.dp,
                                stroke = if (canSave) palette.ink else palette.line,
                                enabled = canSave,
                                onClick = {
                                    haptics.perform(HapticCue.Commit)
                                    if (isCourse) {
                                        onSaveCourse(course.copy(title = title))
                                    } else {
                                        onSaveTask(task.copy(title = title))
                                    }
                                },
                            )
                            .testTag("edit_submit"),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = submitText,
                            style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.ExtraBold),
                            color = if (canSave) palette.onPrimary else palette.text3,
                        )
                    }
                }
            }
        }

        // ---------- 时间选择器叠在弹层之上 ----------
        if (pickerOpen) {
            // 选择器开着时返回键先关它，别把整个弹层一起关掉
            androidx.activity.compose.BackHandler { pickerOpen = false }

            TimePickerSheet(
                value = if (isCourse) {
                    if (course.hasTime) course.time else ""
                } else {
                    if (task.hasTime) task.time else ""
                },
                endValue = if (isCourse) course.endTime else task.endTime,
                onDismiss = { pickerOpen = false },
                onConfirm = { time, duration ->
                    if (isCourse) {
                        course = course.copy(
                            hasTime = true,
                            time = time,
                            durationMinutes = duration,
                        )
                    } else {
                        task = task.copy(
                            hasTime = true,
                            time = time,
                            durationMinutes = duration,
                        )
                    }
                    pickerOpen = false
                },
                onClear = {
                    if (isCourse) course = course.copy(hasTime = false)
                    else task = task.copy(hasTime = false)
                    pickerOpen = false
                },
            )
        }
    }
}

// ---------------------------------------------------------------- 零件

private const val EMPTY_TIME_HINT = "选择时间"

/** 星期胶囊每行 4 个，排成原站那样的 4+3 */
private const val WEEKDAY_PER_ROW = 4

/** `.icon-btn`：38×38、圆角 12、surface 底、text-2 的 ✕ */
@Composable
private fun CloseButton(onClick: () -> Unit) {
    val palette = LocalPalette.current
    QingJiIconButton(
        icon = Icons.Outlined.Close,
        contentDescription = "✕",
        onClick = onClick,
        size = 38.dp,
        tint = palette.text,
        testTag = "edit_close",
    )
}

/**
 * `.batch-mode` 分段控件。
 *
 * 内容跟批量录入页那个同款，统一走 [SegmentedControl] —— 原来这里圆角是 12
 * 而另外两处是 14，肉眼几乎看不出但是真的不一致。
 *
 * [locked] 用在「正在编辑已有条目」时：类型不允许中途改，两段都压暗且点不动。
 */
@Composable
private fun ModeTabs(
    current: EditMode,
    locked: Boolean,
    onSelect: (EditMode) -> Unit,
) {
    SegmentedControl(
        segments = listOf(
            // 【2026-10 鲸鱼主题】原来的 📝📚 emoji 换成线性图标 ——
            // emoji 的样子随系统字体变，跟墨线画风也不搭
            Segment("计划", "edit_tab_task", Icons.Outlined.Edit),
            Segment("课程", "edit_tab_course", Icons.Outlined.MenuBook),
        ),
        selectedIndex = current.ordinal,
        onSelect = { index -> onSelect(EditMode.entries[index]) },
        enabled = !locked,
        modifier = Modifier.padding(bottom = 12.dp),
    )
}

/**
 * `.sheet-row`：左侧标签固定 40dp 宽 + 12dp 间距，右边是内容。
 * 那个 40dp 就是「星期」标签在左、且所有行对齐的原因。
 */
@Composable
private fun SheetRow(
    label: String,
    /** 内容是多行（分类、星期）时标签贴顶，和第一行胶囊对齐 */
    alignTop: Boolean = false,
    content: @Composable () -> Unit,
) {
    val palette = LocalPalette.current
    Row(
        Modifier.fillMaxWidth().padding(top = 12.dp),
        verticalAlignment = if (alignTop) Alignment.Top else Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = label,
            style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold),
            color = palette.text2,
            modifier = Modifier
                .width(44.dp)
                .then(if (alignTop) Modifier.padding(top = 7.dp) else Modifier),
        )
        Box(Modifier.weight(1f)) { content() }
    }
}

/**
 * 输入框。`compact` = 原站 `.sheet-inline`（教室行，14px），否则是 `.sheet-input`（15px）。
 * 聚焦时描边变主色、底色变白，跟原站的 `:focus` 一致。
 */
@Composable
private fun SheetInput(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    testTag: String,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    autofocus: Boolean = false,
) {
    val palette = LocalPalette.current
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(autofocus) {
        if (autofocus) runCatching { focusRequester.requestFocus() }
    }

    // 设计稿 `.field`：50 高、圆角 14、次级底 + 墨线。聚焦时描边换成主色
    val shape = RoundedCornerShape(if (compact) 12.dp else 14.dp)

    Box(
        modifier
            .fillMaxWidth()
            .then(if (compact) Modifier.heightIn(min = 40.dp) else Modifier.padding(bottom = 6.dp).heightIn(min = 50.dp))
            .sticker(
                shape = shape,
                fill = palette.surface2,
                drop = 0.dp,
                stroke = if (focused) palette.primary else palette.ink,
            )
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (value.isEmpty()) {
            Text(
                text = placeholder,
                style = TextStyle(fontSize = if (compact) 14.sp else 15.sp),
                color = palette.text3,
            )
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            interactionSource = interaction,
            textStyle = TextStyle(
                fontSize = if (compact) 14.sp else 15.sp,
                color = palette.text,
            ),
            cursorBrush = SolidColor(palette.primary),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
                .testTag(testTag),
        )
    }
}

/** `.time-btn`：长得像输入框的按钮，没选时间时文字是 text-3 的「选择时间」 */
@Composable
private fun TimeField(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    // 设计稿：一颗 36 高的胶囊，钟表图标 + Fredoka 数字，不再是通栏的输入框
    val palette = LocalPalette.current
    Row(
        modifier
            .height(36.dp)
            .stickerClickable(shape = RoundedCornerShape(999.dp), fill = palette.surface, drop = 0.dp, onClick = onClick)
            .padding(horizontal = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Outlined.Schedule,
            contentDescription = null,
            tint = if (selected) palette.text else palette.text3,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = text,
            style = if (selected) AppText.numSmall else TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
            color = if (selected) palette.text else palette.text3,
            modifier = Modifier.padding(start = 6.dp),
        )
    }
}

/**
 * `.sheet-cat`：圆角胶囊。
 *
 * 默认选中态是 `--primary-soft` + `--primary-deep` + 1.5px 内描边 `#c97b4a66`；
 * 分类行会传入自己的 `soft` / `deep` 覆盖底色和字色（描边仍是那个固定的橙）。
 * **不带色点**——原站就没有。
 */
@Composable
private fun SheetChip(
    text: String,
    active: Boolean,
    modifier: Modifier = Modifier,
    activeFill: Color? = null,
    activeText: Color? = null,
    dotColor: Color? = null,
    onClick: () -> Unit,
) {
    val palette = LocalPalette.current
    QingJiChip(
        label = text,
        modifier = modifier,
        selected = active,
        onClick = onClick,
        selectedContainer = activeFill ?: palette.primary,
        selectedContentColor = activeText ?: palette.onPrimary,
        dotColor = dotColor,
    )
}

/**
 * `.sheet-prio`：普通选中是主色底，「重要」选中是**胸针金**。
 *
 * 【2026-10 鲸鱼主题】「重要」原来用危险色（error），和「删除」撞在一起 ——
 * 重要的任务不该看起来像要被删掉。现在整 App 只有一块金色，它是其中之一。
 */
@Composable
private fun PriorityPill(
    text: String,
    active: Boolean,
    modifier: Modifier = Modifier,
    important: Boolean = false,
    /** 文字前挂一颗金星 */
    star: Boolean = false,
    onClick: () -> Unit,
) {
    val palette = LocalPalette.current
    QingJiChip(
        label = text,
        modifier = modifier,
        selected = active,
        onClick = onClick,
        selectedContainer = if (important) palette.gold else palette.primary,
        // 金底上用藏青字（深色模式的墨线是近黑，同样看得清）
        selectedContentColor = if (important) palette.ink else palette.onPrimary,
        leading = if (star) {
            { com.qingji.plan.ui.today.StickerStar(size = 14.dp, fill = if (active) palette.surface else palette.gold) }
        } else {
            null
        },
    )
}

