package com.qingji.plan.ui.today

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.withInfiniteAnimationFrameMillis
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Undo
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.qingji.plan.data.db.entity.TaskEntity
import com.qingji.plan.data.model.Categories
import com.qingji.plan.data.model.Category
import com.qingji.plan.ui.components.SwipeAction
import com.qingji.plan.ui.components.SwipeListState
import com.qingji.plan.ui.components.SwipeRevealRow
import com.qingji.plan.ui.components.dimIndication
import com.qingji.plan.ui.components.sticker
import com.qingji.plan.ui.haptics.HapticCue
import com.qingji.plan.ui.haptics.LocalHaptics
import com.qingji.plan.ui.theme.AppMotion
import com.qingji.plan.ui.theme.AppText
import com.qingji.plan.ui.theme.Dimens
import com.qingji.plan.ui.theme.LocalPalette

/** 列表左右内边距（和页头一致） */
private val SidePadding = 18.dp

/**
 * 一个 item 属于哪一层海水。挂在 LazyColumn item 的 `contentType` 上，
 * [depthBands] 在绘制期读它，把整条色带（连同组头的渐变、深水层的光斑）画在列表**背后**。
 *
 * @param prev 上一层的深度。组头那一格从 prev 渐变到 depth（设计稿 `.band` 的 56px 渐变）
 */
data class BandType(val depth: Int, val prev: Int = depth, val head: Boolean = false)

/**
 * 今日页的分组任务列表。
 *
 * 【2026-10 鲸鱼主题】每一组是一层**海水**：整条铺满屏幕宽度的深度色带，
 * 组头色带上写着组名、数量和深度（浅滩 · 5m），任务卡是浮在这层水里的贴纸。
 * 往下滚 = 往深处潜。
 *
 * 色带本身**不由各 item 画**，而是由 LazyColumn 上的 [depthBands] 统一画在背后：
 * 这样深水层的光斑能跨过整层水一路往上飘，item 自己只管内容。
 *
 * 仍然是 `LazyListScope` 扩展，今日页整页共用**一个** LazyColumn（页头也是它的 item）。
 * 横向内边距下放到每个 item 里 —— 色带要铺满整宽，不能跟着 LazyColumn 一起缩进。
 */
fun LazyListScope.todayGroups(
    groups: List<TaskGroup>,
    customs: List<Category>,
    collapsed: Set<TodayGroup>,
    selection: Set<String>,
    selectionMode: Boolean,
    onToggleCollapse: (TodayGroup) -> Unit,
    onToggle: (TaskEntity, Offset) -> Unit,
    onClick: (TaskEntity) -> Unit,
    onLongClick: (TaskEntity) -> Unit,
    onDelete: (TaskEntity) -> Unit,
    /** 整份列表共享「哪一行滑开着」——同一时刻只允许一行 */
    swipeState: SwipeListState,
    /** 筛完一条都没有时的提示语 */
    emptyHint: String? = null,
) {
    if (groups.isEmpty()) {
        item(key = "empty", contentType = BandType(0)) { EmptyDay(hint = emptyHint) }
        return
    }

    groups.forEachIndexed { index, group ->
        val isCollapsed = group.group in collapsed
        val depth = group.group.depth
        val prev = groups.getOrNull(index - 1)?.group?.depth ?: depth

        item(key = "head_${group.group.name}", contentType = BandType(depth, prev, head = true)) {
            GroupHeader(
                group = group.group,
                count = group.tasks.size,
                collapsed = isCollapsed,
                onToggle = { onToggleCollapse(group.group) },
            )
        }

        if (!isCollapsed) {
            // key 用 task.uid 而不是下标：删除之后下标会整体前移，
            // 用下标做 key 会让下面每一条都被当成「换了身份」而重建
            items(items = group.tasks, key = { it.uid }, contentType = { BandType(depth) }) { task ->
                val palette = LocalPalette.current
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = SidePadding),
                ) {
                    SwipeRevealRow(
                        itemKey = task.uid,
                        listState = swipeState,
                        gesturesEnabled = !selectionMode,
                        testTag = "swipe_${task.uid}",
                        // 右滑露出「完成 / 取消完成」
                        startActions = listOf(
                            SwipeAction(
                                icon = if (task.isDone) Icons.Outlined.Undo else Icons.Outlined.Check,
                                label = if (task.isDone) "取消完成" else "完成",
                                container = palette.primary,
                                content = palette.onPrimary,
                                onAction = { onToggle(task, Offset.Zero) },
                            )
                        ),
                        // 左滑露出「删除」
                        endActions = listOf(
                            SwipeAction(
                                icon = Icons.Outlined.Delete,
                                label = "删除",
                                container = palette.coral,
                                content = androidx.compose.ui.graphics.Color.White,
                                // 破坏性：执行前连震 + 整行飞出屏幕，飞完才真删
                                isDestructive = true,
                                onAction = { onDelete(task) },
                            )
                        ),
                        // 删除时这条淡出、下面的条目弹性补位
                        modifier = Modifier
                            .animateItem(
                                placementSpec = AppMotion.springOf(durationMillis = 415, bounce = 0.35),
                                fadeOutSpec = AppMotion.Springs.effectsSlow,
                                fadeInSpec = AppMotion.Springs.effects,
                            )
                            .padding(vertical = 4.5.dp),
                    ) {
                        TaskRow(
                            task = task,
                            category = Categories.of(task.category, customs),
                            selectionMode = selectionMode,
                            selected = task.uid in selection,
                            onToggle = { pos -> onToggle(task, pos) },
                            onClick = { onClick(task) },
                            onLongClick = { onLongClick(task) },
                        )
                    }
                }
            }
        }

        // 每层水底下留一截（设计稿 `.band` 的 14px 下内边距）
        item(key = "gap_${group.group.name}", contentType = BandType(depth)) {
            Box(Modifier.fillMaxWidth().height(12.dp))
        }
    }
}

