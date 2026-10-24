package com.qingji.plan.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.qingji.plan.ui.haptics.HapticCue
import com.qingji.plan.ui.haptics.LocalHaptics
import com.qingji.plan.ui.theme.AppMotion
import com.qingji.plan.ui.theme.Dimens
import kotlinx.coroutines.launch
import kotlin.math.abs

/** 一颗动作钮占据的**列宽**。圆钮本身是 48dp，居中放在这一列里 */
private val SwipeActionColumnWidth = 60.dp

/** 圆钮直径 */
private val SwipeActionButtonSize = 48.dp

/** 圆钮里的图标 */
private val SwipeActionIconSize = 28.dp

/** 动作组与卡片之间、以及动作组与屏幕边之间的缝 */
private val SwipeActionsGap = 6.dp

/** 飞出屏幕时多飞的一点余量，保证连圆角都出了界 */
private val SwipeFlyOutSlack = 8.dp

/** 甩动判定阈值 */
private val SwipeVelocityThreshold = 500.dp

/**
 * 展开 / 回弹用的弹簧。400ms + 回弹 0.3，与参考实现一致。
 * 回弹是有意的：吸附到位的那个「弹一下」正是这块的手感来源。
 */
private val SwipeSettleSpec = AppMotion.springOf<Float>(durationMillis = 400, bounce = 0.3)

/**
 * 按钮收回去用的弹簧。bounce = 0 —— 缩小**不该回弹**，
 * 否则会先缩过头再涨回来，看着像抖了一下。
 */
private val SwipeButtonHideSpec = AppMotion.springOf<Float>(durationMillis = 160, bounce = 0.0)

/**
 * 整份列表共享的「哪一行是滑开的」状态。
 *
 * 只有它一个字段：**同一时刻只可能有一行是滑开的**。
 * 滑开新的一行会自动把上一行合上 —— 这是 iOS 邮件那套的手感，
 * 也顺便避免了「列表里同时挂着三行动作钮」的脏画面。
 */
@Stable
class SwipeListState {
    var openKey: Any? by mutableStateOf(null)
        private set

    fun open(key: Any) {
        openKey = key
    }

    fun close() {
        openKey = null
    }
}

@Composable
fun rememberSwipeListState(): SwipeListState = remember { SwipeListState() }

/**
 * 滑开后露出的一个动作。
 *
 * @param icon 圆钮里的图标
 * @param label 无障碍描述（没有可见文字了，钮是纯图标）
 * @param container 圆钮的填充色
 * @param content 图标的颜色
 * @param onAction 执行
 * @param deepSwipe 已滑开之后**再朝同方向划第二次**、且划过深划阈值时，
 *   是否可以不用点按钮直接执行。默认 true。一侧只认第一个为 true 的动作。
 * @param isDestructive 破坏性动作：执行前先连震 + 整行飞出屏幕，飞出结束才调 [onAction]。
 */
data class SwipeAction(
    val icon: ImageVector,
    val label: String,
    val container: Color,
    val content: Color,
    val onAction: () -> Unit,
    val deepSwipe: Boolean = true,
    val isDestructive: Boolean = false,
)

/** 本行的实测宽度（px）。橡皮筋拿它当软硬基准，飞出拿它算距离。 */
private class RowWidthHolder {
    var px: Float = 0f
}

