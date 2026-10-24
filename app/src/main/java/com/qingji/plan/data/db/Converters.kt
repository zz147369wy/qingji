package com.qingji.plan.data.db

import androidx.room.TypeConverter

/**
 * Room 类型转换器。
 *
 * 只处理一个：长期计划的「周几」是一个 [0,1,3,5] 这样的整数列表，
 * SQLite 存不了数组，转成 `"0,1,3,5"` 存。
 */
class Converters {

    @TypeConverter
    fun fromIntList(value: List<Int>?): String =
        value?.joinToString(",") ?: ""

    @TypeConverter
    fun toIntList(value: String?): List<Int> =
        value?.split(",")
            ?.mapNotNull { it.trim().toIntOrNull() }
            ?.filter { it in 0..6 }
            ?: emptyList()
}
