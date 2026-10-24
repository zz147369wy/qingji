package com.qingji.plan.ui.plans

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qingji.plan.data.db.entity.LongTermPlanEntity
import com.qingji.plan.data.model.Cadence
import com.qingji.plan.data.model.Categories
import com.qingji.plan.data.model.Category
import com.qingji.plan.ui.components.PageHeader
import com.qingji.plan.ui.components.QingJiCard
import com.qingji.plan.ui.components.QingJiChip
import com.qingji.plan.ui.components.QingJiIconButton
import com.qingji.plan.ui.components.QingJiIconButtonBox
import com.qingji.plan.ui.components.dimIndication
import com.qingji.plan.ui.haptics.HapticCue
import com.qingji.plan.ui.haptics.LocalHaptics
import com.qingji.plan.ui.theme.AppText
import com.qingji.plan.ui.theme.Dimens
import com.qingji.plan.ui.theme.LocalPalette
import com.qingji.plan.util.TimeUtils

/**
 * 长期计划页。
 *
 * 2026-09-29 按原站 `plan-row` 重做过。之前每一行是「标题 + 一整条拼起来的中文描述
 * + 一个「删除」文字按钮」，跟原站差三处：描述没拆成彩色标签、没有编辑按钮、
 * 删除是文字不是图标。现在逐个对齐（规格见 `扒站/app.css` 和 `app.js` 的 `Fx`）。
 *
 * 另外**所有行装在同一个 `.card` 里**，靠行底那条 1px 分隔线分开 ——
 * 原站是 `<div className="card" style={{padding:8}}>` 里面一串 `plan-row`，
 * 不是一行一张卡。空状态也在同一张卡里面。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LongTermPlanScreen(
    plans: List<LongTermPlanEntity>,
    modifier: Modifier = Modifier,
    customs: List<Category> = emptyList(),
    onBack: () -> Unit = {},
    /** [existing] 为 null 就是新建 */
    onSave: (existing: LongTermPlanEntity?, draft: PlanDraft) -> Unit = { _, _ -> },
    onDelete: (LongTermPlanEntity) -> Unit = {},
) {
    val palette = LocalPalette.current
    val haptics = LocalHaptics.current

    // 正在编辑哪一条；null 表示编辑器关着。用 PlanDraft 的「新建默认值」当哨兵太绕，
    // 所以另开一个布尔量区分「关着」和「开着但新建」
    var editorOpen by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<LongTermPlanEntity?>(null) }

    val openEditor: (LongTermPlanEntity?) -> Unit = { plan ->
        haptics.perform(HapticCue.Tap)
        editing = plan
        editorOpen = true
    }

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            // ★ 必须有不透明背景！否则下面的页面会透上来，
            //   用起来就像'贴在原页面的一层'，而不是一个独立页面
            .background(palette.bg)
            .statusBarsPadding()
            .padding(horizontal = 18.dp)
            .padding(top = 16.dp, bottom = 30.dp)
            .testTag("plans_screen"),
    ) {
        // ---------- 顶栏 ----------
        PageHeader(
            title = "长期计划",
            subtitle = "每天/每周/每月自动生成任务",
            leading = {
                IconButtonBox(
                    tag = "plans_back",
                    onClick = {
                        haptics.perform(HapticCue.Tap)
                        onBack()
                    },
                ) {
                    Icon(
                        Icons.Outlined.ArrowBack,
                        contentDescription = "返回",
                        tint = palette.text2,
                        modifier = Modifier.size(19.dp),
                    )
                }
            },
            actions = {
                IconButtonBox(tag = "plans_add", onClick = { openEditor(null) }) {
                    Icon(
                        Icons.Filled.Add,
                        contentDescription = "新建长期计划",
                        tint = palette.text2,
                        modifier = Modifier.size(20.dp),
                    )
                }
            },
        )

        Spacer(Modifier.height(16.dp))

        // ---------- 一整张卡装所有行 ----------
        // 走通用卡片 —— QingJiCard 的文档里点名的「计划大卡」就是它，投影给到原来的 6dp，
        // 内边距沿用原站那张卡被 inline 改成的 padding:8。
        QingJiCard(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("plans_card"),
            contentPadding = PaddingValues(8.dp),
        ) {
            if (plans.isEmpty()) {
                EmptyState(onCreate = { openEditor(null) })
            } else {
                plans.forEachIndexed { index, plan ->
                    PlanRow(
                        plan = plan,
                        meta = planMetaOf(plan, customs),
                        // `.plan-row:last-child{border-bottom:none}`
                        showDivider = index != plans.lastIndex,
                        onEdit = { openEditor(plan) },
                        onDelete = {
                            haptics.perform(HapticCue.Commit)
                            onDelete(plan)
                        },
                    )
                }
            }
        }
    }

    if (editorOpen) {
        val existing = editing
        PlanEditorDialog(
            initial = remember(existing) { PlanDraft.from(existing) },
            isEditing = existing != null,
            customs = customs,
            onDismiss = { editorOpen = false },
            onConfirm = { draft ->
                haptics.perform(HapticCue.Commit)
                onSave(existing, draft)
                editorOpen = false
            },
        )
    }
}

