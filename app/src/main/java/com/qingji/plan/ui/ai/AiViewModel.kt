package com.qingji.plan.ui.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.qingji.plan.data.db.dao.ChatLogDao
import com.qingji.plan.data.db.entity.ChatLogEntity
import com.qingji.plan.data.model.AiConfig
import com.qingji.plan.data.remote.AiAction
import com.qingji.plan.data.remote.AiActionExecutor
import com.qingji.plan.data.remote.AiActionParser
import com.qingji.plan.data.remote.AiClient
import com.qingji.plan.data.remote.AiPrompts
import com.qingji.plan.data.remote.HttpCaller
import com.qingji.plan.data.remote.OkHttpCaller
import com.qingji.plan.data.repo.CourseRepository
import com.qingji.plan.data.repo.LongTermPlanRepository
import com.qingji.plan.data.repo.SettingsRepository
import com.qingji.plan.data.repo.TaskRepository
import com.qingji.plan.util.DateUtils
import com.qingji.plan.util.Ids
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * AI 聊天页的状态持有者。
 *
 * 流程（严格照原站）：
 *   用户说话 → AI 返回操作清单 → **先不执行**，显示「我准备执行以下 N 项」
 *   → 用户回复「确认」才执行 → 回执
 *   → 用户回复别的 → 丢掉操作，当普通聊天
 */
