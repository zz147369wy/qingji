package com.qingji.plan.ui.today

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.size

/**
 * 今日页分组的自绘图标。
 *
 * 【2026-10 鲸鱼主题】原来是 🌙 凌晨 / 🕊 随时 / 📌 长期计划 这种 emoji ——
 * emoji 的样子随系统字体变，而且是彩色的，跟墨线描边的画风打架。
 * 这里每个分组配一个「深度的意象」：
 *
 * | 分组 | 意象 |
 * |---|---|
 * | 已过时 | 搁浅（退潮的波纹 + 一个点） |
 * | 上午 | 日出 |
 * | 下午 | 珊瑚 |
 * | 晚上 | 月牙 |
 * | 凌晨 | 水母 |
 * | 随时 | 漂流瓶 |
 * | 长期计划 | 锚 |
 *
 * 全部用 24×24 画布、2dp 描边，和 Material 图标同一套参数，混在一起看不出来。
 */
object GroupIcons {
    private const val SIZE = 24f

    private fun icon(name: String, block: androidx.compose.ui.graphics.vector.PathBuilder.() -> Unit): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = SIZE,
            viewportHeight = SIZE,
        ).apply {
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                pathBuilder = block,
            )
        }.build()

    /** 已过时：退潮的波纹 + 搁浅的一个点 */
    val Stranded = icon("stranded") {
        moveTo(3f, 18f)
        curveTo(6f, 18f, 6f, 16f, 9f, 16f)
        curveTo(12f, 16f, 12f, 18f, 15f, 18f)
        curveTo(18f, 18f, 18f, 16f, 21f, 16f)
        moveTo(12f, 4f)
        verticalLineTo(11f)
        moveTo(12f, 13.5f)
        verticalLineTo(14f)
    }

    /** 上午：日出（半轮太阳 + 水平线 + 两道光线） */
    val Sunrise = icon("sunrise") {
        moveTo(7f, 13f)
        arcTo(5f, 5f, 0f, false, true, 17f, 13f)
        moveTo(12f, 3.5f)
        verticalLineTo(6f)
        moveTo(4.5f, 7.5f)
        lineTo(6f, 9f)
        moveTo(19.5f, 7.5f)
        lineTo(18f, 9f)
        moveTo(3f, 17f)
        curveTo(6f, 17f, 6f, 15f, 9f, 15f)
        curveTo(12f, 15f, 12f, 17f, 15f, 17f)
        curveTo(18f, 17f, 18f, 15f, 21f, 15f)
    }

    /** 下午：珊瑚（三根分叉） */
    val Coral = icon("coral") {
        moveTo(12f, 21f)
        verticalLineTo(14f)
        moveTo(12f, 14f)
        curveTo(12f, 11f, 8f, 11f, 8f, 7f)
        moveTo(12f, 14f)
        curveTo(12f, 12f, 16f, 12f, 16f, 8f)
        moveTo(8f, 7f)
        verticalLineTo(4f)
        moveTo(16f, 8f)
        verticalLineTo(5f)
        moveTo(12f, 21f)
        horizontalLineTo(8f)
        moveTo(12f, 21f)
        horizontalLineTo(16f)
    }

    /** 晚上：月牙 */
    val Moon = icon("moon") {
        moveTo(20f, 14.5f)
        arcTo(8f, 8f, 0f, true, true, 9.5f, 4f)
        arcTo(6.5f, 6.5f, 0f, false, false, 20f, 14.5f)
        close()
    }

    /** 凌晨：水母（伞盖 + 三条飘带） */
    val Jellyfish = icon("jellyfish") {
        moveTo(5f, 12f)
        arcTo(7f, 7f, 0f, false, true, 19f, 12f)
        close()
        moveTo(8f, 12f)
        curveTo(8f, 15f, 7f, 17f, 8f, 20f)
        moveTo(12f, 12f)
        verticalLineTo(20f)
        moveTo(16f, 12f)
        curveTo(16f, 15f, 17f, 17f, 16f, 20f)
    }

    /** 随时：漂流瓶 */
    val Bottle = icon("bottle") {
        moveTo(10f, 3f)
        horizontalLineTo(14f)
        moveTo(10.5f, 3f)
        verticalLineTo(7f)
        lineTo(8f, 10f)
        verticalLineTo(20f)
        horizontalLineTo(16f)
        verticalLineTo(10f)
        lineTo(13.5f, 7f)
        verticalLineTo(3f)
        moveTo(8f, 14f)
        horizontalLineTo(16f)
    }

    /** 长期计划：锚 */
    val Anchor = icon("anchor") {
        moveTo(12f, 7f)
        arcTo(2f, 2f, 0f, true, true, 12f, 3f)
        arcTo(2f, 2f, 0f, true, true, 12f, 7f)
        close()
        moveTo(12f, 7f)
        verticalLineTo(21f)
        moveTo(8f, 11f)
        horizontalLineTo(16f)
        moveTo(4f, 14f)
        arcTo(8f, 8f, 0f, false, false, 20f, 14f)
    }

    /** 重要任务的星。填充色由调用处给（胸针金），这里只画形状 */
    val Star = icon("star") {
        moveTo(12f, 3f)
        lineTo(14.7f, 8.6f)
        lineTo(20.8f, 9.5f)
        lineTo(16.4f, 13.8f)
        lineTo(17.4f, 19.9f)
        lineTo(12f, 17f)
        lineTo(6.6f, 19.9f)
        lineTo(7.6f, 13.8f)
        lineTo(3.2f, 9.5f)
        lineTo(9.3f, 8.6f)
        close()
    }

    fun of(icon: GroupIcon): ImageVector = when (icon) {
        GroupIcon.STRANDED -> Stranded
        GroupIcon.SUNRISE -> Sunrise
        GroupIcon.CORAL -> Coral
        GroupIcon.MOON -> Moon
        GroupIcon.JELLYFISH -> Jellyfish
        GroupIcon.BOTTLE -> Bottle
        GroupIcon.ANCHOR -> Anchor
    }
}

/**
 * 「重要」的星：胸针金实心 + 墨线描边（设计稿 `.row .title .star`）。
 * [GroupIcons.Star] 只是线框，压在深色行上看不出是金色的，所以单独画一颗实心的。
 */
@androidx.compose.runtime.Composable
fun StickerStar(
    modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier,
    size: androidx.compose.ui.unit.Dp = 15.dp,
    fill: Color = com.qingji.plan.ui.theme.LocalPalette.current.gold,
    stroke: Color = com.qingji.plan.ui.theme.LocalPalette.current.ink,
) {
    androidx.compose.foundation.Canvas(modifier.then(androidx.compose.ui.Modifier.size(size))) {
        val k = this.size.minDimension / 24f
        val pts = listOf(
            12f to 3f, 14.7f to 8.6f, 20.8f to 9.5f, 16.4f to 13.8f, 17.4f to 19.9f,
            12f to 17f, 6.6f to 19.9f, 7.6f to 13.8f, 3.2f to 9.5f, 9.3f to 8.6f,
        )
        val path = androidx.compose.ui.graphics.Path().apply {
            pts.forEachIndexed { i, (x, y) -> if (i == 0) moveTo(x * k, y * k) else lineTo(x * k, y * k) }
            close()
        }
        drawPath(path, fill)
        drawPath(
            path, stroke,
            style = androidx.compose.ui.graphics.drawscope.Stroke(
                width = 1.5f * k, join = StrokeJoin.Round,
            ),
        )
    }
}
