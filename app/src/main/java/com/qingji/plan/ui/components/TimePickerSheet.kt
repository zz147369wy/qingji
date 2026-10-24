package com.qingji.plan.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qingji.plan.ui.haptics.HapticCue
import com.qingji.plan.ui.haptics.LocalHaptics
import com.qingji.plan.ui.theme.LocalPalette
import kotlin.math.abs

/**
 * 「选择时间」面板。
 *
 * 逐行复刻原站的 `dy` 组件（`扒站/app.js`）：
 * ```
 * 取消            选择时间            确定
 * ┌──────────────────────────────────┐   bg #fbf3e0 / border #F0E2BE / radius 18
 * │        09  :  00                 │   双列滚轮，高 176，item 高 44，上下各留 66
 * │ 持续  [15分][30分][45分][1时]     │
 * │       [1.5时][2时][3时][4时]      │
 * │ 自定义 [    ] 分钟                │
 * └──────────────────────────────────┘
 * ```
 *
 * 原站的打开逻辑（照抄，别改）：
 *   - 没传值就当 `09:00`
 *   - 时长优先从 `endValue - value` 反推，但只在 **0 < 差值 ≤ 240 分钟** 时采用，否则回落 15
 *
 * 比原站多的只有一个「清除时间」：原站一旦选过时间就再也清不掉，
 * 而安卓版的「🕊 随时」区需要它。位置放在滚轮框下面，不影响头部三栏的 1:1。
 */
