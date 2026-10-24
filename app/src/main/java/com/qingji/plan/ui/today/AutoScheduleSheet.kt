package com.qingji.plan.ui.today

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qingji.plan.data.db.entity.TaskEntity
import com.qingji.plan.service.AutoScheduler
import com.qingji.plan.ui.components.TopSheet
import com.qingji.plan.ui.haptics.HapticCue
import com.qingji.plan.ui.haptics.LocalHaptics
import com.qingji.plan.ui.theme.AppText
import com.qingji.plan.ui.theme.Dimens
import com.qingji.plan.ui.theme.LocalPalette

/**
 * AI 自动排任务弹层。
 *
 * 对应原站那句原文：
 * > AI 会把今天「未排时间」的待办任务，自动安排到 08:00-21:00 不冲突的时间段
 * > （已排时间的任务保持不变）。
 *
 * 注意这里的「AI」其实是**本地算法**（[AutoScheduler]），不调大模型 ——
 * 原站也是这么做的，排期是个确定性计算，不需要联网。
 */
@Composable
fun AutoScheduleSheet(
    tasks: List<TaskEntity>,
    onDismiss: () -> Unit,
    onApply: (List<Pair<TaskEntity, Pair<String, Int>>>) -> Unit,
    modifier: Modifier = Modifier,
    /** 是否在看今天。只有今天才需要用「此刻」兜底，排未来日期仍从 08:00 起 */
    isToday: Boolean = true,
    /** 当前时刻换算成当天第几分钟。注入是为了可测，跟今日页那个 hourProvider 一个路子 */
    nowMinuteProvider: () -> Int = {
        java.time.LocalTime.now().let { it.hour * 60 + it.minute }
    },
) {
    val palette = LocalPalette.current
    val haptics = LocalHaptics.current

    val (busy, unscheduled) = remember(tasks) { AutoScheduler.split(tasks) }
    var result by remember(tasks) { mutableStateOf<com.qingji.plan.service.ScheduleResult?>(null) }

    TopSheet(
        onDismiss = onDismiss,
        testTag = "autoschedule_sheet",
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
        ) {
            // ---------- 标题 ----------
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "✨ AI 自动排任务",
                    style = AppText.body.copy(fontWeight = FontWeight.Bold, fontSize = 17.sp),
                    color = palette.text,
                    modifier = Modifier.weight(1f),
                )
                Box(
                    Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(onClick = onDismiss)
                        .testTag("autoschedule_close"),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Outlined.Close,
                        contentDescription = "关闭",
                        tint = palette.text2,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            Text(
                "AI 会把今天「未排时间」的待办任务，自动安排到 08:00-21:00 不冲突的时间段" +
                    "（已排时间的任务保持不变）。",
                style = AppText.body,
                color = palette.text2,
            )

            Spacer(Modifier.height(12.dp))

            // ---------- 现状 ----------
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Dimens.radiusSm.dp))
                    .background(palette.surface2)
                    .padding(12.dp),
            ) {
                Text(
                    "已排时间 ${busy.size} 条 · 未排时间 ${unscheduled.size} 条",
                    style = AppText.sub,
                    color = palette.text2,
                )
            }

            Spacer(Modifier.height(14.dp))

            val r = result
            if (r == null) {
                PrimaryButton(
                    text = "开始自动排期",
                    enabled = unscheduled.isNotEmpty(),
                    onClick = {
                        haptics.perform(HapticCue.Commit)
                        // 排今天时起点取「此刻」和 08:00 里较晚者 —— 否则下午排出来的
                        // 任务全在过去，今日页立刻把它们归进「已过时」
                        result = AutoScheduler.schedule(
                            unscheduled,
                            busy,
                            nowMinute = if (isToday) nowMinuteProvider() else null,
                        )
                    },
                    modifier = Modifier.testTag("autoschedule_run"),
                )
                if (unscheduled.isEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "今天没有「未排时间」的待办任务，不需要排期",
                        style = AppText.sub,
                        color = palette.text3,
                        fontSize = 11.sp,
                    )
                }
            } else {
                // ---------- 排期结果 ----------
                if (r.placed.isEmpty()) {
                    Text(
                        "08:00-21:00 之间没有足够的空档，一条都没排进去",
                        style = AppText.body,
                        color = palette.danger,
                    )
                } else {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(Dimens.radiusSm.dp))
                            .background(palette.surface2)
                            .padding(12.dp),
                    ) {
                        r.placed.forEach { slot ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    slot.label,
                                    style = AppText.sub.copy(fontWeight = FontWeight.SemiBold),
                                    color = palette.primaryDeep,
                                    modifier = Modifier.padding(end = 10.dp),
                                )
                                Text(
                                    slot.task.title,
                                    style = AppText.body,
                                    color = palette.text,
                                )
                            }
                        }
                    }
                }

                if (r.skipped.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "有 ${r.skipped.size} 条排不下（空档不够），它们会留在「🕊 随时」区",
                        style = AppText.sub,
                        color = palette.text3,
                        fontSize = 11.sp,
                    )
                }

                Spacer(Modifier.height(14.dp))

                PrimaryButton(
                    text = if (r.placed.isEmpty()) "关闭" else "就这么排（${r.placed.size} 条）",
                    onClick = {
                        if (r.placed.isEmpty()) {
                            onDismiss()
                        } else {
                            haptics.perform(HapticCue.Commit)
                            onApply(r.placed.map { it.task to (it.startHHmm to it.durationMinutes) })
                        }
                    },
                    modifier = Modifier.testTag("autoschedule_apply"),
                )
            }
        }
    }
}

@Composable
private fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val palette = LocalPalette.current
    Box(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(999.dp))
            .background(if (enabled) palette.primary else palette.surface2)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = AppText.body.copy(fontWeight = FontWeight.Bold),
            color = if (enabled) palette.scheme.onPrimary else palette.text3,
        )
    }
}