// ---------------------------------------------------------------- 空状态

/**
 * 原站 `.empty`（被 inline 覆盖成 `padding:"22px 8px"`）：
 * 34px 的 emoji + 一句提示 + 一个胶囊按钮 `.ct-addbtn`。
 *
 * ⚠️ emoji 是 **`🔁`（U+1F501）**，不是 `🔄`（U+1F504）—— 两者在安卓的 emoji 字体里
 * 长得完全不一样：🔁 是橙底白色循环箭头（跟原站截图一致），🔄 是蓝色箭头。别换回去。
 */
@Composable
private fun EmptyState(onCreate: () -> Unit) {
    val palette = LocalPalette.current
    val haptics = LocalHaptics.current

    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("🔁", fontSize = 34.sp)
        Spacer(Modifier.height(8.dp))
        Text("还没有长期计划", style = AppText.sub.copy(fontSize = 13.sp), color = palette.text3)
        Spacer(Modifier.height(12.dp))
        // `.ct-addbtn`：全圆角胶囊，底色/字色是主色的浅底 + 深字 —— 正好是 QingJiChip 的形态
        QingJiChip(
            label = "＋ 新建长期计划",
            onClick = {
                haptics.perform(HapticCue.Tap)
                onCreate()
            },
            container = palette.primarySoft,
            contentColor = palette.primaryDeep,
            testTag = "plans_empty_add",
        )
    }
}

// ---------------------------------------------------------------- 列表行

/**
 * 一行长期计划。对应原站的 `.plan-row`：
 *
 * ```
 * plan-row     flex, gap6, padding 10/8, 底部 1px 分隔线
 *   plan-main  flex:1, 可点 → 编辑
 *     plan-title  15px / weight500
 *     plan-meta   一组彩色小标签，可换行
 *   plan-edit   铅笔按钮（size15）
 *   task-del    垃圾桶按钮（size16）
 * ```
 *
 * 行体和铅笔按钮点下去**做同一件事**（打开编辑）—— 原站这两个的 onClick 就是同一个调用。
 */
