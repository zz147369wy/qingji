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
 * 识图导入。
 *
 * 用**内存数据库 + 假 HTTP 出口**跑完整链路：图片 → 多模态请求 → 解析出 actions →
 * 待确认 → 回「确认」→ 真的写进课程表。
 *
 * 重点锁死三件事：
 *   1. 发出去的确实是 OpenAI 的**多模态**请求体（`image_url` + data URL），不是纯文本
 *   2. **不确认不写库**——这条是 PRD §10.4 的 A3，打字那条路有保护，识图这条也得有
 *   3. 模型不支持图片时，报错里要带「换个能识图的模型」的提示，不然用户看不懂
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AiImageImportTest {

    private lateinit var db: AppDatabase
    private lateinit var vm: AiViewModel

    /** 最后一次请求体，用来断言发的是什么 */
    private var lastBody = ""

    /** 下一次请求要返回的 `choices[0].message.content` */
    private var reply = ""

    /** 假 HTTP 出口。`HttpCaller` 是 fun interface，这样塞最省事 */
    private val fakeHttp = HttpCaller { _, _, body ->
        lastBody = body
        Result.success(
            """{"choices":[{"message":{"role":"assistant","content":${quote(reply)}}}]}"""
        )
    }

    private val imageUrl = "data:image/jpeg;base64,/9j/4AAQSkZJRgABAQAAAQ=="

    private val today = LocalDate.of(2026, 9, 27)

    /** 全用同一个 Unconfined 调度器，让整条链路在测试线程上同步跑完 */
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        db = AppDatabase.inMemory(ApplicationProvider.getApplicationContext())
        val settings = SettingsRepository(db.settingDao())
        vm = AiViewModel(
            tasks = TaskRepository(db.taskDao()),
            plans = LongTermPlanRepository(db.longTermPlanDao(), db.taskDao()),
            courses = CourseRepository(db.courseDao()),
            settings = settings,
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

    /** 三张表都空的课表 JSON，正好覆盖 addCourse 的各个字段 */
    private val courseTableJson = """
        {"actions":[
          {"op":"addCourse","course":{"title":"高等数学","weekday":0,"time":"08:00","durationMinutes":90,"room":"A-301","category":"study"}},
          {"op":"addCourse","course":{"title":"大学英语","weekday":2,"time":"10:00","durationMinutes":45,"category":"study"}},
          {"op":"addCourse","course":{"title":"体育","weekday":4,"time":"14:30","durationMinutes":45,"room":"操场","category":"health"}}
        ]}
    """.trimIndent()

    @Test
    fun `发的是多模态请求体，图片以 data URL 塞在 image_url 里`() = runTest {
        configure()
        reply = "{}"

        vm.sendImage(imageUrl, "")

        assertTrue("请求体里没有 image_url", lastBody.contains("\"image_url\""))
        assertTrue("data URL 没原样带过去", lastBody.contains(imageUrl))
        assertTrue("没有 text 块", lastBody.contains("\"type\":\"text\""))
        // 系统提示词那条也要在，否则模型不知道要输出 actions
        assertTrue("缺少识图提示词", lastBody.contains("图片识别助手"))
        assertTrue("缺少 actions 格式要求", lastBody.contains("actions"))
    }

    @Test
    fun `识别出课程时先进待确认，不直接写库`() = runTest {
        configure()
        reply = courseTableJson

        vm.sendImage(imageUrl, "")

        // 课程表里一门课都不该有
        assertEquals("不确认就写库了！", 0, CourseRepository(db.courseDao()).getAll().size)
        // 而是挂起等确认
        assertTrue("没有进入待确认状态", vm.pending.value is PendingState.Awaiting)
        assertEquals(3, (vm.pending.value as PendingState.Awaiting).actions.size)
    }

    @Test
    fun `回确认之后三节课才真的进课程表`() = runTest {
        configure()
        reply = courseTableJson

        vm.sendImage(imageUrl, "")
        vm.setDraft("确认")
        vm.send()

        val courses = CourseRepository(db.courseDao()).getAll()
        assertEquals(3, courses.size)
        assertEquals(
            listOf("高等数学", "大学英语", "体育"),
            courses.sortedBy { it.weekday }.map { it.title },
        )
        // 字段要原样落地
        val math = courses.first { it.title == "高等数学" }
        assertEquals(0, math.weekday)   // 周一
        assertEquals("08:00", math.time)
        assertEquals(90, math.durationMinutes)
        assertEquals("A-301", math.room)
    }

    @Test
    fun `回别的内容不会写库`() = runTest {
        configure()
        reply = courseTableJson

        vm.sendImage(imageUrl, "")
        vm.setDraft("算了")
        vm.send()

        assertEquals(0, CourseRepository(db.courseDao()).getAll().size)
        assertFalse(vm.pending.value is PendingState.Awaiting)
    }

    @Test
    fun `模型不支持图片时，报错要点名让用户换模型`() = runTest {
        configure()
        // 纯文本模型遇到图片，服务端一般回 400
        reply = ""
        val failing = AiViewModel(
            tasks = TaskRepository(db.taskDao()),
            plans = LongTermPlanRepository(db.longTermPlanDao(), db.taskDao()),
            courses = CourseRepository(db.courseDao()),
            settings = SettingsRepository(db.settingDao()),
            chatDao = db.chatLogDao(),
            todayProvider = { today },
            httpCaller = HttpCaller { _, _, _ ->
                Result.failure(RuntimeException("AI 请求失败（400）：model does not support image input"))
            },
            ioDispatcher = testDispatcher,
        )

        failing.sendImage(imageUrl, "")

        val message = failing.error.value.orEmpty()
        assertTrue("报错里没提换模型：$message", message.contains("deepseek-flash"))
    }

    @Test
    fun `没配 Key 时给的是配置提示，不是崩掉`() = runTest {
        // 故意不调 configure()
        vm.sendImage(imageUrl, "")

        assertTrue(vm.error.value.orEmpty().contains("设置"))
        assertEquals(0, CourseRepository(db.courseDao()).getAll().size)
    }

    @Test
    fun `认不出东西时给一句话，不报错也不写库`() = runTest {
        configure()
        reply = "这是一张风景照，我没看到课程表。"

        vm.sendImage(imageUrl, "")

        assertEquals(0, CourseRepository(db.courseDao()).getAll().size)
        assertFalse("不该挂起操作", vm.pending.value is PendingState.Awaiting)
        assertEquals(null, vm.error.value)
    }

    @Test
    fun `打了字再发图，字会原样带进请求`() = runTest {
        configure()
        reply = "{}"

        vm.sendImage(imageUrl, "把这张课表加进去")

        assertTrue(lastBody.contains("把这张课表加进去"))
    }

    private companion object {
        /** 把字符串安全地嵌进测试用的 JSON 里 */
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
