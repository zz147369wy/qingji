package com.qingji.plan.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.qingji.plan.R

/**
 * 数字字体：Fredoka（Google Fonts，SIL Open Font License 1.1）。
 *
 * 【2026-10 鲸鱼主题】只给数字、时间、百分比用 —— 圆头圆脑，和卡通描边贴纸是一路的。
 * 中文继续用系统字体：中文字库动辄几 MB，而 vivo 的系统字体本身就够好看。
 * 三个静态字重共约 145KB。
 */
val NumberFont = FontFamily(
    Font(R.font.fredoka_medium, FontWeight.Medium),
    Font(R.font.fredoka_semibold, FontWeight.SemiBold),
    Font(R.font.fredoka_bold, FontWeight.Bold),
)

/**
 * 全局色板。任何界面都从这里取色，**不允许在页面里硬编码颜色**。
 *
 * 用 CompositionLocal 是为了让「换主题」只改这一个值，整个 App 自动重绘。
 */
val LocalPalette = staticCompositionLocalOf { DefaultPalette }

/**
 * 字号表。
 *
 * 【2026-10 重设计】原来是「逐字照抄原站 CSS」，现在是按 M3 Expressive 的层级重排：
 * 大字号更重更紧（ExtraBold + 负字距），小字号保持可读，层级跨度拉大。
 *
 * 字段名一个没删 —— 全项目有 72 处绕过 AppText 直接写 fontSize 的地方，
 * 那些留给后续阶段收拾，这一轮先保证用 AppText 的地方自动变。
 */
object AppText {
    /** 超大号：进度环百分比这类主角数字 */
    val display = TextStyle(
        fontSize = 40.sp,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = (-1).sp,
        lineHeight = 44.sp,
    )

    /** 页面标题 */
    val title = TextStyle(
        fontSize = 26.sp,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = (-0.6).sp,
        lineHeight = 32.sp,
    )

    /** 区块标题 / 弹层标题 */
    val headline = TextStyle(
        fontSize = 19.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.2).sp,
    )

    /** 卡片标题 */
    val cardTitle = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold)

    /** 正文 */
    val body = TextStyle(fontSize = 15.sp, lineHeight = 22.sp)

    /** 副标题 / 说明文字 */
    val sub = TextStyle(fontSize = 13.sp, lineHeight = 19.sp)

    /** 胶囊文字 */
    val chip = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium)

    /** 底部导航文字 */
    val navItem = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium)

    /** 时间轴整点刻度 */
    val hourLabel = TextStyle(fontSize = 11.sp)

    /** 任务块里的时间 */
    val blockTime = TextStyle(fontSize = 10.sp)

    /** 进度环百分比 */
    val ringPct = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)

    // ---- 数字（Fredoka）----

    /** 大数字：舷窗百分比、统计指标 */
    val numLarge = TextStyle(fontFamily = NumberFont, fontSize = 28.sp, fontWeight = FontWeight.Bold, lineHeight = 30.sp)

    /** 中数字：日期、月份 */
    val numMedium = TextStyle(fontFamily = NumberFont, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)

    /** 小数字：任务行时间、计数 */
    val numSmall = TextStyle(fontFamily = NumberFont, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)

    /** 迷你数字：日历格、坐标轴 */
    val numTiny = TextStyle(fontFamily = NumberFont, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)

    /** 进度环单位 */
    val ringUnit = TextStyle(fontSize = 11.sp)

    /** 小标签（徽章、角标） */
    val label = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium)
}

/**
 * 给 M3 自带组件用的字体表。
 *
 * 这个 app 自己的界面一律走 [AppText]，但 Switch / AlertDialog / DropdownMenu /
 * TextField 这些框架组件读的是 MaterialTheme.typography —— 不映射的话它们
 * 会用系统默认字号，跟页面里的字对不上。
 */
private val AppTypography = Typography().let { base ->
    base.copy(
        titleLarge = AppText.title,
        titleMedium = AppText.headline,
        titleSmall = AppText.cardTitle,
        bodyLarge = AppText.body,
        bodyMedium = AppText.body,
        bodySmall = AppText.sub,
        labelLarge = AppText.chip,
        labelMedium = AppText.navItem,
        labelSmall = AppText.label,
    )
}

/**
 * 把「鲸鱼配色 + 明暗模式」解析成色板。
 *
 * 「跟随系统」要读系统明暗，只能在 Composable 层解析；另外两种模式与系统无关。
 */
@Composable
fun resolvePalette(schemeId: String?, mode: ThemeMode): ThemePalette {
    val dark = when (mode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    return whalePalette(schemeId, dark)
}

@Composable
fun QingJiTheme(
    palette: ThemePalette = DefaultPalette,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalPalette provides palette) {
        MaterialTheme(
            colorScheme = palette.scheme,
            typography = AppTypography,
            content = content,
        )
    }
}
