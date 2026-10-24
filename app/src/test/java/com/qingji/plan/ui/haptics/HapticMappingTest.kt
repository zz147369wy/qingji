package com.qingji.plan.ui.haptics

import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 震动词汇 → 安卓 API 的映射测试。纯 JVM，不需要 Robolectric。
 *
 * 这里锁的是**设计依据**：常量值一旦变了（比如 androidx.core 升级改了降级表），
 * 测试就会红，提醒重新评估。
 */
class HapticMappingTest {

    private fun typeOf(cue: HapticCue): HapticFeedbackType =
        (hapticRequestFor(cue) as HapticRequest.Type).type

    // ---------- 逐个词汇走 Compose 语义常量 ----------

    @Test
    fun `按住整体用长按常量`() {
        assertEquals(HapticFeedbackType.LongPress, typeOf(HapticCue.GrabMove))
    }

    @Test
    fun `落定用确认常量`() {
        assertEquals(HapticFeedbackType.Confirm, typeOf(HapticCue.Commit))
    }

    @Test
    fun `完成和取消完成用开关常量`() {
        assertEquals(HapticFeedbackType.ToggleOn, typeOf(HapticCue.ToggleOn))
        assertEquals(HapticFeedbackType.ToggleOff, typeOf(HapticCue.ToggleOff))
    }

    @Test
    fun `普通点击用虚拟按键常量`() {
        assertEquals(HapticFeedbackType.VirtualKey, typeOf(HapticCue.Tap))
    }

    @Test
    fun `翻页用高频刻度常量`() {
        assertEquals(HapticFeedbackType.SegmentFrequentTick, typeOf(HapticCue.PageSwipe))
    }

    @Test
    fun `深划就绪用阈值激活常量`() {
        // 「手势越过了一道判定阈值」—— 正是滑动行跨过深划阈值的语义
        assertEquals(HapticFeedbackType.GestureThresholdActivate, typeOf(HapticCue.DeepSwipeArmed))
    }

    // ---------- 数值锁 ----------
    // HapticFeedbackType 的构造器和 value 字段都是私有的，
    // 纯 JVM 里拿不到整数值，用 hashCode 代替（value class 的 hashCode 就是底层值）。

    @Test
    fun `Compose 常量的底层数值与安卓框架一致`() {
        assertEquals(1, HapticFeedbackType.VirtualKey.hashCode())
        assertEquals(6, HapticFeedbackType.ContextClick.hashCode())
        assertEquals(16, HapticFeedbackType.Confirm.hashCode())
        assertEquals(21, HapticFeedbackType.ToggleOn.hashCode())
        assertEquals(22, HapticFeedbackType.ToggleOff.hashCode())
        assertEquals(27, HapticFeedbackType.SegmentFrequentTick.hashCode())
    }

    // ---------- 完整性 ----------

    @Test
    fun `每个词汇都有映射`() {
        HapticCue.entries.forEach { cue ->
            hapticRequestFor(cue) // 不抛异常即通过
        }
    }

    @Test
    fun `每个词汇映射到互不相同的震动`() {
        val mapped = HapticCue.entries.map { cue ->
            when (val r = hapticRequestFor(cue)) {
                is HapticRequest.Type -> "type:${r.type.hashCode()}"
                is HapticRequest.Constant -> "const:${r.value}"
            }
        }
        assertEquals("有两个词汇映射到了同一种震动", mapped.size, mapped.toSet().size)
    }
}
