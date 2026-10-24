package com.qingji.plan.ui.today

import com.qingji.plan.data.db.entity.TaskEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 今日页分组逻辑的测试。
 *
 * 分组是「时间轴换成列表」这个改动的核心 —— 任务的**位置**原来由像素坐标决定，
 * 现在由时间带决定，所以边界值必须钉死。
 *
 * 跟 `TimelineGestureLogicTest`（已随时间轴删除）一样，这里测的是纯函数，
 * 不涉及 Compose。
 */
class TodayGroupingTest {

    private val dateKey = "2026-09-26"
    private val now = 1_800_000_000_000L

    private fun task(
        title: String,
        time: String? = null,
        duration: Int? = if (time != null) 60 else null,
        done: Boolean = false,
        isLongTerm: Boolean = false,
    ) = TaskEntity(
        uid = "u_$title",
        date = dateKey,
        title = title,
        category = "work",
        time = time,
        durationMinutes = duration,
        status = if (done) "done" else "todo",
        isLongTerm = isLongTerm,
        createdAt = now,
        updatedAt = now,
    )

    private fun groups(
        timed: List<TaskEntity> = emptyList(),
        anytime: List<TaskEntity> = emptyList(),
        longTerm: List<TaskEntity> = emptyList(),
        nowMinute: Int? = null,
    ) = TodayGrouping.group(timed, anytime, longTerm, nowMinute)

    /** 从分组结果里找某一组，找不到返回 null */
    private fun List<TaskGroup>.of(group: TodayGroup) = firstOrNull { it.group == group }

    private fun List<TaskGroup>.titlesOf(group: TodayGroup) =
        of(group)?.tasks?.map { it.title } ?: emptyList()

    // ---------- 时间带边界 ----------

    @Test
    fun `凌晨的边界是零点到五点五十九`() {
        val r = groups(timed = listOf(task("零点", "00:00"), task("五点五九", "05:59")))
        assertEquals(listOf("零点", "五点五九"), r.titlesOf(TodayGroup.LATE_NIGHT))
    }

    @Test
    fun `六点整开始算上午`() {
        val r = groups(timed = listOf(task("六点整", "06:00"), task("十一点五九", "11:59")))
        assertEquals(listOf("六点整", "十一点五九"), r.titlesOf(TodayGroup.MORNING))
    }

    @Test
    fun `十二点整开始算下午`() {
        val r = groups(timed = listOf(task("十二点整", "12:00"), task("十七点五九", "17:59")))
        assertEquals(listOf("十二点整", "十七点五九"), r.titlesOf(TodayGroup.AFTERNOON))
    }

    @Test
    fun `十八点整开始算晚上`() {
        val r = groups(timed = listOf(task("十八点整", "18:00"), task("二十三点五九", "23:59")))
        assertEquals(listOf("十八点整", "二十三点五九"), r.titlesOf(TodayGroup.EVENING))
    }

    // ---------- 分组顺序 ----------

    @Test
    fun `分组顺序是上午 下午 晚上 凌晨 随时 长期计划`() {
        // nowMinute 传 null（当作「不是今天」），这样不会有过时任务混进来 ——
        // 已过时的排位单独用一个用例测
        val r = groups(
            timed = listOf(
                task("晚上", "20:00"),
                task("凌晨", "02:00"),
                task("上午", "09:00"),
                task("下午", "14:00"),
            ),
            anytime = listOf(task("随时的事")),
            longTerm = listOf(task("长期", isLongTerm = true)),
            nowMinute = null,
        )
        assertEquals(
            listOf(
                TodayGroup.MORNING,
                TodayGroup.AFTERNOON,
                TodayGroup.EVENING,
                TodayGroup.LATE_NIGHT,
                TodayGroup.ANYTIME,
                TodayGroup.LONG_TERM,
            ),
            r.map { it.group },
        )
    }

    @Test
    fun `已过时排在所有分组最前面`() {
        val r = groups(
            timed = listOf(
                task("上午已过", "09:00"),
                task("下午没到", "15:00"),
            ),
            anytime = listOf(task("随时的事")),
            nowMinute = 12 * 60,
        )
        assertEquals(TodayGroup.OVERDUE, r.first().group)
        assertEquals(listOf("上午已过"), r.titlesOf(TodayGroup.OVERDUE))
    }

    @Test
    fun `空分组不出现`() {
        val r = groups(timed = listOf(task("上午", "09:00")))
        assertEquals(listOf(TodayGroup.MORNING), r.map { it.group })
    }

    @Test
    fun `一条任务都没有时返回空列表`() {
        assertTrue(groups().isEmpty())
    }

    // ---------- 已过时 ----------

    @Test
    fun `结束了还没完成的算已过时`() {
        // 09:00 开始、60 分钟，10:30 时看 → 已过时
        val r = groups(timed = listOf(task("开会", "09:00")), nowMinute = 10 * 60 + 30)
        assertEquals(listOf("开会"), r.titlesOf(TodayGroup.OVERDUE))
    }

    @Test
    fun `正在进行中的任务不算已过时`() {
        // 09:00–10:00，09:30 时看 → 还没结束，不算
        val r = groups(timed = listOf(task("开会", "09:00")), nowMinute = 9 * 60 + 30)
        assertEquals(listOf("开会"), r.titlesOf(TodayGroup.MORNING))
        assertTrue(r.of(TodayGroup.OVERDUE) == null)
    }

