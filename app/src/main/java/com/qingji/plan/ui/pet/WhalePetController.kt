package com.qingji.plan.ui.pet

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/**
 * 让空空的**身体**对外面的操作有反应。
 *
 * 【2026-10 鲸鱼主题】她住在这个 App 里，所以该对这里发生的事有反应：
 * 你完成一件事她跳一下，全部完成她转个圈，你删掉一条她摇摇头。
 *
 * 页面（今日页、编辑弹层…）只管往这里 [emit] 一个事件，真正「怎么动、说什么」
 * 全在 [WhalePetOverlay] 里决定 —— 页面不需要知道鲸鱼接口长什么样。
 */
class WhalePetController {

    private val _events = MutableSharedFlow<PetEvent>(extraBufferCapacity = 16)

    val events: SharedFlow<PetEvent> = _events

    fun emit(event: PetEvent) {
        _events.tryEmit(event)
    }
}

/** 空空会对哪些事有反应 */
enum class PetEvent {
    /** 完成了一件任务 */
    TASK_DONE,

    /** 今天全部完成了 */
    ALL_DONE,

    /** 删掉了一条任务 */
    DELETED,

    /** 新建了一条任务 */
    ADDED,

    /** 进入 App 时的问候 */
    GREET,

    /** 深夜还开着 App */
    LATE_NIGHT,
}
