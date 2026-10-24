package com.qingji.plan.ui.batch

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qingji.plan.data.model.Category
import com.qingji.plan.service.BatchParser
import com.qingji.plan.service.ParsedLine
import com.qingji.plan.ui.components.PageHeader
import com.qingji.plan.ui.components.QingJiCard
import com.qingji.plan.ui.components.QingJiChip
import com.qingji.plan.ui.components.QingJiIconButton
import com.qingji.plan.ui.components.Segment
import com.qingji.plan.ui.components.SegmentedControl
import com.qingji.plan.ui.components.dimIndication
import com.qingji.plan.ui.haptics.HapticCue
import com.qingji.plan.ui.haptics.LocalHaptics
import com.qingji.plan.ui.theme.AppText
import com.qingji.plan.ui.theme.Dimens
import com.qingji.plan.ui.theme.LocalPalette
import com.qingji.plan.util.DateUtils
import java.time.LocalDate

/** 批量录入的两个 Tab */
enum class BatchTab(val label: String) {
    TASK("计划"),
    COURSE("课程"),
}

/**
 * 批量录入页。
 *
 * 对应原站那个二级页：粘贴多行，自动识别 时间/日期/分类/重要/重复。
 * 页面上那几段提示文字都是**原文照抄原站**的。
 */
