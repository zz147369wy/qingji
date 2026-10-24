package com.qingji.plan.ui.components

import kotlin.math.abs

/**
 * 滑动行的**判定与几何** —— 全是纯函数。
 *
 * ## 为什么要单独一个文件
 *
 * 手感这东西本来是最难验的：模拟器只有 4fps、截图是缩略图，滑得对不对没法在 CI 里看。
 * 但这套交互真正的难点**不在渲染，在判定** —— 「松手之后算展开还是算执行」
 * 是一张真值表，跟画布没关系。
 *
 * 所以把它整张抽出来：`SwipeRevealRow` 只负责把手指事件喂进来、把结果画出去，
 * 所有「该不该执行」的决定都在这里，可以在纯 JVM 测试里逐行钉死。
 *
 * 这个文件**不 import 任何 `android.*` / `androidx.compose.*`**，
 * 和 `ui/haptics/HapticCue.kt` 同一个路数 —— 测试跑的是逻辑，不是框架。
 *
 * ## 出处
 *
 * 公式与判定逐行对应第三方参考实现 Cresto（Apache-2.0）的 `GlasenseSwipeable`，
 * 用户明确要求手感与它一致。命名按本工程的读法重写过（Cresto 里
 * `leftActionsWidth` 其实存的是右侧动作的宽度，照搬会越读越乱）。
 */

/**
 * 内容滑动的方向。
 *
 * `sign` 把「位移」与「速度」统一到同一个坐标系里：
 * 正 = 内容向右，负 = 内容向左。有了它，判定里就不用到处写 `if` 分支翻符号。
 */
enum class SwipeDirection(val sign: Float) {
    /** 内容向左滑 —— 露出行**右侧**的动作（今日页是「删除」） */
    Left(-1f),

    /** 内容向右滑 —— 露出行**左侧**的动作（今日页是「完成 / 取消完成」） */
    Right(1f),
}

/** 拖动中、以及松手那一瞬间，行处于哪一态。 */
enum class SwipeState {
    /** 没滑开（位移没过动作区一半） */
    Idle,

    /** 已经算「滑开着」 */
    Revealed,
}

/** 松手之后该发生什么。 */
enum class SwipeOutcome {
    /** 深划第二击达标 —— **直接执行**那个动作，不必再点一下按钮 */
    ExecuteDeepSwipe,

    /** 吸附展开到整条动作位，等用户点按钮 */
    Reveal,

    /** 弹回原位 */
    Collapse,
}

/** [resolveSwipeRelease] 的结果。[SwipeOutcome.Collapse] 时 [direction] 为 null。 */
data class SwipeRelease(
    val outcome: SwipeOutcome,
    val direction: SwipeDirection?,
)

/**
 * 橡皮筋系数。越大越「硬」—— 划出动作区之后，同样的手指位移换到的超额位移更少。
 * 取 0.55 与参考实现一致。
 */
const val RUBBER_BAND_CONSTANT = 0.55f

/** 位移是零就没有方向。省得调用方到处写 `!= 0f`。 */
fun swipeDirectionOf(offset: Float): SwipeDirection? = when {
    offset < 0f -> SwipeDirection.Left
    offset > 0f -> SwipeDirection.Right
    else -> null
}

/**
 * 一组动作占据的总宽度。
 *
 * 除了 N 颗钮本身，两侧各留 [gapPx] 的缝（钮与卡片之间、钮与屏幕边之间）。
 * 所以两侧都有动作时，两侧算出来的宽度是一样的公式，不用分开写。
 */
fun actionsWidthPx(actionCount: Int, buttonWidthPx: Float, gapPx: Float): Float =
    if (actionCount == 0) 0f else buttonWidthPx * actionCount + gapPx * 2