@Composable
fun TimePickerSheet(
    /** 当前时间 `HH:mm`，空串表示还没选过 */
    value: String,
    /** 当前结束时间，用来反推时长。没有就传 null */
    endValue: String?,
    onDismiss: () -> Unit,
    /** 确定：开始时间 + 时长（结束时间由调用方按时长算） */
    onConfirm: (time: String, durationMinutes: Int) -> Unit,
    /** 清除时间，把任务/课程变回「随时」 */
    onClear: () -> Unit,
) {
    val palette = LocalPalette.current
    val haptics = LocalHaptics.current

    val start = value.takeIf { it.isNotBlank() } ?: DEFAULT_TIME
    val hours = remember { (0..23).map { pad(it) } }
    val minutes = remember { (0..59).map { pad(it) } }

    var hour by remember { mutableStateOf(start.substringBefore(":")) }
    var minute by remember { mutableStateOf(start.substringAfter(":")) }
    var duration by remember { mutableStateOf(initialDuration(value, endValue)) }
    var custom by remember { mutableStateOf("") }

    TopSheet(onDismiss = onDismiss, testTag = "time_picker_sheet") {
        Column(Modifier.fillMaxWidth().testTag("time_picker")) {

            // ---------- 头部：取消 / 选择时间 / 确定 ----------
            Row(
                Modifier.fillMaxWidth().padding(bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                PickerHeadButton(
                    text = "取消",
                    color = palette.text2,
                    modifier = Modifier.testTag("picker_cancel"),
                    onClick = onDismiss,
                )
                Text(
                    text = "选择时间",
                    style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold),
                    color = palette.text,
                )
                PickerHeadButton(
                    text = "确定",
                    color = palette.primaryDeep,
                    bold = true,
                    modifier = Modifier.testTag("picker_confirm"),
                    onClick = {
                        haptics.perform(HapticCue.Commit)
                        onConfirm("$hour:$minute", duration)
                    },
                )
            }

            // ---------- 奶黄滚轮箱 ----------
            Column(
                Modifier
                    .padding(top = 8.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(palette.scheme.surfaceContainerHighest)
                    .border(1.dp, palette.line, RoundedCornerShape(18.dp))
                    .padding(start = 8.dp, end = 8.dp, top = 16.dp, bottom = 10.dp),
            ) {
                // 双列滚轮
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Wheel(
                        values = hours,
                        initialIndex = hours.indexOf(hour).coerceAtLeast(0),
                        modifier = Modifier.testTag("wheel_hour"),
                    ) { hour = hours[it] }
                    Text(
                        text = ":",
                        style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold),
                        color = palette.text3,
                        modifier = Modifier.padding(horizontal = 2.dp),
                    )
                    Wheel(
                        values = minutes,
                        initialIndex = minutes.indexOf(minute).coerceAtLeast(0),
                        modifier = Modifier.testTag("wheel_minute"),
                    ) { minute = minutes[it] }
                }

                // 持续
                Row(
                    Modifier.padding(top = 14.dp, start = 8.dp, end = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = "持续",
                        style = TextStyle(fontSize = 12.sp),
                        color = palette.text3,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                    // 原站是 `flex-wrap` 自然折行，在它那个窄视口里正好断成 4+4；
                    // 安卓屏幕更宽会挤成一行，所以这里按行切，保证跟原站一样是两行。
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        DURATION_PRESETS.chunked(DURATION_PER_ROW).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                row.forEach { (mins, label) ->
                                    DurationChip(
                                        label = label,
                                        active = duration == mins,
                                        modifier = Modifier.testTag("dur_$mins"),
                                        onClick = {
                                            haptics.perform(HapticCue.Tap)
                                            duration = mins
                                            custom = ""
                                        },
                                    )
                                }
                            }
                        }
                    }
                }

                // 自定义分钟
                Row(
                    Modifier.padding(top = 14.dp, start = 8.dp, end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = "自定义",
                        style = TextStyle(fontSize = 12.sp),
                        color = palette.text3,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                    Box(
                        Modifier
                            .width(76.dp)
                            .clip(RoundedCornerShape(9.dp))
                            .background(palette.surface2)
                            .border(1.dp, palette.line, RoundedCornerShape(9.dp))
                            .padding(horizontal = 9.dp, vertical = 6.dp),
                    ) {
                        if (custom.isEmpty()) {
                            Text(
                                text = "分钟",
                                style = TextStyle(fontSize = 13.sp),
                                color = palette.text3,
                            )
                        }
                        BasicTextField(
                            value = custom,
                            onValueChange = { raw ->
                                val digits = raw.filter { it.isDigit() }.take(3)
                                custom = digits
                                digits.toIntOrNull()
                                    ?.coerceIn(MIN_CUSTOM, MAX_CUSTOM)
                                    ?.let { duration = it }
                            },
                            singleLine = true,
                            textStyle = TextStyle(fontSize = 13.sp, color = palette.text),
                            cursorBrush = SolidColor(palette.primary),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth().testTag("dur_custom"),
                        )
                    }
                    Text(
                        text = "分钟",
                        style = TextStyle(fontSize = 11.sp),
                        color = palette.text2,
                    )
                }
            }

            // ---------- 清除时间（原站没有，见类注释） ----------
            if (value.isNotBlank()) {
                Box(
                    Modifier
                        .align(Alignment.End)
                        .padding(top = 10.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            haptics.perform(HapticCue.Tap)
                            onClear()
                        }
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                        .testTag("picker_clear"),
                ) {
                    Text(
                        text = "清除时间",
                        style = TextStyle(fontSize = 12.sp),
                        color = palette.text3,
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------- 零件

/** 原站 `.picker-head-btn`：无底、15px、左右 padding 6/10 */
@Composable
private fun PickerHeadButton(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    bold: Boolean = false,
    onClick: () -> Unit,
) {
    Box(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Text(
            text = text,
            style = TextStyle(
                fontSize = 15.sp,
                fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
            ),
            color = color,
        )
    }
}

/**
 * 滚轮。
 *
 * 原站是 `overflow-y:auto` + `scroll-snap-type:y mandatory` + `scroll-snap-align:center`，
 * 这里用 LazyColumn + `rememberSnapFlingBehavior` 等价实现。
 * 尺寸照抄：可视高 176、每项 44、上下各留 66（= (176-44)/2）。
 */
@Composable
private fun Wheel(
    values: List<String>,
    initialIndex: Int,
    modifier: Modifier = Modifier,
    onSelect: (Int) -> Unit,
) {
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)
    val haptics = LocalHaptics.current

    // 正中间那一项。用「离视口中点最近」来判定，不依赖 firstVisibleItemIndex 的 padding 语义
    val centered by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val mid = (info.viewportStartOffset + info.viewportEndOffset) / 2f
            info.visibleItemsInfo
                .minByOrNull { abs((it.offset + it.size / 2f) - mid) }
                ?.index
                ?: initialIndex
        }
    }

    // 滚停之后才回调，避免拖动过程中疯狂改状态
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress to centered }
            .collect { (scrolling, index) ->
                if (!scrolling) {
                    haptics.perform(HapticCue.Tap)
                    onSelect(index)
                }
            }
    }

    val palette = LocalPalette.current

    Box(
        modifier
            .width(52.dp)
            .height(ITEM_HEIGHT.dp * VISIBLE_ITEMS),
    ) {
        LazyColumn(
            state = listState,
            flingBehavior = rememberSnapFlingBehavior(listState),
            contentPadding = PaddingValues(vertical = (ITEM_HEIGHT.dp * (VISIBLE_ITEMS - 1)) / 2),
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxSize(),
        ) {
            itemsIndexed(values) { index, label ->
                val active = index == centered
                Box(
                    Modifier.fillMaxWidth().height(ITEM_HEIGHT.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label,
                        style = TextStyle(
                            fontSize = if (active) 21.sp else 17.sp,
                            fontWeight = if (active) FontWeight.ExtraBold else FontWeight.Normal,
                            // tabular-nums：等宽数字，滚动时不会左右抖
                            fontFeatureSettings = "tnum",
                        ),
                        color = if (active) palette.text else palette.text3,
                        modifier = Modifier.alpha(if (abs(index - centered) > 1) 0.4f else 1f),
                    )
                }
            }
        }

        // 中间的高亮带，画在文字之上（原站 `.wheel-band` 也是叠在上层）
        Box(
            Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .height(ITEM_HEIGHT.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(palette.primary.copy(alpha = 0.07f))
                .border(1.dp, palette.primary.copy(alpha = 0.18f), RoundedCornerShape(12.dp)),
        )
    }
}

