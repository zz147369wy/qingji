package com.qingji.plan.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * 滑动判定与几何的测试。
 *
 * 这一层之所以能被测，是因为手感里唯一**难验**的部分（动效、帧率）被隔离在
 * `SwipeRevealRow` 的渲染里了，而「松手之后该干什么」是一张纯粹的真值表 ——
 * 跟画布无关，跟设备帧率无关。
 *
 * 本工程没有手势仿真测试（全库零 `performTouchInput`），所以**这里就是滑动逻辑的
 * 唯一防线**：真机上再滑得对不对是主观的，但「一次划到底不该执行删除」是客观的。
 */
class SwipeGestureMathTest {

    /** 一颗钮一列 60dp、缝 6dp —— 与 SwipeRevealRow 里的常量一致 */
    private val buttonWidth = 60f
    private val gap = 6f

    /** 单侧一颗钮时的露出总宽 = 6 + 60 + 6 */
    private val revealWidth = actionsWidthPx(1, buttonWidth, gap)

    /** 视口宽，只影响橡皮筋软硬 */
    private val viewport = 500f

    /** 甩动阈值 */
    private val velocityThreshold = 500f

    /** 一次拖动划到底：开始拖的时候行还是关的 */
    private fun dragFromClosed(
        offset: Float,
        velocity: Float,
        deepSwipe: SwipeDirection? = null,
        deepSwipeEnabled: Boolean = true,
    ) = resolveSwipeRelease(
        direction = swipeDirectionOf(offset),
        currentOffsetPx = offset,
        velocityPxPerSec = velocity,
        initialSwipeState = SwipeState.Idle,
        initialRevealedDirection = null,
        deepSwipeDirection = deepSwipe,
        revealWidthPx = revealWidth,
        deepSwipeEnabled = deepSwipeEnabled,
        velocityThresholdPx = velocityThreshold,
    )

    /** 从「已经滑开着」的状态再划一次 —— 深划直达只可能发生在这条路径上 */
    private fun dragFromRevealed(
        offset: Float,
        velocity: Float,
        direction: SwipeDirection,
        deepSwipe: SwipeDirection?,
        deepSwipeEnabled: Boolean = true,
    ) = resolveSwipeRelease(
        direction = swipeDirectionOf(offset),
        currentOffsetPx = offset,
        velocityPxPerSec = velocity,
        initialSwipeState = SwipeState.Revealed,
        initialRevealedDirection = direction,
        deepSwipeDirection = deepSwipe,
        revealWidthPx = revealWidth,
        deepSwipeEnabled = deepSwipeEnabled,
        velocityThresholdPx = velocityThreshold,
    )

    // ---------- 动作区几何 ----------

    @Test
    fun `没有动作时动作区宽度为零`() {
        assertEquals(0f, actionsWidthPx(0, buttonWidth, gap))
    }

    @Test
    fun `动作区宽度含两侧的缝`() {
        // 一颗钮：6(缝) + 60(钮) + 6(缝)
        assertEquals(72f, actionsWidthPx(1, buttonWidth, gap))
        // 两颗钮
        assertEquals(132f, actionsWidthPx(2, buttonWidth, gap))
    }

    // ---------- 橡皮筋 ----------

    @Test
    fun `未超过露出宽度时位移一比一跟手`() {
        // 跟手的前提：没过动作区之前不做任何阻尼，手指走多少内容走多少
        assertEquals(
            -50f,
            rubberBandSwipeOffset(0f, -50f, revealWidth, revealWidth, viewport),
        )
        assertEquals(
            30f,
            rubberBandSwipeOffset(0f, 30f, revealWidth, revealWidth, viewport),
        )
    }

