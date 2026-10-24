package com.qingji.plan.ui.plans

import com.qingji.plan.data.db.entity.LongTermPlanEntity
import com.qingji.plan.data.model.Cadence
import com.qingji.plan.util.DateUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/**
 * 长期计划行上那组小标签，以及编辑器的草稿。
 *
 * 这一块是 2026-09-29 按原站 `plan-row` 重做的：原来是一整条拼起来的中文描述，
 * 现在拆成 `.tag-longterm` / `.cat-tag` / `.time-chip` / `.dur-tag` / `{startDate}起`
 * 五个小块。每个块的文案都有原站源码里的对应函数，这里逐个盯住。
 */
class PlanMetaTest {

    private val now = 1_800_000_000_000L

    /** 某一刻的毫秒数（按本地时区），用来造 createdAt */
    private fun millisAt(date: LocalDate): Long =
        date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun plan(
        title: String = "背单词",
        cadence: Cadence = Cadence.DAILY,
        weekdays: List<Int> = emptyList(),
        dayOfMonth: Int? = null,
        time: String? = null,
        durationMinutes: Int? = null,
        category: String = "study",
        createdAt: Long = now,
    ) = LongTermPlanEntity(
        uid = "p_$title", title = title, cadence = cadence.value,
        weekdays = weekdays, dayOfMonth = dayOfMonth, time = time,
        durationMinutes = durationMinutes, category = category,
        createdAt = createdAt, updatedAt = createdAt,
    )

    // ---------- 重复方式（原站 Ix） ----------

    @Test
    fun `每天`() {
        assertEquals("每天", cadenceText(plan(cadence = Cadence.DAILY)))
    }

    @Test
    fun `每周的几天用斜杠连起来`() {
        // ⚠️ 原站是 `.join("/")` —— 每周一/三/五，不是「每周一三五」也不是顿号
        val text = cadenceText(plan(cadence = Cadence.WEEKLY, weekdays = listOf(0, 2, 4)))
        assertEquals("每周一/三/五", text)
    }

    @Test
    fun `每周的日子会先排序再拼`() {
        val text = cadenceText(plan(cadence = Cadence.WEEKLY, weekdays = listOf(4, 0, 2)))
        assertEquals("每周一/三/五", text)
    }

    @Test
    fun `每周没挑日子时只说每周`() {
        assertEquals("每周", cadenceText(plan(cadence = Cadence.WEEKLY, weekdays = emptyList())))
    }

    @Test
    fun `每月几号`() {
        assertEquals("每月5日", cadenceText(plan(cadence = Cadence.MONTHLY, dayOfMonth = 5)))
    }

    // ---------- 标签组 ----------

    @Test
    fun `没排时间的计划没有时间标签也没有时长标签`() {
        val meta = planMetaOf(plan())
        assertNull(meta.timeText)
        assertNull(meta.durationText)
    }

    @Test
    fun `排了时间的计划时间标签用原站的短横格式`() {
        // 原站这里是 ny()：空格 + en dash + 空格。结束时间由 time + duration 算
        val meta = planMetaOf(plan(time = "11:02", durationMinutes = 15))
        assertEquals("11:02 – 11:17", meta.timeText)
        assertEquals("15分", meta.durationText)
    }

    @Test
    fun `有时长档位跟着 rd 走`() {
        assertEquals(
            "1.5时",
            planMetaOf(plan(time = "09:00", durationMinutes = 90)).durationText,
        )
    }

    @Test
    fun `分类标签取的是分类自己的配色`() {
        val meta = planMetaOf(plan(category = "work"))
        assertEquals("工作", meta.category.label)
    }

    @Test
    fun `起始日从创建时间推出来`() {
        val created = LocalDate.of(2026, 9, 27)
        val meta = planMetaOf(plan(createdAt = millisAt(created)))
        assertEquals("2026-09-27起", meta.startDateText)
    }

    @Test
    fun `起始日真的写在实体上`() {
        // startDate 没有单独存一列，是从 createdAt 推的 —— 这条盯住推导本身
        val entity = plan(createdAt = millisAt(LocalDate.of(2025, 1, 3)))
        assertEquals("2025-01-03", entity.startDate)
    }

    // ---------- 草稿：能不能存 ----------

    @Test
    fun `没名字不能存`() {
        assertFalse(PlanDraft.from(null).copy(title = "  ").canSave)
    }

    @Test
    fun `只有名字就能存`() {
        assertTrue(PlanDraft.from(null).copy(title = "背单词").canSave)
    }

    @Test
    fun `周重复必须至少挑一天`() {
        val weekly = PlanDraft.from(null).copy(title = "开会", cadence = Cadence.WEEKLY)
        assertFalse(weekly.canSave)
        assertTrue(weekly.copy(weekdays = listOf(2)).canSave)
    }