/**
 * 拖动增量 → 新位移。
 *
 * 没过动作区之前**一比一跟手**（手指走多少，内容走多少）——
 * 这是「跟手」的来源，任何提前阻尼都会让人觉得粘。
 *
 * 划出动作区之后转成橡皮筋：`resistance = dimension / (dimension + k * 越界量)`，
 * 位移越大阻力越大，永远到不了无限远。参照 iOS 邮件那套手感。
 *
 * **本侧没有动作时恒返回 0** —— 不是「能滑但不展开」，是干脆滑不动。
 *
 * @param leftSwipeRevealWidthPx 往左滑能露出的总宽（= 右侧动作组的总宽）
 * @param rightSwipeRevealWidthPx 往右滑能露出的总宽（= 左侧动作组的总宽）
 * @param viewportWidthPx 视口宽度，用来定橡皮筋的「软硬基准」
 */
fun rubberBandSwipeOffset(
    currentOffset: Float,
    delta: Float,
    leftSwipeRevealWidthPx: Float,
    rightSwipeRevealWidthPx: Float,
    viewportWidthPx: Float,
): Float {
    val leftReveal = leftSwipeRevealWidthPx.coerceAtLeast(0f)
    val rightReveal = rightSwipeRevealWidthPx.coerceAtLeast(0f)

    val nextOffset = currentOffset + delta
    val direction = swipeDirectionOf(nextOffset) ?: return 0f
    val revealWidth = when (direction) {
        SwipeDirection.Left -> leftReveal
        SwipeDirection.Right -> rightReveal
    }

    // 这一侧压根没配动作 —— 锁死，别让用户划出一片空白
    if (revealWidth == 0f) return 0f

    val directedOffset = nextOffset * direction.sign
    if (directedOffset <= revealWidth || viewportWidthPx <= 0f) return nextOffset

    val directedCurrentOffset = currentOffset * direction.sign
    val overscroll = directedOffset - revealWidth
    val previousOverscroll = (directedCurrentOffset - revealWidth).coerceAtLeast(0f)

    val dimension = viewportWidthPx.coerceAtLeast(revealWidth)
    val resistance = dimension / (dimension + RUBBER_BAND_CONSTANT * previousOverscroll)
    val resistedOverscroll = previousOverscroll + (overscroll - previousOverscroll) * resistance

    return direction.sign * (revealWidth + resistedOverscroll.coerceAtLeast(0f))
}

/**
 * 第 i 颗钮露出来所需要的位移（i 从 0 数起，0 = 最靠近卡片的那颗）。
 *
 * 取的是钮的**中线**：手指划过钮的一半，那颗钮才弹出来。
 * 早一点会显得按钮「抢跑」，晚一点会觉得卡住不动。
 *
 * 返回的列表长度就是动作条数，[revealedCount] 只数其中被越过的几条，不关心顺序。
 */
fun revealThresholdsPx(
    actionCount: Int,
    buttonWidthPx: Float,
    gapPx: Float,
): List<Float> = List(actionCount) { index ->
    gapPx + buttonWidthPx * index + buttonWidthPx / 2f
}

/** 位移越过了几条中线 —— 也就是该露出几颗钮。 */
fun revealedCount(offset: Float, thresholdsPx: List<Float>): Int {
    val travelled = abs(offset)
    return thresholdsPx.count { travelled >= it }
}

/**
 * 位移是否已经进入「深划」区间。
 *
 * 门槛是**动作区总宽之外，再一颗钮宽** —— 不是「滑得够远」，
 * 而是「明显滑过头了」。这个区间只用来提示（震一下）和判定，
 * 真正能不能执行还要看 [resolveSwipeRelease] 里的「必须先展开」这条。
 */
fun deepSwipeDirectionOf(
    offset: Float,
    leftSwipeRevealWidthPx: Float,
    rightSwipeRevealWidthPx: Float,
    buttonWidthPx: Float,
): SwipeDirection? = when {
    offset < -(leftSwipeRevealWidthPx + buttonWidthPx) -> SwipeDirection.Left
    offset > rightSwipeRevealWidthPx + buttonWidthPx -> SwipeDirection.Right
    else -> null
}

