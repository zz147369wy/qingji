package com.qingji.plan.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import kotlin.math.PI

/**
 * 动效规格。
 *
 * 重设计前全 App **一处弹簧都没有** —— 10 处 `tween` + 6 处裸 `Animatable`。
 * Material 3 Expressive 的核心是弹簧物理：运动有质量和惯性，
 * **中途打断会从当前速度继续**，手感才是活的。
 *
 * ## 参数为什么用「时长 + 回弹」而不是「刚度 + 阻尼」
 *
 * 最初这里写的是裸的 `spring(dampingRatio = 0.62f, stiffness = 430f)` —— 那是物理量，
 * 但调参的人想的是「慢一点」「弹一点」。两个数还是纠缠的：只降阻尼会同时变弹和变拖沓。
 *
 * 现在改用 [springOf]，用两个正交的直觉量描述：
 *   - `durationMillis` —— 大体多久停下来
 *   - `bounce`         —— 过冲多少（0 = 不回弹，越大越弹）
 *
 * 于是「弹过头了」就只动一个数：把 `bounce` 调小。
 * （这个换算 API 参考了第三方项目 Cresto 的 Glasense 设计系统，实现是自己按
 * 标准二阶系统公式写的：ω_n = 2π/T，k = ω_n²，ζ = 1 - bounce。）
 *
 * ## 最重要的一条区分
 *
 *   - [Springs.spatialFast]（位置、尺寸、圆角）**允许回弹** —— 这是 Expressive 的手感来源。
 *   - [Springs.effects]（颜色、透明度）**必须 bounce = 0**。回弹意味着中途会**越过**
 *     目标值 —— 透明度越过 1 会截断、颜色越过终点会闪一下。这是弹簧动效最常见的翻车点。
 */
object AppMotion {

    /**
     * 按「时长 + 回弹」造一条弹簧。
     *
     * 二阶系统：无阻尼自然频率 ω_n = 2π / T，刚度 k = ω_n²，阻尼比 ζ = 1 - bounce。
     * `bounce` 限制在 [0, 1) —— 负回弹（过阻尼）在这个 app 里没有用武之地。
     */
    fun <T> springOf(
        durationMillis: Int,
        bounce: Double,
        visibilityThreshold: T? = null,
    ): SpringSpec<T> {
        require(durationMillis > 0) { "durationMillis 必须为正，收到 $durationMillis" }

        val seconds = durationMillis / 1000.0
        val omega = 2.0 * PI / seconds
        val stiffness = (omega * omega).toFloat()
        val dampingRatio = (1.0 - bounce).coerceIn(0.0001, 1.0).toFloat()

        return spring(
            dampingRatio = dampingRatio,
            stiffness = stiffness,
            visibilityThreshold = visibilityThreshold,
        )
    }

    /** 位置 / 尺寸 / 圆角类 —— 可以有回弹 */
    object Springs {

        /**
         * 快速的。小元件、点按反馈。原来的 ζ=0.42 / k=900。
         */
        val spatialFast: SpringSpec<Float> = springOf(durationMillis = 210, bounce = 0.58)

        /**
         * 弹跳的。用在需要「跳出来」的地方，比如勾选、导航指示器。原来的 ζ=0.34 / k=470。
         */
        val spatialBouncy: SpringSpec<Float> = springOf(durationMillis = 290, bounce = 0.66)

        // ---- 颜色 / 透明度类：bounce 恒为 0，绝不允许过冲 ----

        val effects: SpringSpec<Float> = springOf(durationMillis = 157, bounce = 0.0)

        val effectsFast: SpringSpec<Float> = springOf(durationMillis = 102, bounce = 0.0)

        val effectsSlow: SpringSpec<Float> = springOf(durationMillis = 222, bounce = 0.0)
    }

    /** 常用时长（毫秒）。只在弹簧不合适的场合用，比如径向主题切换那种一次性的大揭示 */
    object Duration {
        const val quick = 150

        /** 删除飞出屏幕 */
        const val flingOut = 100
    }

    /**
     * 缓动曲线。**弹簧覆盖不到的场合才用** —— 能用 [springOf] 就别来这儿。
     *
     * 建这个类是为了让「唯一的例外」是个**命名 token** 而不是散落在组件里的魔法数字：
     * 别处再要同样的曲线时，抄的是这个名字，不是四个小数。
     */
    object Easing {
        /**
         * 「甩出屏幕」：起步即高速、末段减速，一次冲到底不再回头。
         *
         * 曲线值与第三方参考实现 Cresto（GlasenseSwipeable 的删除飞出）逐点相同。
         * 这里**刻意不用弹簧**：弹簧会回弹，而「删掉的东西飞出去」不该弹回来；
         * 而且弹簧的时长由物理量推出来不可控，这条要求就是干脆的 100ms。
         */
        val flingOut = CubicBezierEasing(0.2f, 0f, 0.56f, 0.48f)
    }

    /**
     * 「整行飞出屏幕」。用于滑动删除的那一下收尾。
     *
     * 位置类动效本来该走 [Springs.spatialFast]，但这是**一次性**位移 ——
     * 没有中途打断、没有回弹、时长要确定，弹簧的三个优点一个都用不上。
     */
    fun flingOut(): TweenSpec<Float> = tween(
        durationMillis = Duration.flingOut,
        easing = Easing.flingOut,
    )
}
