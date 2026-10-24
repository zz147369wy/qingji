package com.qingji.plan.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.qingji.plan.data.db.AppDatabase
import com.qingji.plan.data.model.Categories
import com.qingji.plan.data.repo.SettingsRepository
import android.content.res.Configuration
import com.qingji.plan.ui.theme.ThemeMode
import com.qingji.plan.ui.theme.whalePalette
import com.qingji.plan.util.DateUtils
import java.time.LocalDate

/**
 * 桌面小组件：不用打开 App 就能看到今天要干的事。
 *
 * ## ⚠️ 只能用 `androidx.glance.*` 的组件
 *
 * 这是**最容易踩的坑**（验证员实测）：因为 material3 / foundation / compose-ui 都在
 * classpath 上，你写 `androidx.compose.material3.Text` 会**零 error 编译通过**，
 * 但 Glance 的 Applier 没有对应节点，**运行时静默不渲染** —— 白板一块，还不报错。
 *
 * 所以下面所有 import 都是 `androidx.glance.*`，一个 Compose 组件都不能混。
 *
 * ## 取数
 *
 * `AppDatabase.get(context)` 本身就是按 applicationContext 做单例的，
 * 和 App 里用的是同一个连接，直接在 `provideGlance` 里 suspend 读就行。
 *
 * ## 配色
 *
 * 直接复用 `ui/theme/ThemePalette.kt` 里的 `whalePalette(scheme, dark)` —— 它是纯 Color 数据、
 * 不依赖 CompositionLocal，所以小组件能自动跟随主题切换，不用复制一套色值。
 */
class TodayWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appContext = context.applicationContext
        val db = AppDatabase.get(appContext)

        // 读设置里的主题 = 鲸鱼配色 × 明暗模式。小组件没有 Composable 环境，
        // 「跟随系统」只能自己读 uiMode
        val schemeId = runCatching {
            db.settingDao().get(SettingsRepository.Keys.WHALE_SCHEME)?.value
        }.getOrNull()
        val mode = ThemeMode.of(
            runCatching { db.settingDao().get(SettingsRepository.Keys.THEME_MODE)?.value }.getOrNull(),
        )
        val systemDark = (appContext.resources.configuration.uiMode and
            Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val dark = when (mode) {
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
            ThemeMode.SYSTEM -> systemDark
        }
        val palette = whalePalette(schemeId, dark)

        // 读今天的任务
        val today = LocalDate.now()
        val tasks = runCatching {
            db.taskDao().getByDate(DateUtils.toKey(today))
        }.getOrDefault(emptyList())

        provideContent {
            GlanceTheme {
                WidgetContent(
                    palette = palette,
                    dateLabel = DateUtils.dateTitle(today),
                    tasks = tasks.take(MAX_ROWS).map {
                        WidgetTask(
                            title = it.title,
                            time = it.time ?: "随时",
                            done = it.isDone,
                            categoryId = it.category,
                        )
                    },
                    totalCount = tasks.size,
                    doneCount = tasks.count { it.isDone },
                    hasMore = tasks.size > MAX_ROWS,
                )
            }
        }
    }

    companion object {
        /** 最多显示几条。再多就放不下了 */
        const val MAX_ROWS = 6
    }
}

/** 小组件里的一条任务（只带渲染需要的字段，不持有实体） */
internal data class WidgetTask(
    val title: String,
    val time: String,
    val done: Boolean,
    val categoryId: String,
)

@Composable
private fun WidgetContent(
    palette: com.qingji.plan.ui.theme.ThemePalette,
    dateLabel: String,
    tasks: List<WidgetTask>,
    totalCount: Int,
    doneCount: Int,
    hasMore: Boolean,
) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(palette.surface)
            .padding(12.dp),
    ) {
        // ---------- 顶栏：日期 ----------
        // 不用 defaultWeight —— 那个 API 在 Glance 1.2.0 里不好找，
        // 用「日期一行、进度并到同一行右边」的简化写法，效果一样。
        Text(
            text = "$dateLabel　$doneCount/$totalCount",
            style = TextStyle(
                color = ColorProvider(palette.text),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
            ),
        )

        Spacer(GlanceModifier.height(8.dp))

        if (tasks.isEmpty()) {
            Text(
                text = "今天还没有计划",
                style = TextStyle(
                    color = ColorProvider(palette.text3),
                    fontSize = 12.sp,
                ),
            )
        } else {
            tasks.forEach { task ->
                TaskRow(task = task, palette = palette)
            }
            if (hasMore) {
                Spacer(GlanceModifier.height(2.dp))
                Text(
                    text = "还有 ${totalCount - TodayWidget.MAX_ROWS} 项…",
                    style = TextStyle(
                        color = ColorProvider(palette.text3),
                        fontSize = 10.sp,
                    ),
                )
            }
        }
    }
}

@Composable
private fun TaskRow(task: WidgetTask, palette: com.qingji.plan.ui.theme.ThemePalette) {
    val category = Categories.of(task.categoryId)

    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 左侧分类色条
        Spacer(
            GlanceModifier
                .width(3.dp)
                .height(14.dp)
                .background(category.color),
        )
        Spacer(GlanceModifier.width(8.dp))
        Text(
            text = if (task.done) "${task.time} · ${task.title}" else "${task.time} · ${task.title}",
            style = TextStyle(
                color = ColorProvider(if (task.done) palette.text3 else palette.text),
                fontSize = 12.sp,
            ),
        )
    }
}
