package com.qingji.plan.ui.haptics

/**
 * App 的**震动词汇表**。
 *
 * 刻意不直接用安卓的 [androidx.compose.ui.hapticfeedback.HapticFeedbackType] 当词汇：
 * 它读起来是 API 常量名，而这里想要的是「勾选完成」这种**行为语义**。
 *
 * 这个文件**不 import 任何 android.* 或 compose.\*** —— 纯 Kotlin，
 * 所以测试里断言的是「勾选完成时请求了 ToggleOn」，而不是「请求了常量 21」。
 */
enum class HapticCue {
    /** 长按任务行开始多选——一声闷响 */
    GrabMove,

    /** 落定（新建任务 / 改期写库成功）——干脆的「嗒」 */
    Commit,

    /** 勾选完成 */
    ToggleOn,

    /** 取消完成 */
    ToggleOff,

    /** 普通按钮点击（导航、筛选、返回今天） */
    Tap,

    /** 一天一天翻页 */
    PageSwipe,

    /** 滑动行「深划就绪」—— 已滑开、又朝同方向划过了阈值，提示「再松手就执行了」 */
    DeepSwipeArmed,
}