/** 原站 `.dur-chip` */
@Composable
private fun DurationChip(
    label: String,
    active: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val palette = LocalPalette.current
    val shape = RoundedCornerShape(999.dp)
    Box(
        modifier
            .clip(shape)
            .background(if (active) palette.primary else palette.surface)
            .border(1.dp, if (active) palette.primary else palette.line, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 5.dp),
    ) {
        Text(
            text = label,
            style = TextStyle(
                fontSize = 12.sp,
                fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
            ),
            color = if (active) palette.scheme.onPrimary else palette.text2,
        )
    }
}

// ---------------------------------------------------------------- 常量与纯函数

private const val DEFAULT_TIME = "09:00"

/** 「持续」档位每行 4 个，排成原站那样的 4+4 */
private const val DURATION_PER_ROW = 4
private const val ITEM_HEIGHT = 44
private const val VISIBLE_ITEMS = 4
private const val MIN_CUSTOM = 5
private const val MAX_CUSTOM = 480

/** 原站 `zw` 数组，顺序和文案一字不差 */
private val DURATION_PRESETS = listOf(
    15 to "15分",
    30 to "30分",
    45 to "45分",
    60 to "1时",
    90 to "1.5时",
    120 to "2时",
    180 to "3时",
    240 to "4时",
)

private fun pad(value: Int): String = if (value < 10) "0$value" else "$value"

/**
 * 从「已有的起止时间」反推时长。
 *
 * 原站的规则（照抄）：只有当 `0 < 结束 - 开始 ≤ 240 分钟` 时才用它，
 * 否则一律回落 15 分钟。跨零点用取模处理。
 */
internal fun initialDuration(value: String, endValue: String?): Int {
    if (value.isBlank() || endValue.isNullOrBlank()) return 15
    val start = minutesOf(value) ?: return 15
    val end = minutesOf(endValue) ?: return 15
    val diff = ((end - start) % 1440 + 1440) % 1440
    return if (diff in 1..240) diff else 15
}

private fun minutesOf(hhmm: String): Int? {
    val parts = hhmm.split(":")
    if (parts.size != 2) return null
    val h = parts[0].toIntOrNull() ?: return null
    val m = parts[1].toIntOrNull() ?: return null
    return h * 60 + m
}

/** 仅供测试用：把时长换算成结束时间，等价于原站的 `Bw()` */
internal fun endTimeOf(start: String, durationMinutes: Int): String {
    val base = minutesOf(start) ?: return start
    val total = (base + durationMinutes) % 1440
    return "${pad(total / 60)}:${pad(total % 60)}"
}

/** 给测试留个入口，避免测试去碰 private 常量 */
internal val durationPresetMinutes: List<Int> = DURATION_PRESETS.map { it.first }