@Composable
private fun PlanRow(
    plan: LongTermPlanEntity,
    meta: PlanMeta,
    showDivider: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val palette = LocalPalette.current
    val haptics = LocalHaptics.current
    // 主体那块的按压反馈。形状跟组头一致（8dp 圆角），压暗按这个形状描边
    val mainShape = RoundedCornerShape(Dimens.shapeSm.dp)
    val mainInteraction = remember { MutableInteractionSource() }

    Column(Modifier.fillMaxWidth().testTag("plan_${plan.uid}")) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // ---------- 主体（可点，进编辑） ----------
            Column(
                Modifier
                    .weight(1f)
                    .clip(mainShape)
                    .clickable(
                        interactionSource = mainInteraction,
                        indication = dimIndication(mainShape),
                    ) {
                        haptics.perform(HapticCue.Tap)
                        onEdit()
                    }
                    .testTag("plan_main_${plan.uid}"),
            ) {
                Text(
                    text = plan.title,
                    style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Medium),
                    color = palette.text,
                )

                FlowRow(
                    Modifier.padding(top = 4.dp),
                    itemVerticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    // `.tag-longterm`：循环图标 + 重复方式
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(palette.successContainer)
                            .padding(horizontal = 7.dp, vertical = 1.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Outlined.Repeat,
                            contentDescription = null,
                            tint = palette.success,
                            modifier = Modifier.size(10.dp),
                        )
                        Spacer(Modifier.width(2.dp))
                        Text(
                            text = meta.cadenceText,
                            style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.SemiBold),
                            color = palette.success,
                            modifier = Modifier.testTag("plan_cadence_${plan.uid}"),
                        )
                    }

                    // `.cat-tag` —— 跟日历页任务行同一个规格：-1.5° 倾斜、彩色底
                    Text(
                        text = meta.category.label,
                        style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold),
                        color = palette.tagFg(meta.category.color),
                        modifier = Modifier
                            .rotate(-1.5f)
                            .clip(RoundedCornerShape(4.dp))
                            .background(palette.tagBg(meta.category.color))
                            .padding(horizontal = 8.dp, vertical = 1.dp)
                            .testTag("plan_cat_${plan.uid}"),
                    )

                    // `.time-chip` —— 没排时间就不画
                    meta.timeText?.let {
                        PlanChip(it, palette.surface2, palette.text2, 12.sp, "plan_time_${plan.uid}")
                    }

                    // `.dur-tag`
                    meta.durationText?.let {
                        PlanChip(it, palette.warningContainer, palette.warning, 11.sp, "plan_dur_${plan.uid}")
                    }

                    // `.muted.small`：「2026-09-27起」
                    Text(
                        text = meta.startDateText,
                        style = TextStyle(fontSize = 12.sp),
                        color = palette.text2,
                        modifier = Modifier.testTag("plan_start_${plan.uid}"),
                    )
                }
            }

            // ---------- 编辑 ----------
            // 收敛到通用的方形图标按钮。卡片底就是 palette.surface，按钮底色也是 surface，
            // 所以视觉上仍是「无底色」，只是触摸区从原来的 28dp 放大到 40dp。
            QingJiIconButton(
                icon = Icons.Outlined.Edit,
                contentDescription = "编辑",
                tint = palette.text3,
                onClick = {
                    haptics.perform(HapticCue.Tap)
                    onEdit()
                },
                testTag = "plan_edit_${plan.uid}",
            )

            // ---------- 删除 ----------
            QingJiIconButton(
                icon = Icons.Outlined.Delete,
                contentDescription = "删除",
                tint = palette.text3,
                onClick = onDelete,
                testTag = "plan_delete_${plan.uid}",
            )
        }

        // `.plan-row{border-bottom:1px solid var(--line)}` —— 整行宽，不含左右 padding
        if (showDivider) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(palette.line))
        }
    }
}

/**
 * `.plan-meta` 里那些小标签的统一外观。
 *
 * ⚠️ 刻意**不**并入 [QingJiChip]：这里是 4dp 直角的方形小标签（11-12sp 字、1dp 竖边距），
 * 跟 999dp 全圆角的胶囊是两种形态 —— QingJiChip 也画不了长期计划节律前面那个小图标。
 * 同一处理见 AI 页的 MetaChip，别当成漏网之鱼。详见 de94861 的提交说明。
 */
@Composable
private fun PlanChip(
    text: String,
    background: Color,
    textColor: Color,
    fontSize: androidx.compose.ui.unit.TextUnit,
    tag: String,
) {
    Text(
        text = text,
        style = TextStyle(fontSize = fontSize, fontWeight = FontWeight.Medium),
        color = textColor,
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(background)
            .padding(horizontal = 7.dp, vertical = 1.dp)
            .testTag(tag),
    )
}

/**
 * `.icon-btn` 的方块按钮：38×38、圆角 12、`surface` 底、带 `--shadow-sm`。
 */
@Composable
private fun IconButtonBox(
    tag: String,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) = QingJiIconButtonBox(onClick = onClick, testTag = tag, content = content)

// ---------------------------------------------------------------- 编辑器

