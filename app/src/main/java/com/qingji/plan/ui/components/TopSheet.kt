package com.qingji.plan.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qingji.plan.ui.theme.AppMotion
import com.qingji.plan.ui.theme.LocalPalette
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * 顶部下拉弹层。
 *
 * 【2026-10 鲸鱼主题】下沿从圆角换成一排**女仆围裙的蕾丝花边**（扇贝 + 小孔），
 * 遮罩改用墨线色，跟整套贴纸风格对上。
 *
 * 为什么不用 Material3 的 ModalBottomSheet：**因为原站不是从底部弹的**。
 *
 * 原站只有一个弹层样式，它的定义是：
 *   - 圆角：上面 0（贴顶），下面 24px   → 说明它贴着屏幕顶边
 *   - 动画：从 translateY(-24px) 滑到 0，200ms
 *   - 背景：透明度 0 到 1，150ms
 *
 * 所以原站的弹层是**从屏幕顶部往下拉的整宽面板**。
 * 截图也印证了：AI 自动排、每日结算的标题和关闭按钮都在屏幕顶部。
 */
@Composable
fun TopSheet(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    /** 最多占屏幕高度的比例，超了内部滚动 */
    maxHeightFraction: Float = 0.85f,
    testTag: String = "top_sheet",
    content: @Composable ColumnScope.() -> Unit,
) {
    val palette = LocalPalette.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    // 背景淡入
    val backdropAlpha = remember { Animatable(0f) }
    // 面板下滑：从 -24px 到 0
    val panelOffset = remember { Animatable(-24f) }
    val panelAlpha = remember { Animatable(0f) }

    // 【2026-10 重设计】原来位移和透明度都用同一个 tween，现在分开：
    //   位移走 spatial（允许回弹，面板「落」下来时有一点重量感）
    //   透明度走 effects（dampingRatio = 1，绝不能回弹 —— 越过 1 会闪）
    LaunchedEffect(Unit) {
        launch { backdropAlpha.animateTo(1f, AppMotion.Springs.effects) }
        launch {
            panelOffset.animateTo(0f, AppMotion.Springs.spatialFast)
            panelAlpha.animateTo(1f, AppMotion.Springs.effects)
        }
    }

    // 关闭时先播反向动画再真正消失
    fun dismiss() {
        scope.launch {
            launch { backdropAlpha.animateTo(0f, AppMotion.Springs.effectsFast) }
            launch {
                panelAlpha.animateTo(0f, AppMotion.Springs.effectsFast)
                panelOffset.animateTo(-24f, AppMotion.Springs.spatialFast)
            }
        }
        // 动画跑完再回调。**刻意不用 await** —— 关闭是用户最不能等的一步，
        // 万一动画因为任何原因没跑完，await 会让弹层卡在那不动。
        // 弹簧通常比原来那个 tween 停得更快，这个延迟是上限而不是实际时长。
        scope.launch {
            kotlinx.coroutines.delay(AppMotion.Duration.quick.toLong())
            onDismiss()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            // 键盘弹起时整块遮罩跟着缩，面板的 verticalScroll 会自动接管剩余高度。
            // 「添加计划」弹层里的输入框是自动聚焦的（原站就是 autoFocus），没有这一句
            // 键盘会直接盖住分类行和底部按钮。
            .imePadding()
            .testTag(testTag),
    ) {
        // ---------- 背景遮罩：淡入 + 点击关闭 ----------
        Box(
            Modifier
                .fillMaxSize()
                .alpha(backdropAlpha.value)
                .background(palette.ink.copy(alpha = 0.45f))  // 设计稿 .scrim：墨线 45%
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { dismiss() },
        )

        // ---------- 面板：从顶部滑下，下沿是女仆围裙的蕾丝花边 ----------
        Column(
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .offsetY(panelOffset.value, density.density)
                .alpha(panelAlpha.value)
                .padding(bottom = LACE_DEPTH.dp)
                .drawWithCache {
                    val lace = lacePath(size.width, size.height, LACE_DEPTH.dp.toPx())
                    val holes = laceHoles(size.width, size.height, LACE_DEPTH.dp.toPx())
                    val stroke = Stroke(width = 1.5.dp.toPx())
                    val holeR = 1.6.dp.toPx()
                    onDrawWithContent {
                        // 先画花边（在面板主体下方、布局边界之外），再用主体底色盖住花边路径顶上那条封口线
                        drawPath(lace, palette.surface)
                        drawPath(lace, palette.ink, style = stroke)
                        drawRect(palette.surface)
                        holes.forEach { drawCircle(palette.ink.copy(alpha = 0.5f), holeR, it) }
                        drawContent()
                    }
                }
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .statusBarsPadding()
                .padding(top = 18.dp, bottom = 22.dp),
            content = content,
        )
    }
}

/** 蕾丝花边的深度（扇贝的高度） */
private const val LACE_DEPTH = 13

/** 一个扇贝的宽度目标值；实际按屏宽整除后微调，保证两端对齐 */
private const val SCALLOP_WIDTH_DP = 20f

/**
 * 花边路径：面板底边往下一排半圆扇贝，沿着整条边连成一个闭合形状。
 * [h] 是面板主体的高度（花边从 y = h 开始往下画）。
 */
private fun androidx.compose.ui.unit.Density.lacePath(w: Float, h: Float, depth: Float): Path {
    val n = (w / SCALLOP_WIDTH_DP.dp.toPx()).toInt().coerceAtLeast(6)
    val step = w / n
    return Path().apply {
        moveTo(0f, h - 1f)
        for (i in 0 until n) {
            val x0 = i * step
            quadraticTo(x0 + step / 2, h + depth * 2f, x0 + step, h)
        }
        lineTo(w, h - 1f)
        close()
    }
}

/** 每个扇贝里的一颗小孔（蕾丝的镂空） */
private fun androidx.compose.ui.unit.Density.laceHoles(w: Float, h: Float, depth: Float): List<androidx.compose.ui.geometry.Offset> {
    val n = (w / SCALLOP_WIDTH_DP.dp.toPx()).toInt().coerceAtLeast(6)
    val step = w / n
    return List(n) { i -> androidx.compose.ui.geometry.Offset(i * step + step / 2, h + depth * 0.45f) }
}

/** 按像素平移。用 offset 而不是动画 API，因为我们要自己控制值 */
private fun Modifier.offsetY(px: Float, density: Float): Modifier =
    this.then(offset { IntOffset(0, (px * density).roundToInt()) })
