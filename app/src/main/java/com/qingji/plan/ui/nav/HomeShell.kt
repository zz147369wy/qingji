package com.qingji.plan.ui.nav

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.activity.result.PickVisualMediaRequest
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.qingji.plan.QingJiApp
import com.qingji.plan.ui.ai.AiScreen
import com.qingji.plan.ui.batch.BatchInputScreen
import com.qingji.plan.ui.batch.BatchTab
import com.qingji.plan.ui.plans.LongTermPlanScreen
import com.qingji.plan.ui.today.AutoScheduleSheet
import com.qingji.plan.ui.today.SettleSheet
import com.qingji.plan.ui.ai.AiViewModel
import com.qingji.plan.ui.calendar.CalendarScreen
import com.qingji.plan.ui.calendar.CalendarViewModel
import com.qingji.plan.ui.course.CourseEditState
import com.qingji.plan.ui.edit.EditSheet
import com.qingji.plan.ui.edit.EditTarget
import com.qingji.plan.ui.haptics.ProvideHaptics
import com.qingji.plan.ui.screens.PlaceholderScreen
import com.qingji.plan.ui.settings.SettingsScreen
import com.qingji.plan.ui.settings.SettingsViewModel
import com.qingji.plan.ui.stats.StatsScreen
import com.qingji.plan.ui.stats.StatsViewModel
import com.qingji.plan.ui.pet.WhalePetOverlay
import com.qingji.plan.ui.theme.QingJiBackground
import com.qingji.plan.ui.theme.LocalPalette
import com.qingji.plan.ui.today.TaskEditState
import com.qingji.plan.util.ImageUtils
import com.qingji.plan.ui.today.TodayScreen
import com.qingji.plan.ui.today.TodayViewModel
import com.qingji.plan.util.TimeUtils

/**
 * App 主骨架：带圆点纹理的背景 + 底部导航 + 内容区。
 *
 * 今日页已经接上真数据（P1）；其余四页暂时是占位，P3–P5 逐个替换。
 */
