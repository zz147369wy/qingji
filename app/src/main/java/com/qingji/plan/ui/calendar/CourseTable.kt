package com.qingji.plan.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qingji.plan.data.db.entity.CourseEntity
import com.qingji.plan.data.model.PeriodSchedules
import com.qingji.plan.data.model.SchoolPeriod
import com.qingji.plan.ui.components.QingJiCard
import com.qingji.plan.ui.components.QingJiChip
import com.qingji.plan.ui.components.dimIndication
import com.qingji.plan.ui.course.CourseEditLogic
import com.qingji.plan.ui.haptics.HapticCue
import com.qingji.plan.ui.haptics.LocalHaptics
import com.qingji.plan.ui.theme.AppText
import com.qingji.plan.ui.theme.Dimens
import com.qingji.plan.ui.theme.LocalPalette
import com.qingji.plan.ui.theme.ThemePalette
import com.qingji.plan.util.DateUtils
import java.time.LocalDate

/**
 * 周课表。
 *
 * **一节一行，行行等高**。左边那列写「节次号 + 起 + 止」，右边 7 列是周一到周日。
 *
 * 作息（几点到几点算第几节）是学校的、因人而异、换学期还会变，所以存在设置里，
 * 默认给的是 [PeriodSchedules.DEFAULT]。
 *
 * 课程**不作绝对时间定位**——用户说过他的课永远落在作息这几节里，
 * 而且嫌背景格子太多。所以一门课直接归到「它的开始时间落在哪一节」那一行，
 * 一格填满。落不进任何一节的课不会丢，单独列在课表下面。
 *
 * 每门课的颜色**按课程取**（见 [CourseColors]），不是按分类。
 */
@Composable
fun CourseTable(
    courses: List<CourseEntity>,
    modifier: Modifier = Modifier,
    periods: List<SchoolPeriod> = PeriodSchedules.DEFAULT,
    /** 用来在日期行里高亮今天。注入是为了能测 */
    today: LocalDate = LocalDate.now(),
    onCourseClick: (CourseEntity) -> Unit = {},
    onAddCourse: () -> Unit = {},
) {
    val palette = LocalPalette.current
    val haptics = LocalHaptics.current

    val rows = remember(courses, periods) { CourseTableLayout.rows(courses, periods) }
    val noTime = remember(courses) { CourseEditLogic.withoutTime(courses) }
    val outside = remember(courses, periods) { CourseTableLayout.outside(courses, periods) }

    Column(modifier.testTag("course_table")) {
        if (courses.isEmpty()) {
            EmptyCourses(palette)
            return@Column
        }

        // 提示行
        Text(
            "点课程块可修改/删除；右下角 ➕ 新建课程",
            style = AppText.sub,
            color = palette.text2,
            modifier = Modifier.padding(bottom = 8.dp),
        )

        QingJiCard(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(8.dp),
        ) {
            Header(today, palette)

            // ---------- 一节一行 ----------
            rows.forEach { row ->
                PeriodRowView(
                    row = row,
                    palette = palette,
                    onCourseClick = { course ->
                        haptics.perform(HapticCue.Tap)
                        onCourseClick(course)
                    },
                )
            }
        }

        // ---------- 不在作息里的课 ----------
        // 用户说不会有这种课，但真出现了不能藏起来，不然就是静默丢数据
        if (outside.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            CourseChipsSection(
                title = "不在作息里的课（时间对不上任何一节）：",
                courses = outside,
                showTime = true,
                onCourseClick = onCourseClick,
            )
        }

        // ---------- 未排固定时间 ----------
        if (noTime.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            CourseChipsSection(
                title = "未排固定时间：",
                courses = noTime,
                showTime = false,
                onCourseClick = onCourseClick,
            )
        }
    }
}

// ---------------------------------------------------------------- 表头

/**
 * 表头两行：第一行是本周日期（今天高亮），第二行是「节次 / 一二三四五六日」。
 */
