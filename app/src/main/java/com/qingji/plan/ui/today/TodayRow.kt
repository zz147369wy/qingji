package com.qingji.plan.ui.today

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.qingji.plan.ui.theme.mixWith
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.qingji.plan.data.db.entity.TaskEntity
import com.qingji.plan.data.model.Category
import com.qingji.plan.ui.components.CheckCircle
import com.qingji.plan.ui.components.dimIndication
import com.qingji.plan.ui.components.sticker
import com.qingji.plan.ui.haptics.HapticCue
import com.qingji.plan.ui.haptics.LocalHaptics
import com.qingji.plan.ui.theme.AppText
import com.qingji.plan.ui.theme.LocalPalette

/** 行的最小高度。也是勾选框触摸区的下界 */
private val RowMinHeight = 56.dp

/**
 * 列表里的一条任务（**只画内容，不管手势**）。
 *
 * 【2026-10 鲸鱼主题】一条任务是一张**白色贴纸**：墨线描边 + 2dp 实色投影，
 * 浮在所属那层海水色带上。左边是泡泡勾，中间时间用 Fredoka 数字字体，
 * 右边是分类小标签，重要的任务在标题后挂一颗金星。
 *
 * 原来左侧那条 4dp 的分类色条去掉了 —— 贴纸描边已经足够分开每一条，
 * 再加一条色条会让行里出现两个视觉锚点。分类的颜色现在由右侧标签承担。
 *
 * ## 手势（没变）
 *
 * | 操作 | 结果 |
 * |---|---|
 * | 点泡泡 | 完成 / 取消完成（戳破） |
 * | 点行的其它地方 | 打开编辑 |
 * | 长按 | 进入多选 |
 * | 左滑 / 右滑 | 见 `SwipeRevealRow`（删除 / 完成） |
 *
 * **勾选框的触摸区是 48dp，视觉只有 26dp** —— 手指稍偏就命中行、打开编辑而没勾上，
 * 这是上一版实际存在的体验问题，别退回去。
 */
@Composable
fun TaskRow(
    task: TaskEntity,
    category: Category,
    selectionMode: Boolean,
    selected: Boolean,
    onToggle: (Offset) -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalPalette.current
    val haptics = LocalHaptics.current
    val shape = RoundedCornerShape(18.dp)
    val interactionSource = remember { MutableInteractionSource() }

    val done = task.isDone

    // 勾选框的圆心（根坐标系）。完成时气泡就从这里冒出来
    var checkCenter by remember { mutableStateOf(Offset.Zero) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = RowMinHeight)
            .sticker(
                shape = shape,
                fill = if (selected) palette.primarySoft else palette.surface,
                interactionSource = interactionSource,
                drop = 3.dp,
            )
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onLongClick = {
                    haptics.perform(HapticCue.GrabMove)
                    onLongClick()
                },
                onClick = {
                    if (!selectionMode) haptics.perform(HapticCue.Tap)
                    onClick()
                },
            )
            .padding(start = 4.dp, end = 12.dp)
            .testTag("task_row_${task.uid}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 泡泡勾。外面这层 48dp 是纯触摸区，里面那颗泡泡才是看得见的
        Box(
            Modifier
                .size(48.dp)
                .onGloballyPositioned { coords ->
                    checkCenter = coords.positionInRoot() +
                        Offset(coords.size.width / 2f, coords.size.height / 2f)
                }
                .testTag("task_check_${task.uid}"),
            contentAlignment = Alignment.Center,
        ) {
            CheckCircle(
                checked = done,
                onToggle = {
                    haptics.perform(if (done) HapticCue.ToggleOff else HapticCue.ToggleOn)
                    onToggle(checkCenter)
                },
            )
        }

        // 时间。没时间的显示「随时」。数字走 Fredoka
        Text(
            text = task.time ?: "随时",
            style = if (task.time != null) AppText.numSmall.copy(fontSize = 14.sp)
            else AppText.label.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
            color = palette.text2,
            modifier = Modifier.width(44.dp),
        )

        // 标题 + 「重要」星：星紧跟在标题后面（标题短时不被推到最右），整块占掉中间的剩余宽度
        Row(Modifier.weight(1f).padding(start = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = task.title,
                style = AppText.body.copy(fontWeight = FontWeight.SemiBold),
                color = if (done) palette.text3 else palette.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textDecoration = if (done) TextDecoration.LineThrough else null,
                modifier = Modifier.weight(1f, fill = false),
            )
            // 「重要」用胸针金的一颗星，别跟分类色混在一起
            if (task.isImportant) {
                StickerStar(Modifier.padding(start = 5.dp).semantics { contentDescription = "重要" })
            }
        }

        // 分类标签：软底小胶囊 + 分类色小圆点（设计稿 `.tag`），已完成时淡一半
        CategoryTag(category, Modifier.padding(start = 8.dp).alpha(if (done) 0.55f else 1f))
    }
}

/**
 * 分类小标签：`● 工作`。底和字按主题明暗推导，圆点是分类色本身（设计稿 `.tag`）。
 * 今日页、日历当日计划共用。
 */
@Composable
fun CategoryTag(category: Category, modifier: Modifier = Modifier) {
    val palette = LocalPalette.current
    Row(
        modifier
            .widthIn(max = 84.dp)
            .background(palette.tagBg(category.color), RoundedCornerShape(999.dp))
            .padding(start = 6.dp, end = 8.dp, top = 3.dp, bottom = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(7.dp)
                .background(category.color, CircleShape)
                .border(1.dp, category.color.mixWith(palette.tagFg(category.color), 0.5f), CircleShape),
        )
        Text(
            text = category.label,
            style = AppText.label.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold),
            color = palette.tagFg(category.color),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}