@Composable
fun BatchInputScreen(
    defaultDate: LocalDate,
    modifier: Modifier = Modifier,
    customs: List<Category> = emptyList(),
    onBack: () -> Unit = {},
    onDefaultDateChange: (LocalDate) -> Unit = {},
    onApply: (List<ParsedLine>, BatchTab) -> Unit = { _, _ -> },
) {
    val palette = LocalPalette.current
    val haptics = LocalHaptics.current

    var tab by remember { mutableStateOf(BatchTab.TASK) }
    var text by remember { mutableStateOf("") }
    var copied by remember { mutableStateOf(false) }

    // 实时解析，让用户边打字边看到结果
    val parsed = remember(text, defaultDate, customs) {
        BatchParser.parse(text, defaultDate, customs)
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            // ★ 必须有不透明背景！否则下面的页面会透上来，
            //   用起来就像'贴在原页面上的一层'，而不是一个独立页面
            .background(palette.bg)
            .statusBarsPadding()
            .padding(horizontal = 18.dp)
            .padding(top = 16.dp, bottom = 30.dp)
            .testTag("batch_screen"),
    ) {
        // ---------- 顶栏 ----------
        PageHeader(
            title = "批量录入",
            subtitle = "粘贴/输入多行，自动识别日期·时间·分类·重复",
            leading = {
                QingJiIconButton(
                    icon = Icons.Outlined.ArrowBack,
                    contentDescription = "返回",
                    onClick = {
                        haptics.perform(HapticCue.Tap)
                        onBack()
                    },
                    testTag = "batch_back",
                )
            },
        )

        Spacer(Modifier.height(14.dp))

        // ---------- 计划 / 课程 ----------
        SegmentedControl(
            segments = BatchTab.entries.map { Segment(it.label, "batch_tab_${it.name}") },
            selectedIndex = tab.ordinal,
            onSelect = { index ->
                haptics.perform(HapticCue.Tap)
                tab = BatchTab.entries[index]
            },
        )

        Spacer(Modifier.height(14.dp))

        // ---------- 输入框 ----------
        // 底子就是一个 surface 方块，走 QingJiCard。里面套 Box 是因为占位提示要跟
        // 输入框重叠（Column 会把提示顶到输入框上面去）
        QingJiCard(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(14.dp),
        ) {
            Box {
                if (text.isEmpty()) {
                    Text(
                        "每行一条，自动识别 时间/日期/分类/重要/重复。" +
                            "示例：9/1 9:00 开会 @工作；每天 背单词 20 个；每周五 写周报；周一 14:30 健身 重要",
                        style = AppText.body,
                        color = palette.text3,
                    )
                }
                BasicTextField(
                    value = text,
                    onValueChange = { text = it },
                    textStyle = AppText.body.copy(color = palette.text),
                    cursorBrush = SolidColor(palette.primary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .testTag("batch_input"),
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        // ---------- 复制 AI 提示词 ----------
        QingJiChip(
            label = if (copied) "已复制，去粘给 AI 吧" else "📋 复制 AI 提示词",
            onClick = {
                haptics.perform(HapticCue.Tap)
                copied = true
            },
            container = palette.surface2,
            contentColor = palette.text2,
            testTag = "batch_copy_prompt",
        )

        Spacer(Modifier.height(16.dp))

        // ---------- 未指定日期默认 ----------
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("未指定日期默认", style = AppText.sub, color = palette.text2)
            Spacer(Modifier.size(10.dp))
            Box(
                Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(palette.surface2)
                    .padding(horizontal = 12.dp, vertical = 7.dp),
            ) {
                Text(
                    DateUtils.toKey(defaultDate),
                    style = AppText.body.copy(fontWeight = FontWeight.SemiBold),
                    color = palette.text,
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("今天" to 0L, "明天" to 1L, "后天" to 2L).forEach { (label, offset) ->
                val target = LocalDate.now().plusDays(offset)
                // 选中态（主色底 + onPrimary 字）跟 QingJiChip 的 selected 正好一致
                QingJiChip(
                    label = label,
                    selected = target == defaultDate,
                    onClick = {
                        haptics.perform(HapticCue.Tap)
                        onDefaultDateChange(target)
                    },
                    testTag = "batch_date_$label",
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        Text(
            "解析为：${DateUtils.toKey(defaultDate)} · 未写日期的行将用这一天",
            style = AppText.sub,
            color = palette.text3,
            fontSize = 11.sp,
        )

        Spacer(Modifier.height(6.dp))
        Text(
            "支持：HH:mm 时间 · 30分钟/1小时 持续 · 9/1 或 8月25日 日期 · 明天/今天/周X · " +
                "@工作@学习… · 重要 · 每天/每周X/每月X日",
            style = AppText.sub,
            color = palette.text3,
            fontSize = 11.sp,
        )

        // ---------- 解析预览 ----------
        if (parsed.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Text(
                "解析结果 · ${parsed.count { it.isOk }} 条可用",
                style = AppText.cardTitle,
                color = palette.text,
                modifier = Modifier.testTag("batch_preview"),
            )
            Spacer(Modifier.height(8.dp))
            QingJiCard(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(12.dp),
            ) {
                parsed.forEach { line ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            if (line.isOk) "✓" else "✕",
                            color = if (line.isOk) palette.primary else palette.danger,
                            modifier = Modifier.padding(end = 8.dp),
                        )
                        Column {
                            Text(
                                if (line.isOk) line.title else line.raw,
                                style = AppText.body,
                                color = palette.text,
                            )
                            Text(
                                describeLine(line),
                                style = AppText.sub,
                                color = if (line.isOk) palette.text2 else palette.danger,
                                fontSize = 11.sp,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            // 「全部录入」是通栏 CTA（文字居中、整宽点击区），不是胶囊标签 —— 所以不并进
            // QingJiChip（那会把居中的文字带到左边），但按压反馈仍按家规换成 dimIndication。
            val canApply = parsed.any { it.isOk }
            val applyShape = RoundedCornerShape(Dimens.shapeFull.dp)
            val applyInteraction = remember { MutableInteractionSource() }
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(applyShape)
                    .background(if (canApply) palette.primary else palette.surface2)
                    .clickable(
                        interactionSource = applyInteraction,
                        indication = dimIndication(applyShape),
                        enabled = canApply,
                    ) {
                        haptics.perform(HapticCue.Commit)
                        onApply(parsed.filter { it.isOk }, tab)
                    }
                    .padding(vertical = 14.dp)
                    .testTag("batch_apply"),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "全部录入（${parsed.count { it.isOk }} 条）",
                    style = AppText.body.copy(fontWeight = FontWeight.Bold),
                    color = if (canApply) palette.scheme.onPrimary else palette.text3,
                )
            }
        }
    }
}

/** 一行解析结果的人话描述 */
private fun describeLine(line: ParsedLine): String {
    if (!line.isOk) return line.error ?: "解析失败"
    val parts = mutableListOf<String>()
    if (line.isPlan) {
        parts.add(
            when (line.cadence?.name) {
                "DAILY" -> "每天"
                "WEEKLY" -> "每周" + line.weekdays.joinToString("") { "一二三四五六日"[it].toString() }
                "MONTHLY" -> "每月${line.dayOfMonth}日"
                else -> "重复"
            }
        )
    } else {
        parts.add("${line.date.monthValue}月${line.date.dayOfMonth}日")
    }
    line.time?.let { parts.add(it) }
    line.durationMinutes?.let { parts.add(com.qingji.plan.util.TimeUtils.formatDuration(it)) }
    parts.add("分类:${line.category}")
    if (line.priority.name == "HIGH") parts.add("重要")
    return parts.joinToString(" · ")
}
