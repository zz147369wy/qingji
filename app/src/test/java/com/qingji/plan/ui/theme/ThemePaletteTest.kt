package com.qingji.plan.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.qingji.plan.data.model.Categories
import com.qingji.plan.ui.pet.WHALE_SCHEMES
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 主题系统的验收测试。
 *
 * 【2026-10 重设计】原来是「逐字段对照原站的 16 个色值」，色值一换就全红。
 * 现在改成断言**性质**而不是具体色值 —— 色板已经由 Google 官方的 M3 算法
 * （`@material/material-color-utilities` 的 SchemeTonalSpot）离线生成后烘进来，
 * 再把十六进制手抄一遍到测试里，只是把生成器的输出抄第二遍，抓不到真问题。
 *
 * 现在守的是真正会出错的东西：
 *   - 主题数量 / 顺序 / 名字（设置页色点的排列依赖它）
 *   - 查找与兜底
 *   - **对比度**：文字必须看得见。动态取色最容易在这里翻车
 *   - **层级**：卡片必须浮在页面上。M3 里 background 和 surface 是同一个色，
 *     直接把 surface 当卡片色用会让卡片隐形
 */
class ThemePaletteTest {

    // ---------- 数量与顺序 ----------
    //
    // 【2026-10 鲸鱼主题】7 套静态主题 × 明暗两档 → 8 套鲸鱼配色 × 浅色/深色 = 16 套。
    // 配色全部由一个主色按固定公式推出来（见 whalePalette），所以下面守的不是具体色值，
    // 而是**性质**：文字看得见、卡片浮得起来、深浅分得开。

    @Test
    fun `一共十六套配色——八套鲸鱼配色乘浅色与深色`() {
        assertEquals(16, AllWhalePalettes.size)
        assertEquals(8, WHALE_SCHEMES.size)
        assertEquals(8, AllWhalePalettes.count { !it.isDark })
        assertEquals(8, AllWhalePalettes.count { it.isDark })
    }

    @Test
    fun `配色顺序就是设置页色点的排列顺序`() {
        assertEquals(
            listOf("deepseek", "kimi", "qwen", "gemini", "claude", "minimax", "chatgpt", "harness"),
            WHALE_SCHEMES.map { it.id },
        )
    }

    @Test
    fun `每套配色的 id 都由配色名加明暗拼成`() {
        WHALE_SCHEMES.forEach { s ->
            assertEquals("${s.id}-light", whalePalette(s.id, dark = false).id)
            assertEquals("${s.id}-dark", whalePalette(s.id, dark = true).id)
        }
    }

    @Test
    fun `id 不重复`() {
        assertEquals(16, AllWhalePalettes.map { it.id }.toSet().size)
    }

    // ---------- 默认配色 ----------

    @Test
    fun `默认是原版蓝的浅色档`() {
        assertEquals("deepseek-light", DefaultPalette.id)
        assertEquals("原版蓝", DefaultPalette.label)
        assertFalse(DefaultPalette.isDark)
    }

    // ---------- 查找与兜底 ----------

    @Test
    fun `认不出的配色 id 退回原版蓝`() {
        assertEquals(DefaultPalette, whalePalette(null, dark = false))
        assertEquals(DefaultPalette, whalePalette("", dark = false))
        assertEquals(DefaultPalette, whalePalette("不存在的配色", dark = false))
    }

    @Test
    fun `同一个配色重复取到的是同一个实例`() {
        assertSame(whalePalette("qwen", dark = false), whalePalette("qwen", dark = false))
    }

    // ---------- 对比度 ----------

    /** 两色的相对亮度差 */
    private fun contrast(a: Color, b: Color) = kotlin.math.abs(a.luminance() - b.luminance())

    @Test
    fun `每套配色的正文色都能看清页面`() {
        AllWhalePalettes.forEach { p ->
            assertTrue("${p.id}：正文色和页面底色太接近", contrast(p.text, p.bg) > 0.3f)
            assertTrue("${p.id}：次级文字和页面底色太接近", contrast(p.text2, p.bg) > 0.15f)
            assertTrue("${p.id}：三级文字和页面底色太接近", contrast(p.text3, p.bg) > 0.08f)
        }
    }

    @Test
    fun `主色上都放得住前景色`() {
        AllWhalePalettes.forEach { p ->
            // WCAG 对比度。主色上放的都是粗体按钮字，浅色按「大字 / 界面元素」的 3:1；
            // 深色下主色是浅的、字是深的，要求正文级别 4.5:1
            val min = if (p.isDark) 4.5f else 3f
            assertTrue("${p.id}：onPrimary 在主色上看不清", contrastRatio(p.onPrimary, p.primary) >= min)
            assertTrue("${p.id}：primaryDeep 在 primarySoft 上看不清", contrast(p.primaryDeep, p.primarySoft) > 0.25f)
        }
    }

    @Test
    fun `墨线在卡片上看得见——贴纸没有描边就散了`() {
        AllWhalePalettes.forEach { p ->
            if (p.isDark) {
                // 深色下墨线是近黑（设计稿 #04061A）：要比卡片和页面底色都暗，
                // 夹在亮一档的卡片和页面之间才是一道看得见的黑边
                val l = { c: Color -> ColorMix.toOklch(c)[0] }
                assertTrue("${p.id}：墨线要比卡片暗得多", l(p.surface) - l(p.ink) > 0.10f)
                assertTrue("${p.id}：墨线要比页面底色暗", l(p.bg) - l(p.ink) > 0.05f)
            } else {
                assertTrue(
                    "${p.id}：描边和卡片底太接近，贴纸的边会糊掉",
                    contrast(p.ink, p.surface) > 0.3f,
                )
            }
            assertTrue("${p.id}：正文在卡片上看不清", contrast(p.text, p.surface) > 0.3f)
        }
    }