@Composable
private fun Header(today: LocalDate, palette: ThemePalette) {
    val weekDays = remember(today) { DateUtils.weekOf(today) }

    Row(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "${today.monthValue}月",
            style = AppText.sub.copy(fontSize = 10.sp),
            color = palette.text3,
            modifier = Modifier.width(TIME_COLUMN_WIDTH.dp),
        )
        weekDays.forEach { day ->
            DayDateCell(
                dayOfMonth = day.dayOfMonth,
                isToday = day == today,
                modifier = Modifier.weight(1f),
            )
        }
    }

    Row(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "节次",
            style = AppText.sub.copy(fontSize = 10.sp),
            color = palette.text3,
            modifier = Modifier.width(TIME_COLUMN_WIDTH.dp),
        )
        WEEKDAY_LABELS.forEach { label ->
            Text(
                text = label,
                style = AppText.sub.copy(fontSize = 10.sp),
                color = palette.text3,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** 日期行里的一格。今天画成圆圈高亮——用户视频里就是这么标今天的 */
@Composable
private fun DayDateCell(
    dayOfMonth: Int,
    isToday: Boolean,
    modifier: Modifier = Modifier,
) {
    val palette = LocalPalette.current
    Box(modifier, contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(if (isToday) palette.surface else Color.Transparent)
                .then(if (isToday) Modifier.shadow(2.dp, CircleShape) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "$dayOfMonth",
                style = AppText.sub.copy(
                    fontSize = 11.sp,
                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                ),
                color = if (isToday) palette.primaryDeep else palette.text3,
            )
        }
    }
}

// ---------------------------------------------------------------- 一行

@Composable
private fun PeriodRowView(
    row: PeriodRow,
    palette: ThemePalette,
    onCourseClick: (CourseEntity) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(PERIOD_ROW_HEIGHT.dp)
            .testTag("ct_row_${row.period.index}"),
        verticalAlignment = Alignment.Top,
    ) {
        // ---- 左边：节次号 + 起 + 止 ----
        Column(
            Modifier
                .width(TIME_COLUMN_WIDTH.dp)
                .padding(top = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "${row.period.index}",
                style = AppText.sub.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold),
                color = palette.text2,
            )
            Text(
                text = row.period.startLabel,
                style = AppText.sub.copy(fontSize = 10.sp),
                color = palette.text3,
            )
            Text(
                text = row.period.endLabel,
                style = AppText.sub.copy(fontSize = 10.sp),
                color = palette.text3,
            )
        }

        // ---- 右边：7 天 ----
        (0..6).forEach { weekday ->
            val cellCourses = row.cells[weekday]
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(vertical = 3.dp),
            ) {
                // 这个格子左边那条竖线
                Box(
                    Modifier
                        .align(Alignment.CenterStart)
                        .width(1.dp)
                        .fillMaxHeight()
                        .background(palette.line.copy(alpha = 0.5f)),
                )

                // 有课就并排分栏。用 weight 分，几门就几栏，不用自己算像素
                Row(
                    Modifier
                        .fillMaxWidth()
                        .fillMaxHeight()
                        .padding(start = 1.dp),
                ) {
                    cellCourses.forEach { course ->
                        val color = CourseColors.colorFor(course)
                        // 每块课程要有自己的 interactionSource：共用一个会让同格里
                        // 几门课一起压暗（跟 SegmentedControl 里那条注释同一个坑）
                        val chipShape = RoundedCornerShape(6.dp)
                        val chipInteraction = remember { MutableInteractionSource() }
                        Box(
                            Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .padding(horizontal = 1.dp)
                                .clip(chipShape)
                                .background(color.soft)
                                .clickable(
                                    interactionSource = chipInteraction,
                                    indication = dimIndication(chipShape),
                                    onClick = { onCourseClick(course) },
                                )
                                .padding(horizontal = 3.dp, vertical = 3.dp)
                                .testTag("ct_chip_${course.uid}"),
                        ) {
                            Text(
                                text = if (course.room.isNullOrBlank()) {
                                    course.title
                                } else {
                                    "${course.title}\n${course.room}"
                                },
                                style = AppText.sub.copy(fontSize = COURSE_TEXT_SIZE.sp),
                                color = color.deep,
                                maxLines = 5,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }

    // 行底部的横线
    Box(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(palette.line.copy(alpha = 0.5f)),
    )
}

// ---------------------------------------------------------------- 底部的两个小列表

@Composable
private fun CourseChipsSection(
    title: String,
    courses: List<CourseEntity>,
    showTime: Boolean,
    onCourseClick: (CourseEntity) -> Unit,
) {
    val palette = LocalPalette.current
    val haptics = LocalHaptics.current

    QingJiCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("ct_notime"),
        shape = Dimens.radiusSm.dp,
        contentPadding = PaddingValues(12.dp),
    ) {
        Text(title, style = AppText.sub, color = palette.text2)
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            courses.forEach { course ->
                val color = CourseColors.colorFor(course)
                QingJiChip(
                    label = if (showTime && !course.time.isNullOrBlank()) {
                        "${course.title} ${course.time}"
                    } else {
                        course.title
                    },
                    container = color.soft,
                    contentColor = color.deep,
                    onClick = {
                        haptics.perform(HapticCue.Tap)
                        onCourseClick(course)
                    },
                )
            }
        }
    }
}

@Composable
private fun EmptyCourses(palette: ThemePalette) {
    QingJiCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(vertical = 26.dp),
    ) {
        // QingJiCard 内层是个 Column 且没暴露横向对齐 —— 自己套一层把空状态居中
        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("📋", fontSize = 30.sp)
            Spacer(Modifier.height(6.dp))
            Text(
                "还没有课程，点右下角 ➕ 新建（顶部切「课程」）",
                style = AppText.sub,
                color = palette.text3,
            )
        }
    }
}

// ---------------------------------------------------------------- 常量

/** 左边那列（节次号 + 起止时间）多宽 */
private const val TIME_COLUMN_WIDTH = 44

/** 一节多高。用户要求把课块调大，所以从 40 → 90 */
private const val PERIOD_ROW_HEIGHT = 90

/** 课程块字号。用户要求调大，8 → 11 */
private const val COURSE_TEXT_SIZE = 11

/** 原站课表表头用的就是单字 */
private val WEEKDAY_LABELS = listOf("一", "二", "三", "四", "五", "六", "日")
