package com.qingji.plan.ui.today

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.dp
import com.qingji.plan.ui.theme.LocalPalette
import kotlin.math.cos
import kotlin.math.sin

/**
 * 完成任务的庆祝：从**勾选框的位置**冒出一串气泡。
 *
 * 【2026-10 鲸鱼主题】原来炸的是彩色纸屑（confetti）。鲸鱼主题改成**上浮的气泡** ——
 * 符合这套「海」的语言，也更安静：纸屑是往四周炸，气泡是往上飘、慢慢散掉。
 *
 * ## 帧率是怎么保证的
 *
 * 这是这个文件里唯一值得解释的东西。粒子动画很容易写成「每帧改一个 Compose 状态
 * → 触发重组 → 重组里再读状态」，那样每帧都要跑一遍组合，粒子一多就掉帧。
 *
 * 这里的做法是：
 *   1. 气泡存在一个**普通 MutableList** 里，不是 Compose 状态
 *   2. `withFrameNanos` 每帧只做两件事：更新物理、把 [frame] 加一
 *   3. [frame] **只在 `drawBehind` 的 lambda 里读** —— 状态在绘制阶段被读取时，
 *      Compose 只失效**绘制**，不失效组合
 *
 * 于是每帧只有一次重绘，没有组合、没有布局。
 */
private class Bubble(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    val radius: Float,
    /** 每个气泡有自己的横向摆动相位，飘起来才不像一束直线 */
    val wobblePhase: Float,
    val wobbleAmp: Float,
    var life: Float,
    val maxLife: Float,
)

/** 水的浮力（负值 = 向上加速） */
private const val BUOYANCY = -900f

/** 每秒的速度衰减。水有阻力，气泡很快就到匀速 */
private const val DRAG = 2.4f

private const val BUBBLE_COUNT = 14

/** 一次爆发的触发点。id 变了就重新冒一次 */
data class BurstTrigger(val id: Long, val offset: Offset)

/**
 * 铺满全屏的气泡层。没有正在播的爆发时它什么都不画，也不占任何开销。
 */
@Composable
fun CompletionBurstOverlay(
    trigger: BurstTrigger?,
    modifier: Modifier = Modifier,
) {
    val palette = LocalPalette.current

    // 普通 list，不是 Compose 状态 —— 刻意如此，见类注释
    val bubbles = remember { mutableListOf<Bubble>() }

    // 每帧 +1。**只在绘制阶段读**，所以只引起重绘
    var frame by remember { mutableIntStateOf(0) }

    val fill = palette.primary
    val highlight = Color.White

    LaunchedEffect(trigger?.id) {
        val t = trigger ?: return@LaunchedEffect
        bubbles.clear()
        spawn(bubbles, t.offset)

        var last = 0L
        var elapsed = 0f
        while (bubbles.isNotEmpty()) {
            withFrameNanos { now ->
                // 第一帧还没有上一帧时间，按 16ms 估一个，避免 dt = 0 让粒子卡住
                val dt = if (last == 0L) 1f / 60f else ((now - last) / 1_000_000_000.0).toFloat()
                last = now
                // 掉帧时钳一下，别让气泡在下一帧瞬移出去
                val step = dt.coerceAtMost(1f / 30f)
                elapsed += step
                stepBubbles(bubbles, step, elapsed)
                frame++
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .drawBehind {
                // 读 frame 是为了在绘制阶段订阅它 —— 这一行就是「只重绘不重组」的关键
                @Suppress("UNUSED_EXPRESSION")
                frame
                val strokePx = 1.4.dp.toPx()
                bubbles.forEach { b ->
                    val k = (b.life / b.maxLife).coerceIn(0f, 1f)
                    // 只在最后 40% 淡出，前半段保持实心
                    val alpha = (k / 0.4f).coerceIn(0f, 1f)
                    translate(left = 0f, top = 0f) {
                        drawCircle(
                            color = fill.copy(alpha = alpha * 0.35f),
                            radius = b.radius,
                            center = Offset(b.x, b.y),
                        )
                        drawCircle(
                            color = fill.copy(alpha = alpha * 0.8f),
                            radius = b.radius,
                            center = Offset(b.x, b.y),
                            style = Stroke(width = strokePx),
                        )
                        // 左上角的高光，气泡才有「球」的感觉
                        drawCircle(
                            color = highlight.copy(alpha = alpha * 0.9f),
                            radius = b.radius * 0.22f,
                            center = Offset(b.x - b.radius * 0.32f, b.y - b.radius * 0.34f),
                        )
                    }
                }
            }
    )
}

private fun spawn(out: MutableList<Bubble>, origin: Offset) {
    // 用固定序列而不是随机数：同一个触发点每次冒的样子一致，
    // 也让「同样的操作看起来一样」
    for (i in 0 until BUBBLE_COUNT) {
        // 向上为主，左右各铺开一点
        val angle = -Math.PI.toFloat() / 2f + ((i % 9) - 4) * 0.22f
        val speed = 130f + (i % 5) * 46f
        val life = 0.9f + (i % 4) * 0.16f
        out += Bubble(
            x = origin.x,
            y = origin.y,
            vx = cos(angle) * speed,
            vy = sin(angle) * speed,
            radius = 4f + (i % 4) * 2.6f,
            wobblePhase = i * 0.8f,
            wobbleAmp = 8f + (i % 3) * 5f,
            life = life,
            maxLife = life,
        )
    }
}

private fun stepBubbles(bubbles: MutableList<Bubble>, dt: Float, elapsed: Float) {
    val decay = (1f - DRAG * dt).coerceAtLeast(0f)
    val it = bubbles.iterator()
    while (it.hasNext()) {
        val b = it.next()
        b.vy += BUOYANCY * dt
        b.vx *= decay
        b.vy *= decay
        b.x += b.vx * dt + sin(elapsed * 3.4f + b.wobblePhase) * b.wobbleAmp * dt
        b.y += b.vy * dt
        b.life -= dt
        if (b.life <= 0f) it.remove()
    }
}