    @Test
    fun `深色墨线照设计稿是近黑，不是亮边`() {
        // PM 2026-10-05 否掉了「深色下墨线改亮边」。这条守住别被改回去
        WHALE_SCHEMES.forEach { s ->
            assertEquals(Color(0xFF04061A), whalePalette(s.id, dark = true).ink)
        }
    }

    @Test
    fun `深水色带上放得住反白的字`() {
        AllWhalePalettes.forEach { p ->
            // 3 及以上是深水层：文字用 onDeep
            (3..5).forEach { d ->
                assertTrue("${p.id}：深水层 $d 上的 onDeep 看不清", contrast(p.onDeep, p.depths[d]) > 0.2f)
            }
        }
    }

    // ---------- 层级关系 ----------

    @Test
    fun `卡片浮在页面上`() {
        AllWhalePalettes.forEach { p ->
            assertNotEquals("${p.id}：卡片色不能等于页面底色，否则卡片直接隐形", p.bg, p.surface)
        }
    }

    @Test
    fun `次级底色跟页面底色区分得开`() {
        AllWhalePalettes.forEach { p ->
            assertNotEquals("${p.id}：surface2 和 bg 不能一样", p.bg, p.surface2)
        }
    }

    @Test
    fun `八个主色互不相同`() {
        val accents = AllWhalePalettes.filter { !it.isDark }.map { it.primary }
        assertEquals("主色重复的话设置页的色点就分不清了", 8, accents.toSet().size)
    }

    @Test
    fun `isDark 跟着参数走`() {
        AllWhalePalettes.forEach { p ->
            assertEquals(p.id, p.isDark, p.id.endsWith("-dark"))
        }
    }

    @Test
    fun `深色配色的文字比底色亮、浅色反过来`() {
        val dark = whalePalette("deepseek", dark = true)
        assertTrue("深色配色的字应该比底亮", dark.text.luminance() > dark.bg.luminance())

        val light = whalePalette("deepseek", dark = false)
        assertTrue("浅色配色的字应该比底暗", light.text.luminance() < light.bg.luminance())
    }

    // ---------- 配色推导 ----------

    @Test
    fun `换配色只换主色，结构不变`() {
        // 深色/浅色由参数决定，与选了哪个配色无关
        WHALE_SCHEMES.forEach { s ->
            assertFalse(whalePalette(s.id, dark = false).isDark)
            assertTrue(whalePalette(s.id, dark = true).isDark)
        }
    }

    @Test
    fun `配色舞台与深度色带都有六层`() {
        AllWhalePalettes.forEach { p ->
            assertEquals(6, p.depths.size)
        }
    }

    @Test
    fun `深水判定与明暗一致`() {
        val light = whalePalette("deepseek", dark = false)
        assertFalse(light.isDeep(0))
        assertFalse(light.isDeep(2))
        assertTrue(light.isDeep(3))
        // 深水只看层数，不看明暗：深色模式下浅滩也不是深水（不冒光斑）
        val dark = whalePalette("deepseek", dark = true)
        (0..2).forEach { assertFalse(dark.isDeep(it)) }
        (3..5).forEach { assertTrue(dark.isDeep(it)) }
    }

    @Test
    fun `分类标签的底与字在明暗两种模式下都分得开`() {
        val light = whalePalette("deepseek", dark = false)
        val dark = whalePalette("deepseek", dark = true)
        listOf(light, dark).forEach { p ->
            listOf(Categories.WORK, Categories.STUDY, Categories.LIFE).forEach { cat ->
                assertTrue(
                    "${p.id} 的 ${cat.label} 标签字看不清",
                    contrast(p.tagFg(cat.color), p.tagBg(cat.color)) > 0.25f,
                )
            }
        }
    }

    // ---------- 形状与间距 token ----------

    @Test
    fun `形状阶梯是单调递增的`() {
        val ladder = listOf(
            Dimens.shapeNone, Dimens.shapeXs, Dimens.shapeSm, Dimens.shapeMd,
            Dimens.shapeLg, Dimens.shapeXl, Dimens.shape2xl,
        )
        assertEquals("形状阶梯必须递增，否则命名就失去意义了", ladder, ladder.sorted())
    }

    @Test
    fun `兼容旧接口的两个圆角指向形状阶梯`() {
        assertEquals(Dimens.shapeXl, Dimens.radius, 0.001f)
        assertEquals(Dimens.shapeLg, Dimens.radiusSm, 0.001f)
    }

    @Test
    fun `间距阶梯是单调递增的`() {
        val ladder = listOf(
            Dimens.space1, Dimens.space2, Dimens.space3, Dimens.space4,
            Dimens.space5, Dimens.space6, Dimens.space8,
        )
        assertEquals(ladder, ladder.sorted())
    }

    @Test
    fun `尺寸 token 都在合理范围内`() {
        assertTrue("超大圆角别超过 32，再大就不是圆角是异形了", Dimens.shape2xl <= 32f)
        assertTrue("胶囊用一个远大于任何边长的值即可", Dimens.shapeFull >= 100f)
        assertTrue("导航栏高度要够得着", Dimens.navHeight >= 48f)
        assertTrue("图标按钮的触摸目标不小于 40", Dimens.iconButton >= 40f)
    }
}