/**
 * 左滑 / 右滑露出动作按钮的行容器。
 *
 * 机制来自第三方参考实现 Cresto 的 GlasenseSwipeable（Apache-2.0），
 * 判定部分抽成了纯函数放在 [SwipeGestureMath]，这里只负责渲染与事件接线。
 *
 * ## 和上一版的区别
 *
 * | | 上一版 | 这里 |
 * |---|---|---|
 * | 手势 | `pointerInput` + `detectHorizontalDragGestures` | `draggable`，拿到**真实速度** |
 * | 松手速度 | 假速度（总位移 × 8） | 速度追踪器给的真实 px/s |
 * | 划出动作区 | 硬夹在 1.25 倍处 | **橡皮筋**，越划越沉 |
 * | 深划直达 | 划得够远就执行 | **必须先滑开松手，再划第二次**（防误删） |
 * | 动作按钮 | 整高色块 + 图标 + 文字 | **圆形悬浮钮**，逐颗弹出来 |
 * | 删除收尾 | 原地淡出 | 连打 5 下短震 + **整行飞出屏幕** |
 *
 * ## 帧率
 *
 * 位移走 `graphicsLayer { translationX = ... }` —— 那是**绘制期**属性。
 * 拖动时每帧只重绘，不重新测量、不重新布局，所以跟手且不掉帧。
 * 若换成 `Modifier.offset` 就会每帧触发布局，长列表上立刻能感觉出来。
 *
 * ⚠️ **这一层绝不能加 `.clip()`**。`Modifier.clip` 会把被 `graphicsLayer`
 * 平移出边界的后代一起裁掉 —— 删除时「整行飞出屏幕」就变成「原地消失」。
 * 圆钮自身是圆的，也不需要裁剪。
 *
 * ⚠️ 所有 `remember` / `derivedStateOf` **只 key 在 [itemKey] 与原始数值上**，
 * 绝不要 key 在 `List<SwipeAction>` 上：调用点每次重组都会造一个新的 `listOf(...)`，
 * 拿它当 key 会让阈值每帧重算、位移被重置。
 */