    @Test
    fun `每天每月不要求挑日子`() {
        assertTrue(PlanDraft.from(null).copy(title = "喝水", cadence = Cadence.DAILY).canSave)
        assertTrue(PlanDraft.from(null).copy(title = "交房租", cadence = Cadence.MONTHLY).canSave)
    }

    // ---------- 草稿：加载与折回 ----------

    @Test
    fun `新建时的默认值`() {
        val draft = PlanDraft.from(null)
        assertEquals("", draft.title)
        assertEquals(Cadence.DAILY, draft.cadence)
        // 时间默认留空（进「随时」区），这是用户明确要的，跟原站不同
        assertNull(draft.time)
        assertEquals(15, draft.durationMinutes)
        assertEquals("life", draft.category)
    }

    @Test
    fun `编辑时把已有计划铺进去`() {
        val draft = PlanDraft.from(
            plan(
                title = "开会",
                cadence = Cadence.WEEKLY,
                weekdays = listOf(0, 3),
                time = "09:30",
                durationMinutes = 45,
                category = "work",
            )
        )
        assertEquals("开会", draft.title)
        assertEquals(Cadence.WEEKLY, draft.cadence)
        assertEquals(listOf(0, 3), draft.weekdays)
        assertEquals("09:30", draft.time)
        assertEquals(45, draft.durationMinutes)
        assertEquals("work", draft.category)
    }

    @Test
    fun `草稿折回去时保留 id 和 uid 和创建时间`() {
        val existing = plan(title = "开会").copy(id = 7L, uid = "keep-me")
        val entity = PlanDraft.from(existing).toEntity(existing, now + 999)

        assertEquals(7L, entity.id)
        // uid 必须沿用：生成出来的任务靠 planUid 关联，换了就断链
        assertEquals("keep-me", entity.uid)
        assertEquals(existing.createdAt, entity.createdAt)
        assertEquals(now + 999, entity.updatedAt)
    }

    @Test
    fun `新建时 id 留零交给自增但 uid 要有`() {
        val entity = PlanDraft.from(null).copy(title = "新计划").toEntity(null, now)
        assertEquals(0L, entity.id)
        assertTrue(entity.uid.isNotBlank())
    }

    @Test
    fun `改完名字会去掉首尾空格`() {
        val entity = PlanDraft.from(null).copy(title = "  背单词  ").toEntity(null, now)
        assertEquals("背单词", entity.title)
    }

    @Test
    fun `从周重复改成每天会把星期清掉`() {
        val draft = PlanDraft.from(
            plan(title = "开会", cadence = Cadence.WEEKLY, weekdays = listOf(0, 1))
        ).copy(cadence = Cadence.DAILY)
        assertTrue(draft.toEntity(null, now).weekdays.isEmpty())
    }

    @Test
    fun `从每天改成每月会把每月几号带上`() {
        val draft = PlanDraft.from(null).copy(
            title = "交房租",
            cadence = Cadence.MONTHLY,
            dayOfMonth = 15,
        )
        assertEquals(15, draft.toEntity(null, now).dayOfMonth)
    }

    @Test
    fun `不是每月的话每月几号不留`() {
        val draft = PlanDraft.from(null).copy(title = "喝水", dayOfMonth = 15)
        assertNull(draft.toEntity(null, now).dayOfMonth)
    }

    @Test
    fun `没排时间的计划不留时长`() {
        // 原站是 `durationMinutes: x ? N||15 : undefined`
        val draft = PlanDraft.from(null).copy(title = "随手做", time = null, durationMinutes = 90)
        assertNull(draft.toEntity(null, now).durationMinutes)
    }

    @Test
    fun `排了时间但没挑时长时兜底十五分钟`() {
        val draft = PlanDraft.from(null).copy(title = "开会", time = "09:00", durationMinutes = null)
        assertEquals(15, draft.toEntity(null, now).durationMinutes)
    }

    @Test
    fun `草稿折回去后复制规则仍然正确`() {
        val draft = PlanDraft.from(
            plan(title = "开会", cadence = Cadence.WEEKLY, weekdays = listOf(0, 4))
        )
        val entity = draft.toEntity(null, now)
        // 2026-09-28 是周一，2026-10-02 是周五
        assertTrue(entity.matches(LocalDate.of(2026, 9, 28)))
        assertTrue(entity.matches(LocalDate.of(2026, 10, 2)))
        assertFalse(entity.matches(LocalDate.of(2026, 9, 29)))
    }

    @Test
    fun `起始日跟 DateUtils 的键格式一致`() {
        // 备份、任务日期、startDate 用的必须是同一套键，不然对不上
        val date = LocalDate.of(2026, 10, 1)
        val entity = plan(createdAt = millisAt(date))
        assertEquals(DateUtils.toKey(date), entity.startDate)
    }
}