class AiViewModel(
    private val tasks: TaskRepository,
    private val plans: LongTermPlanRepository,
    private val courses: CourseRepository,
    private val settings: SettingsRepository,
    private val chatDao: ChatLogDao,
    private val todayProvider: () -> LocalDate = { LocalDate.now() },
    private val httpCaller: HttpCaller = OkHttpCaller(),
    /** 发 AI 请求用的调度器。抽出来是为了能测，见 [AiClient] 的同名参数 */
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {

    /** 聊天记录，按时间正序 */
    val messages: StateFlow<List<ChatLogEntity>> = chatDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 正在等用户点头的操作 */
    private val _pending = MutableStateFlow<PendingState>(PendingState.None)
    val pending: StateFlow<PendingState> = _pending

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    /** 输入框的草稿 */
    private val _draft = MutableStateFlow("")
    val draft: StateFlow<String> = _draft

    private val executor = AiActionExecutor(tasks, plans, courses, todayProvider)

    // ---------- 输入 ----------

    fun setDraft(text: String) {
        _draft.value = text
    }

    fun clearError() {
        _error.value = null
    }

    /** 给界面用的：图片处理失败之类，界面上层自己发现的问题也走这里显示 */
    fun showError(message: String) {
        _error.value = message
    }

    // ---------- 气泡上的两个按钮 ----------

    /**
     * 点「确认执行」。
     *
     * 跟用户手打「确认」走的是**同一条路**——都是把挂起的 actions 交给执行器。
     * 打字的入口在 [handle] 里那个确认状态机，这里只是给它加了个按钮。
     */
    fun confirmPending() {
        val pending = _pending.value
        if (pending !is PendingState.Awaiting || _busy.value) return
        _pending.value = PendingState.None
        viewModelScope.launch {
            chatDao.markAllPendingDone()
            executeAndReport(pending.actions)
        }
    }

    /**
     * 点「取消」。跟手打「算了」一样：回一句就停，不再去问 AI。
     */
    fun cancelPending() {
        if (_pending.value !is PendingState.Awaiting || _busy.value) return
        _pending.value = PendingState.None
        viewModelScope.launch {
            chatDao.cancelAllPending()
            insertAssistant("好的，已取消刚才的操作。")
        }
    }

    // ---------- 发送 ----------

    fun send() {
        val text = _draft.value.trim()
        if (text.isEmpty() || _busy.value) return
        _draft.value = ""
        viewModelScope.launch { handle(text) }
    }

    private suspend fun handle(input: String) {
        val now = System.currentTimeMillis()
        val config = settings.aiConfig()

        // 记下用户说了什么
        chatDao.insert(
            ChatLogEntity(uid = Ids.uid(), role = "user", content = input, createdAt = now)
        )

        if (!config.isConfigured) {
            _error.value = "还没配置 AI：请到「设置 → AI 助手」填写 API Key"
            return
        }

        // ★ 关键分支：有挂起的操作时，先按确认状态机决定
        when (AiChatLogic.decide(_pending.value, input)) {
            ChatDecision.Execute -> {
                val actions = (_pending.value as PendingState.Awaiting).actions
                _pending.value = PendingState.None
                chatDao.cancelAllPending()
                executeAndReport(actions)
                return
            }

            ChatDecision.Cancel -> {
                // 明确说了取消 → 回一句就停，**别再问 AI**（否则它可能把同一份操作再吐一遍）
                _pending.value = PendingState.None
                chatDao.cancelAllPending()
                insertAssistant("好的，已取消刚才的操作。")
                return
            }

            ChatDecision.PlainChat -> {
                // 回复了别的内容 → 原站行为：取消挂起的操作
                if (_pending.value is PendingState.Awaiting) {
                    _pending.value = PendingState.None
                    chatDao.cancelAllPending()
                }
            }
        }

        askAi(input, config)
    }

    // ---------- 问 AI ----------

    private suspend fun askAi(input: String, config: AiConfig) {
        _busy.value = true
        _error.value = null
        try {
            val client = AiClient(config, httpCaller, ioDispatcher)
            val modifyAllowed = settings.aiModifyAllowed()

            if (modifyAllowed) {
                // 走「执行助手」：先看它想改什么
                val snapshot = buildSnapshot()
                val system = AiPrompts.executor(
                    date = DateUtils.toKey(todayProvider()),
                    tasks = snapshot.first,
                    plans = snapshot.second,
                    courses = snapshot.third,
                )
                val raw = client.chat(system, "用户指令：$input")
                val actions = AiActionParser.parse(raw)

                if (actions.isNotEmpty()) {
                    // 有操作 → 不执行，先问
                    chatDao.insert(
                        ChatLogEntity(
                            uid = Ids.uid(),
                            role = "assistant",
                            content = AiChatLogic.proposeMessage(actions, modifyAllowed = true),
                            actionsJson = raw,
                            actionState = "pending",
                            createdAt = System.currentTimeMillis(),
                        )
                    )
                    _pending.value = PendingState.Awaiting(actions)
                    return
                }
                // AI 没给出操作 → 当成普通聊天，再问一次
                val reply = client.chat(systemSnapshot(input), input)
                insertAssistant(reply)
            } else {
                insertAssistant(client.chat(systemSnapshot(input), input))
            }
        } catch (e: Exception) {
            _error.value = e.message ?: "AI 调用失败"
        } finally {
            _busy.value = false
        }
    }

    // ---------- 发图片（识图导入） ----------

    /**
     * 发一张图给 AI 认，让它吐出操作清单。
     *
     * 图片在**外面**就已经编码成 data URL 了（见 [com.qingji.plan.util.ImageUtils]），
     * ViewModel 不碰 Android 的 Uri / Context —— 这样这条链路能脱离设备测。
     *
     * 走完拿到 actions 之后，**完全复用**打字那条路：同一个 [AiActionParser]、
     * 同一个 [PendingState.Awaiting] 确认状态机、同一个 [AiActionExecutor]。
     * 所以用户看到的交互也一样：AI 先说「我准备添加 N 项」，回「确认」才真的写库。
     *
     * @param dataUrl `data:image/jpeg;base64,...`
     * @param note 用户在输入框里打的字，可以为空
     */
    fun sendImage(dataUrl: String, note: String) {
        if (_busy.value) return
        _draft.value = ""
        viewModelScope.launch { handleImage(dataUrl, note.trim()) }
    }

    private suspend fun handleImage(dataUrl: String, note: String) {
        val now = System.currentTimeMillis()

        // 原站也只把文字存进聊天记录，图片本身不入库（省得数据库 schema 变动）
        chatDao.insert(
            ChatLogEntity(
                uid = Ids.uid(),
                role = "user",
                content = if (note.isEmpty()) "（发了一张图片）" else "$note（附图片）",
                createdAt = now,
            )
        )

        val config = settings.aiConfig()
        if (!config.isConfigured) {
            _error.value = "还没配置 AI：请到「设置 → AI 助手」填写 API Key"
            return
        }

        // 跟前一条挂起的操作冲突了 → 丢掉它，跟打字发消息的处理一致
        if (_pending.value is PendingState.Awaiting) {
            _pending.value = PendingState.None
            chatDao.cancelAllPending()
        }

        _busy.value = true
        _error.value = null
        try {
            val client = AiClient(config, httpCaller, ioDispatcher)
            val coursesText = buildSnapshot().third
            val system = AiPrompts.imageImporter(
                date = DateUtils.toKey(todayProvider()),
                courses = coursesText,
            )
            val ask = note.ifBlank { "请识别这张图片，把里面的课程/安排整理成操作清单。" }

            val raw = client.chatWithImage(system, ask, dataUrl)
            val actions = AiActionParser.parse(raw)

            if (actions.isNotEmpty()) {
                chatDao.insert(
                    ChatLogEntity(
                        uid = Ids.uid(),
                        role = "assistant",
                        content = AiChatLogic.proposeMessage(actions, modifyAllowed = true),
                        actionsJson = raw,
                        actionState = "pending",
                        createdAt = System.currentTimeMillis(),
                    )
                )
                _pending.value = PendingState.Awaiting(actions)
            } else {
                // 一张合影、一张风景照都会走到这儿
                insertAssistant(
                    raw.ifBlank { "没从这张图里认出课程或安排。换一张课表截图试试？" }
                )
            }
        } catch (e: Exception) {
            _error.value = friendlyImageError(e.message)
        } finally {
            _busy.value = false
        }
    }

    /**
     * 把识图失败的报错翻译成人话。
     *
     * 最常见的一种：模型是纯文本的（比如默认之外的 `deepseek-chat`），
     * 服务端会回 400，光看原始报错根本不知道要改什么。
     */
    private fun friendlyImageError(raw: String?): String {
        val text = raw.orEmpty()
        val looksLikeModelProblem = text.contains("400") ||
            text.contains("model", ignoreCase = true) ||
            text.contains("image", ignoreCase = true)
        return if (looksLikeModelProblem) {
            "$text\n\n（多半是当前模型不支持图片。到「设置 → AI 助手」把模型换成 " +
                "deepseek-flash 或其它能识图的模型）"
        } else {
            text.ifBlank { "图片识别失败" }
        }
    }

    private fun systemSnapshot(input: String): String = AiPrompts.CHAT_WITH_SNAPSHOT

    private suspend fun insertAssistant(text: String) {
        chatDao.insert(
            ChatLogEntity(
                uid = Ids.uid(),
                role = "assistant",
                content = text.ifBlank { "（AI 没有返回内容）" },
                createdAt = System.currentTimeMillis(),
            )
        )
    }

    // ---------- 执行 ----------

    private suspend fun executeAndReport(actions: List<AiAction>) {
        _busy.value = true
        try {
            val result = executor.execute(actions)
            insertAssistant(result.receipt())
        } catch (e: Exception) {
            _error.value = e.message ?: "执行失败"
        } finally {
            _busy.value = false
        }
    }

    /** 拼当前数据快照给 AI。它得拿到"真实名称"才能匹配 */
    private suspend fun buildSnapshot(): Triple<String, String, String> {
        val today = todayProvider()
        val taskList = tasks.getByDate(today)
        val planList = plans.getAll()
        val courseList = courses.getAll()

        val tasksText = AiChatLogic.snapshot(
            tasks = taskList.map { it.title to (it.time ?: "") },
            plans = planList.map { it.title },
            courses = emptyList(),
        )
        val plansText = if (planList.isEmpty()) "（无）" else planList.joinToString("；") {
            "${it.title} ${it.cadence}"
        }
        val coursesText = if (courseList.isEmpty()) "（无）" else courseList.joinToString("；") {
            "周${it.weekday + 1} ${it.time ?: "未排"} ${it.title}"
        }

        // 任务那段只取第一行，避免重复拼
        return Triple(tasksText.lines().firstOrNull().orEmpty(), plansText, coursesText)
    }

    // ---------- 清空对话 ----------

    fun clearChat() {
        viewModelScope.launch {
            chatDao.deleteAll()
            _pending.value = PendingState.None
        }
    }

    /** 测试连接 */
    fun testConnection(config: AiConfig, onResult: (Result<String>) -> Unit) {
        viewModelScope.launch {
            onResult(AiClient(config, httpCaller).testConnection())
        }
    }

    class Factory(
        private val tasks: TaskRepository,
        private val plans: LongTermPlanRepository,
        private val courses: CourseRepository,
        private val settings: SettingsRepository,
        private val chatDao: ChatLogDao,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            AiViewModel(tasks, plans, courses, settings, chatDao) as T
    }
}
