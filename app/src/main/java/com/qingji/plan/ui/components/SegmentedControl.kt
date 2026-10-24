package com.qingji.plan.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qingji.plan.ui.theme.AppText
import com.qingji.plan.ui.theme.LocalPalette

/** 分段控件里的一段 */
data class Segment(
    val label: String,
    val testTag: String? = null,
    /** 文字前的小图标（替代原来写在文字里的 📝📚 emoji） */
    val icon: ImageVector? = null,
    /** 文字后面的数字（「全部 13」里的 13），用 Fredoka、淡一点（设计稿 `.seg .n`） */
    val count: String? = null,
)

/**
 * 全局统一的分段控件（「月视图 / 课程表」那种）。
 *
 * 【2026-10 鲸鱼主题】轨道是一条墨线描边的浅底槽，选中的那段是一张**贴纸**
 * （白底墨线 + 2dp 投影），像从槽里浮起来的一块。
 */
@Composable
fun SegmentedControl(
    segments: List<Segment>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    testTag: String? = null,
) {
    val palette = LocalPalette.current
    val trackShape = RoundedCornerShape(16.dp)
    val segShape = RoundedCornerShape(11.dp)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .sticker(shape = trackShape, fill = palette.surface2, drop = 0.dp)
            .padding(4.dp)
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        segments.forEachIndexed { index, segment ->
            val active = index == selectedIndex
            // ⚠️ 每一段要有**自己的** interactionSource，共用会导致一起下沉
            val interactionSource = remember { MutableInteractionSource() }
            val fg = when {
                !enabled -> palette.text3
                active -> palette.text
                else -> palette.text2
            }
            Box(
                Modifier
                    .weight(1f)
                    .then(
                        if (active) {
                            Modifier.sticker(
                                shape = segShape,
                                fill = palette.surface,
                                interactionSource = interactionSource,
                                drop = 2.dp,
                            )
                        } else {
                            Modifier.sticker(
                                shape = segShape,
                                fill = Color.Transparent,
                                interactionSource = interactionSource,
                                drop = 0.dp,
                                stroke = Color.Transparent,
                            )
                        }
                    )
                    .then(
                        if (enabled) {
                            Modifier.clickable(
                                interactionSource = interactionSource,
                                indication = null,
                            ) { onSelect(index) }
                        } else {
                            Modifier
                        }
                    )
                    .padding(vertical = 8.dp)
                    .then(
                        if (segment.testTag != null) Modifier.testTag(segment.testTag) else Modifier
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (segment.icon != null) {
                        Icon(segment.icon, contentDescription = null, tint = fg, modifier = Modifier.size(17.dp))
                    }
                    Text(
                        text = segment.label,
                        style = AppText.body.copy(fontSize = 14.sp),
                        color = fg,
                        fontWeight = if (active) FontWeight.ExtraBold else FontWeight.SemiBold,
                    )
                    if (segment.count != null) {
                        Text(
                            text = segment.count,
                            style = AppText.numSmall.copy(fontSize = 14.sp),
                            color = fg.copy(alpha = 0.7f),
                        )
                    }
                }
            }
        }
    }
}
