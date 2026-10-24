package com.qingji.plan.ui.ai

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import com.qingji.plan.ui.components.stickerClickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.net.Uri
import coil.compose.AsyncImage
import com.qingji.plan.data.db.entity.ChatLogEntity
import com.qingji.plan.ui.components.AppIcons
import com.qingji.plan.ui.components.QingJiChip
import com.qingji.plan.ui.components.QingJiIconButton
import com.qingji.plan.ui.components.dimIndication
import com.qingji.plan.ui.components.sticker
import com.qingji.plan.ui.pet.petDock
import com.qingji.plan.ui.haptics.HapticCue
import com.qingji.plan.ui.haptics.LocalHaptics
import com.qingji.plan.ui.theme.AppText
import com.qingji.plan.ui.theme.Dimens
import com.qingji.plan.ui.theme.LocalPalette

/** 聊天记录里「等着用户点头」那个状态值，跟 ChatLogDao 里的查询串一致 */
private const val ACTION_PENDING = "pending"

/**
 * AI 聊天页 —— 和空空聊天（设计稿第 6 屏）。
 *
 * 【2026-10 鲸鱼主题】
 *   - 页顶是**通栏的水族箱**：一截海水渐变，下沿一排扇贝边；左上返回、右上清空；
 *     左下是超大的「空空」二字 + 在线状态，她本人站在右边（桌宠覆盖层画上去，这里只登记窝）
 *   - 她的每条消息前面挂头像，气泡是白色贴纸；你的消息是主色贴纸
 *   - 「确认执行 / 取消」是气泡里的两颗贴纸胶囊
 *   - 底部输入栏：图片 · 输入框 · 发送
 */
