package com.qingji.plan.notify

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import com.qingji.plan.data.repo.SettingsRepository
import com.qingji.plan.service.Reminder
import com.qingji.plan.service.ReminderSchedule
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * 把 [ReminderSchedule] 算出来的提醒真正排进系统闹钟。
 *
 * 这是整个提醒链路里唯一碰 `AlarmManager` 的地方，逻辑薄得只剩「排 / 取消 / 重排」。
 *
 * ## 为什么用 `setAlarmClock()` 而不是 `setExactAndAllowWhileIdle()`
 *
 * 「锁屏也能收到」意味着屏幕关着、设备可能已经进 Doze。
 * `setExactAndAllowWhileIdle` 确实能穿透 Doze，但**国产 ROM 有自己的冻结策略**，
 * 它会把普通后台闹钟连同进程一起冻掉。
 *
 * `setAlarmClock()` 走的是**系统闹钟那条路**——状态栏会显示闹钟图标、系统时钟 App 共用同一路径，
 * ROM 侧不太可能像对待普通后台闹钟那样冻结它。
 *
 * 代价：状态栏会出现一个闹钟图标。作者已确认接受。
 *
 * ## 权限
 *
 * 用 `USE_EXACT_ALARM`（API 33 引入，**normal 权限、装完即授予、零交互**），
 * **不用** `SCHEDULE_EXACT_ALARM` —— 那个是 appop，targetSdk≥33 新装默认拒绝，
 * 调用时直接抛 `SecurityException`。
 */
class AlarmScheduler(
    private val context: Context,
    private val settings: SettingsRepository,
) {

    private val alarmManager: AlarmManager? =
        context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager

    /**
     * 重新排期：先撤掉所有旧的，再排新的。
     *
     * **幂等**：调多少次结果都一样，不会留下孤儿闹钟。
     */
    suspend fun rescheduleAll(now: LocalDateTime = LocalDateTime.now()) {
        val tasks = runCatching { com.qingji.plan.data.db.AppDatabase.get(context).taskDao().getAll() }
            .getOrDefault(emptyList())
        val courses = runCatching { com.qingji.plan.data.db.AppDatabase.get(context).courseDao().getAll() }
            .getOrDefault(emptyList())

        val settleTime = settings.settleTime()
        val notifyEnabled = settings.getBoolean(SettingsRepository.Keys.NOTIFY_ENABLED, true)

        val reminders = ReminderSchedule.upcoming(
            now = now,
            tasks = tasks,
            courses = courses,
            settleTime = settleTime,
            settleEnabled = notifyEnabled,
            zone = ZoneId.systemDefault(),
        )

        sync(reminders)
    }

    /**
     * 把系统里的闹钟对齐到 [reminders]。
     */
    suspend fun sync(reminders: List<Reminder>) {
        val am = alarmManager ?: return

        // 1. 先撤掉上一次排的全部
        armedKeys().forEach { key -> cancelByKey(am, key) }

        // 2. 再排新的
        val armed = mutableListOf<String>()
        reminders.forEach { reminder ->
            runCatching {
                val pi = pendingIntent(reminder.key, reminder, create = true) ?: return@runCatching
                // setAlarmClock 需要传一个「点闹钟图标要打开什么」的 PendingIntent
                val showIntent = PendingIntent.getActivity(
                    context, 0,
                    Intent(context, com.qingji.plan.MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
                am.setAlarmClock(
                    AlarmManager.AlarmClockInfo(reminder.atMillis, showIntent),
                    pi,
                )
                armed.add(reminder.key)
            }.onFailure { Log.w(TAG, "排闹钟失败 ${reminder.key}: ${it.message}") }
        }

        // 3. 记下这次排了哪些，下次好撤
        settings.putString(SettingsRepository.Keys.ARMED_ALARMS, armed.joinToString("|"))
    }

    /**
     * 精确闹钟能力探测。
     *
     * 用了 `USE_EXACT_ALARM` 的话正常情况下一直是 true；
     * 但个别 ROM 行为有差异，所以还是探一下，false 时降级成不精确的闹钟。
     */
    fun canScheduleExact(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager?.canScheduleExactAlarms() ?: false
        } else {
            true
        }

    // ---------- 内部 ----------

    /** 上次排了哪些 key（存在设置表里） */
    private suspend fun armedKeys(): List<String> =
        settings.getString(SettingsRepository.Keys.ARMED_ALARMS)
            ?.split("|")
            ?.filter { it.isNotBlank() }
            ?: emptyList()

    private fun cancelByKey(am: AlarmManager, key: String) {
        runCatching {
            val pi = pendingIntent(key, null, create = false) ?: return@runCatching
            am.cancel(pi)
        }
    }

    /**
     * 造 PendingIntent。
     *
     * ⚠️ `AlarmManager.cancel` 靠 `Intent.filterEquals` 匹配（action + data + type + class + categories），
     * 所以**排和撤必须用完全一致的 Intent**。
     * 唯一性全部由 `data` 这个 URI 承担，requestCode 恒为 0。
     *
     * URI 用 reminder 的 key 拼，key 里只有 uid / 日期，都是 `[0-9a-f-]`，不会破坏 URI。
     */
    private fun pendingIntent(key: String, reminder: Reminder?, create: Boolean): PendingIntent? {
        val intent = Intent(context, NotifyReceiver::class.java).apply {
            action = ACTION_FIRE_REMINDER
            data = Uri.parse("$URI_SCHEME://reminder/$key")
            if (reminder != null) {
                putExtra(EXTRA_TITLE, reminder.title)
                putExtra(EXTRA_BODY, reminder.body)
                putExtra(EXTRA_KIND, reminder.kind.name)
                putExtra(EXTRA_KEY, reminder.key)
            }
        }
        val flags = PendingIntent.FLAG_IMMUTABLE or
            if (create) PendingIntent.FLAG_UPDATE_CURRENT else PendingIntent.FLAG_NO_CREATE
        return PendingIntent.getBroadcast(context, 0, intent, flags)
    }

    companion object {
        private const val TAG = "AlarmScheduler"

        /** 所有提醒共用这一个 action，唯一性靠 data URI */
        const val ACTION_FIRE_REMINDER = "com.qingji.plan.action.FIRE_REMINDER"

        const val URI_SCHEME = "qingji"

        const val EXTRA_TITLE = "title"
        const val EXTRA_BODY = "body"
        const val EXTRA_KIND = "kind"
        const val EXTRA_KEY = "key"
    }
}
