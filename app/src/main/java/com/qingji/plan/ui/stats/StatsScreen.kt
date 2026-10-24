package com.qingji.plan.ui.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.qingji.plan.ui.components.AppIcons
import com.qingji.plan.ui.components.sticker
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.ShowChart
import androidx.compose.material.icons.outlined.TrendingUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qingji.plan.data.model.Categories
import com.qingji.plan.data.model.Category
import com.qingji.plan.ui.components.PageHeader
import com.qingji.plan.ui.components.QingJiCard
import com.qingji.plan.ui.theme.AppText
import com.qingji.plan.ui.theme.Dimens
import com.qingji.plan.ui.theme.LocalPalette

/**
 * 统计页。
 *
 * 四块内容：四个指标卡、近 14 天趋势、类别占比、最近结算记录。
 * 底下一行汇总。文案照抄原站。
 */
@Composable
fun StatsScreen(
    state: StatsUiState,
    modifier: Modifier = Modifier,
    customs: List<Category> = emptyList(),
) {
    val palette = LocalPalette.current

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp)
            .padding(top = 16.dp, bottom = 30.dp)
            .testTag("stats_screen"),
    ) {
        PageHeader(title = "统计", subtitle = "完成趋势 · 类别占比 · 坚持打卡")

        Spacer(Modifier.height(16.dp))

        // ---------- 四个指标卡：图标 / 大数字 / 名称，竖排（设计稿 `.metric`） ----------
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetricCard(
                icon = AppIcons.Trophy,
                iconBg = palette.goldSoft,
                iconTint = if (palette.isDark) palette.gold else palette.goldDeep,
                value = state.totalDone.toString(),
                label = "累计完成",
                modifier = Modifier.weight(1f).testTag("stat_done"),
            )
            MetricCard(
                icon = AppIcons.Trend,
                iconBg = palette.primarySoft,
                iconTint = palette.primaryDeep,
                value = state.completionRate.toString(),
                unit = "%",
                label = "完成率",
                modifier = Modifier.weight(1f).testTag("stat_rate"),
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetricCard(
                icon = AppIcons.Shell,
                iconBg = palette.mintSoft,
                iconTint = palette.mint,
                value = state.averageScoreText,
                label = "平均分",
                modifier = Modifier.weight(1f).testTag("stat_avg"),
            )
            MetricCard(
                icon = AppIcons.Flag,
                iconBg = palette.coralSoft,
                iconTint = palette.coral,
                value = state.settleDays.toString(),
                label = "结算天数",
                modifier = Modifier.weight(1f).testTag("stat_days"),
            )
        }

        Spacer(Modifier.height(14.dp))

        // ---------- 近 14 天趋势：泡泡堆 ----------
        QingJiCard(modifier = Modifier.fillMaxWidth()) {
            CardHead(AppIcons.Trend, "近 ${StatsCalculator.TREND_DAYS} 天完成趋势", badge = "泡泡堆 · 一颗 = 一件")
            TrendChart(state = state, modifier = Modifier.testTag("stat_trend"))
        }

        Spacer(Modifier.height(14.dp))

        // ---------- 类别占比 · 鱼群 ----------
        QingJiCard(modifier = Modifier.fillMaxWidth()) {
            CardHead(AppIcons.Fish, "类别占比 · 鱼群")
            if (state.categoryShares.isEmpty()) {
                Text("还没有任务", style = AppText.sub, color = palette.text3)
            } else {
                // 鱼的长度按**最大的那一类**铺满，而不是按 100% —— 五类平分时每条鱼都只有一截，看不出谁多谁少
                val maxPercent = state.categoryShares.maxOf { it.percent }.coerceAtLeast(1)
                state.categoryShares.forEach { share ->
                    FishRow(
                        label = share.label,
                        color = Categories.of(share.categoryId, customs).color,
                        fraction = share.percent / maxPercent.toFloat(),
                        value = "${share.count} · ${share.percent}%",
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        // ---------- 最近结算 ----------
        QingJiCard(modifier = Modifier.fillMaxWidth()) {
            CardHead(AppIcons.Settle, "最近结算")
            if (state.recentReviews.isEmpty()) {
                Text(
                    "回到「今日」页点「每日结算」即可开始打分记录",
                    style = AppText.sub,
                    color = palette.text3,
                )
            } else {
                state.recentReviews.take(10).forEachIndexed { i, review ->
                    SettleRow(review, first = i == 0)
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // ---------- 底部汇总 ----------
        Text(
            "任务总数 ${state.totalTasks} · 长期计划 ${state.totalPlans} 项",
            style = AppText.sub,
            color = palette.text3,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("stat_footer"),
        )
    }
}

// ---------------------------------------------------------------- 卡片标题

/** 卡片标题行：`[图标] 标题            [小徽章]`（设计稿 `.card-h`） */
@Composable
private fun CardHead(icon: ImageVector, title: String, badge: String? = null) {
    val palette = LocalPalette.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = palette.text, modifier = Modifier.size(22.dp))
        Text(
            title,
            style = AppText.cardTitle.copy(fontWeight = FontWeight.ExtraBold),
            color = palette.text,
            modifier = Modifier
                .padding(start = 8.dp)
                .weight(1f),
        )
        if (badge != null) {
            Text(
                badge,
                style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold),
                color = palette.primaryDeep,
                modifier = Modifier
                    .background(palette.primarySoft, RoundedCornerShape(999.dp))
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            )
        }
    }
}

// ---------------------------------------------------------------- 指标卡

@Composable
private fun MetricCard(
    icon: ImageVector,
    iconBg: Color,
    iconTint: Color,
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    unit: String? = null,
) {
    val palette = LocalPalette.current
    QingJiCard(modifier = modifier, contentPadding = PaddingValues(14.dp)) {
        Box(
            Modifier
                .size(36.dp)
                .sticker(shape = RoundedCornerShape(12.dp), fill = iconBg, drop = 0.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(19.dp))
        }
        Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.Bottom) {
            Text(
                value,
                style = AppText.numLarge.copy(fontSize = 26.sp, lineHeight = 26.sp),
                color = palette.text,
            )
            if (unit != null) {
                Text(
                    unit,
                    style = AppText.numSmall.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold),
                    color = palette.text,
                    modifier = Modifier.padding(start = 2.dp, bottom = 2.dp),
                )
            }
        }
        Text(
            label,
            style = TextStyle(fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold),
            color = palette.text2,
            modifier = Modifier.padding(top = 10.dp),
        )
    }
}

// ---------------------------------------------------------------- 鱼群

/**
 * 一条鱼：`工作  [◉━━━━━━━━◁      ]  34 · 32%`
 *
 * 鱼身是分类色的进度条（墨线描边，右端不描），左边一只白眼睛，右端拖一条鱼尾（设计稿 `.fish`）。
 */
@Composable
private fun FishRow(label: String, color: Color, fraction: Float, value: String) {
    val palette = LocalPalette.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold),
            color = palette.text,
            maxLines = 1,
            modifier = Modifier.width(52.dp),
        )
        Canvas(
            Modifier
                .padding(start = 8.dp, end = 8.dp)
                .weight(1f)
                .height(17.dp),
        ) {
            val trackH = 14.dp.toPx()
            val top = (size.height - trackH) / 2f
            val r = trackH / 2f
            // 轨道
            drawRoundRect(palette.surface2, topLeft = Offset(0f, top), size = Size(size.width, trackH), cornerRadius = CornerRadius(r))
            val tailW = 11.dp.toPx()
            val bodyW = ((size.width - tailW) * fraction.coerceIn(0f, 1f)).coerceAtLeast(r * 2)
            val stroke = 1.5.dp.toPx()
            // 鱼尾：一个燕尾三角，挂在鱼身右端（设计稿 clip-path: polygon(0 50%, 100% 0, 75% 50%, 100% 100%)）
            val tail = Path().apply {
                val x0 = bodyW - 1f
                moveTo(x0, size.height / 2f)
                lineTo(x0 + tailW + 1f, 0f)
                lineTo(x0 + (tailW + 1f) * 0.75f, size.height / 2f)
                lineTo(x0 + tailW + 1f, size.height)
                close()
            }
            drawPath(tail, color)
            // 鱼身：左边圆头、右边平切
            val body = Path().apply {
                addRoundRect(
                    RoundRect(
                        left = 0f, top = top, right = bodyW, bottom = top + trackH,
                        topLeftCornerRadius = CornerRadius(r), bottomLeftCornerRadius = CornerRadius(r),
                    ),
                )
            }
            drawPath(body, color)
            // 描边：上、左（圆头）、下三边
            val outline = Path().apply {
                moveTo(bodyW, top + stroke / 2)
                lineTo(r, top + stroke / 2)
                arcTo(Rect(stroke / 2, top + stroke / 2, trackH - stroke / 2, top + trackH - stroke / 2), 270f, -180f, false)
                lineTo(bodyW, top + trackH - stroke / 2)
            }
            drawPath(outline, palette.ink, style = Stroke(width = stroke))
            // 眼睛
            val eye = Offset(5.dp.toPx() + 2.dp.toPx(), top + 3.dp.toPx() + 2.dp.toPx())
            drawCircle(palette.ink, radius = 3.dp.toPx(), center = eye)
            drawCircle(Color.White, radius = 2.dp.toPx(), center = eye)
        }
        Text(
            value,
            style = AppText.numSmall.copy(fontSize = 12.sp),
            color = palette.text2,
            textAlign = TextAlign.End,
            maxLines = 1,
            modifier = Modifier.width(64.dp),
        )
    }
}

