package com.qingji.plan.data.model

import kotlinx.serialization.Serializable

/**
 * AI 接口配置。
 *
 * 原站默认走 DeepSeek，但接口是 **OpenAI 兼容**格式，
 * 所以换成任何兼容端点（自建、通义、智谱…）都能用，只要改地址和模型名。
 *
 * 存进 settings 表时是 JSON 字符串。
 */
@Serializable
data class AiConfig(
    val baseUrl: String = DEFAULT_BASE_URL,
    val apiKey: String = "",
    val model: String = DEFAULT_MODEL,
) {
    /** 填了 Key 才算配置好了 */
    val isConfigured: Boolean get() = apiKey.isNotBlank()

    /** 请求时要拼 `/chat/completions`，所以要先把末尾的斜杠去掉 */
    val normalizedBaseUrl: String get() = baseUrl.trim().trimEnd('/')

    companion object {
        const val DEFAULT_BASE_URL = "https://api.deepseek.com"

        /**
         * 默认用 flash 而不是 chat。
         *
         * 因为 `deepseek-flash` **同时支持文字和图片输入**，识图（截图课表 → 自动导入）
         * 就靠它。`deepseek-chat` 是纯文本模型，发图片会直接报错。
         *
         * 换别的也行，但那个模型必须能吃图（Qwen-VL / GLM-4V / GPT-4o 之类）。
         */
        const val DEFAULT_MODEL = "deepseek-flash"

        /** 设置页里给用户的提示，列几个能识图的模型 */
        const val VISION_MODEL_HINT =
            "识图（发课表截图自动导入）需要能看图的模型，如 deepseek-flash / qwen-vl-max / glm-4v / gpt-4o。用纯文本模型发图片会报错。"
    }
}
