package com.foodmoment.app.ai

import com.foodmoment.app.core.MealType
import com.foodmoment.app.core.Money
import com.foodmoment.app.core.Settings
import com.foodmoment.app.core.Time
import com.foodmoment.app.data.ChatMessage
import com.foodmoment.app.data.DietRecord
import com.foodmoment.app.data.Food
import com.foodmoment.app.data.Plan
import com.foodmoment.app.data.PlanItem
import com.foodmoment.app.data.RecordItem
import com.foodmoment.app.data.Repository
import com.foodmoment.app.domain.NutrientItem
import com.foodmoment.app.domain.NutritionCalculator
import com.foodmoment.app.domain.HabitAnalyzer
import com.foodmoment.app.domain.PlanValidator
import com.foodmoment.app.net.LlmClient
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * 对话编排（5.1 ~ 5.4）。
 *
 * 原则：模型只产出「意图 + 语言」，所有金额、营养、统计由本地纯函数计算并组装卡片。
 * 模型的 params 只作为输入建议，最终数值一律本地重算。
 */
class ChatEngine(
    private val repo: Repository,
    private val settings: Settings
) {

    data class SendResult(val ok: Boolean, val error: String? = null)

    suspend fun send(sessionId: String, userText: String): SendResult {
        val now = Time.now()
        val userMessage = ChatMessage(
            id = "m_" + UUID.randomUUID().toString().take(8),
            sessionId = sessionId,
            role = "user",
            content = userText,
            cardsJson = null,
            requestId = "req_" + UUID.randomUUID().toString().take(8),
            createdAt = now,
            status = "ok"
        )
        repo.saveMessage(userMessage)

        if (!settings.modelReady) {
            repo.saveMessage(
                assistantMessage(
                    sessionId,
                    "还没有配置模型。请到「设置 → 模型」填写 Base URL、模型名与 API Key。\n\n" +
                        "在配置完成前，记录、营养换算、习惯统计、离线模板计划都可以正常使用。",
                    null,
                    "error",
                    "未配置模型"
                )
            )
            return SendResult(false, "未配置模型")
        }

        val payload = buildUserPayload(userText)
        val outcome = repo.llm.chat(payload)

        return when (outcome) {
            is LlmClient.Outcome.Fail -> {
                repo.saveMessage(
                    assistantMessage(
                        sessionId,
                        "请求没有成功：${outcome.message}",
                        null,
                        "error",
                        outcome.kind.name
                    )
                )
                SendResult(false, outcome.message)
            }

            is LlmClient.Outcome.Ok -> {
                val reply = outcome.reply
                val cards = JSONArray()

                when (reply.intent) {
                    "plan" -> handlePlan(reply.params, cards)
                    "record" -> handleRecord(reply.params, cards)
                    "nutrition" -> handleNutrition(reply.params, cards)
                    "habit" -> handleHabit(reply.params, cards)
                    "budget" -> handleBudget(reply.params, reply.text, cards)
                    else -> Unit
                }

                val meta = buildString {
                    append("${reply.model} · ${reply.latencyMs}ms")
                    if (reply.tokensIn != null || reply.tokensOut != null) {
                        append(" · ")
                        append("↓${reply.tokensIn ?: 0} ↑${reply.tokensOut ?: 0} tokens")
                    }
                    if (reply.warnings.isNotEmpty()) {
                        append("\n⚠ ")
                        append(reply.warnings.joinToString("；"))
                    }
                }

                val body = if (reply.warnings.isEmpty()) reply.text
                else reply.text + "\n\n> " + reply.warnings.joinToString("；")

                repo.saveMessage(
                    assistantMessage(
                        sessionId,
                        body,
                        if (cards.length() == 0) null else cards.toString(),
                        "ok",
                        meta
                    )
                )
                if (userMessage.let { it.content.length > 0 }) {
                    val sessions = repo.sessions()
                    val current = sessions.firstOrNull { it.id == sessionId }
                    if (current != null && current.title == "新会话") {
                        repo.renameSession(sessionId, userText.take(16))
                    }
                }
                SendResult(true)
            }
        }
    }

    // ------------------------------------------------------------------ 意图处理

    private suspend fun handlePlan(params: JSONObject?, cards: JSONArray) {
        if (params == null) return
        val mealsObj = params.optJSONObject("meals") ?: return
        val budgetYuan = params.doubleOrNull("budget_yuan")
        val budgetFen = budgetYuan?.let { (it * 100).toLong() } ?: settings.dailyBudgetFen
        val day = Time.todayKey()
        val planId = "plan_" + UUID.randomUUID().toString().take(8)

        val items = mutableListOf<PlanItem>()
        val mealKeys = listOf(MealType.BREAKFAST, MealType.LUNCH, MealType.DINNER, MealType.SNACK)
        for (meal in mealKeys) {
            val arr = mealsObj.optJSONArray(meal) ?: continue
            for (i in 0 until arr.length()) {
                val entry = arr.optJSONObject(i) ?: continue
                val name = entry.optString("food").ifBlank { entry.optString("name") }
                if (name.isBlank()) continue
                val grams = entry.doubleOrNull("grams") ?: 100.0
                val food: Food? = repo.findFoodByName(name)
                val factor = grams * (food?.edibleRatio ?: 1.0) / 100.0
                items += PlanItem(
                    id = "pi_" + UUID.randomUUID().toString().take(8),
                    planId = planId,
                    mealType = meal,
                    foodId = food?.id,
                    name = food?.displayName ?: name,
                    grams = grams,
                    costFen = null,
                    kcal = food?.kcal?.let { it * factor },
                    protein = food?.protein?.let { it * factor },
                    fat = food?.fat?.let { it * factor },
                    carbs = food?.carbs?.let { it * factor }
                )
            }
        }
        if (items.isEmpty()) return

        val plan = Plan(
            id = planId,
            date = day,
            budgetFen = budgetFen,
            status = "draft",
            source = "ai",
            version = 1,
            createdAt = Time.now(),
            items = items
        )
        repo.savePlan(plan)
        cards.put(dietPlanCard(plan))
    }

    private fun handleRecord(params: JSONObject?, cards: JSONArray) {
        if (params == null) return
        val mealType = params.optString("meal_type").ifBlank { Time.mealTypeByHour(Time.now()) }
            .let { if (MealType.all.contains(it)) it else Time.mealTypeByHour(Time.now()) }
        val costYuan = params.doubleOrNull("cost_yuan")
        val costFen = costYuan?.let { (it * 100).toLong() }
        val arr = params.optJSONArray("items") ?: JSONArray()
        val items = mutableListOf<JSONObject>()
        for (i in 0 until arr.length()) {
            val entry = arr.optJSONObject(i) ?: continue
            val name = entry.optString("food").ifBlank { entry.optString("name") }
            if (name.isBlank()) continue
            val grams = entry.doubleOrNull("grams") ?: 100.0
            items += JSONObject().put("name", name).put("grams", grams)
        }
        if (items.isEmpty() && costFen == null) return

        cards.put(
            JSONObject()
                .put("id", "c_" + UUID.randomUUID().toString().take(6))
                .put("type", "record_draft")
                .put("status", "partial")
                .put("data", JSONObject()
                    .put("mealType", mealType)
                    .put("costFen", costFen ?: JSONObject.NULL)
                    .put("eatenAt", Time.now())
                    .put("items", JSONArray(items))
                )
        )
    }

    private suspend fun handleNutrition(params: JSONObject?, cards: JSONArray) {
        if (params == null) return
        val arr = params.optJSONArray("items") ?: return
        val nutrients = mutableListOf<NutrientItem>()
        for (i in 0 until arr.length()) {
            val entry = arr.optJSONObject(i) ?: continue
            val name = entry.optString("food").ifBlank { entry.optString("name") }
            if (name.isBlank()) continue
            val grams = entry.doubleOrNull("grams") ?: 100.0
            nutrients += NutrientItem(name = name, grams = grams, food = repo.findFoodByName(name))
        }
        if (nutrients.isEmpty()) return
        val result = NutritionCalculator.compute(nutrients)
        cards.put(nutritionCard(result))
    }

    private suspend fun handleHabit(params: JSONObject?, cards: JSONArray) {
        val days = params?.optInt("days", 7)?.coerceIn(1, 90) ?: 7
        val to = Time.todayKey()
        val from = Time.addDays(to, -(days - 1))
        val records = repo.recordsBetween(from, to)
        val stats = HabitAnalyzer.analyze(records, from, to)
        cards.put(habitCard(stats))
    }

    private fun handleBudget(params: JSONObject?, text: String, cards: JSONArray) {
        val budgetYuan = params?.doubleOrNull("budget_yuan") ?: return
        val itemsArr = params.optJSONArray("items") ?: return
        val items = mutableListOf<com.foodmoment.app.domain.BudgetItem>()
        for (i in 0 until itemsArr.length()) {
            val entry = itemsArr.optJSONObject(i) ?: continue
            val name = entry.optString("name").ifBlank { "项目" }
            val unit = entry.doubleOrNull("unit_yuan")?.let { (it * 100).toLong() }
            val qty = entry.optInt("quantity", 1).coerceAtLeast(1)
            items += com.foodmoment.app.domain.BudgetItem(name, unit, qty)
        }
        if (items.isEmpty()) return

        val result = com.foodmoment.app.domain.BudgetCalculator.calculate(
            items = items,
            budgetFen = (budgetYuan * 100).toLong()
        )
        val data = JSONObject()
            .put("budgetFen", result.budgetFen)
            .put("knownSubtotalFen", result.knownSubtotalFen)
            .put("deltaFen", result.deltaFen)
            .put("unknownCount", result.unknownItems.size)
            .put("unknownItems", JSONArray(result.unknownItems))
        cards.put(
            JSONObject()
                .put("id", "c_" + UUID.randomUUID().toString().take(6))
                .put("type", "budget")
                .put("status", if (result.unknownItems.isEmpty()) "success" else "partial")
                .put("data", data)
        )
    }

    // ------------------------------------------------------------------ 事实包

    private suspend fun buildUserPayload(userText: String): String {
        val today = Time.todayKey()
        val from = Time.addDays(today, -6)
        val records = repo.recordsBetween(from, today)
        val foods = repo.foods()

        val profile = JSONObject()
            .put("daily_budget_fen", settings.dailyBudgetFen)
            .put("preferences", JSONArray(settings.preferences))
            .put("allergens", JSONArray(settings.allergens))

        val todayRecords = records.filter { Time.dayKey(it.eatenAt) == today }
        val previousRecords = records.filter { Time.dayKey(it.eatenAt) != today }

        // 今天：逐餐明细，模型才能回答「我今天吃了什么」
        val todayLog = JSONObject()
            .put("date", today)
            .put("budget_fen", settings.dailyBudgetFen)
            .put("spent_fen", todayRecords.mapNotNull { it.costFen }.sum())
            .put("meal_count", todayRecords.size)
            .put(
                "meals",
                JSONArray(
                    todayRecords.sortedBy { it.eatenAt }.map { recordJson(it) }
                )
            )
            .put("nutrition", nutritionJson(todayRecords))

        // 前 6 天：按天压缩，保留菜名与克重，用于「昨天吃了什么」「这周如何」
        val previousDays = JSONArray()
        previousRecords
            .groupBy { Time.dayKey(it.eatenAt) }
            .toSortedMap(compareByDescending<String> { it })
            .forEach { (day, list) ->
                previousDays.put(
                    JSONObject()
                        .put("date", day)
                        .put("meal_count", list.size)
                        .put("spent_fen", list.mapNotNull { it.costFen }.sum())
                        .put("kcal", nutritionJson(list).opt("kcal"))
                        .put(
                            "foods",
                            JSONArray(
                                list.sortedBy { it.eatenAt }
                                    .flatMap { record -> record.items.map { foodLine(it) } }
                                    .take(16)
                            )
                        )
                )
            }

        val summary = JSONObject()
            .put("days", 7)
            .put("meals", records.size)
            .put("recorded_days", records.map { Time.dayKey(it.eatenAt) }.distinct().size)
            .put(
                "avg_cost_fen",
                records.mapNotNull { it.costFen }.takeIf { it.isNotEmpty() }
                    ?.average()?.toLong() ?: JSONObject.NULL
            )

        val fact = JSONObject()
            .put("today", today)
            .put("profile", profile)
            .put(
                "diet_log",
                JSONObject()
                    .put("today", todayLog)
                    .put("previous_days", previousDays)
            )
            .put("recent_records_summary", summary)
            .put(
                "available_foods",
                JSONArray(foods.take(60).map { it.displayName })
            )
            .put(
                "note",
                "diet_log 是用户本机真实记录，可直接引用其中的菜名、克重、金额与日期；" +
                    "记录里没有的餐食不要编造。金额与营养的最终数值由 App 本地计算。"
            )

        return "【事实包】\n${fact}\n\n【用户】\n$userText"
    }

    private fun recordJson(record: DietRecord): JSONObject = JSONObject()
        .put("meal_type", record.mealType)
        .put("time", Time.timeLabel(record.eatenAt))
        .put("cost_fen", record.costFen ?: JSONObject.NULL)
        .put("source", record.source)
        .put("items", JSONArray(record.items.map { foodLine(it) }))
        .put("note", record.note.takeIf { it.isNotBlank() } ?: JSONObject.NULL)

    private fun foodLine(item: RecordItem): String =
        if (item.grams > 0) "${item.name}×${item.grams.toInt()}g" else item.name

    private fun nutritionJson(records: List<DietRecord>): JSONObject {
        val result = NutritionCalculator.fromRecordItems(records.flatMap { it.items })
        return JSONObject()
            .put("kcal", result.kcal?.let { Math.round(it).toInt() } ?: JSONObject.NULL)
            .put("protein_g", result.protein?.let { round1(it) } ?: JSONObject.NULL)
            .put("fat_g", result.fat?.let { round1(it) } ?: JSONObject.NULL)
            .put("carbs_g", result.carbs?.let { round1(it) } ?: JSONObject.NULL)
            .put("coverage", round2(result.coverage))
            .put("unknown_items", result.unknownCount)
    }

    private fun round1(value: Double): Double = Math.round(value * 10.0) / 10.0

    private fun round2(value: Double): Double = Math.round(value * 100.0) / 100.0

    // ------------------------------------------------------------------ 卡片构造

    private fun assistantMessage(
        sessionId: String,
        content: String,
        cardsJson: String?,
        status: String,
        detail: String?
    ) = ChatMessage(
        id = "m_" + UUID.randomUUID().toString().take(8),
        sessionId = sessionId,
        role = "assistant",
        content = content,
        cardsJson = cardsJson,
        requestId = "req_" + UUID.randomUUID().toString().take(8),
        createdAt = Time.now(),
        status = status,
        detail = detail
    )

    private fun dietPlanCard(plan: Plan): JSONObject {
        val check = PlanValidator.check(plan)
        val meals = JSONArray()
        listOf(MealType.BREAKFAST, MealType.LUNCH, MealType.DINNER, MealType.SNACK).forEach { meal ->
            val items = plan.itemsOf(meal)
            if (items.isEmpty()) return@forEach
            meals.put(
                JSONObject()
                    .put("mealType", meal)
                    .put("items", JSONArray(items.map { item ->
                        JSONObject()
                            .put("id", item.id)
                            .put("name", item.name)
                            .put("grams", item.grams)
                            .put("costFen", item.costFen ?: JSONObject.NULL)
                            .put("kcal", item.kcal ?: JSONObject.NULL)
                    }))
            )
        }
        return JSONObject()
            .put("id", "c_" + UUID.randomUUID().toString().take(6))
            .put("type", "diet_plan")
            .put("status", if (check.unknownCount > 0) "partial" else "success")
            .put("data", JSONObject()
                .put("planId", plan.id)
                .put("date", plan.date)
                .put("budgetFen", plan.budgetFen)
                .put("knownTotalFen", check.knownTotalFen)
                .put("unknownCount", check.unknownCount)
                .put("deltaFen", check.deltaFen)
                .put("source", plan.source)
                .put("meals", meals)
            )
    }

    private fun nutritionCard(result: com.foodmoment.app.domain.NutrientResult): JSONObject {
        val lines = JSONArray()
        result.lines.forEach { line ->
            lines.put(
                JSONObject()
                    .put("name", line.name)
                    .put("grams", line.grams)
                    .put("kcal", line.kcal ?: JSONObject.NULL)
                    .put("protein", line.protein ?: JSONObject.NULL)
                    .put("fat", line.fat ?: JSONObject.NULL)
                    .put("carbs", line.carbs ?: JSONObject.NULL)
                    .put("pending", line.pending)
                    .put("composite", line.composite)
            )
        }
        return JSONObject()
            .put("id", "c_" + UUID.randomUUID().toString().take(6))
            .put("type", "nutrition")
            .put("status", if (result.isEstimate) "partial" else "success")
            .put("data", JSONObject()
                .put("kcal", result.kcal ?: JSONObject.NULL)
                .put("protein", result.protein ?: JSONObject.NULL)
                .put("fat", result.fat ?: JSONObject.NULL)
                .put("carbs", result.carbs ?: JSONObject.NULL)
                .put("coverage", result.coverage)
                .put("unknownCount", result.unknownCount)
                .put("lines", lines)
            )
    }

    private fun habitCard(stats: com.foodmoment.app.domain.HabitStats): JSONObject {
        val meals = JSONObject()
        stats.mealCounts.forEach { (k, v) -> meals.put(k, v) }
        return JSONObject()
            .put("id", "c_" + UUID.randomUUID().toString().take(6))
            .put("type", "habit")
            .put("status", if (stats.enoughData) "success" else "partial")
            .put("data", JSONObject()
                .put("fromDay", stats.fromDay)
                .put("toDay", stats.toDay)
                .put("spanDays", stats.spanDays)
                .put("recordedDays", stats.recordedDays)
                .put("meals", meals)
                .put("knownCostFen", stats.knownCostFen)
                .put("missingCostCount", stats.missingCostCount)
                .put("avgCostFen", stats.avgCostFen ?: JSONObject.NULL)
            )
    }

    /** 供 UI 在校验离线模板计划时复用 */
    fun describePlan(plan: Plan): String {
        val check = PlanValidator.check(plan)
        return buildString {
            append("已知合计 ${Money.format(check.knownTotalFen)}")
            if (check.unknownCount > 0) append("，${check.unknownCount} 项待核价")
            if (check.deltaFen >= 0) append("，余 ${Money.format(check.deltaFen)}")
            else append("，超支 ${Money.format(-check.deltaFen)}")
        }
    }

    companion object {
        fun newRecordItem(recordId: String, name: String, grams: Double, food: Food?): RecordItem =
            RecordItem(
                id = "ri_" + UUID.randomUUID().toString().take(8),
                recordId = recordId,
                foodId = food?.id,
                name = name,
                grams = grams,
                kcal100 = food?.kcal,
                protein100 = food?.protein,
                fat100 = food?.fat,
                carbs100 = food?.carbs
            )
    }
}

private fun JSONObject.doubleOrNull(key: String): Double? =
    if (has(key) && !isNull(key)) optDouble(key) else null