// ---------------------------------------------------------------- 结算记录

/** 一条结算：`(88)  10/4 / 节奏很稳，长期计划全勤`，条与条之间一道虚线（设计稿 `.settle-row`） */
@Composable
private fun SettleRow(review: com.qingji.plan.data.db.entity.ReviewEntity, first: Boolean) {
    val palette = LocalPalette.current
    val date = remember(review.date) {
        com.qingji.plan.util.DateUtils.fromKey(review.date)?.let { "${it.monthValue}/${it.dayOfMonth}" } ?: review.date
    }
    Row(
        Modifier
            .fillMaxWidth()
            .then(
                if (first) {
                    Modifier
                } else {
                    Modifier.drawBehind {
                        val w = 1.5.dp.toPx()
                        drawLine(
                            palette.line,
                            Offset(0f, w / 2),
                            Offset(size.width, w / 2),
                            strokeWidth = w,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())),
                        )
                    }
                }
            )
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(42.dp)
                .sticker(shape = CircleShape, fill = palette.goldSoft, drop = 0.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                review.score.toString(),
                style = AppText.numMedium.copy(fontSize = 16.sp, fontWeight = FontWeight.Bold),
                color = if (palette.isDark) palette.gold else palette.goldDeep,
            )
        }
        Column(Modifier.padding(start = 12.dp)) {
            Text(date, style = AppText.numSmall.copy(fontWeight = FontWeight.Bold), color = palette.text)
            Text(
                review.feedback.ifBlank { review.summary },
                style = AppText.sub,
                color = palette.text2,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

// 趋势图见 TrendChart.kt（泡泡堆）
