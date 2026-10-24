package com.qingji.plan

import android.app.Application
import kotlinx.coroutines.launch
import com.qingji.plan.data.db.AppDatabase
import com.qingji.plan.data.repo.CourseRepository
import com.qingji.plan.data.repo.LongTermPlanRepository
import com.qingji.plan.data.repo.ReviewRepository
import com.qingji.plan.data.repo.SettingsRepository
import com.qingji.plan.data.repo.TaskRepository

/**
 * 极简依赖容器。
 *
 * 这个 App 规模不大，没必要上 Hilt——一个懒加载的单例容器就够了，
 * 而且不引入注解处理器，构建更快、更不容易出兼容问题。
 */
class QingJiApp : Application() {

    val database: AppDatabase by lazy { AppDatabase.get(this) }

    override fun onCreate() {
        super.onCreate()
        // 建通知渠道（幂等）
        com.qingji.plan.notify.NotifyReceiver.ensureChannels(this)
        // 让桌面小组件跟着数据库变化自动刷新
        com.qingji.plan.widget.WidgetRefresher.start(this)
        // ★ 每次冷启动都重排一次提醒。
        //   这是对付国产 ROM 杀后台最有效的一层：ROM 清掉闹钟后，
        //   用户点开 App 这一下就把它们全救回来了。
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            runCatching {
                com.qingji.plan.notify.AlarmScheduler(this@QingJiApp, settingsRepo).rescheduleAll()
            }
        }
    }

    val taskRepo: TaskRepository by lazy { TaskRepository(database.taskDao()) }
    val settingsRepo: SettingsRepository by lazy { SettingsRepository(database.settingDao()) }
    val courseRepo: CourseRepository by lazy { CourseRepository(database.courseDao()) }
    val planRepo: LongTermPlanRepository by lazy {
        LongTermPlanRepository(database.longTermPlanDao(), database.taskDao())
    }
    val reviewRepo: ReviewRepository by lazy { ReviewRepository(database.reviewDao()) }

    companion object {
        fun from(context: android.content.Context): QingJiApp =
            context.applicationContext as QingJiApp
    }
}