/**
 * 把海水色带画在 LazyColumn **背后**。
 *
 * 读的是 [LazyListState.layoutInfo]（绘制期读，滚动只失效绘制），按每个可见 item 的
 * [BandType] 涂色：每格涂到下一格的起点为止，删除动画时 item 之间临时拉开的缝也是同色的水，
 * 不会闪出页面底色。
 *
 * 深水层（凌晨 / 随时 / 长期）里有 5 圈往上飘的光斑（设计稿 `.caustics`）：
 * 从这层水的底部冒出、上浮 260dp 后淡出，压在任务卡下面。
 * 时钟是一个只在这里被读的毫秒数，每帧只失效这一层的绘制。
 */
@Composable
fun Modifier.depthBands(listState: LazyListState): Modifier {
    val palette = LocalPalette.current
    val clock = remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        // withInfiniteAnimationFrameMillis：测试框架会把它当成「无限动画」收掉，不会卡住 idle
        while (true) withInfiniteAnimationFrameMillis { clock.longValue = it }
    }
    return this.drawBehind {
        val items = listState.layoutInfo.visibleItemsInfo
        if (items.isEmpty()) return@drawBehind
        val ringStroke = Stroke(width = 1.5.dp.toPx())
        val ringColor = palette.onDeep.copy(alpha = 0.5f)
        val rise = 260.dp.toPx()
        val fadeTo = 56.dp.toPx()

        fun drawCaustics(top: Float, bottom: Float) {
            val t = clock.longValue / 1000f
            clipRect(top = top, bottom = bottom) {
                for (i in 0 until 5) {
                    val dur = 5f + i * 1.3f
                    val p = ((t + i * 1.7f) / dur) % 1f
                    val r = (6 + (i % 3) * 3).dp.toPx() / 2f
                    val y = bottom + (10 + i * 7).dp.toPx() - rise * p
                    // 0 → 10% 淡入到 0.9，之后线性淡出（设计稿 @keyframes rise）
                    val a = if (p < 0.1f) p / 0.1f * 0.9f else 0.9f * (1f - (p - 0.1f) / 0.9f)
                    drawCircle(
                        color = ringColor.copy(alpha = ringColor.alpha * a),
                        radius = r,
                        center = Offset(size.width * (0.08f + i * 0.21f) + r, y),
                        style = ringStroke,
                    )
                }
            }
        }

        // 一层深水的上界：把同一层连着的 item 合起来，光斑在整层里飘
        var deepTop = Float.NaN
        var deepDepth = -1

        items.forEachIndexed { i, item ->
            val band = item.contentType as? BandType
            val top = item.offset.toFloat()
            val bottom = items.getOrNull(i + 1)?.offset?.toFloat() ?: (top + item.size)
            // 深水层结束（换层或断开）→ 把这一层的光斑画完
            if (!deepTop.isNaN() && (band == null || band.depth != deepDepth || band.head)) {
                drawCaustics(deepTop, top)
                deepTop = Float.NaN
            }
            if (band == null) return@forEachIndexed
            val color = palette.depths[band.depth]
            if (band.head && band.prev != band.depth) {
                drawRect(
                    brush = Brush.verticalGradient(
                        listOf(palette.depths[band.prev], color),
                        startY = top,
                        endY = top + fadeTo,
                    ),
                    topLeft = Offset(0f, top),
                    size = Size(size.width, bottom - top),
                )
            } else {
                drawRect(color, topLeft = Offset(0f, top), size = Size(size.width, bottom - top))
            }
            if (palette.isDeep(band.depth) && deepTop.isNaN()) {
                deepTop = top
                deepDepth = band.depth
            }
        }
        if (!deepTop.isNaN()) {
            val last = items.last()
            drawCaustics(deepTop, (last.offset + last.size).toFloat())
        }
    }
}

/**
 * 组头：`[水母] 凌晨 1  午夜层 · 1000m ▾`
 *
 * 背色由 [depthBands] 画（从上一层渐变到本层），这里只管内容。
 * 深水层（depth ≥ 3）文字自动反白。
 */
