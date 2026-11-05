package com.foodmoment.app.net

import com.foodmoment.app.core.MealType
import com.foodmoment.app.core.Settings
import com.foodmoment.app.core.extractJsonObject
import com.foodmoment.app.data.MenuItem
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
 * 菜品识别（视觉模型）。
 *
 * 关键约束：模型只能从「菜单」里挑，不能凭空造菜名。
 * 返回的每一条都会再和菜单做一次精确匹配，匹配不上的直接丢弃并记 warning。
 */
class VisionClient(private val settings: Settings) {

    data class Dish(
        val menuId: String,
        val name: String,
        val unit: String,
        val count: Int,
        val grams: Double,
        val priceFen: Long?,
        val kcal100: Double?,
        val protein100: Double?,
        val fat100: Double?,
        val carbs100: Double?,
        val confidence: Double
    ) {
        val costFen: Long? get() = priceFen?.let { it * count }
        fun total(per100: Double?): Double? =
            per100?.let { it * grams.coerceAtLeast(0.0) * count / 100.0 }
    }

    data class Result(
        val dishes: List<Dish>,
        val mealType: String,
        val warnings: List<String>,
        val model: String,
        val latencyMs: Long,
        val rawText: String
    )

    sealed interface Outcome {
        data class Ok(val result: Result) : Outcome
        data class Fail(val message: String) : Outcome
    }

