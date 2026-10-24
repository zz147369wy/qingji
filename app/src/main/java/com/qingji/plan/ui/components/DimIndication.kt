package com.qingji.plan.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import com.qingji.plan.ui.theme.AppMotion
import com.qingji.plan.ui.theme.LocalPalette
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * 按下反馈：把**整个组件按它自己的形状**浅浅压暗一层。
 *
 * ## 为什么不用 Material 默认的水波纹
 *
 * `Modifier.clickable` 默认的 ripple 是「从触点扩散的圆」，但它最终**按组件矩形边界裁切**。
 * 在底部导航这种整格矩形的组件上，点下去就是一块突兀的灰方块 —— 用户实际报过这个问题。
 * 早先的处理是把 `indication` 设为 null，即**彻底没有反馈**；按钮按下去毫无回应，
 * 在真机上是能感觉出来的。
 *
 * 这个实现的思路来自第三方项目 Cresto 的 Glasense 设计系统（对方为 Apache-2.0，
 * 此处是按同一思路自行实现，未使用其代码）。做法是：
 *   - 不走「圆扩散」，而是**整块**一起淡入淡出，所以不存在裁切突兀的问题
 *   - 描边用组件**自己的 shape**，圆角胶囊就是圆角胶囊，不会被切方
 *   - 幅度很小（默认 10%），是「被按到了」的暗示，不是视觉噪声
 *
 * 透明度动画走 [AppMotion.Springs.effects] —— bounce 恒为 0。
 * 透明度**绝不能回弹**：过冲会越过 1 然后被截断，看起来就是闪一下。
 */
data class DimIndication(
    val color: Color,
    val shape: Shape,
    val maxAlpha: Float,
) : IndicationNodeFactory {

    override fun create(interactionSource: InteractionSource): DelegatableNode =
        DimIndicationNode(interactionSource, color, shape, maxAlpha)

    // IndicationNodeFactory 会被 Compose 拿去做相等性判断，数据类要自己实现
    override fun equals(other: Any?): Boolean =
        other is DimIndication &&
            other.color == color &&
            other.shape == shape &&
            other.maxAlpha == maxAlpha

    override fun hashCode(): Int {
        var result = color.hashCode()
        result = 31 * result + shape.hashCode()
        result = 31 * result + maxAlpha.hashCode()
        return result
    }
}

private class DimIndicationNode(
    private val interactionSource: InteractionSource,
    private val color: Color,
    private val shape: Shape,
    maxAlpha: Float,
) : Modifier.Node(), DrawModifierNode {

    private val alpha = Animatable(0f)
    private val targetAlpha = maxAlpha.coerceIn(0f, 1f)

    override fun onAttach() {
        super.onAttach()
        coroutineScope.launch {
            interactionSource.interactions.collectLatest { interaction ->
                when (interaction) {
                    is PressInteraction.Press ->
                        alpha.animateTo(targetAlpha, AppMotion.Springs.effectsFast)

                    is PressInteraction.Release ->
                        alpha.animateTo(0f, AppMotion.Springs.effects)

                    is PressInteraction.Cancel ->
                        alpha.animateTo(0f, AppMotion.Springs.effects)
                }
            }
        }
    }

    override fun ContentDrawScope.draw() {
        drawContent()
        val a = alpha.value
        if (a > 0f) {
            drawOutline(
                outline = shape.createOutline(size, layoutDirection, this),
                color = color.copy(alpha = a),
            )
        }
    }
}

/**
 * 取一个当前主题下的 [DimIndication]。
 *
 * 用法跟 ripple 一样：
 * ```
 * val is = remember { MutableInteractionSource() }
 * Modifier.clickable(interactionSource = is, indication = dimIndication(shape))
 * ```
 *
 * [shape] 必须传组件实际用的那个形状，否则描边会和组件对不上。
 */
@Composable
fun dimIndication(
    shape: Shape,
    color: Color = LocalPalette.current.text,
    maxAlpha: Float = 0.10f,
): DimIndication = remember(shape, color, maxAlpha) {
    DimIndication(color = color, shape = shape, maxAlpha = maxAlpha)
}
