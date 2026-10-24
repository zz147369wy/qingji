package com.qingji.plan.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.qingji.plan.ui.theme.AppMotion
import com.qingji.plan.ui.theme.Dimens
import com.qingji.plan.ui.theme.LocalPalette

/**
 * 贴纸：「空空的海」整套视觉的基本单元。
 *
 * 墨线描边 + 正下方一块**实色**错位投影（不是模糊阴影），和鲸鱼的卡通画风是一路的。
 * 可按压的贴纸按下去时**整张往下沉**、投影同步收起 —— 像真的按下一张贴纸。
 *
 * ## 帧率
 *
 * 按压进度 [Animatable] 只在两个地方被读：`graphicsLayer {}` 和 `drawWithCache` 的绘制 lambda。
 * 都是**绘制期**，所以按压动画只失效绘制、不失效组合和布局（家规：位移不用 Modifier.offset）。
 *
 * ## 投影不占布局
 *
 * 投影画在组件边界**外面**（往下 [drop]），不占尺寸。相邻元素之间留够间距即可；
 * 列表里 6-9dp 的行距足够。
 *
 * @param fill 底色。传 [Color.Transparent] 就是「只有描边和投影」
 * @param interactionSource 传了就是可按压贴纸（按下下沉）；不传就是静态贴纸
 * @param drop 投影下移距离，也是按下的下沉距离。0 = 只描边不投影
 * @param stroke 描边色。传 [Color.Transparent] 就是无描边
 */
@Composable
fun Modifier.sticker(
    shape: Shape,
    fill: Color = LocalPalette.current.surface,
    interactionSource: InteractionSource? = null,
    drop: Dp = Dimens.stickerDrop.dp,
    stroke: Color = LocalPalette.current.ink,
    shadow: Color = LocalPalette.current.ink,
    strokeWidth: Dp = Dimens.strokeWidth.dp,
    clipContent: Boolean = true,
): Modifier {
    val density = LocalDensity.current
    val dropPx = with(density) { drop.toPx() }
    val strokePx = with(density) { strokeWidth.toPx() }
    val press = remember { Animatable(0f) }

    if (interactionSource != null) {
        LaunchedEffect(interactionSource) {
            interactionSource.interactions.collect { interaction ->
                when (interaction) {
                    // 按下要快（手指一碰就沉），松开稍慢、带一点回弹
                    is PressInteraction.Press -> press.animateTo(1f, AppMotion.Springs.effectsFast)
                    is PressInteraction.Release,
                    is PressInteraction.Cancel -> press.animateTo(0f, AppMotion.Springs.spatialFast)
                }
            }
        }
    }

    return this
        .then(
            if (interactionSource != null && dropPx > 0f) {
                Modifier.graphicsLayer { translationY = dropPx * press.value }
            } else {
                Modifier
            }
        )
        .drawWithCache {
            val outline = shape.createOutline(size, layoutDirection, this)
            val strokeStyle = Stroke(width = strokePx)
            onDrawWithContent {
                // 投影：组件下沉多少，投影就收回多少，两者加起来恒等于 drop
                val off = dropPx * (1f - press.value)
                if (off > 0.5f && shadow.alpha > 0f) {
                    translate(top = off) { drawOutline(outline, shadow) }
                }
                if (fill.alpha > 0f) drawOutline(outline, fill)
                drawContent()
                if (strokePx > 0f && stroke.alpha > 0f) {
                    drawOutline(outline, stroke, style = strokeStyle)
                }
            }
        }
        .then(if (clipContent) Modifier.clip(shape) else Modifier)
}

/**
 * 可点击的贴纸：[sticker] + 点击，按下整张下沉。
 *
 * 不再叠加 [dimIndication] —— 下沉本身就是按压反馈，再压暗一层反而脏。
 */
@Composable
fun Modifier.stickerClickable(
    shape: Shape,
    onClick: () -> Unit,
    fill: Color = LocalPalette.current.surface,
    drop: Dp = Dimens.stickerDrop.dp,
    stroke: Color = LocalPalette.current.ink,
    enabled: Boolean = true,
    onLongClick: (() -> Unit)? = null,
): Modifier {
    val source = remember { MutableInteractionSource() }
    return this
        .sticker(shape = shape, fill = fill, interactionSource = source, drop = drop, stroke = stroke)
        .then(
            if (onLongClick != null) {
                Modifier.combinedClickable(
                    interactionSource = source,
                    indication = null,
                    enabled = enabled,
                    onClick = onClick,
                    onLongClick = onLongClick,
                )
            } else {
                Modifier.clickable(
                    interactionSource = source,
                    indication = null,
                    enabled = enabled,
                    onClick = onClick,
                )
            }
        )
}
