package com.qingji.plan.data.backup

import com.qingji.plan.data.db.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/**
 * 数据备份与恢复。
 *
 * 三条必须守住的行为（对应 PRD §7）：
 *   1. **恢复 = 覆盖** —— 先清空再导入，不做合并
 *   2. **恢复前先自动存一份临时备份** —— 万一恢复错了还能救回来
 *   3. 导入前**先解析并预览**，让用户看到里面有多少数据再决定
 */
class BackupRepository(
    private val db: AppDatabase,
    private val appVersion: String = "",
) {

    private val json = Json {
        prettyPrint = true          // 导出的文件人也能看懂
        ignoreUnknownKeys = true    // 以后加了字段，旧版本 App 也能读
        encodeDefaults = true
    }

    // ---------- 导出 ----------

    /**
     * 把全部数据打包成 JSON 文本。
     */
    suspend fun export(): String = withContext(Dispatchers.IO) {
        val tasks = db.taskDao().getAll()
        val plans = db.longTermPlanDao().getAll()
        val courses = db.courseDao().getAll()
        val reviews = db.reviewDao().getRecent(Int.MAX_VALUE)
        val settings = db.settingDao().getAll()
        val chat = db.chatLogDao().getAll()

        val file = BackupFile(
            exportedAt = System.currentTimeMillis(),
            appVersion = appVersion,
            counts = BackupCounts(
                tasks = tasks.size,
                plans = plans.size,
                courses = courses.size,
                reviews = reviews.size,
                settings = settings.size,
                chatLog = chat.size,
            ),
            tasks = tasks,
            plans = plans,
            courses = courses,
            reviews = reviews,
            settings = settings,
            chatLog = chat,
        )
        json.encodeToString(file)
    }

    /**
     * 导出文件名。形如 `鲸记备份_2026-09-27.json`
     */
    fun suggestedFileName(today: java.time.LocalDate): String =
        "鲸记备份_${com.qingji.plan.util.DateUtils.toKey(today)}.json"

    // ---------- 预览 ----------

    /**
     * 只解析不写入。给用户看清楚这个备份里有什么。
     *
     * 解析失败会抛出 [BackupError] 的某个子类，每一种都有明确的中文说明。
     */
    fun preview(text: String): BackupFile {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) throw BackupError.Unreadable("文件是空的")

        val file = try {
            json.decodeFromString<BackupFile>(trimmed)
        } catch (e: Exception) {
            // 可能是别的东西塞进来，或者根本不是 JSON
            if (trimmed.startsWith("{")) {
                throw BackupError.NotJson(e.message?.take(120) ?: "解析失败")
            }
            throw BackupError.NotJson("内容开头不是「{」")
        }

        if (file.version > BackupFile.CURRENT_VERSION) {
            throw BackupError.UnknownVersion(file.version)
        }
        if (file.counts.total == 0) throw BackupError.Empty()

        return file
    }

    // ---------- 恢复 ----------

    /**
     * 用备份文件覆盖当前数据。
     *
     * ⚠️ **这是破坏性操作**：会先清空所有表。调用方必须已经让用户二次确认过。
     *
     * 返回恢复的数据条数。
     */
    suspend fun restore(file: BackupFile): BackupCounts = withContext(Dispatchers.IO) {
        // 1. 全部清空（真删，不是软删）
        db.taskDao().deleteAll()
        db.longTermPlanDao().deleteAll()
        db.courseDao().deleteAll()
        db.reviewDao().deleteAll()
        db.settingDao().clear()
        db.chatLogDao().deleteAll()

        // 2. 原样写回。id 是自增主键，保留原值以免破坏内部引用（planUid 之类）
        if (file.tasks.isNotEmpty()) db.taskDao().insertAll(file.tasks)
        if (file.plans.isNotEmpty()) db.longTermPlanDao().insertAll(file.plans)
        if (file.courses.isNotEmpty()) db.courseDao().insertAll(file.courses)
        if (file.reviews.isNotEmpty()) db.reviewDao().insertAll(file.reviews)
        if (file.settings.isNotEmpty()) db.settingDao().putAll(file.settings)
        if (file.chatLog.isNotEmpty()) db.chatLogDao().insertAll(file.chatLog)

        file.counts
    }

    /**
     * 恢复前的安全网：先把当前数据存一份到内存。
     *
     * 万一恢复错了（比如拿了个旧备份覆盖），还能用这份临时备份救回来。
     */
    suspend fun snapshotForSafety(): String = export()
}
