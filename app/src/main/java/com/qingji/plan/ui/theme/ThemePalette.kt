package com.qingji.plan.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.qingji.plan.ui.pet.WHALE_SCHEMES
import com.qingji.plan.ui.pet.whaleSchemeOf

/**
 * 一套完整的主题配色。
 *
 * 【2026-10 鲸鱼主题「空空的海」】
 * 原来是 7 套各自手调的主题（暖纸/森林/樱粉……），现在改成**鲸鱼配色 = App 主题**：
 * 8 套鲸鱼配色 × 浅色/深色，一共 16 套，**全部由一个主色按固定公式推出来**
 * （公式与设计稿 `d:\新日程软件\设计稿\鲸鱼主题\theme.css` 逐条对应，见 [whalePalette]）。
 *
 * 配色全部取自鲸鱼本身：发色 = 主色，裙子藏青 = 墨线（描边 + 主文字），
 * 白蕾丝 = 卡片，胸针金 = 唯一强调色，腮红粉 = 庆祝。
 *
 * 旧属性名（primary / text2 / surface2 / warning …）一个没删，
 * 重设计前写下的几百处 palette.xxx 原样可用；新增的是贴纸风格要用的角色。
 */
data class ThemePalette(
    /** `"$scheme-light"` / `"$scheme-dark"`。圆形换肤动画靠它判断「换没换」 */
    val id: String,
    /** 显示名，设置页里给用户看 */
    val label: String,
    val isDark: Boolean,

    // ---- 墨线与文字 ----
    /** 墨线：贴纸描边 + 实色投影。浅色下也是主文字色（裙子藏青） */
    val ink: Color,
    val text: Color,
    val text2: Color,
    val text3: Color,

    // ---- 主色（发色） ----
    val primary: Color,
    /** 主色的深色变体：软底上的文字、选中态文字 */
    val primaryDeep: Color,
    /** 主色的浅色底 */
    val primarySoft: Color,
    val onPrimary: Color,

    // ---- 面 ----
    /** 页面底色 */
    val bg: Color,
    /** 卡片 = 白蕾丝 */
    val surface: Color,
    /** 次级底色（输入框、内嵌区块、轨道） */
    val surface2: Color,
    /** 细分割线（虚线、浅描边） */
    val line: Color,

    // ---- 强调 ----
    /** 胸针金：今天 / 重要 / AI。全 App 唯一的强调色 */
    val gold: Color,
    /** 金色上的文字、金色小字 */
    val goldDeep: Color,
    val goldSoft: Color,
    /** 珊瑚红：删除、已过时、节假日「休」 */
    val coral: Color,
    val coralSoft: Color,
    /** 海藻绿：长期计划、成功 */
    val mint: Color,
    val mintSoft: Color,
    /** 腮红粉：庆祝 */
    val blush: Color,

    /**
     * 海水深度色带，今日页分组用：
     * 0 浅滩（上午/已过时）· 1 珊瑚层（下午）· 2 暮光层（晚上）·
     * 3 午夜层（凌晨）· 4 漂流瓶（随时）· 5 洋流（长期计划）。
     * 3 起是深色带，上面的文字要用 [onDeep]。
     */
    val depths: List<Color>,
    val onDeep: Color,

    /** 舷窗的玻璃底（水面以上那部分）：浅色是极淡的主色，深色是近黑的深海 */
    val portholeGlass: Color,
    /** 舷窗里的百分比数字，以及包着它的一圈描边（让数字压在水面上也看得清） */
    val portholeText: Color,
    val portholeHalo: Color,

    /** 给 M3 框架组件（Switch / TextField / AlertDialog / DropdownMenu）用的完整方案 */
    val scheme: ColorScheme,
) {
    // ---------- 兼容旧接口的转发属性 ----------

    val danger: Color get() = coral

    /** 提醒/标注色（时长标签、节假日「班」） */
    val warning: Color get() = goldDeep
    val warningContainer: Color get() = goldSoft

    /** 肯定色（长期计划标签） */
    val success: Color get() = mint
    val successContainer: Color get() = mintSoft

    /** 柔和投影（极少数仍用 Modifier.shadow 的地方）。贴纸投影用 [ink] */
    val shadow: Color get() = Color.Black.copy(alpha = if (isDark) 0.35f else 0.13f)

    /** 悬浮 ＋ 的阴影色 */
    val fabShadow: Color get() = ink

    /** 背景纹理圆点（SubtleDots 背景样式用） */
    val dot: Color get() = line

    /**
     * 深度色带 i 是不是「深水」（凌晨/随时/长期）：文字反白、带往上飘的光斑。
     * 与明暗无关 —— 深色模式下文字本来就是亮的，但光斑仍只属于深水层（设计稿 `.band.deep`）
     */
    fun isDeep(depth: Int): Boolean = depth >= 3

    // ---------- 分类标签 ----------
    //
    // 分类色是数据（每个分类一个固定主色），但标签的底和字必须跟着明暗走：
    // 浅色下「浅底 + 深字」，深色下「暗底 + 亮字」。和设计稿 `.tag` 的公式一致。

    private val tagCache = HashMap<Long, Pair<Color, Color>>()

    private fun tagPair(c: Color): Pair<Color, Color> = synchronized(tagCache) {
        tagCache.getOrPut(c.value.toLong()) {
            if (isDark) {
                ColorMix.mix(c, 0.26f, Color(0xFF141A3A)) to ColorMix.mix(c, 0.60f, Color.White)
            } else {
                ColorMix.mix(c, 0.20f, Color.White) to ColorMix.mix(c, 0.55f, Color(0xFF1A1630))
            }
        }
    }

    /** 分类标签的底色 */
    fun tagBg(categoryColor: Color): Color = tagPair(categoryColor).first

    /** 分类标签的文字色 */
    fun tagFg(categoryColor: Color): Color = tagPair(categoryColor).second

    /**
     * 编辑弹层里**选中**的分类胶囊：明暗两档都是浅色马卡龙底 + 深字（设计稿 `.chip.cat.on`），
     * 在深色弹层上一眼就能看出选的是哪颗。
     */
    fun catChipBg(categoryColor: Color): Color = ColorMix.mix(categoryColor, 0.24f, Color.White)
    fun catChipFg(categoryColor: Color): Color = ColorMix.mix(categoryColor, 0.55f, Color(0xFF1A1630))
}

