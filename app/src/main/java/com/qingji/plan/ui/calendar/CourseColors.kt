package com.qingji.plan.ui.calendar

import androidx.compose.ui.graphics.Color
import com.qingji.plan.data.db.entity.CourseEntity

/**
 * 课程块的一对颜色：底色 + 字色。
 *
 * 仿的是分类色那套（`Category.soft` / `Category.deep`）的用法：
 * 浅底 + 同色系深字，在暖纸背景上看得清又不刺眼。
 */
data class CourseColor(val soft: Color, val deep: Color)

/**
 * 课表里每门课的颜色。
 *
 * ⚠️ **这是有意偏离原站的一处。** 原站的课程块是**按「分类」上色**的
 * （`扒站/app.js`：`style={{background: gn(w.category).soft, ...}}`），
 * 所以「工作」类的课全都是同一个颜色。用户要求**每门课颜色不同、方便区分**，
 * 于是改成按课程取色。
 *
 * 取色规则：**拿课程名算哈希**，再对调色板取模。
 *   - 用「课程名」而不是 `uid` —— 课名是用户心里的「同一门课」。
 *     删掉再新建一次，颜色不会变；备份还原到别的设备也是同一个色。
 *   - 同名课同色。这没啥问题，本来就是一回事。
 */
object CourseColors {

    /**
     * 调色板。挑的都是**色相差得开**的颜色，相邻两门课一眼能分辨。
     * 顺序也是刻意的：黄 → 蓝 → 玫红 → 橙 → 紫 → 青绿 → 粉 → 棕，
     * 前 6 个正好对上用户视频里那种「一节一色」的效果。
     */
    val PALETTE: List<CourseColor> = listOf(
        CourseColor(Color(0xFFF7E3B0), Color(0xFF9A7420)),  // 黄
        CourseColor(Color(0xFFCFE6F5), Color(0xFF2F6E96)),  // 蓝
        CourseColor(Color(0xFFF8D3DC), Color(0xFFA63F58)),  // 玫红
        CourseColor(Color(0xFFFBD9C0), Color(0xFFA85A21)),  // 橙
        CourseColor(Color(0xFFDCD3F5), Color(0xFF5B44A8)),  // 紫
        CourseColor(Color(0xFFC9EBDD), Color(0xFF1F7A5C)),  // 青绿
        CourseColor(Color(0xFFF2D6EC), Color(0xFF97407F)),  // 粉紫
        CourseColor(Color(0xFFDCE3C7), Color(0xFF5E7132)),  // 橄榄
    )

    /** 没有课名时兜底用 uid，保证不会因为空串挤到同一个颜色上 */
    fun colorFor(course: CourseEntity): CourseColor = colorForKey(course.title.ifBlank { course.uid })

    fun colorForKey(key: String): CourseColor = PALETTE[stableHash(key) % PALETTE.size]

    /**
     * 稳定哈希。
     *
     * 不能用 `String.hashCode()` —— 它在不同 JVM / 不同版本上**不保证一致**，
     * 换个设备课程颜色就可能全变了。这里自己算一个 djb2，行为固定。
     */
    fun stableHash(text: String): Int {
        var h = 5381
        text.forEach { c -> h = h * 33 + c.code }
        return h and 0x7FFFFFFF   // 取正
    }
}
