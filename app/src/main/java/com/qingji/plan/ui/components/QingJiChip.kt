package com.qingji.plan.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.qingji.plan.ui.theme.AppText
import com.qingji.plan.ui.theme.Dimens
import com.qingji.plan.ui.theme.LocalPalette

/**
 * 全局统一的胶囊 / 芯片。
 *
 * 【2026-10 鲸鱼主题】分两种：
 *
 * - **可点的胶囊**（传了 [onClick]）：一张小贴纸。未选中 = 白底墨线；选中 = 主色底墨线
 *   + 2dp 实色投影，像「被按亮」的那一颗。按下整颗下沉。
 * - **纯标签**（不传 [onClick]）：无描边的软底小胶囊，比如任务行上的分类标签。
 *
 * ```kotlin
 * // 可筛选
 * QingJiChip("全部", selected = true, onClick = {})
 *
 * // 分类标签：底色字色按主题明暗推导
 * QingJiChip(cat.label, container = palette.tagBg(cat.color), contentColor = palette.tagFg(cat.color), dotColor = cat.color)
 *
 * // 「重要」：选中时用胸针金
 * QingJiChip("重要", selected = active, onClick = {}, selectedContainer = palette.gold, selectedContentColor = palette.ink)
 * ```
 */
@Composable
fun QingJiChip(
    label: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
    /** 未选中时的底色。不传：可点的用 surface，纯标签用 surface2 */
    container: Color? = null,
    /** 未选中时的文字色。不传：可点的用 text，纯标签用 text2 */
    contentColor: Color? = null,
    /** 选中时的底色。不传用主题主色 */
    selectedContainer: Color? = null,
    /** 选中时的文字色。不传用 onPrimary */
    selectedContentColor: Color? = null,
    /** 历史参数：选中时描一圈主色细边。贴纸胶囊本来就有墨线，现在不再额外描边 */
    ring: Boolean = false,
    /** 标签前面的小圆点颜色 */
    dotColor: Color? = null,
    /** 标签前面的自定义小图标（比如「重要」的星） */
    leading: (@Composable () -> Unit)? = null,
    compact: Boolean = false,
    testTag: String? = null,
) {
    val palette = LocalPalette.current
    val shape = RoundedCornerShape(Dimens.shapeFull.dp)
    val interactive = onClick != null

    val bg = if (selected) {
        selectedContainer ?: palette.primary
    } else {
        container ?: if (interactive) palette.surface else palette.surface2
    }
    val fg = if (selected) {
        selectedContentColor ?: palette.onPrimary
    } else {
        contentColor ?: if (interactive) palette.text else palette.text2
    }

    val surface = if (interactive) {
        val source = remember { MutableInteractionSource() }
        Modifier
            .sticker(
                shape = shape,
                fill = bg,
                interactionSource = source,
                drop = if (selected) 2.dp else 0.dp,
            )
            .clickable(interactionSource = source, indication = null, onClick = onClick!!)
    } else {
        Modifier.clip(shape).background(bg)
    }

    Row(
        modifier = modifier
            .then(surface)
            .padding(
                horizontal = if (compact) 11.dp else 13.dp,
                vertical = if (compact) 5.dp else 7.dp,
            )
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        leading?.invoke()
        if (dotColor != null) {
            Box(
                Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(dotColor)
                    .then(if (interactive) Modifier.border(1.dp, palette.ink, CircleShape) else Modifier),
            )
        }
        Text(
            text = label,
            style = AppText.chip,
            color = fg,
            fontWeight = when {
                selected -> FontWeight.ExtraBold
                interactive -> FontWeight.SemiBold
                else -> FontWeight.Bold
            },
        )
    }
}
