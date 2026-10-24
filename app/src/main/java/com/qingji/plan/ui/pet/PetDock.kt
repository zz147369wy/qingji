package com.qingji.plan.ui.pet

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned

/**
 * 空空的「窝」。
 *
 * 【2026-10 鲸鱼主题】空空不再满屏乱游，而是有固定的窝：
 *   - 今日页：舷窗卡的右侧（[TODAY]）
 *   - 设置页：配色卡里的小舞台（[SETTINGS]）
 *   - AI 聊天：页顶的水族箱（[AI]）
 *   - 其它页面：趴在底栏左侧（[NAV]）
 *
 * 各页面用 [petDock] 把窝的位置登记到这里，桌宠覆盖层读出来决定空空待在哪。
 * 窝在列表里会跟着滚动 —— 位置只在覆盖层的 graphicsLayer 里读，
 * 所以滚动时只失效绘制，不会让覆盖层每帧重组。
 */
@Stable
class PetDockRegistry {
    val rects = mutableStateMapOf<String, Rect>()

    companion object {
        const val TODAY = "today"
        const val SETTINGS = "settings"
        const val AI = "ai"
        const val NAV = "nav"
    }
}

val LocalPetDocks = staticCompositionLocalOf<PetDockRegistry?> { null }

/**
 * 把这个组件的位置登记成空空的窝 [key]。组件离开界面时自动注销。
 * 坐标系是根布局（与桌宠覆盖层同一个）。
 */
fun Modifier.petDock(key: String): Modifier = composed {
    val registry = LocalPetDocks.current ?: return@composed this
    DisposableEffect(registry, key) {
        onDispose { registry.rects.remove(key) }
    }
    this.onGloballyPositioned { coords ->
        val r = coords.boundsInRoot()
        if (registry.rects[key] != r) registry.rects[key] = r
    }
}