@Composable
fun AiScreen(
    messages: List<ChatLogEntity>,
    draft: String,
    busy: Boolean,
    error: String?,
    modifier: Modifier = Modifier,
    /** 已经选了、还没发的图。非空时输入区上方会显示缩略图 */
    pendingImage: Uri? = null,
    onDraftChange: (String) -> Unit = {},
    onSend: () -> Unit = {},
    onClear: () -> Unit = {},
    onPickImage: () -> Unit = {},
    onClearImage: () -> Unit = {},
    /** 点气泡上的「确认执行」 */
    onConfirmActions: () -> Unit = {},
    /** 点气泡上的「取消」 */
    onCancelActions: () -> Unit = {},
    onDismissError: () -> Unit = {},
    onBack: () -> Unit = {},
) {
    val palette = LocalPalette.current
    val haptics = LocalHaptics.current
    val listState = rememberLazyListState()

    // 有字或有图就能发
    val canSend = draft.isNotBlank() || pendingImage != null

    // 新消息进来时滚到底
    LaunchedEffect(messages.size, busy) {
        val count = listState.layoutInfo.totalItemsCount
        if (count > 0) listState.animateScrollToItem(count - 1)
    }

    Column(
        Modifier
            .fillMaxSize()
            // ★ 必须有不透明背景！AI 页是整页叠在别的页面之上，没有 Scaffold 背景兜底
            .background(palette.bg)
            .testTag("ai_screen"),
    ) {
        Tank(
            onBack = {
                haptics.perform(HapticCue.Tap)
                onBack()
            },
            onClear = {
                haptics.perform(HapticCue.Tap)
                onClear()
            },
        )

        error?.let {
            // 点一下收起
            val shape = RoundedCornerShape(14.dp)
            val interaction = remember { MutableInteractionSource() }
            Box(
                Modifier
                    .padding(horizontal = 16.dp)
                    .padding(top = 8.dp)
                    .fillMaxWidth()
                    .sticker(shape = shape, fill = palette.coralSoft, drop = 0.dp)
                    .clickable(
                        interactionSource = interaction,
                        indication = dimIndication(shape),
                        onClick = onDismissError,
                    )
                    .padding(10.dp)
                    .testTag("ai_error"),
            ) {
                Text(it, style = AppText.sub, color = palette.coral, fontSize = 12.sp)
            }
        }

        // ---------- 消息列表 ----------
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .testTag("ai_messages"),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // 聊天空着的时候顶上放一条问候。**这条不进数据库** —— 原站就是纯展示的：
            // `i(X.length ? X : [{role:"ai", content:kh}])`，X 是读出来的聊天记录。
            // 所以点「清空」之后它会自己回来，不需要额外塞一条进库。
            if (messages.isEmpty()) {
                item(key = "greeting") {
                    AiMessage(Modifier.testTag("ai_greeting")) {
                        Text(GREETING, style = BubbleText, color = palette.text)
                    }
                }
            }
            items(messages, key = { it.id }) { message ->
                ChatBubble(
                    message = message,
                    onConfirm = onConfirmActions,
                    onCancel = onCancelActions,
                )
            }
            if (busy) {
                item(key = "busy") { ThinkingBubble() }
            }
        }

        // ---------- 待发图片的缩略图 ----------
        if (pendingImage != null) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val previewShape = RoundedCornerShape(Dimens.shapeSm.dp)
                Box(
                    Modifier
                        .size(34.dp)
                        .sticker(shape = previewShape, fill = palette.surface2, drop = 0.dp),
                ) {
                    AsyncImage(
                        model = pendingImage,
                        contentDescription = "待发图片",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("ai_image_preview"),
                    )
                }
                Text(
                    text = "这张图会交给空空认一下",
                    style = AppText.sub,
                    color = palette.text2,
                    modifier = Modifier.weight(1f),
                )
                QingJiChip(
                    label = "移除",
                    onClick = {
                        haptics.perform(HapticCue.Tap)
                        onClearImage()
                    },
                    compact = true,
                    testTag = "ai_image_clear",
                )
            }
        }

        // ---------- 输入栏：图片 · 输入框 · 发送（设计稿 `.composer`） ----------
        Row(
            Modifier
                .fillMaxWidth()
                .drawBehind {
                    val w = 1.5.dp.toPx()
                    drawLine(
                        palette.line, Offset(0f, w / 2), Offset(size.width, w / 2), strokeWidth = w,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())),
                    )
                }
                .padding(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            RoundButton(
                icon = AppIcons.Image,
                contentDescription = "发图片",
                fill = palette.surface,
                tint = palette.text,
                onClick = {
                    haptics.perform(HapticCue.Tap)
                    onPickImage()
                },
                testTag = "ai_image",
            )

            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = 44.dp)
                    .sticker(shape = RoundedCornerShape(Dimens.shapeFull.dp), fill = palette.surface, drop = 0.dp)
                    .padding(horizontal = 16.dp, vertical = 11.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (draft.isEmpty()) {
                    Text(
                        "聊天，或让我改计划…",
                        style = AppText.sub.copy(fontSize = 14.sp),
                        color = palette.text3,
                        maxLines = 1,
                    )
                }
                BasicTextField(
                    value = draft,
                    onValueChange = onDraftChange,
                    textStyle = AppText.sub.copy(fontSize = 14.sp, color = palette.text),
                    cursorBrush = SolidColor(palette.primary),
                    maxLines = 3,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("ai_input"),
                )
            }

            // 原站的禁用条件是「加载中 或 既没文字也没图」——**只发图不打字是允许的**
            val sendable = canSend && !busy
            RoundButton(
                icon = AppIcons.Send,
                contentDescription = "发送",
                fill = if (sendable) palette.primary else palette.surface2,
                tint = if (sendable) palette.onPrimary else palette.text3,
                enabled = sendable,
                onClick = {
                    haptics.perform(HapticCue.Commit)
                    onSend()
                },
                testTag = "ai_send",
            )
        }
    }
}

// ---------------------------------------------------------------- 水族箱

