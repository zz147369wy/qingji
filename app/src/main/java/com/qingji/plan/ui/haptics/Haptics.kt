package com.qingji.plan.ui.haptics

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * 震动出口。
 *
 * 界面**不允许**直接调 `performHapticFeedback` —— 那样单元测试只能断言
 * 「有没有响应」，没法断言「请求了哪一种震动」。
 * 抽成接口之后，测试里换成记录版实现，就能精确断言「勾选完成时请求了 ToggleOn」。
 *
 * 默认值是 [NoHaptics]（什么都不做），这样 @Preview 或任何漏包 provider 的地方
 * 会静默降级而不是崩。跟 [com.qingji.plan.ui.theme.LocalPalette] 是同一个套路。
 */
fun interface Haptics {
    fun perform(cue: HapticCue)

    /**
     * 破坏性操作的连震 —— 滑动删除那一下的「唰」。
     *
     * ## 为什么要有这个方法，而不是在调用点写 `repeat(5) { perform(...) }`
     *
     * 连震要**间隔着打 5 下**（总共 250~300ms）。删除一旦执行，那一行很快就被移出列表，
     * 行自己的 `rememberCoroutineScope()` 随之取消 —— 后面的几下就丢了。
     * 所以连震必须由**活得比行久**的出口来调度，不能由发起删除的那一行自己循环。
     * 顺带测试替身也就能确定性地数到 5 下。
     *
     * 默认实现是**同步连打**（不间隔）：[NoHaptics] 与测试替身走这条，行为确定。
     * 真机实现 [ComposeHaptics] 覆写成带间隔的调度。
     *
     * 语义上复用的是 [HapticCue.PageSwipe]：连震的物理原语就是「高频软 tick」，
     * 和翻页是同一个震动。为它单开一个词汇，只会让映射表里出现两个等价的常量。
     */
    fun performDeleteBurst(times: Int = 5) {
        repeat(times) { perform(HapticCue.PageSwipe) }
    }
}

/** 什么都不做的实现。默认值，也是「用户关掉 App 内震动开关」时的实现。 */
val NoHaptics: Haptics = Haptics { }

val LocalHaptics = staticCompositionLocalOf { NoHaptics }

/** 在子树里提供震动能力。HomeShell 里包一层即可。 */
@Composable
fun ProvideHaptics(
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val haptics = if (enabled) rememberComposeHaptics() else NoHaptics
    CompositionLocalProvider(LocalHaptics provides haptics) {
        content()
    }
}
