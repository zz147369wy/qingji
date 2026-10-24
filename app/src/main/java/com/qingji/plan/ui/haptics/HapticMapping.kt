package com.qingji.plan.ui.haptics

import androidx.compose.ui.hapticfeedback.HapticFeedbackType

/**
 * 一次震动请求。两个分支最终都落到 `ViewCompat.performHapticFeedback`，
 * 区别只是「用 Compose 现成的语义常量」还是「用更底层的常量」。
 */
sealed interface HapticRequest {
    /** 走 Compose 的语义常量 */
    data class Type(val type: HapticFeedbackType) : HapticRequest

    /** 走底层常量（Compose 里没有对应语义时才用） */
    data class Constant(val value: Int) : HapticRequest
}

/**
 * 词汇 → 具体 API 的**纯函数映射**。
 *
 * 可以在纯 JVM 测试里跑：`HapticFeedbackType` 是 value class，
 * 映射表在类初始化时只做 int 装箱，不引用任何 android.view 类。
 */
fun hapticRequestFor(cue: HapticCue): HapticRequest = when (cue) {
    // 长按任务行（进入多选）。就是系统长按的经典手感。
    HapticCue.GrabMove -> HapticRequest.Type(HapticFeedbackType.LongPress)

    // 落定。CONFIRM 是「确认一次操作已生效」。
    HapticCue.Commit -> HapticRequest.Type(HapticFeedbackType.Confirm)

    HapticCue.ToggleOn -> HapticRequest.Type(HapticFeedbackType.ToggleOn)
    HapticCue.ToggleOff -> HapticRequest.Type(HapticFeedbackType.ToggleOff)

    // 屏幕上按了一个按钮。
    HapticCue.Tap -> HapticRequest.Type(HapticFeedbackType.VirtualKey)

    // 一天一天翻。SEGMENT_FREQUENT_TICK 的语义是「在大量候选中切换」，
    // 官方要求它必须非常软，最适合高频翻页。删除连震用的也是它 —— 物理原语相同，
    // 所以就复用这一个词，不为「连打」另立门户（见 Haptics.performDeleteBurst）。
    HapticCue.PageSwipe -> HapticRequest.Type(HapticFeedbackType.SegmentFrequentTick)

    // 深划就绪。GESTURE_THRESHOLD_ACTIVATE 的语义正是「一个手势越过了判定阈值」，
    // 官方规定它必须比普通 tick 更「实」，用来提示「越过这道坎事情就变了」。
    HapticCue.DeepSwipeArmed -> HapticRequest.Type(HapticFeedbackType.GestureThresholdActivate)
}