@Composable
fun HomeShell(
    modifier: Modifier = Modifier,
    onThemeTapAt: (androidx.compose.ui.geometry.Offset) -> Unit = {},
) {
    var current by remember { mutableStateOf(TabDestination.TODAY) }
    // 非空表示「任务/课程编辑弹层」开着。任务和课程共用一个弹层，靠 Tab 切
    var editTarget by remember { mutableStateOf<EditTarget?>(null) }
    // AI 页选了、还没发出去的那张图
    var pendingImage by remember { mutableStateOf<android.net.Uri?>(null) }
    val palette = LocalPalette.current
    val app = QingJiApp.from(LocalContext.current)
    val scope = rememberCoroutineScope()

    val todayVm: TodayViewModel = viewModel(
        factory = TodayViewModel.Factory(
            app.taskRepo, app.settingsRepo, app.courseRepo, app.planRepo, app.reviewRepo,
        ),
    )
    val state by todayVm.state.collectAsState()
    val weekCounts by todayVm.weekCounts.collectAsState()

    val calendarVm: CalendarViewModel = viewModel(
        factory = CalendarViewModel.Factory(app.taskRepo, app.courseRepo),
    )
    val calendarState by calendarVm.state.collectAsState()

    val statsVm: StatsViewModel = viewModel(
        factory = StatsViewModel.Factory(app.taskRepo, app.reviewRepo, app.planRepo),
    )
    val statsState by statsVm.state.collectAsState()

    val settingsVm: SettingsViewModel = viewModel(
        factory = SettingsViewModel.Factory(app.settingsRepo, app.taskRepo),
    )
    val settingsState by settingsVm.state.collectAsState()

    val context = LocalContext.current

    // ---------- 作息时间表（课表左边那根按「节次」排的轴） ----------
    val periodsRaw by app.settingsRepo
        .observeString(com.qingji.plan.data.repo.SettingsRepository.Keys.PERIODS)
        .collectAsState(initial = null)
    // 没存过 / 存坏了都会退回默认作息，见 PeriodSchedules.parse
    val periods = remember(periodsRaw) {
        com.qingji.plan.data.model.PeriodSchedules.parse(periodsRaw)
    }

    // ---------- AI 识图：选图 ----------
    // 用 PickVisualMedia（Android 13+ 的系统照片选择器）：**不需要任何读相册权限**，
    // 老系统会自动退化成 GetContent。这是最干净的一条路，manifest 一个字都不用加。
    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) pendingImage = uri
    }

    // ---------- 空空：窝的登记处 + 对外面事件的反应通道 ----------
    val petDocks = remember { com.qingji.plan.ui.pet.PetDockRegistry() }
    val petController = remember { com.qingji.plan.ui.pet.WhalePetController() }
    val petReact by app.settingsRepo
        .observeString(com.qingji.plan.data.repo.SettingsRepository.Keys.PET_REACT)
        .collectAsState(initial = null)
    val petRoam by app.settingsRepo
        .observeString(com.qingji.plan.data.repo.SettingsRepository.Keys.PET_ROAM)
        .collectAsState(initial = null)

    // 进 App 时打个招呼
    var greeted by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!greeted) {
            greeted = true
            kotlinx.coroutines.delay(1200)
            val h = java.time.LocalTime.now().hour
            petController.emit(
                if (h >= 23 || h < 5) com.qingji.plan.ui.pet.PetEvent.LATE_NIGHT
                else com.qingji.plan.ui.pet.PetEvent.GREET,
            )
        }
    }

    // ---------- P9：提醒与通知 ----------
    val notifyEnabled by app.settingsRepo
        .observeString(com.qingji.plan.data.repo.SettingsRepository.Keys.NOTIFY_ENABLED)
        .collectAsState(initial = null)
    val notifyOn = notifyEnabled?.toBooleanStrictOrNull() ?: true
    val askedBefore by app.settingsRepo
        .observeString(com.qingji.plan.data.repo.SettingsRepository.Keys.NOTIFY_PERMISSION_ASKED)
        .collectAsState(initial = null)

    // 通知权限申请器（API 33+）
    val notifyPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        kotlinx.coroutines.MainScope().launch {
            app.settingsRepo.putBoolean(
                com.qingji.plan.data.repo.SettingsRepository.Keys.NOTIFY_PERMISSION_ASKED, true,
            )
            if (granted) app.taskRepo.let { _ -> }
        }
    }

    // ---------- P9：系统日历 ----------
    val calendarId by app.settingsRepo
        .observeString(com.qingji.plan.data.repo.SettingsRepository.Keys.CALENDAR_ID)
        .collectAsState(initial = null)
    var showCalendarPicker by remember { mutableStateOf(false) }
    var writableCalendars by remember { mutableStateOf(emptyList<com.qingji.plan.syscal.WritableCalendar>()) }

    val sysCal = remember(context) { com.qingji.plan.syscal.SystemCalendarSync(context) }

    // 打开选择器时才去查（要权限）
    androidx.compose.runtime.LaunchedEffect(showCalendarPicker) {
        if (showCalendarPicker) {
            writableCalendars = withContext(kotlinx.coroutines.Dispatchers.IO) {
                sysCal.writableCalendars()
            }
        }
    }

    if (showCalendarPicker) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showCalendarPicker = false },
            title = { Text("选择日历") },
            text = {
                if (writableCalendars.isEmpty()) {
                    Text(
                        "手机里没有可写的日历。请先打开系统「日历」App 建一个（或登录一个账号），再回来选。"
                    )
                } else {
                    androidx.compose.foundation.layout.Column {
                        writableCalendars.forEach { cal ->
                            androidx.compose.material3.TextButton(onClick = {
                                kotlinx.coroutines.MainScope().launch {
                                    app.settingsRepo.putString(
                                        com.qingji.plan.data.repo.SettingsRepository.Keys.CALENDAR_ID,
                                        cal.id.toString(),
                                    )
                                }
                                showCalendarPicker = false
                            }) { Text(cal.name) }
                        }
                    }
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { showCalendarPicker = false }) {
                    Text("取消")
                }
            },
        )
    }

    val activity = context as? android.app.Activity
    val granted = if (android.os.Build.VERSION.SDK_INT >= 33) {
        androidx.core.content.ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.POST_NOTIFICATIONS,
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    } else true
    val notifState = com.qingji.plan.notify.NotifyLogic.state(
        sdkInt = android.os.Build.VERSION.SDK_INT,
        permissionGranted = granted,
        askedBefore = askedBefore?.toBooleanStrictOrNull() ?: false,
        shouldShowRationale = activity?.let {
            androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale(
                it, android.Manifest.permission.POST_NOTIFICATIONS,
            )
        } ?: false,
        notificationsEnabled = androidx.core.app.NotificationManagerCompat
            .from(context).areNotificationsEnabled(),
    )

    // ---------- P8：备份 / 恢复 ----------
    val backupRepo = remember { com.qingji.plan.data.backup.BackupRepository(app.database) }
    var pendingRestore by remember { mutableStateOf<com.qingji.plan.data.backup.BackupFile?>(null) }
    var backupMessage by remember { mutableStateOf<String?>(null) }

    // 导出：让用户自己选存到哪
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        kotlinx.coroutines.MainScope().launch {
            runCatching {
                val text = backupRepo.export()
                context.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) }
                backupMessage = "✅ 备份已导出"
            }.onFailure { backupMessage = "❌ 导出失败：${it.message?.take(80)}" }
        }
    }

    // 导入：先预览再让用户确认
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        kotlinx.coroutines.MainScope().launch {
            runCatching {
                val text = context.contentResolver.openInputStream(uri)
                    ?.bufferedReader()?.use { it.readText() } ?: error("文件读不出来")
                pendingRestore = backupRepo.preview(text)
            }.onFailure { backupMessage = "❌ ${it.message?.take(120)}" }
        }
    }

    val aiVm: AiViewModel = viewModel(
        factory = AiViewModel.Factory(
            app.taskRepo, app.planRepo, app.courseRepo, app.settingsRepo, app.database.chatLogDao(),
        ),
    )
    val aiMessages by aiVm.messages.collectAsState()
    val aiDraft by aiVm.draft.collectAsState()
    val aiBusy by aiVm.busy.collectAsState()
    val aiError by aiVm.error.collectAsState()

    // P7 的四个二级界面
    var showBatch by remember { mutableStateOf(false) }
    var showPlans by remember { mutableStateOf(false) }
    var showSettle by remember { mutableStateOf(false) }
    var showAutoSchedule by remember { mutableStateOf(false) }
    var showAi by remember { mutableStateOf(false) }
    var defaultBatchDate by remember { mutableStateOf(java.time.LocalDate.now()) }
    val allPlans by app.planRepo.observeAll().collectAsState(initial = emptyList())
    var settleReview by remember { mutableStateOf<com.qingji.plan.data.db.entity.ReviewEntity?>(null) }
    androidx.compose.runtime.LaunchedEffect(state.date, showSettle) {
        if (showSettle) settleReview = todayVm.reviewFor(state.date)
    }

    // 震动能力的注入点。整个 App 只有 ComposeHaptics 一处真正触发硬件
    ProvideHaptics {
    QingJiBackground(modifier = modifier) {
        androidx.compose.runtime.CompositionLocalProvider(
            com.qingji.plan.ui.pet.LocalPetDocks provides petDocks,
        ) {
        Box(Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = Color.Transparent,
            bottomBar = {
                BottomNav(
                    current = current,
                    onSelect = { current = it },
                    onOpenAi = { showAi = true },
                )
            },
        ) { innerPadding ->
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    // 给状态栏留位置。不加的话内容会顶到时间/电量那一行底下
                    .statusBarsPadding()
            ) {
                when (current) {
                    TabDestination.TODAY -> TodayScreen(
                        state = state,
                        onFilterChange = todayVm::selectFilter,
                        onBackToToday = todayVm::backToToday,
                        onToggleTask = todayVm::toggleDone,
                        onTaskClick = { editTarget = EditTarget.EditTask(TaskEditState.from(it)) },
                        onAddTask = {
                            // 原站新建时时间默认填此刻，不是 09:00
                            editTarget = EditTarget.NewTask(
                                TaskEditState.create(state.date, TimeUtils.nowHHmm(), 15),
                            )
                        },
                        onDeleteTask = {
                            todayVm.delete(it)
                            petController.emit(com.qingji.plan.ui.pet.PetEvent.DELETED)
                        },
                        onCelebrate = { allDone ->
                            petController.emit(
                                if (allDone) com.qingji.plan.ui.pet.PetEvent.ALL_DONE
                                else com.qingji.plan.ui.pet.PetEvent.TASK_DONE,
                            )
                        },
                        // 左右翻页由 TodayScreen 里的 Pager 接管，这里只负责把结果落到状态
                        stateFor = { date -> todayVm.snapshotFor(date) },
                        onGoToDate = { date -> todayVm.goToDate(date) },
                        onOpenBatch = { showBatch = true },
                        onOpenPlans = { showPlans = true },
                        onOpenSettle = { showSettle = true },
                        onOpenAutoSchedule = { showAutoSchedule = true },
                        weekCounts = weekCounts,
                    )
                    TabDestination.CALENDAR -> CalendarScreen(
                        state = calendarState,
                        periods = periods,
                        onViewChange = calendarVm::setView,
                        onPreviousMonth = calendarVm::previousMonth,
                        onNextMonth = calendarVm::nextMonth,
                        onSelectDate = { date ->
                            // 日历和今日页是联动的：点某天，今日页也切到那天
                            calendarVm.selectDate(date)
                            todayVm.goToDate(date)
                        },
                        onFilterChange = calendarVm::setFilter,
                        onTaskClick = { editTarget = EditTarget.EditTask(TaskEditState.from(it)) },
                        onTaskToggle = todayVm::toggleDone,
                        onCourseClick = {
                            editTarget = EditTarget.EditCourse(CourseEditState.from(it))
                        },
                        onAddCourse = {
                            editTarget = EditTarget.NewCourse(
                                CourseEditState.create(CourseEditState.todayWeekday()).copy(
                                    hasTime = true,
                                    time = TimeUtils.nowHHmm(),
                                ),
                            )
                        },
                        onAddTask = {
                            editTarget = EditTarget.NewTask(
                                TaskEditState.create(
                                    calendarState.selected,
                                    TimeUtils.nowHHmm(),
                                    15,
                                ),
                            )
                        },
                    )
                    TabDestination.STATS -> StatsScreen(state = statsState)
                    TabDestination.SETTINGS -> SettingsScreen(
                        state = settingsState,
                        periods = periods,
                        onPeriodsChange = { next ->
                            scope.launch {
                                app.settingsRepo.setPeriods(next)
                            }
                        },
                        onThemeTapAt = onThemeTapAt,
                        onThemeModeChange = settingsVm::setThemeMode,
                        onPetReactChange = settingsVm::setPetReact,
                        onPetRoamChange = settingsVm::setPetRoam,
                        onAddCategory = settingsVm::addCategory,
                        onDeleteCategory = settingsVm::deleteCategory,
                        onWhaleSchemeChange = settingsVm::setWhaleScheme,
                        onTimelineNoOverlapChange = settingsVm::setTimelineNoOverlap,
                        onSettleTimeChange = settingsVm::setSettleTime,
                        onAiConfigChange = settingsVm::setAiConfig,
                        // ★ 之前漏了这行 —— 所以「测试连接」点了没任何反应
                        onTestAi = { config, callback ->
                            aiVm.testConnection(config, callback)
                        },
                        onAiModifyAllowedChange = settingsVm::setAiModifyAllowed,
                        onClearTasks = { settingsVm.clearTasks(it) },
                        onBackup = {
                            exportLauncher.launch(
                                backupRepo.suggestedFileName(java.time.LocalDate.now())
                            )
                        },
                        onRestore = { importLauncher.launch(arrayOf("application/json", "*/*")) },
                        notifyEnabled = notifyOn,
                        notifyStateText = com.qingji.plan.notify.NotifyLogic.describe(notifState),
                        notifyActionLabel = com.qingji.plan.notify.NotifyLogic.actionLabel(notifState),
                        onNotifyEnabledChange = { on ->
                            kotlinx.coroutines.MainScope().launch {
                                app.settingsRepo.putBoolean(
                                    com.qingji.plan.data.repo.SettingsRepository.Keys.NOTIFY_ENABLED, on,
                                )
                                com.qingji.plan.notify.AlarmScheduler(context, app.settingsRepo)
                                    .rescheduleAll()
                            }
                        },
                        // 「写入系统日历」暂时不在设置里露出（写入路径还没接上），见 SettingsScreen 的说明
                        onNotifyAction = {
                            when {
                                com.qingji.plan.notify.NotifyLogic.canRequest(notifState) ->
                                    notifyPermissionLauncher.launch(
                                        android.Manifest.permission.POST_NOTIFICATIONS,
                                    )
                                else -> {
                                    // 永久拒绝 / 被系统关掉 → 只能去系统设置
                                    val i = android.content.Intent(
                                        android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS,
                                    ).putExtra(
                                        android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName,
                                    ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                    runCatching { context.startActivity(i) }
                                }
                            }
                        },
                    )
                }
            }
        }
        // ---------- AI 聊天（整页，底栏正中「空空」进入） ----------
        // 放在这一层是因为**空空本人要出现在这一页的顶部水族箱里** ——
        // 她由下面的 WhalePetOverlay 画，必须叠在 AI 页之上。
        if (showAi) {
            androidx.activity.compose.BackHandler { showAi = false }
            AiScreen(
                messages = aiMessages,
                draft = aiDraft,
                busy = aiBusy,
                error = aiError,
                pendingImage = pendingImage,
                onDraftChange = aiVm::setDraft,
                onSend = {
                    val uri = pendingImage
                    if (uri == null) {
                        aiVm.send()
                    } else {
                        val note = aiDraft
                        pendingImage = null
                        // 读图 + 解码 + 压缩 + base64 都是重活，别堵主线程
                        scope.launch {
                            val dataUrl = withContext(Dispatchers.IO) {
                                runCatching { ImageUtils.toDataUrl(context, uri) }
                            }
                            dataUrl.fold(
                                onSuccess = { aiVm.sendImage(it, note) },
                                onFailure = { aiVm.showError(it.message ?: "这张图片处理不了") },
                            )
                        }
                    }
                },
                onPickImage = {
                    imagePicker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                    )
                },
                onClearImage = { pendingImage = null },
                onConfirmActions = aiVm::confirmPending,
                onCancelActions = aiVm::cancelPending,
                onClear = aiVm::clearChat,
                onDismissError = aiVm::clearError,
                onBack = { showAi = false },
            )
        }

        // 常驻桌面鲸鱼空空：覆盖在界面之上，坐在各页面登记的「窝」里。
        // 批量录入 / 长期计划和编辑弹层打开时她让位 —— 那些界面自己会用到整块屏幕。
        // AI 页**不躲**：那一页顶部的水族箱就是她的窝。
        if (!showBatch && !showPlans && editTarget == null) {
            WhalePetOverlay(
                docks = petDocks,
                scheme = settingsState.whaleScheme,
                controller = petController,
                react = petReact?.toBooleanStrictOrNull() ?: true,
                roam = petRoam?.toBooleanStrictOrNull() ?: true,
            )
        }
        }
        }
    }
    }

    // 任务 / 课程编辑弹层（同一个）。放在最外层，这样它盖在整个界面之上
    editTarget?.let { target ->
        EditSheet(
            target = target,
            onDismiss = { editTarget = null },
            onSaveTask = { updated ->
                todayVm.saveTask(updated)
                editTarget = null
            },
            onSaveCourse = { updated ->
                calendarVm.saveCourse(updated)
                editTarget = null
            },
            onDeleteTask = { doomed ->
                todayVm.deleteByEditState(doomed)
                editTarget = null
            },
            onDeleteCourse = { doomed ->
                calendarVm.deleteCourse(doomed)
                editTarget = null
            },
        )
    }

    // ---------- P7：批量录入（整页） ----------
    if (showBatch) {
        // ★ 按系统返回键应该回到上一页，而不是退出 App。
        //   不加这个，它用起来就像"贴上去的一层"，而不是一个真正的页面。
        androidx.activity.compose.BackHandler { showBatch = false }
        BatchInputScreen(
            defaultDate = defaultBatchDate,
            onBack = { showBatch = false },
            onDefaultDateChange = { defaultBatchDate = it },
            onApply = { lines, tab ->
                kotlinx.coroutines.MainScope().launch {
                    todayVm.applyBatch(lines, tab == BatchTab.COURSE)
                }
                showBatch = false
            },
        )
    }

    // 恢复前的二次确认。**必须让用户看清要覆盖什么**
    pendingRestore?.let { file ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { pendingRestore = null },
            title = { Text("恢复备份") },
            text = {
                Text(
                    "这个备份里有：${file.counts.describe()}" +
                        "  ⚠️ 恢复会用这些数据完全覆盖当前手机上的数据，" +
                        "当前数据会被清空。确定继续吗？",
                )
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    kotlinx.coroutines.MainScope().launch {
                        runCatching {
                            // 先存一份安全网，万一恢复错了还能救
                            backupRepo.snapshotForSafety()
                            backupRepo.restore(file)
                            backupMessage = "✅ 已恢复：${file.counts.describe()}"
                        }.onFailure { backupMessage = "❌ 恢复失败：${it.message?.take(80)}" }
                    }
                    pendingRestore = null
                }) { Text("覆盖并恢复", color = palette.danger) }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { pendingRestore = null }) {
                    Text("取消")
                }
            },
        )
    }

    backupMessage?.let { msg ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { backupMessage = null },
            title = { Text("数据备份") },
            text = { Text(msg) },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { backupMessage = null }) {
                    Text("好")
                }
            },
        )
    }

    // ---------- P7：长期计划（整页） ----------
    if (showPlans) {
        androidx.activity.compose.BackHandler { showPlans = false }
        LongTermPlanScreen(
            plans = allPlans,
            customs = settingsState.customCategories,
            onBack = { showPlans = false },
            onSave = { existing, draft ->
                todayVm.savePlan(draft.toEntity(existing, System.currentTimeMillis()))
            },
            onDelete = { todayVm.deletePlan(it) },
        )
    }

    // ---------- P7：每日结算（弹层） ----------
    if (showSettle) {
        SettleSheet(
            tasks = state.allTasks,
            dateKey = com.qingji.plan.util.DateUtils.toKey(state.date),
            existing = settleReview,
            onDismiss = { showSettle = false },
            onGenerate = {
                kotlinx.coroutines.MainScope().launch {
                    todayVm.settleToday()
                    settleReview = todayVm.reviewFor(state.date)
                }
            },
        )
    }

    // ---------- P7：AI 自动排期（弹层） ----------
    if (showAutoSchedule) {
        AutoScheduleSheet(
            tasks = state.allTasks,
            // 只有在看今天时才拿「此刻」兜底；翻到别的日期仍从 08:00 起排
            isToday = state.isToday,
            onDismiss = { showAutoSchedule = false },
            onApply = { slots ->
                todayVm.applySchedule(slots)
                showAutoSchedule = false
            },
        )
    }

    // 弹层开着时，返回键应该是关弹层而不是退出。
    // 编辑弹层里还叠着「选择时间」时，由 EditSheet 内部那个 BackHandler 先接管。
    if (showSettle || showAutoSchedule || editTarget != null ||
        pendingRestore != null || showCalendarPicker
    ) {
        androidx.activity.compose.BackHandler {
            showSettle = false
            showAutoSchedule = false
            editTarget = null
            pendingRestore = null
            showCalendarPicker = false
        }
    }
}