    @Test
    fun `刚好到结束时刻不算已过时`() {
        // 边界：结束时刻 == 现在，按「已经结束」之前算，不算过时
        val r = groups(timed = listOf(task("开会", "09:00")), nowMinute = 10 * 60)
        assertTrue(r.of(TodayGroup.OVERDUE) == null)
    }

    @Test
    fun `已完成的任务再早也不算已过时`() {
        val r = groups(
            timed = listOf(task("晨跑", "06:00", done = true)),
            nowMinute = 23 * 60,
        )
        assertTrue(r.of(TodayGroup.OVERDUE) == null)
        assertEquals(listOf("晨跑"), r.titlesOf(TodayGroup.MORNING))
    }

    @Test
    fun `看别的日期时不判已过时`() {
        // nowMinute = null 表示「不是今天」——历史日期全都是过时的，未来日期全都不是，
        // 标了反而没有意义
        val r = groups(timed = listOf(task("开会", "09:00")), nowMinute = null)
        assertTrue(r.of(TodayGroup.OVERDUE) == null)
        assertEquals(listOf("开会"), r.titlesOf(TodayGroup.MORNING))
    }

    @Test
    fun `没有时长的任务按最小时长算结束时刻`() {
        // 09:00 开始、没有时长 → 按 15 分钟算，09:10 时看还没结束
        val r = groups(timed = listOf(task("短事", "09:00", duration = null)), nowMinute = 9 * 60 + 10)
        assertTrue(r.of(TodayGroup.OVERDUE) == null)

        // 09:16 时看就过时了
        val r2 = groups(timed = listOf(task("短事", "09:00", duration = null)), nowMinute = 9 * 60 + 16)
        assertEquals(listOf("短事"), r2.titlesOf(TodayGroup.OVERDUE))
    }

    @Test
    fun `凌晨的任务过时后进已过时组而不是凌晨组`() {
        // 02:00 的任务，到中午看时已经过时了
        val r = groups(timed = listOf(task("熬夜写的东西", "02:00")), nowMinute = 12 * 60)
        assertEquals(listOf("熬夜写的东西"), r.titlesOf(TodayGroup.OVERDUE))
        assertTrue(r.of(TodayGroup.LATE_NIGHT) == null)
    }

    // ---------- 随时 / 长期计划 ----------

    @Test
    fun `没有时间且不是长期计划的进随时组`() {
        val r = groups(anytime = listOf(task("买牛奶"), task("修灯")))
        assertEquals(listOf("买牛奶", "修灯"), r.titlesOf(TodayGroup.ANYTIME))
    }

    @Test
    fun `没有时间的长期计划任务进长期计划组而不是随时组`() {
        val r = groups(
            anytime = listOf(task("买牛奶")),
            longTerm = listOf(task("每天背单词", isLongTerm = true)),
        )
        assertEquals(listOf("买牛奶"), r.titlesOf(TodayGroup.ANYTIME))
        assertEquals(listOf("每天背单词"), r.titlesOf(TodayGroup.LONG_TERM))
    }

    @Test
    fun `随时组和长期计划组互不重复`() {
        val r = groups(
            anytime = listOf(task("买牛奶")),
            longTerm = listOf(task("每天背单词", isLongTerm = true)),
        )
        val all = r.flatMap { it.tasks }.map { it.uid }
        assertEquals(all.size, all.toSet().size)
    }

    @Test
    fun `组内保持传进来的顺序`() {
        // 调用方（TodayStateCalculator）已经把 timedTasks 按时间排好，
        // 分组不该打乱它
        val r = groups(
            timed = listOf(task("九点", "09:00"), task("十点", "10:00"), task("十一点", "11:00")),
        )
        assertEquals(listOf("九点", "十点", "十一点"), r.titlesOf(TodayGroup.MORNING))
    }

    @Test
    fun `时间格式非法时该任务被跳过而不是崩溃`() {
        val r = groups(timed = listOf(task("坏的", "二十五点"), task("好的", "09:00")))
        assertEquals(listOf("好的"), r.titlesOf(TodayGroup.MORNING))
        assertEquals(1, r.flatMap { it.tasks }.size)
    }

    // ---------- 鲸鱼主题：分组名不再带 emoji，改配自绘图标 + 深度 ----------

    @Test
    fun `分组名里不再有 emoji`() {
        TodayGroup.entries.forEach { g ->
            val emoji = g.label.filter { c ->
                val v = c.code
                v in 0x1F300..0x1FAFF || v in 0x2600..0x27BF || v == 0xFE0F
            }
            assertEquals(
                "${g.name} 的名字里还留着 emoji —— emoji 的样子随系统字体变，也不跟墨线画风",
                "",
                emoji,
            )
        }
    }

    @Test
    fun `七个分组各有自己的深度和图标`() {
        assertEquals(7, TodayGroup.entries.size)
        assertEquals(7, TodayGroup.entries.map { it.icon }.toSet().size)
        assertEquals(7, TodayGroup.entries.map { it.depthLabel }.toSet().size)
    }

    @Test
    fun `深度随一天往下走——上午最浅、长期计划最深`() {
        assertEquals(0, TodayGroup.MORNING.depth)
        assertEquals(1, TodayGroup.AFTERNOON.depth)
        assertEquals(2, TodayGroup.EVENING.depth)
        assertEquals(3, TodayGroup.LATE_NIGHT.depth)
        assertEquals(4, TodayGroup.ANYTIME.depth)
        assertEquals(5, TodayGroup.LONG_TERM.depth)
    }

    @Test
    fun `已过时和上午在同一层浅滩`() {
        assertEquals(TodayGroup.MORNING.depth, TodayGroup.OVERDUE.depth)
    }
}