@Composable
private fun GroupHeader(
    group: TodayGroup,
    count: Int,
    collapsed: Boolean,
    onToggle: () -> Unit,
) {
    val palette = LocalPalette.current
    val interactionSource = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(Dimens.shapeSm.dp)
    val deep = palette.isDeep(group.depth)
    val fg = if (deep) palette.onDeep else palette.text
    val stranded = group == TodayGroup.OVERDUE

    // 箭头跟着折叠状态转。用弹簧而不是瞬变 —— 折叠展开本来就是「动」的操作
    val arrowRotation by animateFloatAsState(
        targetValue = if (collapsed) -90f else 0f,
        animationSpec = AppMotion.Springs.spatialFast,
        label = "group_arrow",
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = interactionSource,
                indication = dimIndication(shape, color = if (deep) palette.onDeep else palette.ink),
                onClick = onToggle,
            )
            .padding(start = SidePadding + 2.dp, end = SidePadding + 2.dp, top = 16.dp, bottom = 10.dp)
            .testTag("group_${group.name}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 自绘的分组图标，装在一颗小贴纸里。「已过时」是珊瑚红的（设计稿 `.band.stranded .gi`）
        Box(
            Modifier
                .size(30.dp)
                .sticker(
                    shape = RoundedCornerShape(10.dp),
                    fill = if (stranded) palette.coralSoft else palette.surface,
                    drop = 2.dp,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = GroupIcons.of(group.icon),
                contentDescription = null,
                tint = if (stranded) palette.coral else palette.primaryDeep,
                modifier = Modifier.size(18.dp),
            )
        }

        Text(
            text = group.label,
            style = AppText.cardTitle.copy(fontWeight = FontWeight.ExtraBold),
            color = fg,
            modifier = Modifier.padding(start = 8.dp),
        )
        Text(
            text = count.toString(),
            style = AppText.numSmall.copy(fontSize = 12.sp),
            color = fg,
            modifier = Modifier
                .padding(start = 8.dp)
                .background(fg.copy(alpha = 0.14f), RoundedCornerShape(999.dp))
                .padding(horizontal = 7.dp, vertical = 1.dp),
        )

        Box(Modifier.weight(1f))

        Text(
            text = group.depthLabel,
            style = AppText.numTiny,
            color = fg.copy(alpha = 0.65f),
            modifier = Modifier.padding(end = 4.dp),
        )
        Icon(
            imageVector = Icons.Outlined.ExpandMore,
            contentDescription = if (collapsed) "展开" else "收起",
            tint = fg.copy(alpha = 0.6f),
            modifier = Modifier.size(16.dp).rotate(arrowRotation),
        )
    }
}

/**
 * 一条任务都没有时的样子。
 *
 * [hint] 非空表示「是筛选筛空的」而不是「这一天真的没安排」——
 * 两种情况下该说的话不一样。
 */
@Composable
private fun EmptyDay(hint: String? = null) {
    val palette = LocalPalette.current
    Box(
        Modifier
            .fillMaxWidth()
            .padding(top = 34.dp, bottom = 40.dp)
            .testTag("today_empty"),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (hint != null) {
                Text(hint, style = AppText.body.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold), color = palette.text2)
            } else {
                Text("今天还是空的", style = AppText.body, color = palette.text2)
                Text(
                    "点右下角 ＋ 加一条，或长按着说话",
                    style = AppText.sub,
                    color = palette.text3,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }
}

/**
 * 多选模式下贴在底部的操作条。
 *
 * 长按任意一行进入多选；选中项非空时它就出现，同时把悬浮 ＋ 顶掉。
 */
@Composable
fun SelectionBar(
    count: Int,
    onToggleAll: () -> Unit,
    onComplete: () -> Unit,
    onDelete: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalPalette.current
    val haptics = LocalHaptics.current
    val shape = RoundedCornerShape(Dimens.shapeXl.dp)

    Row(
        modifier = modifier
            .sticker(shape = shape, fill = palette.surface, drop = 4.dp)
            .padding(horizontal = 6.dp, vertical = 6.dp)
            .testTag("selection_bar"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        BarAction("取消", palette.text2) {
            haptics.perform(HapticCue.Tap)
            onCancel()
        }
        Text(
            text = "已选 $count 项",
            style = AppText.sub,
            color = palette.text,
            modifier = Modifier.padding(horizontal = 10.dp),
        )
        BarAction("全选", palette.text2) {
            haptics.perform(HapticCue.Tap)
            onToggleAll()
        }
        BarAction("完成", palette.primaryDeep) {
            haptics.perform(HapticCue.Commit)
            onComplete()
        }
        BarAction("删除", palette.coral) {
            haptics.perform(HapticCue.Commit)
            onDelete()
        }
    }
}

@Composable
private fun BarAction(
    label: String,
    color: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(Dimens.shapeMd.dp)
    Text(
        text = label,
        style = AppText.chip.copy(fontWeight = FontWeight.SemiBold),
        color = color,
        modifier = Modifier
            .clip(shape)
            .clickable(
                interactionSource = interactionSource,
                indication = dimIndication(shape),
                onClick = onClick,
            )
            .padding(horizontal = 10.dp, vertical = 8.dp),
    )
}
