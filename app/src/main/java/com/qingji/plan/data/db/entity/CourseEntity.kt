package com.qingji.plan.data.db.entity

import androidx.room.Entity
import kotlinx.serialization.Serializable
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 课程（课表里的一节课）。
 *
 * 对应原站的 courses 表。`weekday` 为 0 表示周一。
 * 课程是「影子」数据——它不在任务列表里，只自动铺到时间轴和课表上。
 */
@Entity(
    tableName = "courses",
    indices = [Index("uid"), Index("weekday"), Index("deletedAt")],
)
@Serializable
data class CourseEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val uid: String,
    val title: String,
    /** 周几，0 = 周一 */
    val weekday: Int,
    /** 上课时间 `HH:mm`，为空表示「未排固定时间」 */
    val time: String? = null,
    /** 时长（分钟）。原站课程默认 45 分钟 */
    val durationMinutes: Int = 45,
    /** 教室，可空 */
    val room: String? = null,
    val category: String = "study",

    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
) {
    val hasTime: Boolean get() = !time.isNullOrBlank()

    /** 课表格子里显示的文字，如「高等数学 ·A301」 */
    val displayLabel: String
        get() = if (room.isNullOrBlank()) title else "$title ·$room"

    fun softDeleted(at: Long = System.currentTimeMillis()): CourseEntity =
        copy(deletedAt = at, updatedAt = at)

    companion object {
        /** 周一 = 0 …… 周日 = 6（原站约定） */
        val WEEKDAY_LABELS = listOf("一", "二", "三", "四", "五", "六", "日")
    }
}
