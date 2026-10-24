package com.qingji.plan.ui.haptics

import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.core.view.ViewCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

/** 连震两下之间的间隔（毫秒）。不等距是故意的，见 [ComposeHaptics.performDeleteBurst] */
private const val DeleteBurstGapMinMs = 50L
private const val DeleteBurstGapMaxMs = 70L

/**
 * 唯一一处真正触发震动的地方。
 *
 * 走 `ViewCompat.performHapticFeedback` 这条通道的好处：
 *   - **不需要 VIBRATE 权限**（官方原文：Use a View — doesn't require the VIBRATE permission）
 *   - **自动尊重系统的触感开关**，用户关掉就是不震，这是正确行为，不要试图绕过
 *     （FLAG_IGNORE_GLOBAL_SETTING 从 API 33 起只对特权应用有效）
 *   - androidx.core 自带 API 降级表，老机器上不会崩也不会静默失效
 *
 * `runCatching` 是为了兜住个别 ROM 的已知缺陷（有记录称小米在无 VIBRATE 权限时
 * 震动请求打到振动服务会崩）。**震动失败绝不该影响业务**。
 *
 * @param scope 只用于 [performDeleteBurst] 的异步调度。传 null 时退回
 *   [Haptics.performDeleteBurst] 的同步默认实现 —— 测试就是这么用的。
 */
class ComposeHaptics(
    private val feedback: HapticFeedback,
    private val view: View?,
    private val scope: CoroutineScope? = null,
) : Haptics {

    override fun perform(cue: HapticCue) {
        runCatching {
            when (val request = hapticRequestFor(cue)) {
                is HapticRequest.Type -> feedback.performHapticFeedback(request.type)
                is HapticRequest.Constant -> view?.let {
                    ViewCompat.performHapticFeedback(it, request.value)
                }
            }
        }
    }

    /**
     * 删除连震：连打 [times] 下高频软 tick，每两下之间等 50~70ms。
     *
     * ## 为什么必须在这儿调度，不能交给调用点
     *
     * 触发删除的那一行**马上就要从列表里消失了**，它自己的协程作用域会随之取消。
     * 由它来循环，只会响头两下就断。这里用的是 [ProvideHaptics] 那一层的 scope
     * （挂在 HomeShell 上），活得比任何一行都久。
     *
     * ## 为什么间隔要随机
     *
     * 等距的连震听起来像机器在响；不等距才有「唰」的一下的感觉。
     * 抖动幅度取 50~70ms 是和参考实现一致的量级 —— 再快会被系统合并成一次震动，
     * 再慢就散成了「嗒、嗒、嗒」。
     */
    override fun performDeleteBurst(times: Int) {
        val scope = scope
        if (scope == null || times <= 0) {
            super.performDeleteBurst(times)
            return
        }
        scope.launch {
            repeat(times) { index ->
                perform(HapticCue.PageSwipe)
                if (index < times - 1) {
                    delay(Random.nextLong(DeleteBurstGapMinMs, DeleteBurstGapMaxMs))
                }
            }
        }
    }
}

/**
 * ⚠️ 这个 remember 不能省。
 *
 * 如果每次都返回新实例，把它当 `pointerInput` 的 key 时会导致每次重组都重启
 * 手势检测器 —— 实测会产生多余的 `onDragCancel`，进而让拖拽中断。
 */
@Composable
fun rememberComposeHaptics(): Haptics {
    val feedback = LocalHapticFeedback.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    return remember(feedback, view, scope) { ComposeHaptics(feedback, view, scope) }
}