/** 页顶的水族箱（设计稿 `.tank`）。高度 = 状态栏 + 228dp */
@Composable
private fun Tank(onBack: () -> Unit, onClear: () -> Unit) {
    val palette = LocalPalette.current
    val water = if (palette.isDark) {
        listOf(palette.depths[0], palette.depths[3])
    } else {
        listOf(palette.depths[1], palette.depths[2], palette.depths[3])
    }
    Box(
        Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(water))
            .statusBarsPadding()
            .height(228.dp)
            .petDock(com.qingji.plan.ui.pet.PetDockRegistry.AI)
            .testTag("ai_tank"),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            QingJiIconButton(
                icon = AppIcons.Back,
                contentDescription = "返回",
                onClick = onBack,
                testTag = "ai_back",
            )
            // 原站用的是 lucide 的 Eraser（Q1=_e("Eraser")），不是垃圾桶
            QingJiIconButton(
                icon = AppIcons.Eraser,
                contentDescription = "清空聊天历史",
                onClick = onClear,
                testTag = "ai_clear",
            )
        }

        Column(
            Modifier
                .align(Alignment.BottomStart)
                .padding(start = 22.dp, bottom = 40.dp),
        ) {
            Text(
                text = "空空",
                style = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp),
                color = palette.text,
            )
            Text(
                text = "你的 AI 小助手",
                style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold),
                color = palette.text2,
                modifier = Modifier.padding(bottom = 10.dp),
            )
            Row(
                Modifier
                    .background(
                        if (palette.isDark) palette.surface2 else palette.ink,
                        RoundedCornerShape(999.dp),
                    )
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(7.dp)
                        .background(palette.mint, CircleShape),
                )
                Text(
                    text = "在线 · 可以直接命令我改计划",
                    style = TextStyle(fontSize = 11.5.sp, fontWeight = FontWeight.Bold),
                    color = palette.onDeep,
                    modifier = Modifier.padding(start = 5.dp),
                )
            }
        }

        // 下沿一排扇贝：页面底色往上鼓起的一个个小弧（设计稿 `.scallop`）
        Canvas(
            Modifier
                .align(Alignment.BottomCenter)
                .offset(y = 1.dp)
                .fillMaxWidth()
                .height(18.dp),
        ) {
            val n = 13
            val w = size.width / n
            val k = size.height / 18f
            val path = Path().apply {
                moveTo(0f, size.height)
                lineTo(0f, 2f * k)
                for (i in 0 until n) quadraticTo(i * w + w / 2f, -6f * k, (i + 1) * w, 2f * k)
                lineTo(size.width, size.height)
                close()
            }
            drawPath(path, palette.bg)
            drawPath(path, palette.ink, style = Stroke(width = 1.5.dp.toPx()))
            // 底边那条描边盖掉，免得和下面的消息区之间多一道线
            drawRect(palette.bg, topLeft = Offset(0f, size.height - 2.dp.toPx()), size = Size(size.width, 2.dp.toPx()))
        }
    }
}

/** 44dp 的圆形贴纸钮（输入栏两侧） */
@Composable
private fun RoundButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    fill: Color,
    tint: Color,
    onClick: () -> Unit,
    testTag: String,
    enabled: Boolean = true,
) {
    Box(
        Modifier
            .size(44.dp)
            .stickerClickable(shape = CircleShape, fill = fill, drop = 2.dp, enabled = enabled, onClick = onClick)
            .testTag(testTag),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(20.dp))
    }
}

// ---------------------------------------------------------------- 气泡

/** 原站的 `kh`：聊天记录为空时顶上那条问候。**不进数据库**，纯展示 */
const val GREETING: String =
    "你好呀，我是空空 ✨ 可以直接命令我修改计划与课表，" +
        "例如：「删除今天计划」「把明天的会议改到 10 点」「周三加一节数学课」。回复「确认」即可执行。"

private val BubbleText = TextStyle(fontSize = 14.sp, lineHeight = 21.7.sp)

/**
 * 空空的一条消息：`(头像) [白色贴纸气泡]`，左下角是小圆角（设计稿 `.msg`）。
 * 问候、思考中、普通回复三种内容共用这一个壳子。
 */
@Composable
private fun AiMessage(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val palette = LocalPalette.current
    Row(
        Modifier.fillMaxWidth(0.88f),
        verticalAlignment = Alignment.Bottom,
    ) {
        Image(
            painter = painterResource(com.qingji.plan.R.drawable.kongkong_avatar),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(34.dp)
                .sticker(shape = CircleShape, fill = palette.primarySoft, drop = 0.dp),
        )
        Box(
            modifier
                .padding(start = 8.dp)
                .sticker(shape = RoundedCornerShape(18.dp, 18.dp, 18.dp, 5.dp), fill = palette.surface, drop = 3.dp)
                .padding(horizontal = 13.dp, vertical = 10.dp),
        ) { content() }
    }
}

