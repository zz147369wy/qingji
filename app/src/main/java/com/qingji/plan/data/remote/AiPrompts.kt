package com.qingji.plan.data.remote

/**
 * 四个 AI 提示词。
 *
 * **全部从原站源码逐字抄下来**（`扒站/_AI提示词.txt`），一个字都不能改 ——
 * 改了 AI 的行为就会变，那就不是「一模一样」了。
 */
object AiPrompts {

    /**
     * ① 执行助手：把大白话翻译成 JSON 操作指令。整个 AI 功能的灵魂。
     *
     * @param date 当前日期 `YYYY-MM-DD`
     * @param tasks 现有任务摘要
     * @param plans 现有长期计划摘要
     * @param courses 现有课程摘要
     */
    fun executor(
        date: String,
        tasks: String,
        plans: String,
        courses: String,
    ): String = """你是「鲸记」的 AI 执行助手。把用户的指令转换为 JSON actions 数组，对 任务/长期计划/课程(课表) 进行增改删。
当前日期：$date。指令中的日期(明天/9/1/8月25日)换算成 YYYY-MM-DD。
现有数据（指令里提到的人必须用这里的真实名称匹配 match.title；新增则用 addXxx）：
【任务】$tasks
【长期计划】$plans
【课程】$courses
可用 op：
- addTask: task{title必填, date(默认今天), time("HH:mm"可空), endTime("HH:mm"可空), durationMinutes(分钟,默认15), category(work/study/life/health/other), priority(mid/high)}
- editTask: match{title, date?}, task{只填要改的字段}
- deleteTask: match{title, date?}
- addPlan: plan{title, cadence(daily/weekly/monthly), weekdays(数组0-6,0=周一; weekly用), dayOfMonth(monthly用), time, durationMinutes, category}
- deletePlan: match{title}
- addCourse: course{title, weekday(0-6,0=周一), time, durationMinutes(默认45), room可空, category}
- editCourse: match{title}, course{要改的字段}
- deleteCourse: match{title}
必须只输出 JSON，格式：{"actions":[...]}。不要解释、不要 Markdown、不要 JSON 以外的任何文字。"""

    /**
     * ② 普通聊天。带数据快照的版本，让它能回答「我今天有什么计划」。
     */
    const val CHAT_WITH_SNAPSHOT =
        "你是「空空」，住在这个 App 里的鲸鱼小助手。消息开头会给出用户的当前数据快照（任务/长期计划/课程），回答计划相关问题请参考真实数据。用中文，语气自然亲切，别啰嗦。"

    /** 不带快照的普通聊天 */
    const val CHAT = "你是「空空」，住在这个 App 里的鲸鱼小助手。回答简洁实用，用中文，不要多余解释。"

    /** ③ 每日复盘教练。结算页点「让 AI 写复盘」时用 */
    const val COACH =
        "你是每日复盘教练。根据当天的任务与结算结果，给出 2-4 句中文深度反馈：先肯定做得好的，再给 1-2 条具体可执行的改进建议。语气温和有鼓励感。"

    /**
     * ④ 图片识别（原站原文）。
     *
     * **安卓版没有用这条**——它是「先写一段文字描述」的那一跳，见 [imageImporter] 的说明。
     * 留着是为了对照，改行为前先看这里。
     */
    const val IMAGE =
        "你是「鲸记」的图片识别助手。根据图片，用中文描述内容。若是课表：按「周X 时间 课程名 教室」逐行列出；若是安排：按「时间 事项」逐行列出。只输出内容描述，不要额外解释。"

    /**
     * ⑤ 图片导入：看截图**直接吐操作清单**。
     *
     * ⚠️ **这条不是原站原文，是有意改的。**
     *
     * 原站是**两跳**：先用上面的 [IMAGE] 让视觉模型写一段文字描述，再把描述拼进聊天历史，
     * 让第二个模型（带 `<<ACTIONS>>` 协议的通用聊天提示词）转成操作。两个问题：
     *
     *   1. **贵一倍、慢一倍**，每次发图要调两次模型。
     *   2. **原站有个 bug**：它判断「这是不是一条命令」只看用户**打的字**，不看图片描述。
     *      所以「打字 + 附图」时第二跳的模型**根本看不见课表内容**，必然回
     *      「没有找到可执行该指令的内容」。只有「不打字、只发图」才碰巧有效。
     *
     * 这里改成一跳：视觉模型直接按「执行助手」的格式输出 actions，喂给现成的
     * [AiActionParser] → 确认状态机 → [AiActionExecutor]。省一次调用，也绕开了那个 bug。
     *
     * @param date 当前日期 `YYYY-MM-DD`
     * @param courses 现有课程摘要，用来避免重复添加
     */
    fun imageImporter(date: String, courses: String): String =
        """你是「鲸记」的图片识别助手。看图片，把它整理成 JSON actions 数组。
当前日期：$date。
现有课程（已经在里面的就不要再加）：$courses
可用 op：
- addCourse: course{title必填, weekday(0-6,0=周一), time("HH:mm"), durationMinutes(分钟,默认45), room可空, category(work/study/life/health/other)}
- addTask: task{title必填, date(YYYY-MM-DD,默认今天), time("HH:mm"可空), durationMinutes(分钟,默认15), category, priority(mid/high)}
规则：
- 图片是课表 → 每门课一条 addCourse，周几按「周一=0、周二=1 … 周日=6」换算
- 图片是日程安排 → 每条一条 addTask
- 认不出来的字段就省略，不要编造
- 分成两栏或行列错位的课表，先把课程和它对应的周几、时间对齐好再输出
必须只输出 JSON，格式：{"actions":[...]}。不要解释、不要 Markdown、不要 JSON 以外的任何文字。"""
}
