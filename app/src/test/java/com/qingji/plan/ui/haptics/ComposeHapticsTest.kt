package com.qingji.plan.ui.haptics

import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 震动出口的测试。纯 JVM —— `HapticFeedback` 是单方法接口，直接写个假的即可。
 */
class ComposeHapticsTest {

    /** 记录收到了哪些震动请求 */
    private class FakeFeedback : HapticFeedback {
        val calls = mutableListOf<HapticFeedbackType>()
        override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
            calls += hapticFeedbackType
        }
    }

    @Test
    fun `走 Compose 通道的词汇会打到 HapticFeedback 上`() {
        val fake = FakeFeedback()
        val haptics = ComposeHaptics(fake, view = null)

        haptics.perform(HapticCue.Commit)

        assertEquals(listOf(HapticFeedbackType.Confirm), fake.calls)
    }

    @Test
    fun `每个词汇挨个调一遍都不抛异常`() {
        val fake = FakeFeedback()
        val haptics = ComposeHaptics(fake, view = null)

        HapticCue.entries.forEach { haptics.perform(it) }
    }

    @Test
    fun `没有调度器时连震退回同步默认实现`() {
        // 删除连震的正常路径是异步的（要间隔），但测试替身与 NoHaptics 走同步兜底，
        // 这样「请求了几次」才是确定的
        val fake = FakeFeedback()
        val haptics = ComposeHaptics(fake, view = null)

        haptics.performDeleteBurst()

        assertEquals(5, fake.calls.size)
        assertEquals(List(5) { HapticFeedbackType.SegmentFrequentTick }, fake.calls)
    }

    @Test
    fun `连震次数可以指定`() {
        val fake = FakeFeedback()
        val haptics = ComposeHaptics(fake, view = null)

        haptics.performDeleteBurst(times = 2)

        assertEquals(2, fake.calls.size)
    }

    @Test
    fun `完成与取消完成打到的是两个不同的常量`() {
        val fake = FakeFeedback()
        val haptics = ComposeHaptics(fake, view = null)

        haptics.perform(HapticCue.ToggleOn)
        haptics.perform(HapticCue.ToggleOff)

        assertEquals(
            listOf(HapticFeedbackType.ToggleOn, HapticFeedbackType.ToggleOff),
            fake.calls,
        )
    }

    @Test
    fun `空实现什么都不做也不崩`() {
        HapticCue.entries.forEach { NoHaptics.perform(it) }
    }
}