    @Test
    fun `超过露出宽度后每等量增量的增量递减`() {
        val atEdge = -revealWidth
        val afterFirst = rubberBandSwipeOffset(atEdge, -60f, revealWidth, revealWidth, viewport)
        val afterSecond = rubberBandSwipeOffset(afterFirst, -60f, revealWidth, revealWidth, viewport)

        // 第一步正好落在边界上，还不减速
        assertEquals(60f, abs(afterFirst - atEdge), 0.01f)

        val firstStep = abs(afterFirst - atEdge)
        val secondStep = abs(afterSecond - afterFirst)
        assertTrue(
            "第二步应该被橡皮筋吃掉一部分，实际 $secondStep 不小于第一步 $firstStep",
            secondStep < firstStep,
        )
    }

    @Test
    fun `本侧没有动作时位移恒为零`() {
        // 不是「能滑但不展开」，是干脆滑不动
        assertEquals(0f, rubberBandSwipeOffset(0f, -80f, 0f, revealWidth, viewport))
        assertEquals(0f, rubberBandSwipeOffset(0f, 80f, revealWidth, 0f, viewport))
        // 已经偏了一段再往没动作的那侧划，同样锁回零
        assertEquals(0f, rubberBandSwipeOffset(-50f, -10f, 0f, revealWidth, viewport))
    }

    @Test
    fun `位移为零时方向为空`() {
        assertNull(swipeDirectionOf(0f))
        assertEquals(0f, rubberBandSwipeOffset(0f, 0f, revealWidth, revealWidth, viewport))
    }

    // ---------- 展开阈值与计数 ----------

    @Test
    fun `展开阈值按按钮中线从内向外排列`() {
        // 6(缝) + 60*i + 30(半颗钮)
        assertEquals(
            listOf(36f, 96f, 156f),
            revealThresholdsPx(3, buttonWidth, gap),
        )
    }

    @Test
    fun `位移越过几条中线就露出几颗`() {
        val thresholds = revealThresholdsPx(3, buttonWidth, gap)
        assertEquals(0, revealedCount(-35f, thresholds))
        assertEquals(1, revealedCount(-36f, thresholds))
        assertEquals(2, revealedCount(-96f, thresholds))
        assertEquals(3, revealedCount(-156f, thresholds))
        // 划过头也还是三颗，不会数出第四条
        assertEquals(3, revealedCount(-400f, thresholds))
    }

    @Test
    fun `位移为零时一颗都不露`() {
        assertEquals(0, revealedCount(0f, revealThresholdsPx(2, buttonWidth, gap)))
    }

    // ---------- 深划区间 ----------

    @Test
    fun `深划阈值在露出宽度之外一个按钮宽`() {
        assertEquals(
            SwipeDirection.Left,
            deepSwipeDirectionOf(-(revealWidth + buttonWidth) - 0.1f, revealWidth, revealWidth, buttonWidth),
        )
        assertEquals(
            SwipeDirection.Right,
            deepSwipeDirectionOf((revealWidth + buttonWidth) + 0.1f, revealWidth, revealWidth, buttonWidth),
        )
    }

    @Test
    fun `未越过深划阈值时方向为空`() {
        assertNull(deepSwipeDirectionOf(-100f, revealWidth, revealWidth, buttonWidth))
        assertNull(deepSwipeDirectionOf(0f, revealWidth, revealWidth, buttonWidth))
        // 边界是开区间：正好等于阈值还不算
        assertNull(
            deepSwipeDirectionOf(-(revealWidth + buttonWidth), revealWidth, revealWidth, buttonWidth),
        )
    }

    // ---------- 松手三态（核心） ----------

    @Test
    fun `一次划到底再松手只展开不执行`() {
        // ★ 这是「必须先滑开、松手、再划第二次」这道防误触的坎的回归锁。
        // 从关闭状态一次划到底，划得再深、甩得再快，也只能到展开。
        val release = dragFromClosed(
            offset = -200f,
            velocity = -3000f, // 甩得极快
            deepSwipe = SwipeDirection.Left, // 位移远超深划阈值
        )
        assertEquals(SwipeOutcome.Reveal, release.outcome)
        assertEquals(SwipeDirection.Left, release.direction)
    }

