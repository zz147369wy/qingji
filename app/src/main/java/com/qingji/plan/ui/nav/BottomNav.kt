package com.qingji.plan.ui.nav

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qingji.plan.R
import com.qingji.plan.ui.components.sticker
import com.qingji.plan.ui.components.stickerClickable
import com.qingji.plan.ui.pet.petDock
import com.qingji.plan.ui.haptics.HapticCue
import com.qingji.plan.ui.haptics.LocalHaptics
import com.qingji.plan.ui.theme.AppMotion
import com.qingji.plan.ui.theme.AppText
import com.qingji.plan.ui.theme.Dimens
import com.qingji.plan.ui.theme.LocalPalette
import com.qingji.plan.ui.theme.mixWith

/**
 * 底部导航栏。
 *
 * 【2026-10 鲸鱼主题】
 * - 白蕾丝底，**上沿是一道浪**（墨线描边），不再是一条直的分割线
 * - 选中的那格：图标后面弹出一颗贴纸胶囊（浅主色 + 墨线 + 2dp 投影）
 * - **正中是「空空」的头像钮**，比别的格子大一圈、凸出浪线 —— AI 聊天的唯一入口。
 *   它不是 Tab（不切换当前页），点了由 [onOpenAi] 打开整页聊天
 */
@Composable
fun BottomNav(
    current: TabDestination,
    onSelect: (TabDestination) -> Unit,
    modifier: Modifier = Modifier,
    onOpenAi: () -> Unit = {},
) {
    val palette = LocalPalette.current
    val waveDepth = 6.dp

    Box(modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .drawWithCache {
                    // 浪线：每 30dp 一个起伏，画在组件顶边上方（不占布局）
                    val d = waveDepth.toPx()
                    val step = 30.dp.toPx()
                    val n = (size.width / step).toInt() + 1
                    val wave = Path().apply {
                        moveTo(0f, d)
                        for (i in 0 until n) {
                            val x = i * step
                            quadraticTo(x + step / 4, -d * 0.2f, x + step / 2, d * 0.5f)
                            quadraticTo(x + step * 3 / 4, d * 1.2f, x + step, d * 0.5f)
                        }
                        lineTo(size.width + step, size.height)
                        lineTo(0f, size.height)
                        close()
                    }
                    val stroke = Stroke(width = Dimens.strokeWidth.dp.toPx())
                    onDrawBehind {
                        translate(top = -d) {
                            drawPath(wave, palette.surface)
                            drawPath(wave, palette.ink, style = stroke)
                        }
                        drawRect(palette.surface, topLeft = androidx.compose.ui.geometry.Offset(0f, d * 0.6f))
                    }
                }
                .navigationBarsPadding()
                .testTag("bottom_nav"),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(Dimens.navHeight.dp)
                    // 别的页面（日历/统计）没有自己的窝时，她趴在底栏左侧的浪线上
                    .petDock(com.qingji.plan.ui.pet.PetDockRegistry.NAV),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val tabs = TabDestination.entries
                tabs.forEachIndexed { index, tab ->
                    // 正中留给空空：在第 2 个 Tab（日历）后面插一格
                    if (index == tabs.size / 2) {
                        AiSlot(onClick = onOpenAi, modifier = Modifier.weight(1f))
                    }
                    NavItem(
                        tab = tab,
                        selected = tab == current,
                        onClick = { onSelect(tab) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/**
 * 正中那颗「空空」头像钮。
 *
 * 58dp 的圆，向上凸出浪线一半；头像用鲸鱼的真实贴图（从 web 角色导出的正脸）。
 * 外面一圈底色描边把它和浪线隔开，像是从水面冒出来的一颗泡泡。
 */
@Composable
private fun AiSlot(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val palette = LocalPalette.current
    val haptics = LocalHaptics.current
    // 字和别的格子的字对齐在同一条底线上；头像叠在字上方、往上凸出浪线。
    // 头像比这一格高，所以不能和字一起排进 Column（会把字挤成 0 高 —— 上一版就是这样「空空」两个字没了）
    Box(modifier = modifier.fillMaxHeight()) {
        Text(
            text = "空空",
            style = AppText.navItem.copy(fontSize = 11.5.sp),
            color = palette.text,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 7.dp),
        )
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .offset(y = (-25).dp)
                .size(58.dp)
                .drawBehind {
                    // 外圈的底色描边：把头像和浪线隔开（设计稿 `0 0 0 4px var(--paper)`）
                    drawCircle(palette.surface, radius = size.minDimension / 2 + 4.dp.toPx())
                }
                .stickerClickable(
                    shape = CircleShape,
                    fill = palette.primarySoft,
                    onClick = {
                        haptics.perform(HapticCue.Tap)
                        onClick()
                    },
                )
                .background(
                    Brush.radialGradient(
                        listOf(palette.primarySoft, palette.primary.mixWith(Color.White, 0.45f)),
                    ),
                )
                .testTag("nav_ai_button"),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.kongkong_avatar),
                contentDescription = "和空空聊天",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(58.dp)
                    .graphicsLayer {
                        // 头像图本身留了一点头顶空间，放大一点让脸更满
                        scaleX = 1.18f
                        scaleY = 1.18f
                        translationY = 3.dp.toPx()
                    },
            )
        }
    }
}

@Composable
private fun NavItem(
    tab: TabDestination,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalPalette.current
    val haptics = LocalHaptics.current
    // 选中：浅色下图标是主色深，深色下是正文色（设计稿 `[data-mode=dark] .tab.on .pill`）
    val tint = if (selected) (if (palette.isDark) palette.text else palette.primaryDeep) else palette.text3

    // 选中时用弹跳弹簧（跳出来的手感），取消选中时用不回弹的 effects ——
    // 后者如果回弹，胶囊会缩到负尺寸然后消失，看着像闪了一下
    val indicator by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = if (selected) AppMotion.Springs.spatialBouncy else AppMotion.Springs.effectsFast,
        label = "nav_indicator",
    )

    val interactionSource = remember { MutableInteractionSource() }

    Column(
        modifier = modifier
            .clickable(interactionSource = interactionSource, indication = null) {
                // 点已经选中的那个 tab 不震 —— 什么都没变，震一下反而像坏了
                if (!selected) haptics.perform(HapticCue.Tap)
                onClick()
            }
            .padding(vertical = 6.dp)
            .testTag("nav_${tab.route}"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(contentAlignment = Alignment.Center) {
            // 贴纸胶囊。缩放是绘制期变换，不占布局，图标位置不会跟着动
            Box(
                Modifier
                    .size(width = 56.dp, height = 32.dp)
                    .graphicsLayer {
                        scaleX = indicator
                        scaleY = indicator
                        alpha = indicator.coerceIn(0f, 1f)
                    }
                    .sticker(shape = CircleShape, fill = palette.primarySoft, drop = 2.dp),
            )
            Icon(
                imageVector = tab.icon,
                contentDescription = tab.label,
                tint = tint,
                modifier = Modifier.size(22.dp),
            )
        }
        Text(
            text = tab.label,
            style = AppText.navItem.copy(fontSize = 11.5.sp),
            color = if (selected) palette.text else tint,
            fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold,
            modifier = Modifier.padding(top = 3.dp),
        )
    }
}
