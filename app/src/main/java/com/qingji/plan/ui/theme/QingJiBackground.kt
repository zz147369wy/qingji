package com.qingji.plan.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 全局背景样式。
 *
 * 重设计前，背景是一层固定的圆点纹理（每 20×20 一个半径 1px 的点）——
 * 那是「手帐」质感的来源，但也是全 App 最明显的「网页味」。
 * 这里把候选方案做成可选，让用户挑一个。
 */
enum class BackgroundStyle {
    /**
     * 纯色。M3 的做法：背景只是一块素净的底色，层级完全靠 surface 容器色撑起来。
     * 最干净、最现代，性能也最好（不用逐帧画几千个圆点）。
     */
    Plain,

    /**
     * 弱化圆点。保留一丝纸张余韵，但把点的对比度压到很低，
     * 不再抢内容。介于「手帐」和「纯 M3」之间。
     */
    SubtleDots,

    /**
     * 渐变。顶部一片主色柔化地过渡到底色，M3 Expressive 里常见的大面积色域。
     * 比纯色有层次，同时彻底摆脱纹理感。
     */
    Gradient,
}

/**
 * 铺满整屏的背景。三种样式由 [BackgroundStyle] 决定。
 */
@Composable
fun QingJiBackground(
    modifier: Modifier = Modifier,
    style: BackgroundStyle = CurrentBackgroundStyle,
    content: @Composable () -> Unit,
) {
    val palette = LocalPalette.current

    val base = when (style) {
        BackgroundStyle.Plain, BackgroundStyle.SubtleDots -> Modifier.background(palette.bg)

        BackgroundStyle.Gradient -> Modifier.background(
            Brush.verticalGradient(
                0f to lerp(palette.bg, palette.primarySoft, 0.75f),
                0.42f to palette.bg,
                1f to palette.bg,
            )
        )
    }

    val textured = when (style) {
        BackgroundStyle.SubtleDots -> base.dottedBackground(
            // 点色向底色靠拢一大步 —— 这就是「弱化」的全部含义
            dotColor = lerp(palette.bg, palette.dot, 0.4f),
            gridSize = 22.dp,
            dotRadius = 1.dp,
        )
        else -> base
    }

    Box(modifier.fillMaxSize().then(textured)) { content() }
}

/**
 * 圆点纹理。
 *
 * 原站 CSS：
 *   background-image: radial-gradient(var(--dot) 1px, transparent 1.2px);
 *   background-size: 20px 20px;
 */
fun Modifier.dottedBackground(
    dotColor: Color,
    gridSize: Dp = 20.dp,
    dotRadius: Dp = 1.dp,
): Modifier = this.drawBehind {
    val step = gridSize.toPx()
    val radius = dotRadius.toPx()
    if (step <= 0f) return@drawBehind

    var y = 0f
    while (y <= size.height + step) {
        var x = 0f
        while (x <= size.width + step) {
            drawCircle(color = dotColor, radius = radius, center = Offset(x, y))
            x += step
        }
        y += step
    }
}

/**
 * 当前生效的背景样式。
 *
 * 2026-10 重设计评审时三种都真机跑过、截图对比过，用户选了 [BackgroundStyle.Gradient]。
 * 另外两种留在枚举里，是为了想改回去时不用重写 —— 各自只占几行。
 */
val CurrentBackgroundStyle = BackgroundStyle.Gradient