@Composable
fun SwipeRevealRow(
    itemKey: Any,
    listState: SwipeListState,
    modifier: Modifier = Modifier,
    /** 往右滑露出的动作（放在行的左侧） */
    startActions: List<SwipeAction> = emptyList(),
    /** 往左滑露出的动作（放在行的右侧） */
    endActions: List<SwipeAction> = emptyList(),
    /** 滑开时禁用手势（例如多选模式下） */
    gesturesEnabled: Boolean = true,
    testTag: String? = null,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val haptics = LocalHaptics.current
    val scope = rememberCoroutineScope()

    // 几何全部由纯函数算 —— 与测试里用的是同一份定义
    val buttonWidthPx = with(density) { SwipeActionColumnWidth.toPx() }
    val gapPx = with(density) { SwipeActionsGap.toPx() }
    val velocityThresholdPx = with(density) { SwipeVelocityThreshold.toPx() }
    val flyOutSlackPx = with(density) { SwipeFlyOutSlack.toPx() }

    // 往左滑能露出的总宽 = 右侧动作组；往右滑的反之
    val leftSwipeRevealWidthPx = actionsWidthPx(endActions.size, buttonWidthPx, gapPx)
    val rightSwipeRevealWidthPx = actionsWidthPx(startActions.size, buttonWidthPx, gapPx)

    // 两个位移加起来才是最终位移：flingOffset 管拖动与吸附，deleteFlingOffset 管飞出。
    // 分开放是因为飞出的目标值要「接着当前位移继续飞」，不能把拖动位移清掉。
    val flingOffset = remember(itemKey) { Animatable(0f) }
    val deleteFlingOffset = remember(itemKey) { Animatable(0f) }

    var swipeState by remember(itemKey) { mutableStateOf(SwipeState.Idle) }
    var revealedDirection by remember(itemKey) { mutableStateOf<SwipeDirection?>(null) }

    // 拖动**开始那一刻**的状态快照 —— 「必须先滑开再划第二次」全靠它
    var initialSwipeState by remember(itemKey) { mutableStateOf(SwipeState.Idle) }
    var initialRevealedDirection by remember(itemKey) { mutableStateOf<SwipeDirection?>(null) }

    // 动作钮懒组合：平时树里压根没有按钮，只有开始拖了才建
    var shouldComposeActions by remember(itemKey) { mutableStateOf(false) }
    var isFlyingOut by remember(itemKey) { mutableStateOf(false) }

    // 不走快照：拖动中每帧都读它，进快照会白引重组。它在布局阶段写入，拖动时早已有值。
    val rowWidth = remember(itemKey) { RowWidthHolder() }

    val startThresholds = remember(itemKey, startActions.size, buttonWidthPx, gapPx) {
        revealThresholdsPx(startActions.size, buttonWidthPx, gapPx)
    }
    val endThresholds = remember(itemKey, endActions.size, buttonWidthPx, gapPx) {
        revealThresholdsPx(endActions.size, buttonWidthPx, gapPx)
    }

    // derivedStateOf 只在**结果真的变了**时才通知 —— 一整个手势里只通知几次，
    // 不会每帧重组
    val startRevealedCount by remember(itemKey, startThresholds) {
        derivedStateOf { revealedCount(flingOffset.value, startThresholds) }
    }
    val endRevealedCount by remember(itemKey, endThresholds) {
        derivedStateOf { revealedCount(flingOffset.value, endThresholds) }
    }
    val armedDirection by remember(itemKey, leftSwipeRevealWidthPx, rightSwipeRevealWidthPx, buttonWidthPx) {
        derivedStateOf {
            deepSwipeDirectionOf(
                offset = flingOffset.value,
                leftSwipeRevealWidthPx = leftSwipeRevealWidthPx,
                rightSwipeRevealWidthPx = rightSwipeRevealWidthPx,
                buttonWidthPx = buttonWidthPx,
            )
        }
    }

    val isOpen = listState.openKey == itemKey
    val hasActions = startActions.isNotEmpty() || endActions.isNotEmpty()

    fun actionsFor(direction: SwipeDirection): List<SwipeAction> = when (direction) {
        SwipeDirection.Left -> endActions
        SwipeDirection.Right -> startActions
    }

    fun revealWidthFor(direction: SwipeDirection): Float = when (direction) {
        SwipeDirection.Left -> leftSwipeRevealWidthPx
        SwipeDirection.Right -> rightSwipeRevealWidthPx
    }

    fun deepSwipeActionFor(direction: SwipeDirection): SwipeAction? =
        actionsFor(direction).firstOrNull { it.deepSwipe }

    suspend fun collapse(velocity: Float) {
        if (listState.openKey == itemKey) listState.close()
        swipeState = SwipeState.Idle
        revealedDirection = null
        shouldComposeActions = false
        isFlyingOut = false
        deleteFlingOffset.snapTo(0f)
        flingOffset.animateTo(
            targetValue = 0f,
            animationSpec = SwipeSettleSpec,
            // 从静止处回弹就别带初速了，否则会「自己弹一下」
            initialVelocity = if (flingOffset.value == 0f) 0f else velocity,
        )
    }

    suspend fun executeAction(action: SwipeAction, direction: SwipeDirection) {
        if (action.isDestructive) {
            // 连震交给震动出口调度 —— 这一行马上就要消失，
            // 让行自己循环的话后面的几下会被协程取消吃掉
            haptics.performDeleteBurst()

            isFlyingOut = true
            val distance = flyOutDistancePx(
                containerWidthPx = rowWidth.px,
                actionsWidthPx = revealWidthFor(direction),
                slackPx = flyOutSlackPx,
            )
            // 目标是「两个位移之和」等于飞出距离，所以这里要减掉已经偏出去的那段
            deleteFlingOffset.animateTo(
                targetValue = direction.sign * distance - flingOffset.value,
                animationSpec = AppMotion.flingOut(),
            )

            swipeState = SwipeState.Idle
            revealedDirection = null
            shouldComposeActions = false
            listState.close()
            action.onAction()
        } else {
            // 非破坏性的（完成 / 取消完成）：先执行，再弹回去
            action.onAction()
            haptics.perform(HapticCue.Commit)

            swipeState = SwipeState.Idle
            revealedDirection = null
            shouldComposeActions = false
            listState.close()
            deleteFlingOffset.snapTo(0f)
            isFlyingOut = false
            flingOffset.animateTo(targetValue = 0f, animationSpec = SwipeSettleSpec)
        }
    }

    // 别的行被打开（或者列表滚动把它关掉了）→ 自己合上
    LaunchedEffect(listState.openKey, itemKey) {
        if (!isOpen) collapse(velocity = 0f)
    }

    // 越过深划阈值时提示一声 —— 但只有「本次拖动是从已滑开状态起步的」才算数，
    // 否则一次划到底也会响，那道防误触的坎就白设了
    LaunchedEffect(armedDirection) {
        val direction = armedDirection ?: return@LaunchedEffect
        val armed = initialSwipeState == SwipeState.Revealed &&
            initialRevealedDirection == direction
        if (armed && deepSwipeActionFor(direction) != null) {
            haptics.perform(HapticCue.DeepSwipeArmed)
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .onGloballyPositioned { rowWidth.px = it.size.width.toFloat() }
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier),
    ) {
        // ---------- 底层：两侧的动作钮 ----------
        // matchParentSize 而不是 fillMaxHeight：本组件是 LazyColumn 的 item，
        // 高度约束无界，直接 fillMaxHeight 会塌成 0。
        Box(Modifier.matchParentSize()) {
            if (endActions.isNotEmpty()) {
                SwipeActionsRow(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight()
                        .width(with(density) { leftSwipeRevealWidthPx.toDp() })
                        .padding(end = SwipeActionsGap),
                    actions = endActions,
                    // 「先露出来的」是离卡片最近的那颗，也就是列表里最后那颗
                    revealIndex = { index -> endActions.size - index - 1 },
                    revealedCount = endRevealedCount,
                    isFlyingOut = isFlyingOut,
                    arrangement = Arrangement.End,
                    itemKey = itemKey,
                    onClick = { action -> scope.launch { executeAction(action, SwipeDirection.Left) } },
                )
            }
            if (startActions.isNotEmpty()) {
                SwipeActionsRow(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .fillMaxHeight()
                        .width(with(density) { rightSwipeRevealWidthPx.toDp() })
                        .padding(start = SwipeActionsGap),
                    actions = startActions,
                    revealIndex = { index -> index },
                    revealedCount = startRevealedCount,
                    isFlyingOut = isFlyingOut,
                    arrangement = Arrangement.Start,
                    itemKey = itemKey,
                    onClick = { action -> scope.launch { executeAction(action, SwipeDirection.Right) } },
                )
            }
        }

        // ---------- 上层：内容，按绘制期位移 ----------
        Box(
            Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    translationX = flingOffset.value + deleteFlingOffset.value
                }
                .draggable(
                    enabled = gesturesEnabled && !isFlyingOut && hasActions,
                    orientation = Orientation.Horizontal,
                    state = rememberDraggableState { delta ->
                        scope.launch {
                            val newOffset = rubberBandSwipeOffset(
                                currentOffset = flingOffset.value,
                                delta = delta,
                                leftSwipeRevealWidthPx = leftSwipeRevealWidthPx,
                                rightSwipeRevealWidthPx = rightSwipeRevealWidthPx,
                                viewportWidthPx = rowWidth.px,
                            )
                            flingOffset.snapTo(newOffset)

                            val direction = swipeDirectionOf(newOffset)
                            swipeState = if (
                                direction != null &&
                                abs(newOffset) > revealWidthFor(direction) / 2f
                            ) {
                                SwipeState.Revealed
                            } else {
                                SwipeState.Idle
                            }
                        }
                    },
                    onDragStarted = {
                        initialSwipeState = swipeState
                        initialRevealedDirection = revealedDirection
                        isFlyingOut = false
                        shouldComposeActions = true
                        // 打开自己 = 顺手把别的行关掉
                        listState.open(itemKey)
                    },
                    onDragStopped = { velocity ->
                        scope.launch {
                            val currentOffset = flingOffset.value
                            val direction = swipeDirectionOf(currentOffset)
                            val release = resolveSwipeRelease(
                                direction = direction,
                                currentOffsetPx = currentOffset,
                                velocityPxPerSec = velocity,
                                initialSwipeState = initialSwipeState,
                                initialRevealedDirection = initialRevealedDirection,
                                deepSwipeDirection = deepSwipeDirectionOf(
                                    offset = currentOffset,
                                    leftSwipeRevealWidthPx = leftSwipeRevealWidthPx,
                                    rightSwipeRevealWidthPx = rightSwipeRevealWidthPx,
                                    buttonWidthPx = buttonWidthPx,
                                ),
                                revealWidthPx = direction?.let(::revealWidthFor) ?: 0f,
                                deepSwipeEnabled = direction != null &&
                                    deepSwipeActionFor(direction) != null,
                                velocityThresholdPx = velocityThresholdPx,
                            )

                            val settled = release.direction
                            when {
                                release.outcome == SwipeOutcome.ExecuteDeepSwipe && settled != null -> {
                                    val action = deepSwipeActionFor(settled)
                                    if (action != null) executeAction(action, settled)
                                    else collapse(velocity)
                                }

                                release.outcome == SwipeOutcome.Reveal && settled != null -> {
                                    swipeState = SwipeState.Revealed
                                    revealedDirection = settled
                                    flingOffset.animateTo(
                                        targetValue = settled.sign * revealWidthFor(settled),
                                        animationSpec = SwipeSettleSpec,
                                        // 把甩动的速度接上去，吸附才「顺」而不是「拽回来」
                                        initialVelocity = velocity,
                                    )
                                }

                                else -> collapse(velocity)
                            }
                        }
                    },
                ),
        ) {
            content()

            // 滑开着的时候点哪儿都合上。只吃「点击」，横向拖动仍归上面那层 draggable。
            if (isOpen) {
                val shape = RoundedCornerShape(Dimens.shapeLg.dp)
                val interactionSource = remember { MutableInteractionSource() }
                Box(
                    Modifier
                        .matchParentSize()
                        .clip(shape)
                        .clickable(
                            interactionSource = interactionSource,
                            indication = dimIndication(shape),
                            onClick = { listState.close() },
                        )
                )
            }
        }
    }
}