/** 明暗模式。存进设置表的是 [key] */
enum class ThemeMode(val key: String, val label: String) {
    LIGHT("light", "浅色海面"),
    DARK("dark", "深海夜"),
    SYSTEM("system", "跟随系统");

    companion object {
        fun of(key: String?): ThemeMode = entries.firstOrNull { it.key == key } ?: LIGHT
    }
}

/**
 * 按鲸鱼配色和明暗生成整套色板。
 *
 * 每一行都对应设计稿 theme.css 里的一个变量，`mix(a, p, b)` = `color-mix(in oklch, a p%, b)`。
 * 结果按 (scheme, dark) 缓存 —— 一共只有 16 种组合。
 *
 * **纯函数**，不依赖 Context，桌面小组件和单元测试都能直接调。
 */
fun whalePalette(schemeId: String?, dark: Boolean): ThemePalette {
    val ws = whaleSchemeOf(schemeId)
    return synchronized(paletteCache) {
        paletteCache.getOrPut(ws.id to dark) { buildPalette(ws.id, ws.label, Color(ws.appAccent), dark) }
    }
}

private val paletteCache = HashMap<Pair<String, Boolean>, ThemePalette>()

/** 全部 16 套。测试用来逐套检查对比度 */
val AllWhalePalettes: List<ThemePalette>
    get() = WHALE_SCHEMES.flatMap { listOf(whalePalette(it.id, false), whalePalette(it.id, true)) }

/** 默认：原版蓝 · 浅色 */
val DefaultPalette: ThemePalette get() = whalePalette(null, dark = false)

private fun mix(a: Color, p: Float, b: Color) = ColorMix.mix(a, p, b)

/** WCAG 对比度（1 ~ 21） */
internal fun contrastRatio(a: Color, b: Color): Float {
    val la = a.luminance()
    val lb = b.luminance()
    return (maxOf(la, lb) + 0.05f) / (minOf(la, lb) + 0.05f)
}

private val White = Color(0xFFFFFFFF)
private val Gold = Color(0xFFF5B83D)
private val Coral = Color(0xFFF2605C)
private val Mint = Color(0xFF22B893)
private val Blush = Color(0xFFFF9CB5)

