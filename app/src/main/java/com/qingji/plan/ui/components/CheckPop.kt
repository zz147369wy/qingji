package com.qingji.plan.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.qingji.plan.ui.theme.AppMotion
import com.qingji.plan.ui.theme.LocalPalette
import com.qingji.plan.ui.theme.mixWith

/**
 * 勾选圆圈 → **泡泡勾**。
 *
 * 【2026-10 鲸鱼主题】未完成是一颗淡蓝的泡泡（墨线 + 左上高光），
 * 点一下「戳破」：先缩到 0.8 再弹回，填满主色、出现白勾。
 *
 * 缩放走 graphicsLayer（绘制期），不触发重排。
 */
@Composable
fun CheckCircle(
    checked: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    /** 勾选后用哪个颜色填充。默认主色 */
    checkedColor: Color? = null,
    size: androidx.compose.ui.unit.Dp = 26.dp,
) {
    val palette = LocalPalette.current
    val fill = checkedColor ?: palette.primary
    val bubble = if (palette.isDark) palette.surface2 else palette.primary.mixWith(Color.White, 0.10f)
    val highlight = Color.White.copy(alpha = if (palette.isDark) 0.7f else 1f)

    val scale = remember { Animatable(1f) }

    // 变成已勾选时「戳破」一下：0.8 → 弹回 1（spatialBouncy 自然过冲到 1.1 上下）
    LaunchedEffect(checked) {
        if (checked) {
            scale.snapTo(0.8f)
            scale.animateTo(1f, AppMotion.Springs.spatialBouncy)
        }
    }

    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
            }
            .sticker(shape = CircleShape, fill = if (checked) fill else bubble, drop = 0.dp)
            .drawBehind {
                if (!checked) {
                    drawCircle(
                        color = highlight,
                        radius = this.size.minDimension * 0.12f,
                        center = Offset(this.size.width * 0.34f, this.size.height * 0.30f),
                    )
                }
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onToggle,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = "已完成",
                // 主色上的前景色；底色是任意分类色时白勾也够清楚
                tint = if (checkedColor == null) palette.onPrimary else Color.White,
                modifier = Modifier.size(size * 0.6f),
            )
        }
    }
}
