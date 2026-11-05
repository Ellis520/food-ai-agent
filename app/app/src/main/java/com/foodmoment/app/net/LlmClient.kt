package com.foodmoment.app.net

import com.foodmoment.app.core.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * OpenAI 兼容端点客户端（5.2）。
 *
 * 不假设服务端支持 function calling / response_format / usage，
 * 全部走「提示词要求 JSON + 容错解析 + 失败回退纯文本」的路径。
 */
class LlmClient(private val settings: Settings) {

    enum class ErrorKind { NOT_CONFIGURED, BLOCKED_PLAINTEXT, BAD_URL, HTTP, TIMEOUT, NETWORK, EMPTY }

    data class Reply(
        val text: String,
        val intent: String,
        val params: JSONObject?,
        val warnings: List<String>,
        val followups: List<String>,
        val model: String,
        val latencyMs: Long,
        val tokensIn: Int?,
        val tokensOut: Int?
    )

    sealed interface Outcome {
        data class Ok(val reply: Reply) : Outcome
        data class Fail(val kind: ErrorKind, val message: String, val httpCode: Int = 0) : Outcome
    }

    suspend fun test(): Outcome = call(SYSTEM_PROMPT, PROBE_TEXT)

    suspend fun chat(userContent: String): Outcome = call(SYSTEM_PROMPT, userContent)

