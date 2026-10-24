package com.qingji.plan.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.qingji.plan.ui.theme.AppText
import com.qingji.plan.ui.theme.Dimens
import com.qingji.plan.ui.theme.LocalPalette

/**
 * 页面标题区。
 *
 * 重设计前 7 个页面各写各的：副标题样式其实一致（AppText.sub + text2），
 * 但骨架有 3 种，而且长期计划和批量录入页**漏了 `padding(top = 2.dp)`**，
 * 标题和副标题的间距跟别的页面对不上。这里收敛成一个。
 *
 * [leading] 放返回键，[actions] 放右上角那排功能按钮。
 */
@Composable
fun PageHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    titleTestTag: String? = null,
    subtitleTestTag: String? = null,
    leading: (@Composable () -> Unit)? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
) {
    val palette = LocalPalette.current

    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(Dimens.space3.dp))
        }

        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = AppText.title,
                color = palette.text,
                maxLines = 2,
                modifier = if (titleTestTag != null) Modifier.testTag(titleTestTag) else Modifier,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = AppText.sub,
                    color = palette.text2,
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .then(
                            if (subtitleTestTag != null) {
                                Modifier.testTag(subtitleTestTag)
                            } else {
                                Modifier
                            }
                        ),
                )
            }
        }

        if (actions != null) {
            Spacer(Modifier.width(Dimens.space3.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                content = actions,
            )
        }
    }
}

/**
 * 标题区右上角那种方形图标按钮。
 *
 * 【2026-10 鲸鱼主题】一张小贴纸：白底墨线 + 实色投影，按下整颗下沉。
 *
 * @param size 边长，默认 40dp；周条翻页这类次要按钮用 32dp
 */
@Composable
fun QingJiIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color? = null,
    container: Color? = null,
    size: Dp = Dimens.iconButton.dp,
    testTag: String? = null,
) {
    val palette = LocalPalette.current

    Box(
        modifier = modifier
            .size(size)
            .stickerClickable(
                shape = RoundedCornerShape(if (size < 36.dp) 11.dp else 13.dp),
                onClick = onClick,
                fill = container ?: palette.surface,
                drop = if (size < 36.dp) 2.dp else Dimens.stickerDrop.dp,
            )
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint ?: palette.text,
            modifier = Modifier.size(if (size < 36.dp) 17.dp else 20.dp),
        )
    }
}

/**
 * 同上，但内容自己给 —— 用于「✕」这种不是图标的按钮。
 */
@Composable
fun QingJiIconButtonBox(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    container: Color? = null,
    size: Dp = Dimens.iconButton.dp,
    testTag: String? = null,
    content: @Composable () -> Unit,
) {
    val palette = LocalPalette.current

    Box(
        modifier = modifier
            .size(size)
            .stickerClickable(
                shape = RoundedCornerShape(13.dp),
                onClick = onClick,
                fill = container ?: palette.surface,
            )
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier),
        contentAlignment = Alignment.Center,
        content = { content() },
    )
}
