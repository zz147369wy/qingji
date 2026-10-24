package com.qingji.plan.ui.stats

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 泡泡堆的尺寸算式。
 *
 * 【2026-10-05】用户嫌泡泡太小 → 改成「按列宽的 82% 算、上限 24dp」；
 * 又说自己一天最多 5 件左右、**图高要跟着最近最多的一天走**（最多 4 件就只画够 5 颗的高度）。
 * 所以泡泡大小看图宽，图高看数据，两者都由这里算出来。
 *
 * 改的时候发现说明那句「一颗代表多件」原来是写死阈值的，跟实际堆叠数对不上：
 * 窄机上没合并却显示说明、宽机上合并了却不显示说明 —— 用户看到的是一句假话。
 * 这里一并守掉：**说明由真实的合并倍数驱动**（perBubble > 1）。
 */
class BubbleStackTest {

    /** 392dp 宽的手机，卡片内宽约 320dp，14 天 */
    private val phoneSlot = 320f / 14f

    /** 用户日常：一天最多 5 件 */
    private val userMax = 5

    @Test
    fun `手机宽度下泡泡比旧版大`() {
        val plan = bubbleLayout(slot = phoneSlot.dp, maxCount = userMax)
        // 旧版是固定 13dp；现在约 18.7dp
        assertTrue("泡泡应该明显变大，实际 ${plan.bubble}", plan.bubble.value > 17f)
        assertEquals(320f / 14f * 0.82f, plan.bubble.value, 0.05f)
    }

    @Test
    fun `图高跟着最多的一天走：四件只画五颗的高度`() {
        // 用户的原话：有 4 件就只画够 4-5 颗的高度
        val plan = bubbleLayout(slot = phoneSlot.dp, maxCount = 4)
        assertEquals("四件不该合并", 1, plan.perBubble)
        val fiveBubbles = plan.bubble.value + 4 * plan.bubble.value * 0.9f
        assertEquals("四件的高度应该正好是五颗", fiveBubbles, plan.height.value, 0.05f)
        assertTrue("不该再撑到上限高度", plan.height.value < 100f)
    }

    @Test
    fun `一天做的事越多图越高，但不超过上限`() {
        val heights = (1..20).map { bubbleLayout(slot = phoneSlot.dp, maxCount = it).height.value }
        assertEquals("应该单调不减", heights.sorted(), heights)
        assertTrue("再怎么样也不能高过 124dp", heights.max() <= 124f + 0.01f)
        assertEquals(
            "顶到上限之后图高就不再涨了",
            bubbleLayout(slot = phoneSlot.dp, maxCount = 7).height.value,
            bubbleLayout(slot = phoneSlot.dp, maxCount = 20).height.value,
            0.01f,
        )
        // 三层是下限，所以一两件时高度一样；从三件往上，多一件正好多一个步距
        assertEquals(
            bubbleLayout(slot = phoneSlot.dp, maxCount = 1).height.value,
            bubbleLayout(slot = phoneSlot.dp, maxCount = 2).height.value,
            0.01f,
        )
        val stepPx = bubbleLayout(slot = phoneSlot.dp, maxCount = 3).bubble.value * 0.9f
        // 到六件为止每件都还撑高一层；七件起顶到上限，图高就不再涨了（改让一颗代表多件）
        (3..5).forEach { n ->
            assertEquals(
                "第 $n 件应该正好让图高出一个步距",
                stepPx,
                bubbleLayout(slot = phoneSlot.dp, maxCount = n + 1).height.value -
                    bubbleLayout(slot = phoneSlot.dp, maxCount = n).height.value,
                0.01f,
            )
        }
    }

    @Test
    fun `一天都没有时也留三层泡泡的高度，卡片不塌`() {
        val plan = bubbleLayout(slot = phoneSlot.dp, maxCount = 0)
        assertEquals(52f, plan.height.value, 0.01f)
        assertEquals(1, plan.perBubble)
    }

    @Test
    fun `手机宽度下最多堆七件，第八件才合并`() {
        assertEquals(1, bubbleLayout(slot = phoneSlot.dp, maxCount = 7).perBubble)
        assertEquals(2, bubbleLayout(slot = phoneSlot.dp, maxCount = 8).perBubble)
    }

    @Test
    fun `卡片更宽时一颗代表几件会跟着变`() {
        // 内宽 480dp：泡泡被 24dp 上限截住，上限高度里只堆得下 5 颗 —— 做 6 件就得合并
        val wide = 480f / 14f
        assertEquals(24f, bubbleLayout(slot = wide.dp, maxCount = 1).bubble.value, 0.01f)
        assertEquals(1, bubbleLayout(slot = wide.dp, maxCount = 5).perBubble)
        assertEquals(2, bubbleLayout(slot = wide.dp, maxCount = 6).perBubble)
        // 20 件、最多堆 5 颗：(20-1)/5+1 = 4 件一颗 → 画 5 颗，正好堆满
        assertEquals(4, bubbleLayout(slot = wide.dp, maxCount = 20).perBubble)
    }

    @Test
    fun `泡泡有上限，平板或横屏不会变成乒乓球`() {
        assertEquals(24f, bubbleLayout(slot = 64.dp, maxCount = 3).bubble.value, 0.01f)
    }

    @Test
    fun `画出来的总颗数永远不超过图里装得下的层数`() {
        listOf(252f, 288f, 320f, 360f, 400f, 480f).forEach { w ->
            val slot = w / 14f
            (1..40).forEach { count ->
                val plan = bubbleLayout(slot = slot.dp, maxCount = count)
                val drawn = (count - 1) / plan.perBubble + 1
                assertTrue(
                    "内宽 ${w}dp、$count 件时画了 $drawn 颗，图里只装得下 ${plan.levels} 层",
                    drawn <= plan.levels,
                )
                // 最上面那颗（连它那半个描边）不能冒出画布
                val topEdge = plan.bubble.value / 2f + (drawn - 1) * plan.bubble.value * 0.9f
                assertTrue(
                    "内宽 ${w}dp、$count 件时最上面那颗冒出画布了",
                    topEdge <= plan.height.value - plan.bubble.value / 2f + 1f,
                )
            }
        }
    }

    @Test
    fun `没有宽度或没有数据时不崩`() {
        assertEquals(0f, bubbleLayout(slot = 0.dp, maxCount = 3).bubble.value, 0.01f)
        assertEquals(52f, bubbleLayout(slot = 0.dp, maxCount = 3).height.value, 0.01f)
        assertEquals(1, bubbleLayout(slot = 0.dp, maxCount = 0).perBubble)
    }
}