/**
 * 新建 / 编辑长期计划。
 *
 * ⚠️ **跟原站还有一处没对齐**：原站这个弹层是顶部下拉的 `.sheet`（带 ✕、通栏按钮），
 * 这里仍是 Material 的 `AlertDialog`。这一轮先把**行**做对（那才是用户提的），
 * 弹层本体留到下一轮。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlanEditorDialog(
    initial: PlanDraft,
    isEditing: Boolean,
    customs: List<Category>,
    onDismiss: () -> Unit,
    onConfirm: (PlanDraft) -> Unit,
) {
    val palette = LocalPalette.current
    var draft by remember(initial) { mutableStateOf(initial) }

    // 原站那排「持续」档位就是这几个（zw 数组），文案由 formatDuration 产出
    val durationOptions = listOf(15, 30, 45, 60, 90, 120, 180, 240)
    val timeOptions = listOf("08:00", "12:00", "19:00", "21:00")
    val categories = Categories.all(customs)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isEditing) "编辑长期计划" else "新建长期计划") },
        text = {
            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .testTag("plan_editor"),
            ) {
                OutlinedTextField(
                    value = draft.title,
                    onValueChange = { draft = draft.copy(title = it) },
                    label = { Text("要重复做什么") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("plan_title"),
                )

                Spacer(Modifier.height(12.dp))
                Text("重复方式", style = AppText.sub, color = palette.text2)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Cadence.entries.forEach { c ->
                        Pill(
                            text = c.label,
                            active = c == draft.cadence,
                            onClick = { draft = draft.copy(cadence = c) },
                            tag = "plan_cadence_${c.name}",
                        )
                    }
                }

                if (draft.cadence == Cadence.WEEKLY) {
                    Spacer(Modifier.height(10.dp))
                    Text("哪几天", style = AppText.sub, color = palette.text2)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        "一二三四五六日".forEachIndexed { index, ch ->
                            val active = index in draft.weekdays
                            Pill(
                                text = ch.toString(),
                                active = active,
                                onClick = {
                                    draft = draft.copy(
                                        weekdays = if (active) {
                                            draft.weekdays - index
                                        } else {
                                            (draft.weekdays + index).sorted()
                                        }
                                    )
                                },
                                tag = "plan_weekday_$index",
                            )
                        }
                    }
                }

                if (draft.cadence == Cadence.MONTHLY) {
                    Spacer(Modifier.height(10.dp))
                    Text("每月几号：${draft.dayOfMonth ?: 1}", style = AppText.sub, color = palette.text2)
                    Spacer(Modifier.height(6.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(1, 5, 10, 15, 20, 25, 28).forEach { d ->
                            Pill(
                                text = "$d",
                                active = d == (draft.dayOfMonth ?: 1),
                                onClick = { draft = draft.copy(dayOfMonth = d) },
                                tag = "plan_dom_$d",
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                Text(
                    if (draft.time == null) "时间：不指定（进「随时」区）" else "时间：${draft.time}",
                    style = AppText.sub,
                    color = palette.text2,
                )
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Pill(
                        text = "不指定",
                        active = draft.time == null,
                        onClick = { draft = draft.copy(time = null) },
                        tag = "plan_time_none",
                    )
                    timeOptions.forEach { t ->
                        Pill(
                            text = t,
                            active = draft.time == t,
                            onClick = { draft = draft.copy(time = t) },
                            tag = "plan_time_$t",
                        )
                    }
                }

                // 没排时间的计划没有「持续」可言，原站也是这么判断的
                if (draft.time != null) {
                    Spacer(Modifier.height(12.dp))
                    Text("持续", style = AppText.sub, color = palette.text2)
                    Spacer(Modifier.height(6.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        durationOptions.forEach { minutes ->
                            Pill(
                                text = TimeUtils.formatDuration(minutes),
                                active = minutes == draft.durationMinutes,
                                onClick = { draft = draft.copy(durationMinutes = minutes) },
                                tag = "plan_duration_$minutes",
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                Text("分类", style = AppText.sub, color = palette.text2)
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    categories.forEach { category ->
                        Pill(
                            text = category.label,
                            active = category.id == draft.category,
                            onClick = { draft = draft.copy(category = category.id) },
                            tag = "plan_category_${category.id}",
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = draft.canSave,
                onClick = { onConfirm(draft) },
                modifier = Modifier.testTag("plan_confirm"),
            ) { Text(if (isEditing) "保存" else "创建") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

@Composable
private fun Pill(text: String, active: Boolean, onClick: () -> Unit, tag: String) =
    QingJiChip(label = text, selected = active, onClick = onClick, compact = true, testTag = tag)
