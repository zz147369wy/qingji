package com.qingji.plan.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * 原站用的是 lucide 图标集，Material 里没有对应形状的，在这里自己画。
 *
 * Material 里**有**对应形状的就直接用 `Icons.Outlined.*`（编辑=Edit、删除=Delete、
 * 重复=Repeat 这些都对得上），只有下面这些对不上，才值得单独建一个矢量。
 */
object AppIcons {

    /**
     * 橡皮擦。原站 AI 页右上角那个「清空聊天历史」按钮用的就是它
     * （`扒站/app.js`：`Q1=_e("Eraser")`）。
     *
     * Material 的图标集里**没有橡皮擦**，最接近的 `Delete` 是个垃圾桶 ——
     * 跟参考截图里那个斜着的橡皮差得太远，所以按 lucide 的原始路径重画。
     *
     * lucide 是 24×24 的**描边**图标（不是实心），所以这里走 `stroke` 而不是 `fill`。
     * 三条子路径的坐标是照原样翻译过来的（原站那三条 `<path>` 各自从 (0,0) 起算，
     * 合到一个 PathBuilder 里就要把相对坐标全算成绝对坐标）。
     */
    val Eraser: ImageVector by lazy {
        ImageVector.Builder(
            name = "Eraser",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                // 颜色随便给个黑的即可 —— `Icon(...)` 的 tint 会在渲染结果上整体替换颜色，
                // 描边一样会被替换掉
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                // m7 21-4.3-4.3c-1-1-1-2.5 0-3.4l9.6-9.6c1-1 2.5-1 3.4 0l5.6 5.6c1 1 1 2.5 0 3.4L13 21
                moveTo(7f, 21f)
                lineTo(2.7f, 16.7f)
                curveTo(1.7f, 15.7f, 1.7f, 14.2f, 2.7f, 13.3f)
                lineTo(12.3f, 3.7f)
                curveTo(13.3f, 2.7f, 14.8f, 2.7f, 15.7f, 3.7f)
                lineTo(21.3f, 9.3f)
                curveTo(22.3f, 10.3f, 22.3f, 11.8f, 21.3f, 12.7f)
                lineTo(13f, 21f)

                // M22 21H7
                moveTo(22f, 21f)
                lineTo(7f, 21f)

                // m5 11 9 9
                moveTo(5f, 11f)
                lineTo(14f, 20f)
            }
        }.build()
    }

    // ---------------------------------------------------------------- 鲸鱼主题的线性图标
    //
    // 【2026-10】下面这些直接照设计稿 `设计稿/鲸鱼主题/app.js` 里的 SVG path 抄过来
    // （24×24、2dp 描边、圆头圆角），保证 App 和设计稿是同一套图标，而不是「Material 里找个像的」。

    /** 把 SVG 的 d 属性原样变成描边图标 */
    private fun svg(name: String, vararg d: String): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            d.forEach { data ->
                addPath(
                    pathData = addPathNodes(data),
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
        }.build()

    /** SVG `<circle>` → path */
    private fun circle(cx: Float, cy: Float, r: Float) =
        "M${cx - r} ${cy}a$r $r 0 1 0 ${2 * r} 0a$r $r 0 1 0 ${-2 * r} 0"

    /** SVG `<rect rx>` → path */
    private fun rect(x: Float, y: Float, w: Float, h: Float, rx: Float) =
        "M${x + rx} ${y}h${w - 2 * rx}a$rx $rx 0 0 1 $rx $rx" +
            "v${h - 2 * rx}a$rx $rx 0 0 1 ${-rx} $rx" +
            "h${-(w - 2 * rx)}a$rx $rx 0 0 1 ${-rx} ${-rx}" +
            "v${-(h - 2 * rx)}a$rx $rx 0 0 1 $rx ${-rx}z"

    val Trophy by lazy { svg("trophy", "M8 4h8v5a4 4 0 0 1-8 0zM8 6H5a3 3 0 0 0 3 4M16 6h3a3 3 0 0 1-3 4M12 13v4M8 20h8") }
    val Trend by lazy { svg("trend", "M3 17l6-6 4 4 8-8M15 7h6v6") }
    val Shell by lazy { svg("shell", "M12 20c-5 0-8-4-8-8a8 8 0 0 1 16 0c0 4-3 8-8 8z", "M12 20V6M12 20l-5-11M12 20l5-11") }
    val Flag by lazy { svg("flag", "M5 21V4M5 4h12l-2 4 2 4H5") }
    val Fish by lazy { svg("fish", "M3 12c3-5 9-6 14-2l4-3v10l-4-3c-5 4-11 3-14-2z", circle(8f, 11f, 0.8f)) }
    val Settle by lazy { svg("settle", rect(5f, 4f, 14f, 17f, 3f), "M9 4V3h6v1M9 13l2 2 4-4") }
    val Tag by lazy { svg("tag", "M3 12V4h8l9 9-8 8z", circle(7.5f, 7.5f, 1.5f)) }
    val Table by lazy { svg("table", rect(4f, 4f, 16f, 16f, 3f), "M4 10h16M10 10v10") }
    val Bell by lazy { svg("bell", "M6 16V11a6 6 0 0 1 12 0v5l2 2H4zM10 21h4") }
    val Bot by lazy { svg("bot", rect(5f, 8f, 14f, 11f, 4f), "M12 4v4M9 13v1M15 13v1") }
    val Shield by lazy { svg("shield", "M12 3l8 3v6c0 5-3.5 8-8 9-4.5-1-8-4-8-9V6z") }
    val Box by lazy { svg("box", "M4 8l8-4 8 4v8l-8 4-8-4zM4 8l8 4 8-4M12 12v8") }
    val Layers by lazy { svg("layers", "M12 3l9 5-9 5-9-5zM3 13l9 5 9-5") }
    val Heart by lazy { svg("heart", "M12 20s-8-5-8-11a4.5 4.5 0 0 1 8-2.8A4.5 4.5 0 0 1 20 9c0 6-8 11-8 11z") }
    val Clock by lazy { svg("clock", circle(12f, 12f, 8.5f), "M12 7.5V12l3 2") }
    val Broom by lazy { svg("broom", "M14 4l6 6M17 7l-7 7M4 20c0-4 2-7 6-6 1 4-2 6-6 6z") }
    val Image by lazy { svg("image", rect(3.5f, 5f, 17f, 14f, 3f), circle(9f, 10f, 1.6f), "M20 16l-5-5-8 8") }
    val Send by lazy { svg("send", "M5 12h12M12 6l6 6-6 6") }
    val Back by lazy { svg("back", "M15 5l-7 7 7 7") }
}
