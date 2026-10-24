package com.qingji.plan.ui.nav

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Assignment
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * 底部导航的 Tab：4 个页面。
 *
 * 【2026-10 鲸鱼主题】底栏正中还有一颗凸出浪线的「空空」头像钮（AI 聊天入口），
 * 但它**不是 Tab**：点了是从底下浮出一个整页，不切换当前页，所以不在这个枚举里。
 * 见 BottomNav 的 onOpenAi。
 */
enum class TabDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
) {
    TODAY("today", "今日", Icons.Outlined.Assignment),
    CALENDAR("calendar", "日历", Icons.Outlined.CalendarMonth),
    STATS("stats", "统计", Icons.Outlined.BarChart),
    SETTINGS("settings", "设置", Icons.Outlined.Settings),
}

/** 不在导航栏里的二级页 */
object Routes {
    /** 批量录入 */
    const val BATCH = "batch"

    /** 长期计划 */
    const val PLANS = "plans"
}
