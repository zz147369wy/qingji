package com.qingji.plan.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.qingji.plan.QingJiApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 系统重启、App 升级、改时间、换时区之后，把闹钟重新排一遍。
 *
 * **为什么这四种都要管：**
 *   - `BOOT_COMPLETED` —— 重启会清空所有闹钟（实测 pending 从 1 变 0）
 *   - `MY_PACKAGE_REPLACED` —— 覆盖安装/升级同样会清空
 *   - `TIME_SET` —— 闹钟是按「日期 + HH:mm」换算成绝对毫秒排的，
 *     用户手动改系统时间后，已经排的那些会按错误的地方时刻触发
 *   - `TIMEZONE_CHANGED` —— 换时区同理，跨时区飞行后必然错
 *
 * 后两个是**验证员指出的漏项**：原方案只处理前两个，改时间/换时区要等到
 * 下一次冷启动才被纠正。这两个广播都是普通广播，不需要额外权限。
 *
 * ⚠️ 只做重排，**不启动任何前台服务**。Android 15 起 BOOT_COMPLETED 接收器
 * 不能启动 dataSync 等类型的 FGS，会抛 ForegroundServiceStartNotAllowedException。
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            -> Unit
            else -> return
        }

        Log.i(TAG, "收到 ${intent.action}，重排提醒")

        // goAsync 让广播有时间跑完协程。**绝不起前台服务**
        val pending = goAsync()
        val app = context.applicationContext as? QingJiApp ?: run {
            pending.finish()
            return
        }
        CoroutineScope(Dispatchers.IO).launch {
            try {
                AlarmScheduler(context.applicationContext, app.settingsRepo).rescheduleAll()
            } catch (e: Exception) {
                Log.w(TAG, "重排失败：${e.message}")
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private const val TAG = "BootReceiver"
    }
}
