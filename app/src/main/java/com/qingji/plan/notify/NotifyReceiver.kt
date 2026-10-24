package com.qingji.plan.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.qingji.plan.MainActivity
import com.qingji.plan.R

/**
 * 闹钟响了之后弹通知。
 *
 * 也负责建通知渠道 —— 渠道建一次就行，重复建是幂等的。
 */
class NotifyReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != AlarmScheduler.ACTION_FIRE_REMINDER) return

        val title = intent.getStringExtra(AlarmScheduler.EXTRA_TITLE) ?: return
        val body = intent.getStringExtra(AlarmScheduler.EXTRA_BODY).orEmpty()
        val kind = intent.getStringExtra(AlarmScheduler.EXTRA_KIND)
        val key = intent.getStringExtra(AlarmScheduler.EXTRA_KEY).orEmpty()

        ensureChannels(context)
        post(context, title, body, kind, key)
    }

    private fun post(context: Context, title: String, body: String, kind: String?, key: String) {
        // ⚠️ 守卫用 areNotificationsEnabled()，**不能只用 checkSelfPermission**。
        //
        // 国产 ROM 的「通知管理」总开关会翻一个叫 POST_NOTIFICATION 的 appop 置为 ignore，
        // 而权限状态**仍然是 granted**。实测：
        //     appops get com.qingji.plan POST_NOTIFICATION → Uid mode: POST_NOTIFICATION: ignore
        // 只看权限会「自以为发得出去」，提醒静默消失而设置页显示一切正常。
        val nm = NotificationManagerCompat.from(context)
        if (!nm.areNotificationsEnabled()) {
            Log.i(TAG, "通知被系统关掉了，这条不弹：$title")
            return
        }
        // API 33+ 的运行时权限是另一道门，两道都要过
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            Log.i(TAG, "没有通知权限，这条不弹：$title")
            return
        }

        val channel = when (kind) {
            com.qingji.plan.service.Reminder.Kind.SETTLE.name -> CHANNEL_SETTLE
            else -> CHANNEL_TASK
        }

        // 点通知打开 App
        val contentIntent = PendingIntent.getActivity(
            context, key.hashCode(),
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            // 「锁屏也能收到」—— 这两行是默认值，真正的开关在用户手里
            // （小米/华为还有一层 per-app 的「锁屏通知」设置，App 设不了）
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .build()

        runCatching { nm.notify(key.hashCode(), notification) }
            .onFailure { Log.w(TAG, "弹通知失败：${it.message}") }
        // 注意：权限/开关被拒时 notify() **不抛异常**，是静默丢弃的。
        // 所以不能靠 catch 异常判断发没发出去 —— 上面的守卫才是唯一可靠的判据。
    }

    companion object {
        private const val TAG = "NotifyReceiver"

        const val CHANNEL_TASK = "task_reminder"
        const val CHANNEL_SETTLE = "settle_reminder"

        /**
         * 建通知渠道。minSdk 26，渠道是**必需项**不是优化。
         *
         * 重复建是幂等的，系统会忽略已存在的同名渠道（改重要性也得用户手动改）。
         */
        fun ensureChannels(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val manager = context.getSystemService(NotificationManager::class.java) ?: return

            listOf(
                CHANNEL_TASK to "任务到点提醒",
                CHANNEL_SETTLE to "每日结算提醒",
            ).forEach { (id, name) ->
                val channel = NotificationChannel(id, name, NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "鲸记的到点提醒"
                    setShowBadge(true)
                    lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                }
                manager.createNotificationChannel(channel)
            }
        }
    }
}