private fun buildPalette(id: String, label: String, accent: Color, dark: Boolean): ThemePalette =
    if (!dark) {
        val ink = mix(accent, 0.22f, Color(0xFF121633))
        val primary = accent
        val primarySoft = mix(accent, 0.16f, White)
        val primaryDeep = mix(accent, 0.72f, ink)
        val surface2 = mix(accent, 0.07f, White)
        val line = mix(ink, 0.16f, White)
        val bg = mix(accent, 0.09f, White)
        val text2 = mix(ink, 0.72f, White)
        val text3 = mix(ink, 0.45f, White)
        val goldSoft = Color(0xFFFFF0C8)
        val coralSoft = Color(0xFFFFE1DE)
        val mintSoft = Color(0xFFD5F5EA)
        ThemePalette(
            id = "$id-light", label = label, isDark = false,
            ink = ink, text = ink, text2 = text2, text3 = text3,
            primary = primary, primaryDeep = primaryDeep, primarySoft = primarySoft, onPrimary = White,
            bg = bg, surface = White, surface2 = surface2, line = line,
            gold = Gold, goldDeep = Color(0xFF9A6400), goldSoft = goldSoft,
            coral = Coral, coralSoft = coralSoft, mint = Mint, mintSoft = mintSoft, blush = Blush,
            // 深度色带，与设计稿 --d0…--d5 逐条一致
            depths = listOf(
                mix(accent, 0.07f, White),
                mix(accent, 0.14f, White),
                mix(accent, 0.30f, White),
                mix(accent, 0.62f, Color(0xFF1A2A6A)),
                mix(accent, 0.38f, Color(0xFF0D1440)),
                mix(accent, 0.24f, Color(0xFF0A0F2E)),
            ),
            onDeep = Color(0xFFEEF3FF),
            portholeGlass = mix(accent, 0.06f, White),
            portholeText = ink,
            portholeHalo = White,
            scheme = lightColorScheme(
                primary = primary, onPrimary = White,
                primaryContainer = primarySoft, onPrimaryContainer = primaryDeep,
                inversePrimary = mix(accent, 0.6f, White),
                secondary = Mint, onSecondary = White,
                secondaryContainer = mintSoft, onSecondaryContainer = mix(Mint, 0.5f, ink),
                tertiary = Gold, onTertiary = ink,
                tertiaryContainer = goldSoft, onTertiaryContainer = Color(0xFF9A6400),
                error = Coral, onError = White,
                errorContainer = coralSoft, onErrorContainer = mix(Coral, 0.55f, ink),
                background = bg, onBackground = ink,
                surface = White, onSurface = ink,
                surfaceVariant = surface2, onSurfaceVariant = text2,
                surfaceTint = primary,
                inverseSurface = ink, inverseOnSurface = White,
                outline = ink, outlineVariant = line,
                scrim = Color.Black,
                surfaceBright = White, surfaceDim = mix(accent, 0.14f, White),
                surfaceContainerLowest = White,
                surfaceContainerLow = mix(accent, 0.04f, White),
                surfaceContainer = surface2,
                surfaceContainerHigh = bg,
                surfaceContainerHighest = mix(accent, 0.12f, White),
            ),
        )
    } else {
        // 墨线在深海夜下仍是**近黑**（照设计稿，PM 2026-10-05 拍板）：
        // 卡片比页面底色亮一档，黑边夹在两者之间，配上同色的实心投影，贴纸感才立得起来。
        // 之前试过改成亮边，整页发灰发平，被否了 —— 别再改回去。
        val ink = Color(0xFF04061A)
        val text = Color(0xFFEEF2FF)
        val text2 = mix(accent, 0.12f, Color(0xFFB9C2E6))
        val text3 = mix(accent, 0.10f, Color(0xFF7C86AE))
        val primary = mix(accent, 0.78f, White)
        val primaryDeep = mix(accent, 0.40f, White)
        val primarySoft = mix(accent, 0.34f, Color(0xFF141A3F))
        // 主色上的前景：设计稿是深海蓝 #0B1030。只有「纯黑」那套主色本身就是深灰，
        // 深字压上去看不清（对比度 3:1），那一套换成白字 —— 两个里谁对比度高用谁
        val onPrimary = listOf(Color(0xFF0B1030), White).maxBy { contrastRatio(it, primary) }
        val surface = mix(accent, 0.16f, Color(0xFF151A3A))
        val surface2 = mix(accent, 0.14f, Color(0xFF1D2450))
        val line = mix(accent, 0.18f, Color(0xFF2B3366))
        val bg = mix(accent, 0.14f, Color(0xFF0A0D24))
        val goldSoft = Color(0xFF4A3A14)
        val coralSoft = Color(0xFF4A1F2A)
        val mintSoft = Color(0xFF123F39)
        ThemePalette(
            id = "$id-dark", label = label, isDark = true,
            ink = ink, text = text, text2 = text2, text3 = text3,
            primary = primary, primaryDeep = primaryDeep, primarySoft = primarySoft, onPrimary = onPrimary,
            bg = bg, surface = surface, surface2 = surface2, line = line,
            gold = Gold, goldDeep = Gold, goldSoft = goldSoft,
            coral = Coral, coralSoft = coralSoft, mint = Mint, mintSoft = mintSoft, blush = Blush,
            depths = listOf(
                mix(accent, 0.16f, Color(0xFF0D1230)),
                mix(accent, 0.18f, Color(0xFF0B1029)),
                mix(accent, 0.20f, Color(0xFF090D22)),
                mix(accent, 0.22f, Color(0xFF070A1C)),
                mix(accent, 0.18f, Color(0xFF050716)),
                mix(accent, 0.12f, Color(0xFF040512)),
            ),
            onDeep = text,
            portholeGlass = Color(0xFF0B1030),
            portholeText = White,
            portholeHalo = Color(0xFF0B1030),
            scheme = darkColorScheme(
                primary = primary, onPrimary = onPrimary,
                primaryContainer = primarySoft, onPrimaryContainer = primaryDeep,
                inversePrimary = accent,
                secondary = Mint, onSecondary = ink,
                secondaryContainer = mintSoft, onSecondaryContainer = mix(Mint, 0.6f, White),
                tertiary = Gold, onTertiary = ink,
                tertiaryContainer = goldSoft, onTertiaryContainer = Gold,
                error = Coral, onError = White,
                errorContainer = coralSoft, onErrorContainer = mix(Coral, 0.6f, White),
                background = bg, onBackground = text,
                surface = surface, onSurface = text,
                surfaceVariant = surface2, onSurfaceVariant = text2,
                surfaceTint = primary,
                inverseSurface = text, inverseOnSurface = ink,
                outline = text3, outlineVariant = line,
                scrim = Color.Black,
                surfaceBright = surface2, surfaceDim = bg,
                surfaceContainerLowest = bg,
                surfaceContainerLow = mix(accent, 0.15f, Color(0xFF10142F)),
                surfaceContainer = surface,
                surfaceContainerHigh = surface2,
                surfaceContainerHighest = line,
            ),
        )
    }

