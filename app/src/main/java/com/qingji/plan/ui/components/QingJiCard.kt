package com.qingji.plan.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.qingji.plan.ui.theme.Dimens
import com.qingji.plan.ui.theme.LocalPalette

/**
 * 全局统一的卡片。
 *
 * 【2026-10 鲸鱼主题】卡片是一张**贴纸**：白蕾丝底 + 墨线描边 + 实色错位投影，
 * 见 [sticker]。圆角 18（设计稿 `.stk`）。
 *
 * @param drop 投影下移距离。0 = 只描边不投影（卡片里再套卡片时用）
 */
@Composable
fun QingJiCard(
    modifier: Modifier = Modifier,
    shape: Dp = 18.dp,
    container: Color = LocalPalette.current.surface,
    drop: Dp = Dimens.stickerDrop.dp,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .sticker(shape = RoundedCornerShape(shape), fill = container, drop = drop)
            .padding(contentPadding),
        content = content,
    )
}
