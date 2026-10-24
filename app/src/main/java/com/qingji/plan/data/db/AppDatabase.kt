package com.qingji.plan.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.qingji.plan.data.db.dao.ChatLogDao
import com.qingji.plan.data.db.dao.CourseDao
import com.qingji.plan.data.db.dao.LongTermPlanDao
import com.qingji.plan.data.db.dao.ReviewDao
import com.qingji.plan.data.db.dao.SettingDao
import com.qingji.plan.data.db.dao.TaskDao
import com.qingji.plan.data.db.entity.ChatLogEntity
import com.qingji.plan.data.db.entity.CourseEntity
import com.qingji.plan.data.db.entity.LongTermPlanEntity
import com.qingji.plan.data.db.entity.ReviewEntity
import com.qingji.plan.data.db.entity.SettingEntity
import com.qingji.plan.data.db.entity.TaskEntity

/**
 * 本地数据库。全部数据存在手机本机，不需要联网、不需要账号。
 *
 * 六张表对应原站 IndexedDB 的六个对象仓库：
 *   tasks / long_term_plans / courses / reviews / settings / chat_log
 *
 * 原站的 posts（新闻缓存）和 syncQueue（云同步队列）按需求**不做**，故这里没有。
 */
@Database(
    entities = [
        TaskEntity::class,
        LongTermPlanEntity::class,
        CourseEntity::class,
        ReviewEntity::class,
        SettingEntity::class,
        ChatLogEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun taskDao(): TaskDao
    abstract fun longTermPlanDao(): LongTermPlanDao
    abstract fun courseDao(): CourseDao
    abstract fun reviewDao(): ReviewDao
    abstract fun settingDao(): SettingDao
    abstract fun chatLogDao(): ChatLogDao

    companion object {
        const val DB_NAME = "qingji.db"

        @Volatile
        private var instance: AppDatabase? = null

        /** 单例。App 全局共用同一个数据库连接 */
        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DB_NAME,
                ).build().also { instance = it }
            }

        /**
         * 测试用：内存数据库，跑完就没了。
         *
         * 特意把查询 / 事务执行器都换成「就在当前线程跑」的。
         * Room 的挂起 DAO 默认会 `withContext(自己的线程池)`，于是 `vm.xxx()` 一返回
         * 协程其实还没跑完，测试里紧接着的断言看到的是旧状态——表现成**偶发失败**。
         * 换成直接执行器之后测试里的调用是同步的，断言才靠谱。
         */
        fun inMemory(context: Context): AppDatabase =
            Room.inMemoryDatabaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
            )
                .allowMainThreadQueries()
                .setQueryExecutor(Runnable::run)
                .setTransactionExecutor(Runnable::run)
                .build()
    }
}