/**
 * 一侧的动作钮。
 *
 * 每颗钮按「露出的进度」各自缩放淡入 —— 手指划过一颗钮的中线，那颗才弹出来。
 * 一开始就全亮会显得按钮「抢跑」，也让「我到底划开了几颗」失去反馈。
 */
@Composable
private fun SwipeActionsRow(
    modifier: Modifier,
    actions: List<SwipeAction>,
    /** 这一颗在「露出顺序」里排第几（0 = 最先露出来的那颗） */
    revealIndex: (Int) -> Int,
    revealedCount: Int,
    isFlyingOut: Boolean,
    arrangement: Arrangement.Horizontal,
    itemKey: Any,
    onClick: (SwipeAction) -> Unit,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = arrangement,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        actions.forEachIndexed { index, action ->
            val order = revealIndex(index)
            val isVisible = order < revealedCount && !isFlyingOut

            // 名字不能叫 scale / alpha —— 那会和 graphicsLayer 作用域里的同名属性
            // 打架（局部 val 反而先被解析到，赋值直接编译不过）
            val buttonScale = remember(itemKey, order) { Animatable(0.6f) }
            val buttonAlpha = remember(itemKey, order) { Animatable(0f) }

            LaunchedEffect(isVisible) {
                if (isVisible) {
                    launch { buttonAlpha.animateTo(1f, AppMotion.Springs.effects) }
                    launch { buttonScale.animateTo(1f, AppMotion.Springs.spatialBouncy) }
                } else {
                    launch { buttonAlpha.animateTo(0f, AppMotion.Springs.effectsFast) }
                    launch { buttonScale.animateTo(0.6f, SwipeButtonHideSpec) }
                }
            }

            Box(
                Modifier
                    .width(SwipeActionColumnWidth)
                    .fillMaxHeight(),
                contentAlignment = Alignment.Center,
            ) {
                val interactionSource = remember { MutableInteractionSource() }
                Box(
                    Modifier
                        .graphicsLayer {
                            scaleX = buttonScale.value
                            scaleY = buttonScale.value
                            alpha = buttonAlpha.value
                        }
                        .size(SwipeActionButtonSize)
                        .clip(CircleShape)
                        .background(action.container)
                        .clickable(
                            interactionSource = interactionSource,
                            indication = dimIndication(CircleShape),
                            enabled = !isFlyingOut,
                            onClick = { onClick(action) },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = action.icon,
                        contentDescription = action.label,
                        tint = action.content,
                        modifier = Modifier.size(SwipeActionIconSize),
                    )
                }
            }
        }
    }
}
