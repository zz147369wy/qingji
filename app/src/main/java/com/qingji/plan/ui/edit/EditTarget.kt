package com.qingji.plan.ui.edit

import com.qingji.plan.ui.course.CourseEditState
import com.qingji.plan.ui.today.TaskEditState

/**
 * 弹层里的两个 Tab。原站叫 `mode`，值是 `"task"` / `"course"`，
 * 但显示出来是「📝 计划」和「📚 课程」——所以这里沿用原站的内部值。
 */
enum class EditMode { TASK, COURSE }

/**
 * 打开弹层时到底要干什么。
 *
 * 原站是**一个**组件 `sd({defaultMode, editCourse, ...})`，靠两个 prop 区分四种情况。
 * 这里用密封类把它写清楚，顺便把「Tab 能不能切」这件事编码进去：
 * **新建时两个 Tab 随便切，编辑时锁死在那一类**（原站编辑课程时 Tab 仍在，
 * 但切过去就丢了正在编辑的课程，所以这里锁掉）。
 */
sealed interface EditTarget {

    /** 打开时的 Tab */
    val mode: EditMode

    /** 编辑已有条目时为 true：Tab 锁死，按钮文案变「保存修改」，多一个删除按钮 */
    val isEditing: Boolean

    /** 今日页 / 日历页点「+」新建任务 */
    data class NewTask(val state: TaskEditState) : EditTarget {
        override val mode = EditMode.TASK
        override val isEditing = false
    }

    /** 点时间轴上的任务块编辑 */
    data class EditTask(val state: TaskEditState) : EditTarget {
        override val mode = EditMode.TASK
        override val isEditing = true
    }

    /** 课程页点「+」新建课程 */
    data class NewCourse(val state: CourseEditState) : EditTarget {
        override val mode = EditMode.COURSE
        override val isEditing = false
    }

    /** 点课表里的课编辑 */
    data class EditCourse(val state: CourseEditState) : EditTarget {
        override val mode = EditMode.COURSE
        override val isEditing = true
    }

    /** 现在正在编辑的任务状态；不在编辑任务就是 null */
    val taskState: TaskEditState?
        get() = when (this) {
            is NewTask -> state
            is EditTask -> state
            else -> null
        }

    /** 现在正在编辑的课程状态；不在编辑课程就是 null */
    val courseState: CourseEditState?
        get() = when (this) {
            is NewCourse -> state
            is EditCourse -> state
            else -> null
        }
}
