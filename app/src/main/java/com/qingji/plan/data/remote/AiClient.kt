package com.qingji.plan.data.remote

import com.qingji.plan.data.model.AiConfig
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * HTTP 出口。抽出来是为了让 [AiClient] 能脱离真实网络测试。
 */
fun interface HttpCaller {
    suspend fun post(url: String, headers: Map<String, String>, bodyJson: String): Result<String>
}

/**
 * OpenAI 兼容的 AI 客户端。
 *
 * 请求格式与原站一致：
 *   POST {baseUrl}/chat/completions
 *   Authorization: Bearer {apiKey}
 *   { model, messages, temperature: 0.7, max_tokens }
 *
 * 默认走 DeepSeek，但换成任何兼容端点都可以。
 */
class AiClient(
    private val config: AiConfig,
    private val http: HttpCaller,
    /**
     * 发请求用的调度器。默认 IO。
     *
     * 抽出来是为了**能测**——跟 [HttpCaller] 一个道理。写死 `Dispatchers.IO` 的话，
     * 单测里协程会跳到真实线程池，跟 `Dispatchers.setMain` / `resetMain` 抢，
     * 报 `Dispatchers.Main is used concurrently with setting it`。
     */
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /**
     * 纯文本对话。
     */
    suspend fun chat(
        system: String,
        user: String,
        maxTokens: Int = DEFAULT_MAX_TOKENS,
    ): String = complete(
        system = system,
        userContent = JsonPrimitive(user),
        maxTokens = maxTokens,
    )

    /**
     * 带图片的对话（多模态）。
     *
     * 图片走 OpenAI 的 `image_url` + data URI 格式，与原站一致。
     */
    suspend fun chatWithImage(
        system: String,
        text: String,
        imageDataUrl: String,
        maxTokens: Int = IMAGE_MAX_TOKENS,
    ): String = complete(
        system = system,
        userContent = JsonArray(
            listOf(
                buildJsonObject {
                    put("type", "text")
                    put("text", text)
                },
                buildJsonObject {
                    put("type", "image_url")
                    put("image_url", buildJsonObject { put("url", imageDataUrl) })
                },
            )
        ),
        maxTokens = maxTokens,
    )

    /**
     * 测试连接。成功返回模型的回答开头，失败返回带原因的错误。
     */
    suspend fun testConnection(): Result<String> = runCatching {
        if (!config.isConfigured) error("还没填 API Key")
        chat(
            system = "你是测试助手。",
            user = "只回复两个字：可以",
            maxTokens = 16,
        ).ifBlank { "（模型返回了空内容）" }
    }

    // ---------- 内部 ----------

    private suspend fun complete(
        system: String,
        userContent: kotlinx.serialization.json.JsonElement,
        maxTokens: Int,
    ): String = withContext(dispatcher) {
        val body = buildJsonObject {
            put("model", config.model)
            put("temperature", 0.7)
            put("max_tokens", maxTokens)
            put(
                "messages",
                JsonArray(
                    listOf(
                        buildJsonObject {
                            put("role", "system")
                            put("content", JsonPrimitive(system))
                        },
                        buildJsonObject {
                            put("role", "user")
                            put("content", userContent)
                        },
                    )
                ),
            )
        }.toString()

        val url = "${config.normalizedBaseUrl}/chat/completions"
        val headers = mapOf(
            "Content-Type" to "application/json",
            "Authorization" to "Bearer ${config.apiKey}",
        )

        val raw = http.post(url, headers, body).getOrElse { e ->
            throw AiException(e.message ?: "网络请求失败")
        }

        extractContent(raw)
    }

    /**
     * 从返回体里取 `choices[0].message.content`。
     */
    private fun extractContent(raw: String): String {
        val root = runCatching { json.parseToJsonElement(raw).jsonObject }
            .getOrElse { throw AiException("返回的内容不是合法 JSON：${raw.take(120)}") }

        root["choices"]?.jsonArray?.firstOrNull()?.jsonObject
            ?.get("message")?.jsonObject
            ?.get("content")?.jsonPrimitive?.content
            ?.let { return it.trim() }

        // 有些兼容端点会在错误时返回 {"error": {...}}
        root["error"]?.jsonObject?.get("message")?.jsonPrimitive?.content?.let {
            throw AiException(it)
        }
        throw AiException("返回体里没有找到回答内容")
    }

    companion object {
        const val DEFAULT_MAX_TOKENS = 1024
        const val IMAGE_MAX_TOKENS = 2048
    }
}

/** AI 调用相关的错误 */
class AiException(message: String) : Exception(message)

/**
 * 把常见的 HTTP 错误码翻译成人能看懂的话。
 */
fun describeHttpError(code: Int, body: String): String = when (code) {
    401, 403 -> "API Key 不对或没权限（$code）"
    404 -> "接口地址不对（404）。检查 API 地址和模型名"
    429 -> "请求太频繁或额度用完了（429）"
    in 500..599 -> "服务端出错（$code）"
    else -> "AI 请求失败（$code）：${body.take(120)}"
}
