package com.qingji.plan.widget

import android.content.Context
import android.util.Log
import androidx.glance.appwidget.updateAll
import androidx.room.InvalidationTracker
import com.qingji.plan.data.db.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 让桌面小组件跟着数据自动刷新。
 *
 * ## 为什么挂在 Room 的 invalidationTracker 上
 *
 * 这是**最省事的接线点**：挂在数据库层，一次覆盖所有写入路径 ——
 * 界面保存、AI 改数据、备份恢复、批量录入，**不用改任何一个 ViewModel**。
 *
 * ## ⚠️ 一个踩过的坑
 *
 * `InvalidationTracker.Observer` 是 **Kotlin abstract class，不是 fun interface**，
 * 所以 `addObserver(Observer { tables -> ... })` 这种 SAM 写法**编译不过**。
 * 必须老老实实写匿名子类：
 *
 * ```kotlin
 * object : InvalidationTracker.Observer("tasks") {
 *     override fun onInvalidated(tables: Set<String>) { ... }
 * }
 * ```
 *
 * ## 防抖
 *
 * 一次操作（比如批量录入 10 条）会连着触发好几次回调，
 * 加 300ms 防抖，避免小组件被反复重绘。
 */
object WidgetRefresher {

    private const val TAG = "WidgetRefresher"
    private const val DEBOUNCE_MS = 300L

    private var scope: CoroutineScope? = null
    private var pending: Job? = null

    /**
     * 开始监听。在 `QingJiApp.onCreate` 里调一次。
     */
    fun start(context: Context) {
        if (scope != null) return   // 已经启动过

        val appContext = context.applicationContext
        val appScope = CoroutineScope(Dispatchers.IO)
        scope = appScope

        val observer = object : InvalidationTracker.Observer("tasks", "courses", "settings") {
            override fun onInvalidated(tables: Set<String>) {
                // 防抖：取消上一次还没跑完的刷新
                pending?.cancel()
                pending = appScope.launch {
                    delay(DEBOUNCE_MS)
                    runCatching {
                        TodayWidget().updateAll(appContext)
                    }.onFailure {
                        Log.w(TAG, "刷新小组件失败：${it.message}")
                    }
                }
            }
        }

        runCatching {
            AppDatabase.get(appContext).invalidationTracker.addObserver(observer)
        }.onFailure {
            Log.w(TAG, "挂载小组件刷新监听失败：${it.message}")
        }
    }
}
