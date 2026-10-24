package com.qingji.plan.ui.pet

/**
 * 鲸鱼空空的配色。与 web 包 `whale/model.json` 里的 `schemes` 一一对应。
 *
 * 8 套配色共用同一套几何（同一只鲸鱼），区别只在贴图颜色和 accent（给睡眠 z、
 * 倾听弧线、思考泡泡上色）。切换走 web 端已有的 `figure.setScheme(id)`。
 *
 * 【2026-10 鲸鱼主题】**配色 = App 主题**：整套界面的颜色都从 [appAccent] 推出来
 * （见 ui/theme/ThemePalette.kt 的 whalePalette）。
 *
 * @param accent web 端用的原始 accent，只给鲸鱼自己画特效
 * @param appAccent 界面主色。多数配色与 accent 相同；「银白」「纯黑」的 accent
 *   太接近墨线色，当主色会让按钮和描边糊成一片，所以单独调亮一档（与设计稿一致）
 */
data class WhaleScheme(
    val id: String,
    val label: String,
    val accent: String,
    val appAccent: Long,
)

/** 顺序 = 设置页色点的排列：蓝 → 紫 → 暖色 → 黑白，相近的色挨着 */
val WHALE_SCHEMES = listOf(
    WhaleScheme("deepseek", "原版蓝", "#4D6BFE", 0xFF4D6BFE),
    WhaleScheme("kimi", "黑蓝", "#007CFF", 0xFF007CFF),
    WhaleScheme("qwen", "千问紫", "#7F69F0", 0xFF7F69F0),
    WhaleScheme("gemini", "四色", "#4285F4", 0xFF4285F4),
    WhaleScheme("claude", "赤陶", "#D97757", 0xFFD97757),
    WhaleScheme("minimax", "玫红橙", "#D92D7A", 0xFFD92D7A),
    WhaleScheme("chatgpt", "银白", "#303030", 0xFF5B6472),
    WhaleScheme("harness", "纯黑", "#555555", 0xFF3A3D48),
)

/** 默认配色：原版蓝，对应 web 端 model.schemes[0] */
const val DEFAULT_WHALE_SCHEME = "deepseek"

/** 按 id 找配色，找不到退回原版蓝 */
fun whaleSchemeOf(id: String?): WhaleScheme =
    WHALE_SCHEMES.firstOrNull { it.id == id } ?: WHALE_SCHEMES.first()
