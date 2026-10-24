package com.qingji.plan.util

import java.util.UUID

/**
 * 唯一 id 生成。
 *
 * 原站用 `"c" + Date.now().toString(36) + Math.random().toString(36).slice(2,6)`。
 * 这里用 UUID 更稳妥，格式上同样是一个短字符串，不影响备份结构。
 */
object Ids {
    fun uid(): String = UUID.randomUUID().toString().replace("-", "").take(16)
}