/**
 * 全局圆角、间距与尺寸。
 *
 * 命名沿用 Material 3 的形状阶梯（none/xs/sm/md/lg/xl/2xl/full）。
 */
object Dimens {
    // ---- 形状阶梯 ----
    val shapeNone = 0f
    val shapeXs = 4f
    val shapeSm = 8f
    val shapeMd = 12f
    val shapeLg = 16f
    val shapeXl = 24f

    /** 大容器（卡片、弹层）用的超大圆角 */
    val shape2xl = 28f

    /** 胶囊 */
    val shapeFull = 999f

    // ---- 兼容旧接口 ----
    /** 卡片圆角 */
    val radius: Float get() = shapeXl

    /** 次级圆角（内嵌区块、小卡） */
    val radiusSm: Float get() = shapeLg

    // ---- 间距阶梯（4 的倍数）----
    val space1 = 4f
    val space2 = 8f
    val space3 = 12f
    val space4 = 16f
    val space5 = 20f
    val space6 = 24f
    val space8 = 32f

    /** 页面左右内边距，7 个页面统一走这个 */
    const val pagePadding = 20f

    /** 页面顶部内边距 */
    const val pagePaddingTop = 16f

    /** 页面底部内边距（给 FAB 让位） */
    const val pagePaddingBottom = 32f

    /** 页面内区块之间的默认间距 */
    const val blockGap = 16f

    /** 底部导航高度 */
    const val navHeight = 64f

    /** 图标按钮的边长 */
    const val iconButton = 40f

    /** 悬浮按钮直径 */
    const val fab = 64f

    // ---- 贴纸 ----
    /** 贴纸描边粗细 */
    const val strokeWidth = 1.5f

    /** 贴纸实色投影的下移距离（也是按下去的下沉距离） */
    const val stickerDrop = 3f
}
