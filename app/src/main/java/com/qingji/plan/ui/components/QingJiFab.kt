package com.qingji.plan.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.qingji.plan.ui.haptics.HapticCue
import com.qingji.plan.ui.haptics.LocalHaptics
import com.qingji.plan.ui.theme.LocalPalette

/**
 * 悬浮 ＋ 按钮。
 *
 * 【2026-10 鲸鱼主题】一颗**气泡**：主色实心圆 + 墨线 + 4dp 实色投影，
 * 左上角一点高光。按下整颗沉下去。
 */
@Composable
fun QingJiFab(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String = "新建",
    testTag: String = "add_fab",
    /** 要不要在点击时震一下。今日页的 ＋ 会真的落库建任务，值得震；日历页纯打开界面，不震 */
    hapticCue: HapticCue? = null,
) {
    val palette = LocalPalette.current
    val haptics = LocalHaptics.current

    Box(
        modifier = modifier
            .size(62.dp)
            .stickerClickable(
                shape = CircleShape,
                fill = palette.primary,
                drop = 4.dp,
                onClick = {
                    if (hapticCue != null) haptics.perform(hapticCue)
                    onClick()
                },
            )
            .drawBehind {
                // 气泡高光：左上角一颗半透明白点
                drawCircle(
                    color = Color.White.copy(alpha = 0.55f),
                    radius = size.minDimension * 0.11f,
                    center = Offset(size.width * 0.30f, size.height * 0.27f),
                )
            }
            .testTag(testTag),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.Add,
            contentDescription = contentDescription,
            tint = palette.onPrimary,
            modifier = Modifier.size(30.dp),
        )
    }
}
