package com.qingji.plan.ui.ai

import androidx.test.core.app.ApplicationProvider
import com.qingji.plan.data.db.AppDatabase
import com.qingji.plan.data.model.AiConfig
import com.qingji.plan.data.remote.HttpCaller
import com.qingji.plan.data.repo.CourseRepository
import com.qingji.plan.data.repo.LongTermPlanRepository
import com.qingji.plan.data.repo.SettingsRepository
import com.qingji.plan.data.repo.TaskRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * 气泡上的「确认执行 / 取消」两个按钮。
 *
 * 以前用户得**在输入框里再打一遍「确认」**，现在点按钮就行。
 * 但按钮走的是跟打字**同一条执行链路**，所以「不确认不写库」这条不变量必须一样守住。
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AiPendingActionsTest {

    private lateinit var db: AppDatabase
    private lateinit var vm: AiViewModel

    private var reply = ""
    private val fakeHttp = HttpCaller { _, _, _ ->
        Result.success("""{"choices":[{"message":{"content":${quote(reply)}}}]}""")
    }

    private val today = LocalDate.of(2026, 9, 28)
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        db = AppDatabase.inMemory(ApplicationProvider.getApplicationContext())
        vm = AiViewModel(
            tasks = TaskRepository(db.taskDao()),
            plans = LongTermPlanRepository(db.longTermPlanDao(), db.taskDao()),
            courses = CourseRepository(db.courseDao()),
            settings = SettingsRepository(db.settingDao()),
            chatDao = db.chatLogDao(),
            todayProvider = { today },
            httpCaller = fakeHttp,
            ioDispatcher = testDispatcher,
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        db.close()
    }

    private suspend fun configure() = SettingsRepository(db.settingDao())
        .setAiConfig(AiConfig(apiKey = "sk-test", model = "deepseek-flash"))

    private suspend fun courses() = CourseRepository(db.courseDao()).getAll()

    /** 让 AI 先吐出一份待确认的操作 */
    private suspend fun propose() {
        configure()
        reply = """
            {"actions":[
              {"op":"addCourse","course":{"title":"数学","weekday":0,"time":"08:30","durationMinutes":85}},
              {"op":"addCourse","course":{"title":"英语","weekday":2,"time":"10:15","durationMinutes":85}}
            ]}
        """.trimIndent()
        vm.sendImage("data:image/jpeg;base64,AAAA", "")
    }

    // ---------- 确认 ----------

    @Test
    fun `点确认执行会真的写库`() = runTest {
        propose()
        assertEquals("提案阶段不该写库", 0, courses().size)

        vm.confirmPending()

        assertEquals(2, courses().size)
        assertEquals(listOf("数学", "英语"), courses().sortedBy { it.weekday }.map { it.title })
    }

    @Test
    fun `确认之后挂起状态被清掉`() = runTest {
        propose()
        assertTrue(vm.pending.value is PendingState.Awaiting)

        vm.confirmPending()

        assertFalse(vm.pending.value is PendingState.Awaiting)
    }

    @Test
    fun `连点两次确认不会执行两遍`() = runTest {
        propose()

        vm.confirmPending()
        vm.confirmPending()   // 手抖又点了一下

        assertEquals("重复执行了！", 2, courses().size)
    }

    @Test
    fun `确认会把那条待确认的消息标记掉，按钮才会消失`() = runTest {
        propose()
        assertEquals(1, db.chatLogDao().getAll().count { it.actionState == "pending" })

        vm.confirmPending()

        assertEquals(0, db.chatLogDao().getAll().count { it.actionState == "pending" })
    }

    // ---------- 取消 ----------

    @Test
    fun `点取消不会写库`() = runTest {
        propose()

        vm.cancelPending()

        assertEquals(0, courses().size)
        assertFalse(vm.pending.value is PendingState.Awaiting)
    }

    @Test
    fun `取消后也会回一句，并且标记掉待确认的消息`() = runTest {
        propose()

        vm.cancelPending()

        val logs = db.chatLogDao().getAll()
        assertEquals(0, logs.count { it.actionState == "pending" })
        assertTrue(
            "取消后该回一句",
            logs.any { it.content.contains("已取消") },
        )
    }

    // ---------- 没有挂起时的边界 ----------

    @Test
    fun `没有挂起操作时点确认什么都不做，也不崩`() = runTest {
        configure()

        vm.confirmPending()

        assertEquals(0, courses().size)
        assertEquals(null, vm.error.value)
    }

    @Test
    fun `没有挂起操作时点取消，不会平白多出一句「已取消」`() = runTest {
        configure()

        vm.cancelPending()

        val logs = db.chatLogDao().getAll()
        assertFalse("没有挂起操作时不该冒出一句已取消", logs.any { it.content.contains("已取消") })
    }

    private companion object {
        fun quote(s: String): String = buildString {
            append('"')
            s.forEach {
                when (it) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> append(it)
                }
            }
            append('"')
        }
    }
}
