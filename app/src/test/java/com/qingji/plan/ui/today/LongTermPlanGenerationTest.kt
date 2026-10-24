package com.qingji.plan.ui.today

import androidx.test.core.app.ApplicationProvider
import com.qingji.plan.data.db.AppDatabase
import com.qingji.plan.data.model.Cadence
import com.qingji.plan.data.repo.CourseRepository
import com.qingji.plan.data.repo.LongTermPlanRepository
import com.qingji.plan.data.repo.ReviewRepository
import com.qingji.plan.data.repo.SettingsRepository
import com.qingji.plan.data.repo.TaskRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * 长期计划 → 今日任务的**自动生成**。
 *
 * 这里守的是一个真出现过的 bug：`LongTermPlanRepository.generateFor()` 写得很完整
 * （幂等、带时间不带时间都处理了），但**全工程没有任何地方调用它** —— 纯死代码。
 * 结果就是「长期计划建了，今日页里什么都不出现」。
 *
 * 所以这个测试的意义是：**把「打开某一天会顺手生成那天的长期计划任务」这件事钉住**，
 * 以后谁把那段接线删了，这里会红。
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LongTermPlanGenerationTest {

    private lateinit var db: AppDatabase
    private lateinit var plans: LongTermPlanRepository
    private lateinit var tasks: TaskRepository

    private val today = LocalDate.of(2026, 9, 28)   // 周一
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        db = AppDatabase.inMemory(ApplicationProvider.getApplicationContext())
        tasks = TaskRepository(db.taskDao())
        plans = LongTermPlanRepository(db.longTermPlanDao(), db.taskDao())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        db.close()
    }

    private fun viewModel() = TodayViewModel(
        tasks = tasks,
        settings = SettingsRepository(db.settingDao()),
        courses = CourseRepository(db.courseDao()),
        plans = plans,
        reviews = ReviewRepository(db.reviewDao()),
        todayProvider = { today },
        hourProvider = { 9 },
    )

    /**
     * 订阅一下 state，把那个 flow 驱动起来。
     *
     * `state` 是 `WhileSubscribed` 的，**不订阅它就不跑**，也就不会生成任务。
     *
     * 必须用 `backgroundScope` 而不是 `launch`：这是个永不结束的 collect，
     * 挂在 runTest 自己的 scope 上的话，测试结束时会因为「还有活着的子协程」而超时失败。
     */
    private fun TestScope.drive(vm: TodayViewModel) {
        backgroundScope.launch { vm.state.collect { } }
        // 订阅之后上游还要跑一拍才生成完，推一下调度器
        testScheduler.advanceUntilIdle()
    }

    @Test
    fun `建了每天的长期计划，打开今日页就会生成今天的任务`() = runTest(testDispatcher) {
        plans.create(title = "背单词", cadence = Cadence.DAILY)

        val vm = viewModel()
        drive(vm)

        val generated = tasks.getByDate(today)
        assertEquals("长期计划没生成今日任务", 1, generated.size)
        assertEquals("背单词", generated.single().title)
        assertTrue("要标成长期计划，才能进「长期计划」那一栏", generated.single().isLongTerm)
    }

    @Test
    fun `没排时间的长期计划生成的是「随时」任务`() = runTest(testDispatcher) {
        plans.create(title = "喝水", cadence = Cadence.DAILY)

        val vm = viewModel()
        drive(vm)

        val task = tasks.getByDate(today).single()
        assertEquals(null, task.time)
        assertTrue("没时间的长期计划任务该进「长期计划」栏", task.isLongTerm)
    }

    @Test
    fun `排了时间的长期计划生成的是有时间、上时间轴的任务`() = runTest(testDispatcher) {
        plans.create(title = "晨跑", cadence = Cadence.DAILY, time = "07:00", durationMinutes = 30)

        val vm = viewModel()
        drive(vm)

        val task = tasks.getByDate(today).single()
        assertEquals("07:00", task.time)
        assertEquals(30, task.durationMinutes)
    }

    @Test
    fun `不命中的星期不会生成`() = runTest(testDispatcher) {
        // 只在周三，今天(周一)不该生成
        plans.create(title = "周三才做", cadence = Cadence.WEEKLY, weekdays = listOf(2))

        val vm = viewModel()
        drive(vm)

        assertTrue("不该给周一生成周三的任务", tasks.getByDate(today).isEmpty())
    }

    /** 幂等是这个功能的命门：不幂等的话每次进今日页都会多出一堆重复任务 */
    @Test
    fun `反复进出今日页不会重复生成`() = runTest(testDispatcher) {
        plans.create(title = "背单词", cadence = Cadence.DAILY)

        repeat(3) {
            val vm = viewModel()
            drive(vm)
        }

        assertEquals("生成了重复任务", 1, tasks.getByDate(today).size)
    }

    @Test
    fun `切到别的日期会生成那一天的`() = runTest(testDispatcher) {
        plans.create(title = "背单词", cadence = Cadence.DAILY)

        val vm = viewModel()
        drive(vm)
        vm.goToDate(today.plusDays(1))

        assertEquals(1, tasks.getByDate(today).size)
        assertEquals(1, tasks.getByDate(today.plusDays(1)).size)
        // 两天各一条，不是同一条被改了日期
        assertTrue(
            tasks.getByDate(today).single().uid != tasks.getByDate(today.plusDays(1)).single().uid,
        )
    }

    @Test
    fun `删掉长期计划时，它生成的任务也一起删掉`() = runTest(testDispatcher) {
        val id = plans.create(title = "背单词", cadence = Cadence.DAILY)

        val vm = viewModel()
        drive(vm)
        assertEquals(1, tasks.getByDate(today).size)

        plans.delete(plans.getById(id)!!)

        assertTrue(tasks.getByDate(today).isEmpty())
    }

    /**
     * 这一条是用户实际踩到的：**建完计划就停在今日页**，
     * 那个 flow 的日期没变、不会重跑，于是计划建好了但今日页什么都不出现。
     * 所以「计划列表变化」也必须能触发重新生成。
     */
    @Test
    fun `打开今日页之后再建计划，也会立刻生成出来`() = runTest(testDispatcher) {
        val vm = viewModel()
        drive(vm)
        assertTrue("一开始没计划，不该有任务", tasks.getByDate(today).isEmpty())

        // 用户此刻就停在今日页上，然后去建了一条长期计划
        plans.create(title = "背单词", cadence = Cadence.DAILY)
        testScheduler.advanceUntilIdle()

        assertEquals("建完计划没马上生成", 1, tasks.getByDate(today).size)
        assertEquals("背单词", tasks.getByDate(today).single().title)
    }

    @Test
    fun `在今日页上删掉计划，生成的任务也跟着消失`() = runTest(testDispatcher) {
        val id = plans.create(title = "背单词", cadence = Cadence.DAILY)

        val vm = viewModel()
        drive(vm)
        assertEquals(1, tasks.getByDate(today).size)

        plans.delete(plans.getById(id)!!)
        testScheduler.advanceUntilIdle()

        assertTrue("删了计划，生成的任务还在", tasks.getByDate(today).isEmpty())
    }
}