@Composable
private fun ChatBubble(
    message: ChatLogEntity,
    onConfirm: () -> Unit = {},
    onCancel: () -> Unit = {},
) {
    val palette = LocalPalette.current

    // 只有「等着用户点头」的那条才出按钮。执行完/取消后 actionState 变了，按钮自然消失
    val showActions = !message.isUser && message.actionState == ACTION_PENDING

    if (message.isUser) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Box(
                Modifier
                    .widthIn(max = 300.dp)
                    .fillMaxWidth(0.88f)
                    .wrapContentWidth(Alignment.End)
                    .sticker(shape = RoundedCornerShape(18.dp, 18.dp, 5.dp, 18.dp), fill = palette.primary, drop = 3.dp)
                    .padding(horizontal = 13.dp, vertical = 10.dp)
                    .testTag("ai_bubble_${message.id}"),
            ) {
                Text(
                    text = renderMarkdown(message.content, palette.onPrimary),
                    style = BubbleText,
                    color = palette.onPrimary,
                )
            }
        }
    } else {
        AiMessage(Modifier.testTag("ai_bubble_${message.id}")) {
            Column {
                Text(
                    text = renderMarkdown(message.content, palette.primaryDeep),
                    style = BubbleText,
                    color = palette.text,
                )
                if (showActions) PendingActions(message.id, onConfirm, onCancel)
            }
        }
    }
}

/**
 * 「我准备执行以下 N 项」下面那两颗贴纸胶囊。
 * 打字「确认」那条路也留着。
 */
@Composable
private fun PendingActions(
    messageId: Long,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    val haptics = LocalHaptics.current

    Row(
        Modifier
            .padding(top = 10.dp)
            .testTag("ai_actions_$messageId"),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        QingJiChip(
            label = "确认执行",
            selected = true,
            onClick = {
                haptics.perform(HapticCue.Commit)
                onConfirm()
            },
            compact = true,
            testTag = "ai_confirm_$messageId",
        )
        QingJiChip(
            label = "取消",
            onClick = {
                haptics.perform(HapticCue.Tap)
                onCancel()
            },
            compact = true,
            testTag = "ai_cancel_$messageId",
        )
    }
}

/** 空空在想：气泡里三颗一上一下的小点（设计稿 `.typing`） */
@Composable
private fun ThinkingBubble() {
    val palette = LocalPalette.current
    val t = rememberInfiniteTransition(label = "typing")
    val phase by t.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1000, easing = LinearEasing)),
        label = "typing_phase",
    )
    AiMessage(Modifier.testTag("ai_thinking").semantics { contentDescription = "空空正在想…" }) {
        Canvas(Modifier.size(width = 33.dp, height = 21.dp)) {
            val r = 3.5.dp.toPx()
            for (i in 0 until 3) {
                // 每颗错开 0.15 秒，各自在周期的 30% 处跳到最高
                val p = ((phase - i * 0.15f) % 1f + 1f) % 1f
                val lift = when {
                    p < 0.3f -> p / 0.3f
                    p < 0.6f -> 1f - (p - 0.3f) / 0.3f
                    else -> 0f
                }
                drawCircle(
                    palette.text3,
                    radius = r,
                    center = Offset(r + 2.dp.toPx() + i * (2 * r + 4.dp.toPx()), size.height / 2f + 2.dp.toPx() - lift * 5.dp.toPx()),
                )
            }
        }
    }
}

// ---------------------------------------------------------------- Markdown

/**
 * 极简 Markdown 渲染。
 *
 * **原站没做这一步**——它把 `**加粗**` 原样显示成星号。这是我们**唯一一处刻意不同**的地方，
 * 用户明确要求修好。加粗的字用 [boldColor]（空空的气泡里是主色深，和设计稿 `.msg .b b` 一致）。
 *
 * 只处理两件事：`**加粗**` 和换行。够用了，不值得为此引一个完整的 Markdown 库。
 */
internal fun renderMarkdown(text: String, boldColor: Color = Color.Unspecified): AnnotatedString = buildAnnotatedString {
    val pattern = Regex("\\*\\*(.+?)\\*\\*")
    var cursor = 0
    pattern.findAll(text).forEach { match ->
        append(text.substring(cursor, match.range.first))
        withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = boldColor)) {
            append(match.groupValues[1])
        }
        cursor = match.range.last + 1
    }
    append(text.substring(cursor))
}
