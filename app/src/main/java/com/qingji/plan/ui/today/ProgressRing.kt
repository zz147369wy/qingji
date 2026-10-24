package com.qingji.plan.ui.today

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Text
import com.qingji.plan.ui.theme.AppText
import com.qingji.plan.ui.theme.LocalPalette

/**
 * 进度环。
 *
 * 参数取自原站的 bw 组件：
 *   size = 92（直径）, stroke = 9（环粗）, 从 -90° 起画, 圆头笔帽,
 *   底环用 --surface-2, 进度环用 --primary
 *
 * ⚠️ 环中心显示的是**百分比数字**（如 `33`）+ 小字「% 完成」，
 * 不是「已完成/总数」。原站源码里写的就是 `{Math.round(percent)}` 和 `"% 完成"`。
 */
@Composable
fun ProgressRing(
    percent: Int,
    modifier: Modifier = Modifier,
    size: Float = 92f,
    strokeWidth: Float = 9f,
) {
    val palette = LocalPalette.current
    val safePercent = percent.coerceIn(0, 100)

    Box(
        modifier = modifier
            .size(size.dp)
            .testTag("progress_ring"),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(size.dp)) {
            val stroke = strokeWidth.dp.toPx()
            val diameter = this.size.minDimension - stroke
            val topLeft = Offset(
                (this.size.width - diameter) / 2f,
                (this.size.height - diameter) / 2f,
            )
            val arcSize = Size(diameter, diameter)

            // 底环
            drawArc(
                color = palette.surface2,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke),
            )

            // 进度环：从正上方（-90°）开始顺时针
            if (safePercent > 0) {
                drawArc(
                    color = palette.primary,
                    startAngle = -90f,
                    sweepAngle = 360f * safePercent / 100f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = safePercent.toString(),
                style = AppText.ringPct,
                color = palette.primaryDeep,
                fontWeight = FontWeight.ExtraBold,
            )
            Text(
                text = "% 完成",
                style = AppText.ringUnit,
                color = palette.text3,
            )
        }
    }
}
