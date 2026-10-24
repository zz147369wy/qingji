package com.qingji.plan.ui.theme

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath

/**
 * 圆形主题切换。
 *
 * 点哪套主题，新配色就从**那个色点的位置**扩散一个圆，把整个界面「抹」成新主题。
 *
 * 实现的四条关键约束（来自交互设计规格）：
 *
 *   1. **圆心是真实点击位置**，不是屏幕中心
 *   2. **半径按「到最远那个屏幕角的距离」算** —— 不是固定值，
 *      否则点右上角时左下角会先变完、右上角反而没盖到
 *   3. **两层内容尺寸位置完全一致，只裁切上层** —— 不能缩放页面，
 *      否则切换时整页会「弹一下」
 *   4. 动画期间**下层用的是旧主题**，动画结束后才真正提交
 *
 * 代价：切换的那一瞬间界面会被渲染两遍。对一个自用 App 来说完全可以接受，
 * 400ms 的动画比这个开销显眼得多。
 */
@Composable
fun RadialThemeTransition(
    palette: ThemePalette,
    /** 触发点的屏幕坐标（像素）。null 表示不做动画，直接换 */
    revealAt: Offset?,
    modifier: Modifier = Modifier,
    /** 动画跑完的通知（这时才应该把新主题写进数据库） */
    onRevealed: () -> Unit = {},
    content: @Composable () -> Unit,
) {
    // 当前真正在显示的主题
    var displayed by remember { mutableStateOf(palette) }
    // 正在扩散进来的新主题。null 表示没有动画在跑
    var incoming by remember { mutableStateOf<ThemePalette?>(null) }
    var center by remember { mutableStateOf(Offset.Zero) }

    val progress = remember { Animatable(1f) }

    LaunchedEffect(palette.id, revealAt) {
        if (palette.id == displayed.id) return@LaunchedEffect

        // 没给位置（比如冷启动读设置）就直接换，不播动画
        if (revealAt == null) {
            displayed = palette
            return@LaunchedEffect
        }

        center = revealAt
        incoming = palette
        progress.snapTo(0f)
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing),
        )
        // 动画跑完才提交，避免中途闪回旧主题
        displayed = palette
        incoming = null
        onRevealed()
    }

    Box(modifier.fillMaxSize()) {
        // 下层：当前主题
        QingJiTheme(palette = displayed) { content() }

        // 上层：新主题，被一个圆形裁切
        incoming?.let { newPalette ->
            Box(
                Modifier
                    .fillMaxSize()
                    .drawWithContent {
                        val radius = maxRadiusToCorner(center, size.width, size.height) * progress.value
                        val path = Path().apply {
                            addOval(
                                androidx.compose.ui.geometry.Rect(center = center, radius = radius)
                            )
                        }
                        clipPath(path) { this@drawWithContent.drawContent() }
                    }
            ) {
                QingJiTheme(palette = newPalette) { content() }
            }
        }
    }
}

/**
 * 算出「点击位置到最远屏幕角」的距离。半径用这个才盖得满。
 */
internal fun maxRadiusToCorner(point: Offset, width: Float, height: Float): Float {
    val dx = maxOf(point.x, width - point.x)
    val dy = maxOf(point.y, height - point.y)
    return kotlin.math.hypot(dx, dy)
}
