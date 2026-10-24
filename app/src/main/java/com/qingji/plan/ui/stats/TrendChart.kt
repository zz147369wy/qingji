package com.qingji.plan.ui.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.qingji.plan.ui.theme.AppText
import com.qingji.plan.ui.theme.LocalPalette
import kotlin.math.min

/**
 * 泡泡直径占一列宽度的比例。
 *
 * 【2026-10-05】用户嫌泡泡太小（原来固定 13dp，只占列宽一半多一点）。
 * 现在按列宽算、占 82%：392dp 宽的手机上卡片内宽约 320dp，一列约 23dp，泡泡约 19dp。
 */
private const val BubbleOfSlot = 0.82f

/** 泡泡的上限（dp）。平板 / 横屏时列很宽，泡泡别跟着变成乒乓球 */
private const val BubbleMax = 24f

/** 上下两颗泡泡**叠进去**多少（占直径的比例）。设计稿 `margin-top:-2px`：一颗压一点，像真的堆起来 */
private const val BubbleOverlap = 0.10f

/**
 * 图高跟着**最近最多的一天**走（用户 2026-10-05 定的）。
 *
 * 他一天最多 5 件左右，固定图高一定会空着一大半。现在的规则：
 * 层数 = 最多那天的件数 + 1（多留一颗余量），再夹在下面的上下限之间。
 */
private const val ChartMinLevels = 3

/** 图高上限（dp）。再高就常年空着；超过这个高度装得下的层数时，改让一颗代表多件 */
private val ChartMaxHeight = 124.dp

/** 没有数据时的图高（三层泡泡左右），免得卡片塌成一条线 */
private val ChartMinHeight = 52.dp

/** 顶上留的一线（半个描边）。最上面那颗的墨线是居中描的，不然会被画布上沿切掉 */
private val ChartTopMargin = 0.75.dp

/**
 * 一列能堆几颗、堆不下时一颗代表几件、图要多高。
 *
 * **纯函数**：图高、绘制和「一颗代表多件」的说明都从这一份结果里取。
 * 早先两边各算一次（图按列宽算、说明写死一个常数），结果窄机上没合并却显示说明、
 * 宽机上合并了却不显示说明 —— 说明和画面对不上，用户看到的就是一句假话。
 */
internal data class BubbleLayout(
    val bubble: Dp,
    val height: Dp,
    /** 图里画得下几层。合并倍数就是照它算的，测试直接断言「画出来的颗数不超过它」 */
    val levels: Int,
    val perBubble: Int,
)

internal fun bubbleLayout(slot: Dp, maxCount: Int): BubbleLayout {
    if (slot <= 0.dp || maxCount <= 0) return BubbleLayout(0.dp, ChartMinHeight, ChartMinLevels, 1)
    val bubble = min(slot.value * BubbleOfSlot, BubbleMax).dp
    val step = bubble * (1f - BubbleOverlap)

    // 上限高度里装得下几层（列越宽、泡泡越大，装得越少）
    val levelsByHeight = (((ChartMaxHeight - ChartTopMargin - bubble) / step).toInt() + 1)
        .coerceAtLeast(ChartMinLevels)
    // 想画几层：最多的一天 + 一颗余量，至少三层、至多上限
    val levels = (maxCount + 1).coerceAtLeast(ChartMinLevels).coerceAtMost(levelsByHeight)

    val height = bubble + step * (levels - 1).toFloat()
    val perBubble = if (maxCount <= levels) 1 else (maxCount - 1) / levels + 1
    return BubbleLayout(bubble, height, levels, perBubble)
}

/**
 * 近 14 天完成趋势 —— **泡泡堆**。
 *
 * 【2026-10 鲸鱼主题】原来是柱状图，试过「海草」，PM 选了泡泡堆：
 * 每完成一件就是一颗泡泡，一层层堆上去，数得清；今天的泡泡是金色；
 * 一件都没做的日子是一颗虚线空泡（不占高度，但不断档）。
 *
 * 【2026-10-05】图高不再写死，跟着最近最多的一天走 —— 平时一天四五件就画四五颗的高度，
 * 卡片不空。碰上一天几十件也不会长到天上去：超过上限就改让一颗代表多件，并在下面说明。
 *
 * 自己用 Canvas 画，不引第三方图表库。
 */
@Composable
fun TrendChart(state: StatsUiState, modifier: Modifier = Modifier) {
    val palette = LocalPalette.current
    val bars = state.trend
    if (bars.isEmpty()) return

    // BoxWithConstraints 只为拿到这一行的实际宽度：泡泡大小取决于它
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val plan = bubbleLayout(
            slot = maxWidth / bars.size,
            maxCount = bars.maxOfOrNull { it.doneCount } ?: 0,
        )

        Column(Modifier.fillMaxWidth()) {
            Canvas(
                Modifier
                    .fillMaxWidth()
                    .height(plan.height),
            ) {
                if (plan.bubble <= 0.dp) return@Canvas

                val bubble = plan.bubble.toPx()
                val step = bubble * (1f - BubbleOverlap)
                val slotPx = size.width / bars.size
                val strokePx = 1.5.dp.toPx()

                bars.forEachIndexed { index, bar ->
                    val cx = slotPx * index + slotPx / 2f
                    val count = if (bar.doneCount == 0) 0 else (bar.doneCount - 1) / plan.perBubble + 1

                    if (count == 0) {
                        // 一件都没做：一颗小一号的虚线空泡（设计稿 `.bubbles i.none`）
                        val r = bubble * 0.32f
                        drawCircle(
                            color = palette.text3,
                            radius = r,
                            center = Offset(cx, size.height - r),
                            style = Stroke(
                                width = 1.5.dp.toPx(),
                                pathEffect = PathEffect.dashPathEffect(
                                    floatArrayOf(2.5.dp.toPx(), 2.dp.toPx()),
                                ),
                            ),
                        )
                        return@forEachIndexed
                    }

                    repeat(count) { level ->
                        val cy = size.height - bubble / 2f - level * step
                        val fill = if (bar.isToday) palette.gold else palette.primary
                        drawCircle(fill, bubble / 2f, Offset(cx, cy))
                        // 高光 + 描边：泡泡才有「球」的感觉，也跟贴纸风格对得上
                        drawCircle(
                            color = Color.White.copy(alpha = 0.8f),
                            radius = bubble * 0.12f,
                            center = Offset(cx - bubble * 0.16f, cy - bubble * 0.18f),
                        )
                        // 墨线描边（设计稿 `.bubbles i` 是 1.5px 墨线）
                        drawCircle(
                            color = palette.ink,
                            radius = bubble / 2f,
                            center = Offset(cx, cy),
                            style = Stroke(width = strokePx),
                        )
                    }
                }
            }

            Spacer(Modifier.height(6.dp))

            // 日期轴
            Row(Modifier.fillMaxWidth()) {
                bars.forEach { bar ->
                    Text(
                        text = bar.dayOfMonth.toString(),
                        style = AppText.numTiny,
                        color = if (bar.isToday) palette.goldDeep else palette.text3,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                    )
                }
            }

            // 真的合并了才说明「一颗代表几件」，件数就是图上实际用的倍数
            if (plan.perBubble > 1) {
                Text(
                    text = "一天做得多的日子，一颗泡泡代表 ${plan.perBubble} 件，看高度即可",
                    style = AppText.label,
                    color = palette.text3,
                    modifier = Modifier.padding(top = 4.dp).testTag("trend_merged_hint"),
                )
            }
        }
    }
}