    private suspend fun call(system: String, user: String): Outcome = withContext(Dispatchers.IO) {
        val base = settings.modelBaseUrl
        val model = settings.modelName
        val key = settings.apiKey

        if (base.isBlank() || model.isBlank()) {
            return@withContext Outcome.Fail(
                ErrorKind.NOT_CONFIGURED,
                "未配置模型：请填写 Base URL 与模型名"
            )
        }
        if (base.startsWith("http://", true) && !settings.allowHttp) {
            return@withContext Outcome.Fail(
                ErrorKind.BLOCKED_PLAINTEXT,
                "已禁止明文 HTTP，请在设置中允许后再试"
            )
        }
        if (!base.startsWith("http://", true) && !base.startsWith("https://", true)) {
            return@withContext Outcome.Fail(ErrorKind.BAD_URL, "Base URL 必须以 http:// 或 https:// 开头")
        }

        val url = when {
            base.endsWith("/chat/completions") -> base
            base.endsWith("/v1") -> "$base/chat/completions"
            else -> "$base/chat/completions"
        }

        val payload = JSONObject().apply {
            put("model", model)
            put("temperature", settings.temperature / 100.0)
            put("max_tokens", settings.maxTokens)
            put("messages", JSONArray().apply {
                put(JSONObject().put("role", "system").put("content", system))
                put(JSONObject().put("role", "user").put("content", user))
            })
        }

        val client = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(settings.timeoutSeconds.toLong(), TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()

        val builder = Request.Builder()
            .url(url)
            .post(payload.toString().toRequestBody(JSON_MEDIA))
            .header("Accept", "application/json")
        if (!key.isNullOrBlank()) builder.header("Authorization", "Bearer $key")

        val started = System.currentTimeMillis()
        try {
            client.newCall(builder.build()).execute().use { response ->
                val bodyText = response.body?.string().orEmpty()
                val cost = System.currentTimeMillis() - started
                if (!response.isSuccessful) {
                    return@withContext Outcome.Fail(
                        ErrorKind.HTTP,
                        describeHttp(response.code, bodyText),
                        response.code
                    )
                }
                val content = extractContent(bodyText)
                    ?: return@withContext Outcome.Fail(ErrorKind.EMPTY, "服务返回内容为空或格式不被识别")

                val usage = runCatching { JSONObject(bodyText).optJSONObject("usage") }.getOrNull()
                val parsed = parseAssistantContent(content)

                Outcome.Ok(
                    Reply(
                        text = parsed.text,
                        intent = parsed.intent,
                        params = parsed.params,
                        warnings = parsed.warnings,
                        followups = parsed.followups,
                        model = model,
                        latencyMs = cost,
                        tokensIn = usage?.optInt("prompt_tokens")?.takeIf { it > 0 },
                        tokensOut = usage?.optInt("completion_tokens")?.takeIf { it > 0 }
                    )
                )
            }
        } catch (e: java.net.SocketTimeoutException) {
            Outcome.Fail(ErrorKind.TIMEOUT, "请求超时（${settings.timeoutSeconds}s），可重试")
        } catch (e: java.net.UnknownHostException) {
            Outcome.Fail(ErrorKind.NETWORK, "域名解析失败，检查 Base URL 或网络")
        } catch (e: java.io.IOException) {
            Outcome.Fail(ErrorKind.NETWORK, "网络错误：${e.message ?: "无法连接"}")
        } catch (e: Exception) {
            Outcome.Fail(ErrorKind.NETWORK, "请求异常：${e.message ?: e.javaClass.simpleName}")
        }
    }

    private fun describeHttp(code: Int, body: String): String {
        val hint = when (code) {
            401, 403 -> "密钥无效或无权限"
            404 -> "接口或模型不存在"
            429 -> "触发限流或额度不足"
            in 500..599 -> "服务端异常"
            else -> "请求被拒绝"
        }
        val snippet = body.replace("\n", " ").take(180)
        return "HTTP $code · $hint" + if (snippet.isBlank()) "" else " · $snippet"
    }

    private fun extractContent(body: String): String? = runCatching {
        val root = JSONObject(body)
        val choices = root.optJSONArray("choices") ?: return@runCatching null
        if (choices.length() == 0) return@runCatching null
        val message = choices.optJSONObject(0)?.optJSONObject("message")
            ?: return@runCatching null
        message.optString("content").takeIf { it.isNotBlank() }
    }.getOrNull()

    // --------------------------------------------------------------- 内容解析

    private data class Parsed(
        val text: String,
        val intent: String,
        val params: JSONObject?,
        val warnings: List<String>,
        val followups: List<String>
    )

    private fun parseAssistantContent(raw: String): Parsed {
        val candidate = extractJsonObject(raw)
        if (candidate != null) {
            val obj = runCatching { JSONObject(candidate) }.getOrNull()
            if (obj != null) {
                val text = obj.optString("reply").ifBlank { obj.optString("text") }
                if (text.isNotBlank()) {
                    return Parsed(
                        text = text,
                        intent = obj.optString("intent", "none").ifBlank { "none" },
                        params = obj.optJSONObject("params"),
                        warnings = obj.optJSONArray("warnings").toStringList(),
                        followups = obj.optJSONArray("followups").toStringList()
                    )
                }
            }
        }
        // 回退：整段当作文本（5.2）
        return Parsed(raw.trim(), "none", null, emptyList(), emptyList())
    }

    /** 剥离 ```json 围栏并取第一个平衡的 JSON 对象 */
    fun extractJsonObject(raw: String): String? {
        var text = raw.trim()
        if (text.startsWith("```")) {
            text = text.removePrefix("```json").removePrefix("```")
            val end = text.lastIndexOf("```")
            if (end >= 0) text = text.substring(0, end)
            text = text.trim()
        }
        var start = -1
        var depth = 0
        var inString = false
        var escaped = false
        for (i in text.indices) {
            val ch = text[i]
            when {
                escaped -> escaped = false
                ch == '\\' && inString -> escaped = true
                ch == '"' -> inString = !inString
                inString -> Unit
                ch == '{' -> {
                    if (depth == 0) start = i
                    depth++
                }
                ch == '}' -> {
                    depth--
                    if (depth == 0 && start >= 0) return text.substring(start, i + 1)
                }
            }
        }
        return null
    }

    private fun JSONArray?.toStringList(): List<String> {
        if (this == null) return emptyList()
        return buildList {
            for (i in 0 until length()) {
                val v = optString(i)
                if (v.isNotBlank()) add(v)
            }
        }
    }

    companion object {
        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

        private const val PROBE_TEXT = "这是一次连通性测试，请只回复 JSON：{\"reply\":\"ok\",\"intent\":\"none\"}"

        val SYSTEM_PROMPT: String = """
你是「食刻AI」的饮食助手，运行在一个本地优先的 Android 应用中。

输出规则（必须遵守）：
1. 只输出一个 JSON 对象，不要输出 Markdown 代码围栏，不要输出 JSON 之外的任何文字。
2. JSON 结构：{"reply": "给用户看的正文（可用 Markdown 子集：粗体、列表、标题）", "intent": "none|plan|record|nutrition|habit|budget|feed", "params": {...}, "warnings": ["..."], "followups": ["..."]}
3. 你没有任何外部工具：不能查询商家、菜单、实时价格、地理位置、营业时间。
4. 严禁编造价格、商家名称、营业时间、营养数值。用户给的数字才可以用。
5. 金额、营养、预算、统计的最终数值由 App 本地计算，你只负责理解意图、组织语言和给出搭配建议。
6. 无法确定时，把不确定点写进 warnings，并在 reply 中如实说明。
7. 不提供疾病诊断、治疗方案或「绝对安全」的健康承诺。
8. 中文回答，简洁，不超过 300 字（列表除外）。
9. 事实包里的 diet_log 是用户本机的真实饮食记录：today.meals 是今天的逐餐明细（菜名、克重、金额、时间），previous_days 是前 6 天按天汇总的菜品。
   问到「我今天/昨天/这周吃了什么」「吃得怎么样」时，必须依据 diet_log 回答，点名具体菜名与克重；
   diet_log 里没有的餐食不要编造，没有记录时直接说「这几天没有记录」。

intent 与 params 约定：
- plan：安排饮食计划。params: {"meals": {"breakfast": [{"food": "食物名", "grams": 150}], "lunch": [...], "dinner": [...]}, "budget_yuan": 50}
- record：记录一顿饭。params: {"meal_type": "breakfast|lunch|dinner|snack", "cost_yuan": 15, "items": [{"food": "番茄鸡蛋盖饭", "grams": 400}]}
- nutrition：营养换算。params: {"items": [{"food": "白米饭", "grams": 200}]}
- habit：查看统计。params: {"days": 7}
- budget：算预算。params: {"budget_yuan": 30, "items": [{"name": "午饭", "unit_yuan": 15, "quantity": 1}]}
- none：纯聊天或无法归类。
""".trimIndent()
    }
}
