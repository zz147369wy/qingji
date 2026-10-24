package com.qingji.plan.data.db.entity

import androidx.room.Entity
import kotlinx.serialization.Serializable
import androidx.room.PrimaryKey

/**
 * 设置项（键值对）。
 *
 * 与原站的 settings 表一一对应：原站用 IndexedDB 存 theme / settleTime / aiConfig
 * 这类零散配置，这里保持同样的形态，备份时整表打包即可。
 */
@Entity(tableName = "settings")
@Serializable
data class SettingEntity(
    @PrimaryKey
    val key: String,
    val value: String,
    val updatedAt: Long = System.currentTimeMillis(),
)
