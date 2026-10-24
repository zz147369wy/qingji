package com.qingji.plan.ui.theme

import androidx.compose.ui.graphics.Color
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cbrt
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.sin

/**
 * 在 OKLCH 空间里混色 —— 等价于 CSS 的 `color-mix(in oklch, a p%, b)`。
 *
 * 【2026-10 鲸鱼主题】设计稿（`d:\新日程软件\设计稿\鲸鱼主题\theme.css`）里
 * 所有颜色都是从一个主色用 color-mix 推出来的。这里用同一套公式，
 * 保证 App 和设计稿的颜色逐个对得上，而不是凭感觉再调一遍。
 *
 * 为什么是 OKLCH 而不是 RGB 线性插值：RGB 里「蓝 + 白」中间会发灰发紫，
 * OKLCH 里色相保持不动、只有明度和彩度在变，浅色底看起来才是「同一个蓝的浅版」。
 *
 * **纯函数**，零 Compose 运行时依赖，单元测试可以直接跑。
 */
object ColorMix {

    /**
     * 返回 `a * p + b * (1 - p)`（OKLCH 空间）。
     *
     * 色相取最短弧插值。白、黑、灰这种「没有色相」的颜色（彩度≈0），
     * 按 CSS 规范把色相视为缺失、直接取另一方的 —— 否则「蓝混白」会被拉向 0° 的红。
     */
    fun mix(a: Color, p: Float, b: Color): Color {
        val la = toOklch(a)
        val lb = toOklch(b)
        val t = p.coerceIn(0f, 1f)

        val achromaticA = la[1] < ACHROMATIC
        val achromaticB = lb[1] < ACHROMATIC
        val ha = if (achromaticA) lb[2] else la[2]
        val hb = if (achromaticB) la[2] else lb[2]

        var dh = hb - ha
        if (dh > 180f) dh -= 360f
        if (dh < -180f) dh += 360f

        val l = la[0] * t + lb[0] * (1 - t)
        val c = la[1] * t + lb[1] * (1 - t)
        // 从 a 往 b 走 (1 - t) 的比例
        val h = ha + dh * (1 - t)
        return fromOklch(l, c, h)
    }

    /** sRGB → OKLCH，返回 [L(0..1), C, H(度)] */
    fun toOklch(color: Color): FloatArray {
        val lab = toOklab(color)
        val c = hypot(lab[1], lab[2])
        var h = Math.toDegrees(atan2(lab[2], lab[1]).toDouble()).toFloat()
        if (h < 0) h += 360f
        return floatArrayOf(lab[0], c, h)
    }

    /**
     * OKLCH → sRGB。
     *
     * 超出 sRGB 色域时，按 CSS Color 4 的思路**保明度和色相、降彩度**（二分查找），
     * 而不是直接截断三通道 —— 截断会让色相偏掉。
     */
    fun fromOklch(l: Float, c: Float, h: Float): Color {
        val rad = Math.toRadians(h.toDouble())
        val cosH = cos(rad).toFloat()
        val sinH = sin(rad).toFloat()

        fun rgbAt(chroma: Float) = oklabToLinear(l, chroma * cosH, chroma * sinH)
        fun inGamut(rgb: FloatArray) = rgb.all { it in -GAMUT_EPS..1f + GAMUT_EPS }

        var rgb = rgbAt(c)
        if (!inGamut(rgb)) {
            var lo = 0f
            var hi = c
            repeat(24) {
                val mid = (lo + hi) / 2
                if (inGamut(rgbAt(mid))) lo = mid else hi = mid
            }
            rgb = rgbAt(lo)
        }
        return Color(
            red = linearToSrgb(rgb[0]),
            green = linearToSrgb(rgb[1]),
            blue = linearToSrgb(rgb[2]),
            alpha = 1f,
        )
    }

    // ---------------------------------------------------------------------
    // OKLab（Björn Ottosson, 2020）
    // ---------------------------------------------------------------------

    private fun toOklab(color: Color): FloatArray {
        val r = srgbToLinear(color.red)
        val g = srgbToLinear(color.green)
        val b = srgbToLinear(color.blue)

        val l = cbrt(0.4122214708f * r + 0.5363325363f * g + 0.0514459929f * b)
        val m = cbrt(0.2119034982f * r + 0.6806995451f * g + 0.1073969566f * b)
        val s = cbrt(0.0883024619f * r + 0.2817188376f * g + 0.6299787005f * b)

        return floatArrayOf(
            0.2104542553f * l + 0.7936177850f * m - 0.0040720468f * s,
            1.9779984951f * l - 2.4285922050f * m + 0.4505937099f * s,
            0.0259040371f * l + 0.7827717662f * m - 0.8086757660f * s,
        )
    }

    private fun oklabToLinear(lightness: Float, a: Float, b: Float): FloatArray {
        val l = (lightness + 0.3963377774f * a + 0.2158037573f * b).pow(3)
        val m = (lightness - 0.1055613458f * a - 0.0638541728f * b).pow(3)
        val s = (lightness - 0.0894841775f * a - 1.2914855480f * b).pow(3)
        return floatArrayOf(
            4.0767416621f * l - 3.3077115913f * m + 0.2309699292f * s,
            -1.2684380046f * l + 2.6097574011f * m - 0.3413193965f * s,
            -0.0041960863f * l - 0.7034186147f * m + 1.7076147010f * s,
        )
    }

    private fun srgbToLinear(c: Float): Float =
        if (c <= 0.04045f) c / 12.92f else ((c + 0.055f) / 1.055f).pow(2.4f)

    private fun linearToSrgb(c: Float): Float {
        val v = c.coerceIn(0f, 1f)
        val out = if (v <= 0.0031308f) v * 12.92f else 1.055f * v.pow(1f / 2.4f) - 0.055f
        return out.coerceIn(0f, 1f)
    }

    /** 彩度低于它就当作「没有色相」（白/黑/灰） */
    private const val ACHROMATIC = 0.0005f

    /** 色域判定的容差，吸收浮点误差 */
    private const val GAMUT_EPS = 0.0001f

    /** 两色是否几乎相同（每通道差 < 1/255）。测试用 */
    fun near(a: Color, b: Color, tolerance: Float = 1.5f / 255f): Boolean =
        abs(a.red - b.red) < tolerance && abs(a.green - b.green) < tolerance && abs(a.blue - b.blue) < tolerance
}

/** `color-mix(in oklch, this p, other)` 的简写 */
fun Color.mixWith(other: Color, p: Float): Color = ColorMix.mix(this, p, other)
