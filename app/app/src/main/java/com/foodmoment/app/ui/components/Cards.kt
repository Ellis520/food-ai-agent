package com.foodmoment.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.foodmoment.app.core.MealType
import com.foodmoment.app.core.Money
import com.foodmoment.app.ui.theme.DangerRed
import com.foodmoment.app.ui.theme.InfoBlue
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/**
 * 卡片渲染（6.1）。卡片由本地构造，模型只能引用，不能生成（5.4）。
 */
@Composable
fun ChatCardList(
    cardsJson: String?,
    onSavePlan: (String) -> Unit,
    onConfirmRecord: (JSONObject) -> Unit
) {
    if (cardsJson.isNullOrBlank()) return
    val array = runCatching { JSONArray(cardsJson) }.getOrNull() ?: return
    for (i in 0 until array.length()) {
        val card = array.optJSONObject(i) ?: continue
        val data = card.optJSONObject("data") ?: continue
        when (card.optString("type")) {
            "diet_plan" -> DietPlanCard(data, onSavePlan)
            "record_draft" -> RecordDraftCard(data, onConfirmRecord)
            "nutrition" -> NutritionCard(data)
            "habit" -> HabitCard(data)
            "budget" -> BudgetCard(data)
        }
    }
}

@Composable
private fun DietPlanCard(data: JSONObject, onSavePlan: (String) -> Unit) {
    val planId = data.optString("planId")
    val date = data.optString("date")
    val source = data.optString("source")
    val budget = data.optLong("budgetFen")
    val known = data.optLong("knownTotalFen")
    val unknown = data.optInt("unknownCount")
    val delta = data.optLong("deltaFen")
    val meals = data.optJSONArray("meals") ?: JSONArray()

    SectionCard(title = "饮食计划 · $date") {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (source == "template") {
                Tag("离线模板 · 非 AI 生成", MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Tag("AI 生成", MaterialTheme.colorScheme.primary)
            }
            if (unknown > 0) Tag("$unknown 项待核价", com.foodmoment.app.ui.theme.WarningAmber)
        }
        Spacer(Modifier.height(8.dp))

        for (m in 0 until meals.length()) {
            val meal = meals.optJSONObject(m) ?: continue
            Text(
                MealType.label(meal.optString("mealType")),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium
            )
            val items = meal.optJSONArray("items") ?: JSONArray()
            for (i in 0 until items.length()) {
                val item = items.optJSONObject(i) ?: continue
                val cost = if (item.has("costFen") && !item.isNull("costFen")) item.optLong("costFen") else null
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "${item.optString("name")} ${formatGrams(item.optDouble("grams", 0.0))}",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        if (cost == null) "待核价" else Money.format(cost),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (cost == null) com.foodmoment.app.ui.theme.WarningAmber
                        else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
        }

        HorizontalDivider(Modifier.padding(vertical = 6.dp))
        KeyValueRow("预算", Money.format(budget))
        KeyValueRow("已知合计", Money.format(known))
        if (unknown > 0) KeyValueRow("未计入项", "$unknown 项")
        KeyValueRow(
            if (delta >= 0) "预算余量" else "超出预算",
            Money.format(if (delta >= 0) delta else -delta),
            if (delta >= 0) MaterialTheme.colorScheme.onSurface else DangerRed
        )
        RowActions("保存计划" to { onSavePlan(planId) })
    }
}

@Composable
private fun RecordDraftCard(data: JSONObject, onConfirmRecord: (JSONObject) -> Unit) {
    val meal = MealType.label(data.optString("mealType"))
    val cost = if (data.has("costFen") && !data.isNull("costFen")) data.optLong("costFen") else null
    val items = data.optJSONArray("items") ?: JSONArray()

    SectionCard(title = "待确认记录 · $meal") {
        if (items.length() == 0) {
            Text("没有识别到具体食物，确认后仅记录金额", style = MaterialTheme.typography.bodySmall)
        }
        for (i in 0 until items.length()) {
            val item = items.optJSONObject(i) ?: continue
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(item.optString("name"), style = MaterialTheme.typography.bodySmall)
                Text(formatGrams(item.optDouble("grams", 0.0)), style = MaterialTheme.typography.bodySmall)
            }
        }
        Spacer(Modifier.height(6.dp))
        KeyValueRow("金额", if (cost == null) "未填写" else Money.format(cost))
        RowActions("确认记录" to { onConfirmRecord(data) })
    }
}

