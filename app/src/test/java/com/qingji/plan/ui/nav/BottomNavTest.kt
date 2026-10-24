package com.qingji.plan.ui.nav

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.qingji.plan.ui.theme.QingJiTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 底部导航的验收测试。
 *
 * 原站是 5 个 Tab（今日 / 日历 / AI / 统计 / 设置），
 * 现在把「AI」从导航拿掉（改成双击桌面鲸鱼进 AI），所以这里只剩 4 个。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BottomNavTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `一共有四个标签页`() {
        assertEquals(4, TabDestination.entries.size)
    }

    @Test
    fun `标签顺序为今日日历统计设置`() {
        assertEquals(
            listOf("今日", "日历", "统计", "设置"),
            TabDestination.entries.map { it.label },
        )
    }

    @Test
    fun `导航里不再有 AI 标签`() {
        assertEquals(false, TabDestination.entries.any { it.route == "ai" })
    }

    @Test
    fun `路由名不重复`() {
        assertEquals(
            TabDestination.entries.size,
            TabDestination.entries.map { it.route }.toSet().size,
        )
    }

    @Test
    fun `底部导航能渲染出全部四个标签`() {
        compose.setContent {
            QingJiTheme {
                BottomNav(current = TabDestination.TODAY, onSelect = {})
            }
        }

        compose.onNodeWithTag("bottom_nav").assertIsDisplayed()
        TabDestination.entries.forEach { tab ->
            compose.onNodeWithTag("nav_${tab.route}").assertIsDisplayed()
            compose.onNodeWithText(tab.label).assertIsDisplayed()
        }
    }

    @Test
    fun `点击标签会回调对应的目的地`() {
        val clicked = mutableListOf<TabDestination>()
        compose.setContent {
            QingJiTheme {
                BottomNav(current = TabDestination.TODAY, onSelect = { clicked.add(it) })
            }
        }

        compose.onNodeWithTag("nav_calendar").performClick()
        compose.onNodeWithTag("nav_stats").performClick()

        assertEquals(listOf(TabDestination.CALENDAR, TabDestination.STATS), clicked)
    }

    @Test
    fun `主骨架默认停在今日页`() {
        compose.setContent {
            QingJiTheme {
                HomeShell()
            }
        }

        // 今日页在 P1 已经接上真界面，不再是占位页
        compose.onNodeWithTag("today_screen").assertIsDisplayed()
    }

    @Test
    fun `点击导航能切换到统计页`() {
        compose.setContent {
            QingJiTheme {
                HomeShell()
            }
        }

        compose.onNodeWithTag("nav_stats").performClick()
        compose.onNodeWithTag("stats_screen").assertIsDisplayed()
    }

    @Test
    fun `四个页面都能切换过去`() {
        compose.setContent {
            QingJiTheme {
                HomeShell()
            }
        }

        compose.onNodeWithTag("nav_calendar").performClick()
        compose.onNodeWithTag("calendar_screen").assertIsDisplayed()

        compose.onNodeWithTag("nav_settings").performClick()
        compose.onNodeWithTag("settings_screen").assertIsDisplayed()

        compose.onNodeWithTag("nav_today").performClick()
        compose.onNodeWithTag("today_screen").assertIsDisplayed()
    }
}
