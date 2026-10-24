package com.qingji.plan.data.model

import androidx.compose.ui.graphics.Color

/**
 * 任务分类。
 *
 * 5 个内置分类的名称与色值全部来自 `扒站/app.js`：
 * `const Wa = { work:{...}, study:{...}, life:{...}, health:{...}, other:{...} }`
 */
data class Category(
    val id: String,
    val label: String,
    /** 主色：时间轴任务块左侧那根 4px 粗色条 */
    val color: Color,
    /** 浅色底：任务块/标签的填充色 */
    val soft: Color,
    /** 深色：任务块上的文字色 */
    val deep: Color,
)

object Categories {

    // 【2026-10 鲸鱼主题】原站的 5 个分类色是「暖纸手帐」那套大地色，放进蓝色的海里发闷，
    // 换成更清亮的一组（与设计稿 theme.css 的 --c-work 等一致）。
    // 界面上的标签底色/字色不再直接用 soft/deep，而是 ThemePalette.tagBg/tagFg 按明暗推导；
    // soft/deep 仍保留，给备份文件和旧调用方用。
    val WORK = builtin("work", "工作", Color(0xFFFF8A5B))
    val STUDY = builtin("study", "学习", Color(0xFF3D9BFF))
    val LIFE = builtin("life", "生活", Color(0xFFF7B731))
    val HEALTH = builtin("health", "健康", Color(0xFFFF7FA8))
    val OTHER = builtin("other", "其他", Color(0xFF33BE8F))

    private fun builtin(id: String, label: String, color: Color) =
        Category(id, label, color, deriveSoft(color), deriveDeep(color))

    /** 内置分类，顺序与原站设置页一致 */
    val builtIn: List<Category> = listOf(WORK, STUDY, LIFE, HEALTH, OTHER)

    /** 内置分类的 id 列表，原站用它做合法性校验 */
    val builtInIds: Set<String> = builtIn.map { it.id }.toSet()

    /**
     * 自定义分类可用的配色池，共 6 色。用户新增分类时从这里挑颜色。
     *
     * 【2026-10 鲸鱼主题】跟内置分类一起换成清亮的一组，并且刻意避开 5 个内置色，
     * 免得自建分类和「工作」「学习」撞色。
     */
    val customColorPool: List<Color> = listOf(
        Color(0xFF8B5CF6),
        Color(0xFF00B8D9),
        Color(0xFFFF6F91),
        Color(0xFFF59E0B),
        Color(0xFF4F7CFF),
        Color(0xFF2BB673),
    )

    /**
     * 按 id 找分类。找不到就回落到「其他」——原站行为一致（`Yi()` 函数）。
     */
    fun of(id: String?, customs: List<Category> = emptyList()): Category =
        builtIn.firstOrNull { it.id == id }
            ?: customs.firstOrNull { it.id == id }
            ?: OTHER

    /**
     * 全部可选分类 = 内置 + 自定义
     */
    fun all(customs: List<Category> = emptyList()): List<Category> = builtIn + customs

    /**
     * 根据一个任意颜色生成「浅底 + 深字」，用于自定义分类。
     * 原站的 `ay()` / `oy()` 就是干这个的：往白色或黑色方向混合。
     */
    fun deriveSoft(color: Color, ratio: Float = 0.82f): Color = Color(
        red = color.red + (1f - color.red) * ratio,
        green = color.green + (1f - color.green) * ratio,
        blue = color.blue + (1f - color.blue) * ratio,
        alpha = 1f,
    )

    fun deriveDeep(color: Color, ratio: Float = 0.25f): Color = Color(
        red = color.red * (1f - ratio),
        green = color.green * (1f - ratio),
        blue = color.blue * (1f - ratio),
        alpha = 1f,
    )
}