@Composable
private fun NutritionCard(data: JSONObject) {
    val kcal = data.dbl("kcal")
    val protein = data.dbl("protein")
    val fat = data.dbl("fat")
    val carbs = data.dbl("carbs")
    val coverage = data.optDouble("coverage", 0.0)
    val unknown = data.optInt("unknownCount")
    val lines = data.optJSONArray("lines") ?: JSONArray()

    SectionCard(title = "营养估算") {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Metric("能量", kcal?.let { "${it.toInt()}" } ?: "未知", "kcal")
            Metric("蛋白质", protein?.let { one(it) } ?: "未知", "g")
            Metric("脂肪", fat?.let { one(it) } ?: "未知", "g")
            Metric("碳水", carbs?.let { one(it) } ?: "未知", "g")
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "有营养依据的克数占比 ${(coverage * 100).toInt()}%" + if (unknown > 0) "，$unknown 项无数据" else "",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(6.dp))
        for (i in 0 until lines.length()) {
            val line = lines.optJSONObject(i) ?: continue
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(Modifier.weight(1f)) {
                    Text(line.optString("name"), style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (line.optBoolean("pending")) PendingTag()
                        if (line.optBoolean("composite")) CompositeTag()
                    }
                }
                Text(
                    line.dbl("kcal")?.let { "${it.toInt()} kcal" } ?: "未知",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun HabitCard(data: JSONObject) {
    val from = data.optString("fromDay")
    val to = data.optString("toDay")
    val span = data.optInt("spanDays")
    val recorded = data.optInt("recordedDays")
    val knownCost = data.optLong("knownCostFen")
    val missing = data.optInt("missingCostCount")
    val avg = if (data.has("avgCostFen") && !data.isNull("avgCostFen")) data.optLong("avgCostFen") else null
    val meals = data.optJSONObject("meals") ?: JSONObject()
    var mealTotal = 0
    MealType.all.forEach { mealTotal += meals.optInt(it) }

    SectionCard(title = "饮食统计 · $from ~ $to") {
        if (recorded < 3) {
            Tag("样本不足，仅作记录摘要", com.foodmoment.app.ui.theme.WarningAmber)
            Spacer(Modifier.height(6.dp))
        }
        KeyValueRow("记录天数", "$recorded / $span 天")
        KeyValueRow("记录餐次", "$mealTotal 次")
        MealType.all.forEach { type ->
            val count = meals.optInt(type)
            if (count > 0) KeyValueRow("· ${MealType.label(type)}", "$count 次")
        }
        KeyValueRow("有金额合计", Money.format(knownCost))
        if (missing > 0) KeyValueRow("金额缺失", "$missing 条未计入")
        if (avg != null) KeyValueRow("单餐均值", Money.format(avg))
        Spacer(Modifier.height(4.dp))
        Text(
            "漏记不等于没吃；统计只反映已记录的内容",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun BudgetCard(data: JSONObject) {
    val budget = data.optLong("budgetFen")
    val known = data.optLong("knownSubtotalFen")
    val delta = data.optLong("deltaFen")
    val unknown = data.optInt("unknownCount")

    SectionCard(title = "预算核算") {
        KeyValueRow("预算", Money.format(budget))
        KeyValueRow("已知小计", Money.format(known))
        KeyValueRow(
            if (delta >= 0) "余量" else "超支",
            Money.format(if (delta >= 0) delta else -delta),
            if (delta >= 0) MaterialTheme.colorScheme.onSurface else DangerRed
        )
        if (unknown > 0) {
            Spacer(Modifier.height(4.dp))
            Tag("$unknown 项未定价，未知不等于免费", com.foodmoment.app.ui.theme.WarningAmber)
        }
    }
}

@Composable
private fun Metric(label: String, value: String, unit: String) {
    Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(
            "$label/$unit",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun JSONObject.dbl(key: String): Double? =
    if (has(key) && !isNull(key)) optDouble(key) else null

private fun one(value: Double): String = String.format(Locale.CHINA, "%.1f", value)

private fun formatGrams(grams: Double): String =
    if (grams <= 0) "" else "${grams.toInt()}g"

@Suppress("unused")
private val cardTagColor = InfoBlue
