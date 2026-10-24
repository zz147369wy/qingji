package com.qingji.plan.notify

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 通知权限状态机的测试。
 *
 * 这一组锁的是三个实测出来的坑：
 *   1. 拒绝 1 次还能再弹、拒绝 2 次永久静默 —— 必须自己记「问过没有」
 *   2. **权限 granted 但 appop 被关**（国产 ROM 最阴的一刀）
 *   3. API 33 以下压根没这个权限
 */
class NotifyLogicTest {

    private fun state(
        sdk: Int = 34,
        granted: Boolean = false,
        asked: Boolean = false,
        rationale: Boolean = false,
        enabled: Boolean = true,
    ) = NotifyLogic.state(sdk, granted, asked, rationale, enabled)

    // ---------- API 33 以下 ----------

    @Test
    fun `低版本不需要这个权限`() {
        assertEquals(NotifyLogic.State.NotNeeded, state(sdk = 26, granted = false))
        assertEquals(NotifyLogic.State.NotNeeded, state(sdk = 32, granted = false))
    }

    @Test
    fun `低版本但系统层关了通知也算被挡`() {
        assertEquals(
            NotifyLogic.State.BlockedBySystem,
            state(sdk = 30, granted = false, enabled = false),
        )
    }

    // ---------- 从没问过 ----------

    @Test
    fun `从没问过时可以弹系统弹窗`() {
        assertEquals(NotifyLogic.State.CanAsk, state(asked = false, granted = false))
        assertTrue(NotifyLogic.canRequest(NotifyLogic.State.CanAsk))
    }

    // ---------- 问过之后 ----------

    @Test
    fun `问过被拒但系统还愿意听解释时可以再弹`() {
        val s = state(asked = true, granted = false, rationale = true)
        assertEquals(NotifyLogic.State.DeniedCanAskAgain, s)
        assertTrue(NotifyLogic.canRequest(s))
    }

    @Test
    fun `问过被拒且系统不让解释了就是永久拒绝`() {
        // 这是实测行为：拒绝 2 次后 flags=USER_FIXED，
        // requestPermissions 约 100ms 静默回调 granted=-1，不弹窗
        val s = state(asked = true, granted = false, rationale = false)
        assertEquals(NotifyLogic.State.DeniedPermanently, s)
        assertFalse("永久拒绝后不能再弹系统弹窗", NotifyLogic.canRequest(s))
        assertTrue("必须引导去系统设置", NotifyLogic.needsSystemSettings(s))
    }

    @Test
    fun `没问过和永久拒绝的 rationale 都是 false 但状态不同`() {
        // 这正是必须自己记 askedBefore 的原因：
        // 光看 shouldShowRationale 分不清这两种情况
        assertEquals(NotifyLogic.State.CanAsk, state(asked = false, rationale = false))
        assertEquals(NotifyLogic.State.DeniedPermanently, state(asked = true, rationale = false))
    }

    // ---------- 权限给了但系统层关着 ----------

    @Test
    fun `权限授予但系统通知被关时报告被挡`() {
        // 实测：appops get <pkg> POST_NOTIFICATION → Uid mode: ignore
        // 而 checkSelfPermission 仍然返回 GRANTED。
        // 只看权限会「自以为发得出去」，提醒静默消失。
        val s = state(granted = true, enabled = false)
        assertEquals(NotifyLogic.State.BlockedBySystem, s)
        assertTrue(NotifyLogic.needsSystemSettings(s))
        assertFalse(NotifyLogic.canRequest(s))
    }

    @Test
    fun `权限授予且系统没关才算真的能用`() {
        assertEquals(NotifyLogic.State.Granted, state(granted = true, enabled = true))
    }

    // ---------- 文案 ----------

    @Test
    fun `每个状态都有说明文案`() {
        NotifyLogic.State.entries.forEach { s ->
            assertTrue("${s.name} 缺说明", NotifyLogic.describe(s).isNotBlank())
        }
    }

    @Test
    fun `永久拒绝的文案指向系统设置`() {
        val text = NotifyLogic.describe(NotifyLogic.State.DeniedPermanently)
        assertTrue(text.contains("系统设置"))
    }

    @Test
    fun `被系统关掉的文案说清楚是系统层`() {
        val text = NotifyLogic.describe(NotifyLogic.State.BlockedBySystem)
        assertTrue(text.contains("系统"))
    }

    @Test
    fun `按钮文案：能问的写开启，不能问的写去设置`() {
        assertEquals("开启提醒", NotifyLogic.actionLabel(NotifyLogic.State.CanAsk))
        assertEquals("再试一次", NotifyLogic.actionLabel(NotifyLogic.State.DeniedCanAskAgain))
        assertEquals("去系统设置", NotifyLogic.actionLabel(NotifyLogic.State.DeniedPermanently))
        assertEquals("去系统设置", NotifyLogic.actionLabel(NotifyLogic.State.BlockedBySystem))
    }

    @Test
    fun `正常状态不需要按钮`() {
        assertEquals("", NotifyLogic.actionLabel(NotifyLogic.State.Granted))
        assertEquals("", NotifyLogic.actionLabel(NotifyLogic.State.NotNeeded))
    }
}
