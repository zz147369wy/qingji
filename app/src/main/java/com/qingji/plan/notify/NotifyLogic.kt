package com.qingji.plan.notify

/**
 * 通知权限的状态机。**纯逻辑**，可以在 JVM 里测。
 *
 * 为什么要专门做这个：通知权限的坑比看上去深得多。
 *
 * 1. **拒绝 1 次还能再弹**（flags = USER_SET），**拒绝 2 次就永久静默**
 *    （flags = USER_FIXED，之后 requestPermissions 约 100ms 就回调 granted=-1，不弹窗）。
 *    所以必须自己记「问过没有」——光看 `shouldShowRequestPermissionRationale` 分不清
 *    「从未询问」和「永久拒绝」，这两种状态下它都是 false，只看它必然做错文案。
 *
 * 2. **权限 granted ≠ 能发通知**。国产 ROM 的「通知管理」总开关会翻一个叫
 *    POST_NOTIFICATION 的 appop，而权限状态**仍然是 granted**。
 *    实测 `appops get <pkg> POST_NOTIFICATION` → `ignore`。
 *    所以判定必须用 `areNotificationsEnabled()`（它同时覆盖权限和 appop），
 *    不能只用 `checkSelfPermission`。
 *
 * 3. **被拒后 `notify()` 不抛异常**，静默丢弃。不能靠 catch 异常来判断发没发出去。
 */
object NotifyLogic {

    /** 界面上该显示什么 */
    enum class State {
        /** API < 33，不需要这个权限 */
        NotNeeded,

        /** 还没申请过，可以弹系统弹窗 */
        CanAsk,

        /** 申请过被拒，但还能再弹一次 */
        DeniedCanAskAgain,

        /** 被永久拒绝，只能去系统设置里开 */
        DeniedPermanently,

        /** 申请过了，用户也同意了，但系统层面（appop / ROM 开关）关着 */
        BlockedBySystem,

        /** 一切正常 */
        Granted,
    }

    /**
     * 判断该显示哪个状态。
     *
     * @param sdkInt 设备 API 级别
     * @param permissionGranted 运行时权限是否授予
     * @param askedBefore 我们自己记的「有没有申请过」
     * @param shouldShowRationale 系统的 `shouldShowRequestPermissionRationale`
     * @param notificationsEnabled `NotificationManagerCompat.areNotificationsEnabled()`
     */
    fun state(
        sdkInt: Int,
        permissionGranted: Boolean,
        askedBefore: Boolean,
        shouldShowRationale: Boolean,
        notificationsEnabled: Boolean,
    ): State {
        // API 33 以下压根没这个权限，只要系统没关就能发
        if (sdkInt < 33) {
            return if (notificationsEnabled) State.NotNeeded else State.BlockedBySystem
        }

        if (!permissionGranted) {
            return when {
                // 从没问过 → 可以弹
                !askedBefore -> State.CanAsk
                // 问过、系统还愿意让我们说理由 → 还能再弹
                shouldShowRationale -> State.DeniedCanAskAgain
                // 问过、系统也不让我们解释了 → 多半是永久拒绝
                else -> State.DeniedPermanently
            }
        }

        // 权限给了，但系统层面还关着 —— 这正是国产 ROM 最阴的那一刀
        return if (notificationsEnabled) State.Granted else State.BlockedBySystem
    }

    /** 这个状态下还能不能弹系统弹窗 */
    fun canRequest(state: State): Boolean =
        state == State.CanAsk || state == State.DeniedCanAskAgain

    /** 这个状态是不是「需要用户去系统设置里动手」 */
    fun needsSystemSettings(state: State): Boolean =
        state == State.DeniedPermanently || state == State.BlockedBySystem

    /**
     * 给用户看的一句话说明。
     */
    fun describe(state: State): String = when (state) {
        State.NotNeeded -> "提醒已开启"
        State.CanAsk -> "还没开启提醒"
        State.DeniedCanAskAgain -> "提醒被拒绝了，可以再试一次"
        State.DeniedPermanently -> "提醒已被系统记住拒绝，需要到系统设置里手动开启"
        State.BlockedBySystem -> "系统层面的通知开关被关掉了，需要到系统设置里开启"
        State.Granted -> "提醒已开启"
    }

    /** 按钮上该写什么。空字符串表示不需要按钮 */
    fun actionLabel(state: State): String = when (state) {
        State.CanAsk -> "开启提醒"
        State.DeniedCanAskAgain -> "再试一次"
        State.DeniedPermanently, State.BlockedBySystem -> "去系统设置"
        else -> ""
    }
}