/**
 * 松手之后干什么 —— 整个交互最核心的一张真值表。
 *
 * ```
 * directedVelocity = velocity * direction.sign        // 朝展开方向的速度为正
 * isFastSwipe      = directedVelocity > 速度阈值        // 甩得够快
 * startedRevealed  = 拖动开始时就已经展开 且 方向一致
 * canDeepSwipe     = 这一侧配了可深划的动作 && startedRevealed
 * isDeepSwipe      = 位移已越过深划阈值
 * isMovingToward   = directedVelocity >= 0             // 手指在朝展开方向带
 * ```
 *
 * ## `startedRevealed` 用的是**拖动开始时**的初态
 *
 * 这是「必须先滑开、松手，再划第二次才能直达」的全部实现，
 * 也是本工程刻意要的那道**防误触的坎**：从关闭状态一次划到底，
 * 不管划多深、甩多快，`canDeepSwipe` 恒为 false，最多只到展开。
 *
 * 注意 `velocity` 必须是**真实速度**（px/s，来自拖动结束时的速度追踪），
 * 不能是「总位移乘个系数」凑出来的假值 —— 手感差就差在这里。
 *
 * @param direction 由 [swipeDirectionOf] 算出；null 表示没位移，直接判合上
 * @param currentOffsetPx 松手那一刻的位移
 * @param initialSwipeState 本次拖动**开始**时的状态
 * @param initialRevealedDirection 本次拖动**开始**时展开的是哪一侧
 * @param deepSwipeDirection 由 [deepSwipeDirectionOf] 算出
 * @param revealWidthPx 与 [direction] 对应的那一侧的动作区总宽
 * @param deepSwipeEnabled 那一侧是否配了可深划动作（`SwipeAction.deepSwipe`）
 * @param velocityThresholdPx 甩动判定阈值
 */
fun resolveSwipeRelease(
    direction: SwipeDirection?,
    currentOffsetPx: Float,
    velocityPxPerSec: Float,
    initialSwipeState: SwipeState,
    initialRevealedDirection: SwipeDirection?,
    deepSwipeDirection: SwipeDirection?,
    revealWidthPx: Float,
    deepSwipeEnabled: Boolean,
    velocityThresholdPx: Float,
): SwipeRelease {
    if (direction == null) return SwipeRelease(SwipeOutcome.Collapse, null)

    val directedVelocity = velocityPxPerSec * direction.sign
    val isFastSwipe = directedVelocity > velocityThresholdPx
    val startedRevealed =
        initialSwipeState == SwipeState.Revealed && initialRevealedDirection == direction
    val canDeepSwipe = deepSwipeEnabled && startedRevealed
    val isDeepSwipe = deepSwipeDirection == direction
    val isMovingTowardReveal = directedVelocity >= 0f

    val executeDeepSwipe = canDeepSwipe && isMovingTowardReveal && (isDeepSwipe || isFastSwipe)

    val reveal = (canDeepSwipe && isDeepSwipe && !isMovingTowardReveal) ||
        ((abs(currentOffsetPx) > revealWidthPx / 2f || isFastSwipe) && isMovingTowardReveal)

    return when {
        executeDeepSwipe -> SwipeRelease(SwipeOutcome.ExecuteDeepSwipe, direction)
        reveal -> SwipeRelease(SwipeOutcome.Reveal, direction)
        else -> SwipeRelease(SwipeOutcome.Collapse, null)
    }
}

/**
 * 「整行飞出屏幕」要飞多远。
 *
 * 光是视口宽还不够 —— 滑开之后行本身已经偏了一段，所以还要加上动作区宽，
 * 再加一点余量，保证连圆角都看不见。
 */
fun flyOutDistancePx(
    containerWidthPx: Float,
    actionsWidthPx: Float,
    slackPx: Float,
): Float = containerWidthPx + actionsWidthPx + slackPx