    suspend fun analyze(imageBase64: String, menu: List<MenuItem>): Outcome =
        withContext(Dispatchers.IO) {
            if (!settings.visionReady) {
                return@withContext Outcome.Fail(
                    "未配置识别模型：到「我的 → 菜品识别」填写 Base URL 与 API Key"
                )
            }

            val base = settings.effectiveVisionBase
            if (base.startsWith("http://", true) && !settings.allowHttp) {
                return@withContext Outcome.Fail("已禁止明文 HTTP，请在设置中允许后再试")
            }
            if (!base.startsWith("http://", true) && !base.startsWith("https://", true)) {
                return@withContext Outcome.Fail("Base URL 必须以 http:// 或 https:// 开头")
            }
            if (menu.isEmpty()) {
                return@withContext Outcome.Fail("菜单是空的，请先在「菜单管理」里添加菜品")
            }

            val url = when {
                base.endsWith("/chat/completions") -> base
                base.endsWith("/v1") -> "$base/chat/completions"
                else -> "$base/chat/completions"
            }

            val body = JSONObject().apply {
                put("model", settings.effectiveVisionModel)
                put("temperature", 0.1)
                put("max_tokens", 1500)
                put(
                    "messages",
                    JSONArray().apply {
                        put(JSONObject().put("role", "system").put("content", SYSTEM_PROMPT))
                        put(
                            JSONObject().put("role", "user").put(
                                "content",
                                JSONArray().apply {
                                    put(
                                        JSONObject()
                                            .put("type", "text")
                                            .put("text", userPrompt(menu))
                                    )
                                    put(
                                        JSONObject()
                                            .put("type", "image_url")
                                            .put(
                                                "image_url",
                                                JSONObject().put(
                                                    "url",
                                                    "data:image/jpeg;base64,$imageBase64"
                                                )
                                            )
                                    )
                                }
                            )
                        )
                    }
                )
            }

            val client = OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(settings.timeoutSeconds.toLong(), TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build()

            val request = Request.Builder()
                .url(url)
                .post(body.toString().toRequestBody(JSON_MEDIA))
                .header("Authorization", "Bearer ${settings.effectiveVisionKey}")
                .header("Accept", "application/json")
                .build()

            val started = System.currentTimeMillis()
            try {
                client.newCall(request).execute().use { response ->
                    val text = response.body?.string().orEmpty()
                    val elapsed = System.currentTimeMillis() - started

                    if (!response.isSuccessful) {
                        return@withContext Outcome.Fail(httpMessage(response.code, text))
                    }

                    val content = runCatching {
                        JSONObject(text)
                            .optJSONArray("choices")
                            ?.optJSONObject(0)
                            ?.optJSONObject("message")
                            ?.optString("content")
                    }.getOrNull()

                    if (content.isNullOrBlank()) {
                        return@withContext Outcome.Fail("模型没有返回内容")
                    }

                    val parsed = parse(content, menu, elapsed)
                        ?: return@withContext Outcome.Fail(
                            "模型返回的内容无法解析。" + content.take(120)
                        )
                    Outcome.Ok(parsed)
                }
            } catch (e: java.net.SocketTimeoutException) {
                Outcome.Fail("识别超时（${settings.timeoutSeconds}s），可重试")
            } catch (e: java.net.UnknownHostException) {
                Outcome.Fail("域名解析失败，检查 Base URL 或网络")
            } catch (e: java.io.IOException) {
                Outcome.Fail("网络错误：${e.message ?: "无法连接"}")
            } catch (e: Exception) {
                Outcome.Fail("识别失败：${e.message ?: e.javaClass.simpleName}")
            }
        }

    private fun parse(content: String, menu: List<MenuItem>, elapsed: Long): Result? {
        val json = extractJsonObject(content) ?: return null
        val root = runCatching { JSONObject(json) }.getOrNull() ?: return null

        val byName = menu.associateBy { it.name }
        val dishes = mutableListOf<Dish>()
        val warnings = mutableListOf<String>()

        root.optJSONArray("warnings")?.let { array ->
            for (index in 0 until array.length()) {
                val text = array.optString(index)
                if (text.isNotBlank()) warnings += text
            }
        }

        val items = root.optJSONArray("items") ?: JSONArray()
        for (index in 0 until items.length()) {
            val entry = items.optJSONObject(index) ?: continue
            val rawName = entry.optString("name").trim()
            if (rawName.isEmpty()) continue

            val item = byName[rawName]
            if (item == null) {
                // 模型编造了菜单外的菜名：丢弃，不让它进入记录
                warnings += "模型给出菜单外菜品「$rawName」，已忽略"
                continue
            }

            val count = entry.optInt("count", 1).coerceIn(1, 20)
            val grams = entry.doubleOrNull("grams")
                ?: item.grams
                ?: 100.0

            dishes += Dish(
                menuId = item.id,
                name = item.name,
                unit = item.unit,
                count = count,
                grams = grams,
                priceFen = item.priceFen,
                kcal100 = entry.doubleOrNull("kcal100"),
                protein100 = entry.doubleOrNull("protein100"),
                fat100 = entry.doubleOrNull("fat100"),
                carbs100 = entry.doubleOrNull("carbs100"),
                confidence = entry.doubleOrNull("confidence") ?: 0.0
            )
        }

        val meal = root.optString("meal_type")
        val mealType = if (MealType.all.contains(meal)) meal else MealType.LUNCH

        return Result(
            dishes = dishes,
            mealType = mealType,
            warnings = warnings,
            model = settings.effectiveVisionModel,
            latencyMs = elapsed,
            rawText = content
        )
    }

    private fun httpMessage(code: Int, body: String): String {
        val hint = when (code) {
            401, 403 -> "密钥无效或无权限"
            404 -> "接口或模型不存在"
            429 -> "触发限流或额度不足"
            in 500..599 -> "服务端异常"
            else -> "请求被拒绝"
        }
        val snippet = body.replace("\n", " ").take(160)
        return "HTTP $code · $hint" + if (snippet.isBlank()) "" else " · $snippet"
    }

    private fun userPrompt(menu: List<MenuItem>): String {
        val menuText = menu.joinToString("\n") { item ->
            val price = item.priceFen?.let { "%.1f".format(it / 100.0) } ?: "?"
            "${item.name} | ${item.unit.ifBlank { "-" }} | ${item.gramsText.ifBlank { "-" }} | $price"
        }
        return """
            菜单（名称 | 单位 | 克重g | 价格元）：
            $menuText

            请识别图片里这一餐点了哪些菜品，并输出 JSON：
            {"items":[{"name":"菜单中的准确名称","count":1,"grams":220,"kcal100":120,"protein100":7.5,"fat100":5.2,"carbs100":12.3,"confidence":0.8}],"meal_type":"breakfast|lunch|dinner|snack","warnings":[]}

            要求：
            1. name 必须与菜单里的名称逐字一致，不得改写。
            2. 图片里的菜品在菜单中找不到时，不要输出该项，在 warnings 里说明。
            3. grams 优先用菜单克重；菜单是区间（如 150-160）时取中间值。
            4. kcal100 / 蛋白质 / 脂肪 / 碳水 均为每 100g 的估算值，给单个数字。
            5. count 是份数，看不清按 1。
            6. 完全识别不出时返回 {"items":[],"warnings":["原因"]}。
        """.trimIndent()
    }

    private fun JSONObject.doubleOrNull(key: String): Double? =
        if (has(key) && !isNull(key)) optDouble(key) else null

    companion object {
        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

        private val SYSTEM_PROMPT = """
            你是食堂菜品识别助手，输出必须严格遵守以下规则：
            1. 只能识别菜单中存在的菜品，名称必须逐字相同，禁止编造或改写菜名。
            2. 只输出一个 JSON 对象，不要输出代码围栏或任何解释文字。
            3. 营养值按每 100g 估算，这是估算值，不要声称是精确测量。
            4. 不确定就少输出，并在 warnings 里说明原因。
        """.trimIndent()
    }
}