    @Test
    fun `未滑开时快甩只展开不执行`() {
        // 同上，换成「位移很小但甩得很快」的形态 —— 走的是 isFastSwipe 那条分支
        val release = dragFromClosed(
            offset = -10f,
            velocity = -2000f,
            deepSwipe = SwipeDirection.Left,
        )
        assertEquals(SwipeOutcome.Reveal, release.outcome)
    }

    @Test
    fun `已滑开后再同向慢速深划松手直接执行`() {
        // 第二次划：起点已经是展开的，且位移越过了深划阈值 —— 这就是直达
        val release = dragFromRevealed(
            offset = -160f,
            velocity = -100f, // 慢速，靠的是划得够深
            direction = SwipeDirection.Left,
            deepSwipe = SwipeDirection.Left,
        )
        assertEquals(SwipeOutcome.ExecuteDeepSwipe, release.outcome)
        assertEquals(SwipeDirection.Left, release.direction)
    }

    @Test
    fun `已滑开后再同向快甩松手直接执行`() {
        // 位移没过深划阈值，但甩得够快 —— 靠 isFastSwipe 那条分支直达
        val release = dragFromRevealed(
            offset = -80f,
            velocity = -1500f,
            direction = SwipeDirection.Left,
            deepSwipe = null,
        )
        assertEquals(SwipeOutcome.ExecuteDeepSwipe, release.outcome)
    }

    @Test
    fun `已滑开但反向甩动会合上`() {
        // 手指快速往回带 —— 意图明确是「算了」，合上
        val release = dragFromRevealed(
            offset = -80f,
            velocity = 1200f,
            direction = SwipeDirection.Left,
            deepSwipe = null,
        )
        assertEquals(SwipeOutcome.Collapse, release.outcome)
        assertNull(release.direction)
    }

    @Test
    fun `已滑开且深划过头但手指回带时保持展开`() {
        // 划过了深划阈值，但手指在往回带且很慢 —— 不该执行，也不该合上，
        // 保持展开让用户松手后自己决定。这条容易漏。
        val release = dragFromRevealed(
            offset = -160f,
            velocity = 80f,
            direction = SwipeDirection.Left,
            deepSwipe = SwipeDirection.Left,
        )
        assertEquals(SwipeOutcome.Reveal, release.outcome)
    }

    @Test
    fun `位移过半且慢速松手吸附展开`() {
        val release = dragFromClosed(offset = -40f, velocity = -100f)
        assertEquals(SwipeOutcome.Reveal, release.outcome)
    }

    @Test
    fun `位移不过半且慢速松手弹回原点`() {
        val release = dragFromClosed(offset = -20f, velocity = -100f)
        assertEquals(SwipeOutcome.Collapse, release.outcome)
    }

    @Test
    fun `位移为零时松手判为合上`() {
        val release = dragFromClosed(offset = 0f, velocity = 0f)
        assertEquals(SwipeOutcome.Collapse, release.outcome)
        assertNull(release.direction)
    }

    @Test
    fun `未配深划动作的一侧再深划也只展开`() {
        // endActions.deepSwipe = false 时，无论划多深都只到展开
        val release = dragFromRevealed(
            offset = -200f,
            velocity = -3000f,
            direction = SwipeDirection.Left,
            deepSwipe = SwipeDirection.Left,
            deepSwipeEnabled = false,
        )
        assertEquals(SwipeOutcome.Reveal, release.outcome)
    }

    @Test
    fun `两侧的判定互不串味`() {
        // 在右侧（完成）已展开的状态下往左划，初始展开方向对不上 →
        // 不构成深划，只展开 —— 防止「划开删除却触发完成」
        val release = dragFromRevealed(
            offset = -160f,
            velocity = -100f,
            direction = SwipeDirection.Right,
            deepSwipe = SwipeDirection.Left,
        )
        assertEquals(SwipeOutcome.Reveal, release.outcome)
    }

    // ---------- 飞出 ----------

    @Test
    fun `飞出距离覆盖容器宽加动作区宽`() {
        assertEquals(1160f, flyOutDistancePx(1080f, 72f, 8f))
    }
}
