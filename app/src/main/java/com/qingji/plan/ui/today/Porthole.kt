package com.qingji.plan.ui.today

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.scale
import com.qingji.plan.ui.theme.AppText
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qingji.plan.ui.components.sticker
import com.qingji.plan.ui.theme.LocalPalette
import kotlin.math.PI
import kotlin.math.sin

/**
 * 舷窗水位：船上一个圆窗子，里面的水位就是今天的完成率。
 *
 * 【2026-10 鲸鱼主题】取代原来的圆环进度条。金色窗框 + 墨线描边（贴纸），
 * 水面在轻轻晃，中间写百分比。
 *
 * ## 帧率
 *
 * 水面的波动是**逐帧**动画，波纹相位只存在一个 `mutableFloatStateOf` 里，
 * 而它**只被 Canvas 的绘制 lambda 读到** —— 每帧只失效绘制、不失效组合，也不触发重排。
 *
 * 水位本身（完成率变化）走 900ms 缓动，不逐帧。
 */
@Composable
fun Porthole(
    percent: Int,
    modifier: Modifier = Modifier,
    size: Dp = 100.dp,
) {
    val palette = LocalPalette.current
    val measurer = rememberTextMeasurer()

    // 水位。percent 变化时缓动过去
    val level = remember { Animatable((percent / 100f).coerceIn(0f, 1f)) }
    LaunchedEffect(percent) {
        level.animateTo(
            targetValue = (percent / 100f).coerceIn(0f, 1f),
            animationSpec = tween(durationMillis = 900, easing = FastOutSlowInEasing),
        )
    }

    // 波纹相位：一条无限循环的线性动画，只被绘制块读。
    //
    // 用 rememberInfiniteTransition 而不是自己写 `while (true) { withFrameNanos {} }`：
    // 后者会让 Compose 永远处于「还有活要干」的状态，Robolectric 里的 compose 测试
    // 会直接 `AppNotIdleException: Compose did not get idle`。
    // InfiniteTransition 走的是 withInfiniteAnimationFrameNanos，测试框架会把它收掉。
    val phaseAnim = rememberInfiniteTransition(label = "porthole_wave")
    val phase by phaseAnim.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "porthole_phase",
    )

    // 数字走 Fredoka（设计稿 `.porthole .pct b`：700 30px）
    val pctStyle = remember { AppText.numLarge.copy(fontSize = 30.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold) }
    val unitStyle = remember { TextStyle(fontSize = 11.sp, fontWeight = FontWeight.SemiBold) }

    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(
            Modifier
                .size(size)
                .sticker(shape = CircleShape, fill = palette.portholeGlass, drop = 3.dp),
        ) {
            val r = this.size.minDimension / 2f
            val ink = palette.ink
            val strokePx = 1.5.dp.toPx()
            val levelValue = level.value
            val phaseValue = phase

            // ---- 水面（裁在圆里）----
            clipPath(Path().apply { addOval(Rect(center = center, radius = r)) }) {
                val waterTop = this@Canvas.size.height * (1f - levelValue.coerceAtLeast(0.06f))
                translate(top = waterTop) {
                    // 后一层浪：慢、淡
                    drawWave(amplitude = 3.5.dp.toPx(), phase = -phaseValue * 1.1f, color = palette.primary.copy(alpha = 0.45f))
                    // 前一层浪：快、实
                    drawWave(amplitude = 2.6.dp.toPx(), phase = phaseValue * 1.9f, color = palette.primary)
                    // 波谷以下是实心水
                    drawRect(
                        color = palette.primary,
                        topLeft = Offset(0f, 2.dp.toPx()),
                        size = Size(this@Canvas.size.width, this@Canvas.size.height),
                    )
                }
            }

            // ---- 窗框：外圈墨线（sticker 画）→ 6dp 金框 → 1.5dp 墨线（设计稿两道 inset 阴影）----
            val goldW = 6.dp.toPx()
            drawCircle(color = palette.gold, radius = r - strokePx - goldW / 2f, style = Stroke(width = goldW))
            drawCircle(color = ink, radius = r - strokePx - goldW - strokePx / 2f, style = Stroke(width = strokePx))

            // ---- 玻璃高光：左上一片椭圆光（inset 7dp，60% × 45% 的椭圆在 30% / 22% 处）----
            val inset = 7.dp.toPx()
            val glassW = this.size.width - inset * 2
            val glassH = this.size.height - inset * 2
            clipPath(Path().apply { addOval(Rect(center = center, radius = r - inset)) }) {
                scale(scaleX = 1f, scaleY = 0.45f / 0.60f, pivot = Offset(inset + glassW * 0.30f, inset + glassH * 0.22f)) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            0f to Color.White.copy(alpha = 0.75f),
                            0.6f to Color.White.copy(alpha = 0f),
                            center = Offset(inset + glassW * 0.30f, inset + glassH * 0.22f),
                            radius = glassW * 0.60f,
                        ),
                        radius = glassW * 0.60f,
                        center = Offset(inset + glassW * 0.30f, inset + glassH * 0.22f),
                    )
                }
            }
        }

        // 数字压在 Canvas 之上，外面包一圈 4dp 的描边，压在水面上也看得清（设计稿 paint-order: stroke）
        Canvas(Modifier.size(size)) {
            val pct = measurer.measure("$percent", pctStyle)
            val unit = measurer.measure("% 完成", unitStyle)
            val cx = this.size.width / 2f
            val cy = this.size.height / 2f
            val gap = 1.dp.toPx()
            val totalH = pct.size.height + unit.size.height + gap
            val pctTop = Offset(cx - pct.size.width / 2f, cy - totalH / 2f)
            val unitTop = Offset(cx - unit.size.width / 2f, cy - totalH / 2f + pct.size.height + gap)
            val halo = Stroke(width = 4.dp.toPx(), join = androidx.compose.ui.graphics.StrokeJoin.Round)
            drawText(pct, color = palette.portholeHalo, topLeft = pctTop, drawStyle = halo)
            // ⚠️ drawStyle 必须显式传 Fill：TextLayoutResult 的画笔会记住上一次的 Stroke
            drawText(pct, color = palette.portholeText, topLeft = pctTop, drawStyle = Fill)
            drawText(unit, color = palette.portholeHalo, topLeft = unitTop, drawStyle = halo)
            drawText(unit, color = palette.portholeText, topLeft = unitTop, drawStyle = Fill)
        }
    }
}

/**
 * 一条向左右各铺满一倍宽度的正弦波，波谷以下是水。
 *
 * 画两倍宽是为了横向漂移时**首尾无缝**：波形左移一个周期后视觉上完全重合，
 * 所以漂移量取相位除以 2π 再模掉一倍宽度即可。
 */
private fun DrawScope.drawWave(amplitude: Float, phase: Float, color: Color) {
    val w = size.width * 2f
    val path = Path().apply {
        moveTo(0f, 0f)
        val steps = 48
        for (i in 0..steps) {
            val x = w * i / steps
            val y = sin(i.toFloat() / steps * 2f * PI.toFloat() + phase) * amplitude
            lineTo(x, y)
        }
        lineTo(w, size.height * 2f)
        lineTo(0f, size.height * 2f)
        close()
    }
    val shift = ((phase / (2f * PI.toFloat())) % 1f + 1f) % 1f
    translate(left = -size.width - shift * size.width) {
        drawPath(path, color)
    }
}
