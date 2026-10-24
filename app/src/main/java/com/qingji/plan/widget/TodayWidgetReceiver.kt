package com.qingji.plan.widget

import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

/**
 * 小组件的广播入口。
 *
 * 只管一件事：告诉系统「这个 App 提供了哪个小组件」。
 * 渲染和数据都在 [TodayWidget] 里。
 */
class TodayWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodayWidget()
}
