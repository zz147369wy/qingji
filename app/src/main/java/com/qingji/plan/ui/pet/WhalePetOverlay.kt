package com.qingji.plan.ui.pet

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.webkit.WebResourceRequest
import android.webkit.WebView
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewClientCompat
import com.qingji.plan.ui.components.sticker
import com.qingji.plan.ui.theme.AppText
import com.qingji.plan.ui.theme.LocalPalette
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.roundToInt
import kotlin.random.Random

/** 鲸鱼窗口的逻辑尺寸。够本体 + 阴影 + 跳跃空间，又不至于吃掉太多底下 UI 的点击 */
private val WinW = 150.dp
private val WinH = 190.dp

/**
 * 常驻桌面鲸鱼空空。
 *
 * 她是一个「只覆盖鲸鱼大小」的透明 WebView，位置由 Compose 决定。
 * 为什么不是全屏透明覆盖：Android 的 WebView 一旦铺满全屏，会吃掉底下所有 UI 的触摸
 * （任务勾选、滑动、底部导航全点不到），而且没法只让「鲸鱼区域」穿透。
 *
 * ## 她住在哪（「窝」）
 *
 * 【2026-10 鲸鱼主题】不再满屏乱游 —— 满屏游走会挡住正在看的内容。
 * 各页面用 `Modifier.petDock(key)` 登记一个窝的位置，这里读出来让她坐过去：
 * 今日页 = 舷窗卡右边，设置页 = 配色卡，AI 页 = 页顶水族箱，其它页 = 底栏左上。
 * 开了「偶尔出门逛逛」时她隔一会儿游出去转一圈再回来。
 *
 * ## 触摸
 *
 * 全在 WebView 的 JS 层处理（WebView 吞掉小窗内的触摸，Compose 收不到），
 * 通过 JsInterface 回调：
 * - `onPetDrag(dx, dy)`：拖拽 → 移动整个小窗
 * - `onPetDragStart/End`：拖拽期间暂停「自己游走」（否则会和手指抢位置）
 * - `onPetTap()`：单击 → 逗她一下（表情 + 一句气泡话）
 *
 * AI 聊天的入口**不在这里** —— 是底栏正中的「空空」头像钮。
 *
 * @param docks 各页面登记上来的窝。空的时候退回底栏左上角
 * @param controller 外面发生的事（完成任务、删除…）从这里进来
 * @param react 关掉后她只做自己的随机小动作，不理你
 * @param roam 开着才会偶尔游出去逛一圈
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WhalePetOverlay(
    docks: PetDockRegistry,
    modifier: Modifier = Modifier,
    scheme: String = DEFAULT_WHALE_SCHEME,
    controller: WhalePetController? = null,
    react: Boolean = true,
    roam: Boolean = true,
) {
    val context = LocalContext.current
    val palette = LocalPalette.current
    val density = LocalDensity.current
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    val latestScheme by rememberUpdatedState(scheme)
    val latestReact by rememberUpdatedState(react)
    val latestRoam by rememberUpdatedState(roam)
    val densityScale = density.density

    val winWpx = with(density) { WinW.roundToPx() }
    val winHpx = with(density) { WinH.roundToPx() }

    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    var dragging by remember { mutableStateOf(false) }
    /** 用户拖到别处之后她就在那待着，不再回窝 */
    var userPlaced by remember { mutableStateOf(false) }
    var position by remember { mutableStateOf(Offset.Zero) }
    var webView by remember { mutableStateOf<WebView?>(null) }

    /** 说话气泡。她说完一句过几秒自己消失 */
    var speech by remember { mutableStateOf<String?>(null) }
    var speechSeq by remember { mutableIntStateOf(0) }

    var bounds by remember { mutableStateOf(Offset.Zero) }

    val assetLoader = remember {
        WebViewAssetLoader.Builder()
            .addPathHandler("/", WebViewAssetLoader.AssetsPathHandler(context))
            .build()
    }

    // JS 桥调用的「逗她」：换一个表情、说一句
    val pokes = remember {
        listOf(
            Triple("嗯？叫我吗～", "surprised", "hop"),
            Triple("想聊天就点下面那颗我哦", "wink", "look"),
            Triple("别戳啦，好痒", "shy", "shake"),
            Triple("今天也要加油！", "happy", "jump"),
        )
    }
    var pokeIdx by remember { mutableIntStateOf(0) }
    val currentPoke by rememberUpdatedState({ } as () -> Unit)

    fun say(text: String) {
        speechSeq += 1
        speech = text
    }

    // ---------- 窝：她坐哪 ----------
    //
    // 每个窝的尺寸和站位都照设计稿（`.nest` 在各处的 width/height/right/bottom）：
    //   - 今日页：舷窗卡右侧，原尺寸 150×190，右边探出卡片 14dp、脚离卡底 2dp
    //   - 设置舞台：118×150，居中，脚踩舞台底
    //   - AI 水族箱：160×202，离右边 44dp（稿子是 60，再往左会压住「在线」那行字）、脚离底 8dp
    //   - 底栏：112×142，离左边 6dp，脚陷进底栏 26dp（趴在浪线上）
    //
    // 不同的窝**直接改小窗的尺寸**，不用 graphicsLayer 缩放：网页里的鲸鱼按小窗高度自适应大小，
    // 小窗变小她就变小。只缩画面不缩小窗的话，小窗的透明部分仍按原尺寸吃掉触摸 ——
    // 上一版她趴在底栏时，「日历」那一格就点不到了。
    //
    // 底栏那一档也因此**不照设计稿陷进底栏 26dp**，而是脚踩在浪线上（只压进 6dp）：
    // 陷进去的话小窗会盖住「今日」「日历」两格的上半截，点不到。
    // 窝滚出屏幕顶部时（比如设置页往下翻），这个窝就不算数，她退回底栏 —— 不贴在屏幕顶上挡内容。
    data class Dock(val scale: Float, val left: Float, val bottom: Float)

    val minVisible = with(density) { 48.dp.toPx() }
    fun rectOf(key: String) = docks.rects[key]?.takeIf { it.bottom > minVisible }
    val dock: Dock? = with(density) {
        rectOf(PetDockRegistry.AI)?.let { r ->
            val s = 160f / 150f
            Dock(s, r.right - 44.dp.toPx() - winWpx * s, r.bottom - 8.dp.toPx())
        } ?: rectOf(PetDockRegistry.TODAY)?.let { r ->
            Dock(1f, r.right + 14.dp.toPx() - winWpx, r.bottom - 2.dp.toPx())
        } ?: rectOf(PetDockRegistry.SETTINGS)?.let { r ->
            val s = 118f / 150f
            Dock(s, r.center.x - winWpx * s / 2f, r.bottom)
        } ?: rectOf(PetDockRegistry.NAV)?.let { r ->
            Dock(112f / 150f, r.left + 6.dp.toPx(), r.top + 6.dp.toPx())
        }
    }
    val dockScale = dock?.scale ?: 1f
    val curWpx = winWpx * dockScale
    val curHpx = winHpx * dockScale

    /** 小窗左上角该在哪，她才坐在窝里。x 夹在屏内，y 不夹（窝在哪她就在哪） */
    val nestTarget = dock?.let { d ->
        Offset(d.left.coerceIn(0f, (containerSize.width - curWpx).coerceAtLeast(0f)), d.bottom - curHpx)
    }
    val latestNest by rememberUpdatedState(nestTarget)

    /**
     * 她现在是不是「坐在窝里」。坐着的时候位置**直接取窝的实时位置**（窝随列表滚动，她跟着走，
     * 一帧不差）；出门、被拖走时才用 [position]。
     */
    var docked by remember { mutableStateOf(true) }

    // 游走 / 回窝。`dragging` 作为 key：拖拽开始 effect 重启、动画协程取消；松手后重新计时
    LaunchedEffect(containerSize, dragging, userPlaced, latestRoam) {
        if (dragging) return@LaunchedEffect
        if (containerSize.width <= winWpx || containerSize.height <= winHpx) return@LaunchedEffect
        val maxX = (containerSize.width - winWpx).toFloat()
        val maxY = (containerSize.height - winHpx).toFloat()
        if (userPlaced) return@LaunchedEffect

        while (true) {
            // 先平滑游回窝里：终点每帧取窝的实时位置，窝在动也追得上
            if (!docked) {
                val start = position
                animate(0f, 1f, animationSpec = tween(650, easing = FastOutSlowInEasing)) { t, _ ->
                    val home = latestNest ?: start
                    position = Offset(start.x + (home.x - start.x) * t, start.y + (home.y - start.y) * t)
                }
                docked = true
            }
            // 偶尔出门逛一圈再回来。
            // 关键比例：在窝里的时间要**明显长于**出门的时间（约 25-45 秒 vs 4-6 秒），
            // 否则她一直在屏幕上飘，正好挡住用户正在看的内容 —— 那就回到「满屏乱游」了。
            delay(if (latestRoam) Random.nextLong(25_000, 45_000) else Random.nextLong(60_000, 90_000))
            if (!latestRoam) continue

            val from = latestNest ?: position
            position = from
            docked = false
            val away = Offset(
                x = Random.nextFloat() * maxX,
                y = Random.nextFloat() * maxY,
            )
            animate(0f, 1f, animationSpec = tween(Random.nextInt(900, 1500), easing = FastOutSlowInEasing)) { t, _ ->
                position = Offset(from.x + (away.x - from.x) * t, from.y + (away.y - from.y) * t)
            }
            delay(Random.nextLong(3500, 6000))
        }
    }

    /** 此刻小窗实际在哪 */
    fun shownAt(): Offset = if (docked && !userPlaced) (latestNest ?: position) else position

    // 配色变化 → 即时切色
    LaunchedEffect(scheme) {
        webView?.evaluateJavascript("window.WhalePet && window.WhalePet.setScheme('$scheme')", null)
    }

    // 气泡到点自己消失
    LaunchedEffect(speechSeq) {
        if (speech != null) {
            delay(3200)
            speech = null
        }
    }

    // ---------- 对外面发生的事做反应 ----------
    LaunchedEffect(controller) {
        val events = controller?.events ?: return@LaunchedEffect
        events.collectLatest { event ->
            val view = webView ?: return@collectLatest
            if (!latestReact) return@collectLatest
            fun js(expr: String) = mainHandler.post { view.evaluateJavascript(expr, null) }
            when (event) {
                PetEvent.TASK_DONE -> {
                    js("window.WhalePet.doAction('jump'); window.WhalePet.setExpression('happy', 3);")
                    say(pick(listOf("又搞定一件！", "好耶～", "做得漂亮！", "继续保持哦")))
                }
                PetEvent.ALL_DONE -> {
                    js("window.WhalePet.doAction('spin'); window.WhalePet.setExpression('love', 4);")
                    say("全部完成！今天的海好平静～")
                }
                PetEvent.DELETED -> {
                    js("window.WhalePet.doAction('shake'); window.WhalePet.setExpression('sad', 3);")
                    say("好吧，那就不做啦～")
                }
                PetEvent.ADDED -> {
                    js("window.WhalePet.setExpression('surprised', 3);")
                    say("要加点什么呀？")
                }
                PetEvent.GREET -> {
                    js("window.WhalePet.setExpression('happy', 3); window.WhalePet.doAction('hop');")
                    say(greeting())
                }
                PetEvent.LATE_NIGHT -> {
                    js("window.WhalePet.setExpression('sleepy', 5); window.WhalePet.doAction('sit');")
                    say("已经很晚了…早点睡吧")
                }
            }
        }
    }

    Box(
        modifier
            .fillMaxSize()
            .onSizeChanged {
                containerSize = it
                bounds = Offset(
                    (it.width - winWpx).coerceAtLeast(0).toFloat(),
                    (it.height - winHpx).coerceAtLeast(0).toFloat(),
                )
            },
    ) {
        Box(
            Modifier
                .offset { shownAt().let { IntOffset(it.x.roundToInt(), it.y.roundToInt()) } }
                .size(WinW * dockScale, WinH * dockScale),
        ) {
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        webView = this
                        setBackgroundColor(Color.TRANSPARENT)
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        // 不能 setLayerType(SOFTWARE)：那会禁用 GPU 合成，WebGL 直接不渲染
                        addJavascriptInterface(object {
                            @android.webkit.JavascriptInterface
                            fun onPetTap() {
                                mainHandler.post {
                                    val (line, expr, act) = pokes[pokeIdx++ % pokes.size]
                                    val view = webView ?: return@post
                                    view.evaluateJavascript(
                                        "window.WhalePet.setExpression('$expr', 3); window.WhalePet.doAction('$act');",
                                        null,
                                    )
                                    say(line)
                                }
                            }

                            @android.webkit.JavascriptInterface
                            fun onPetDragStart() {
                                mainHandler.post {
                                    // 从她此刻实际所在的位置开始拖（坐在窝里时 position 不是实际位置）
                                    position = shownAt()
                                    docked = false
                                    dragging = true
                                }
                            }

                            @android.webkit.JavascriptInterface
                            fun onPetDragEnd() {
                                mainHandler.post {
                                    dragging = false
                                    // 拖到哪就在哪待着，不再回窝
                                    userPlaced = true
                                }
                            }

                            @android.webkit.JavascriptInterface
                            fun onPetDrag(dx: Float, dy: Float) {
                                // dx/dy 是 CSS px（≈dp），转物理 px 后累加。
                                // JS 层已按帧合并（requestAnimationFrame），这里每帧只进一次
                                mainHandler.post {
                                    val dpx = dx * densityScale
                                    val dpy = dy * densityScale
                                    position = Offset(
                                        (position.x + dpx).coerceIn(0f, bounds.x),
                                        (position.y + dpy).coerceIn(0f, bounds.y),
                                    )
                                }
                            }
                        }, "Android")
                        webViewClient = object : WebViewClientCompat() {
                            override fun shouldInterceptRequest(
                                view: WebView,
                                request: WebResourceRequest,
                            ) = assetLoader.shouldInterceptRequest(request.url)

                            override fun onPageFinished(view: WebView, url: String?) {
                                view.evaluateJavascript(
                                    "window.WhalePet && window.WhalePet.setScheme('$latestScheme')", null,
                                )
                            }
                        }
                        loadUrl("https://appassets.androidplatform.net/whale_pet/index.html?scheme=$scheme")
                    }
                },
                update = {},
                modifier = Modifier.fillMaxSize(),
            )

            // 说话气泡。放在 WebView 外面 —— 小窗里的气泡会被窗口边界裁掉
            speech?.let { text ->
                Text(
                    text = text,
                    style = AppText.sub.copy(fontWeight = FontWeight.Bold),
                    color = palette.text,
                    modifier = Modifier
                        .align(androidx.compose.ui.Alignment.TopStart)
                        .offset { IntOffset(with(density) { (-58).dp.roundToPx() }, 0) }
                        .widthIn(max = 190.dp)
                        .sticker(shape = RoundedCornerShape(16.dp), fill = palette.surface, drop = 3.dp)
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .alpha(1f),
                )
            }
        }
    }
}

private fun pick(options: List<String>): String = options[Random.nextInt(options.size)]

/** 按当前时间打招呼 */
private fun greeting(): String {
    val h = java.time.LocalTime.now().hour
    return when (h) {
        in 5..8 -> "早上好～今天也要慢慢来哦"
        in 9..11 -> "上午好，先从最难的那件开始？"
        in 12..13 -> "中午了，记得吃饭"
        in 14..17 -> "下午好～还有几件事等着你哦"
        in 18..22 -> "晚上好，今天辛苦了"
        else -> "这么晚还不睡呀…"
    }
}
