package com.foodmoment.app.domain

import com.foodmoment.app.data.Food
import com.foodmoment.app.data.Plan
import com.foodmoment.app.data.RecordItem

/**
 * 领域层：全部为不依赖 Android 框架的纯函数，可直接在 JVM 单元测试中运行（14.1）。
 */

// --------------------------------------------------------------------------- 预算

data class BudgetItem(val name: String, val unitFen: Long?, val quantity: Int = 1)

data class BudgetResult(
    val budgetFen: Long,
    val knownSubtotalFen: Long,
    val unknownItems: List<String>,
    val missingFeeItems: List<String>,
    val deltaFen: Long,
    /** 仅在无未知项时才有意义；有未知项时为 null（未知不等于 0） */
    val withinBudget: Boolean?
)

object BudgetCalculator {

    fun calculate(
        items: List<BudgetItem>,
        fees: List<BudgetItem> = emptyList(),
        budgetFen: Long
    ): BudgetResult {
        val known = items.filter { it.unitFen != null }
        val unknown = items.filter { it.unitFen == null }
        val knownFee = fees.filter { it.unitFen != null }
        val missingFee = fees.filter { it.unitFen == null }

        var subtotal = 0L
        known.forEach { subtotal += (it.unitFen ?: 0L) * it.quantity }
        knownFee.forEach { subtotal += (it.unitFen ?: 0L) * it.quantity }

        val hasUnknown = unknown.isNotEmpty() || missingFee.isNotEmpty()
        return BudgetResult(
            budgetFen = budgetFen,
            knownSubtotalFen = subtotal,
            unknownItems = unknown.map { it.name },
            missingFeeItems = missingFee.map { it.name },
            deltaFen = budgetFen - subtotal,
            withinBudget = if (hasUnknown) null else subtotal <= budgetFen
        )
    }
}

// --------------------------------------------------------------------------- 营养

data class NutrientItem(val name: String, val grams: Double, val food: Food?)

data class NutrientLine(
    val name: String,
    val grams: Double,
    val kcal: Double?,
    val protein: Double?,
    val fat: Double?,
    val carbs: Double?,
    val per100Known: Boolean,
    val pending: Boolean,
    val composite: Boolean
)

data class NutrientResult(
    val kcal: Double?,
    val protein: Double?,
    val fat: Double?,
    val carbs: Double?,
    /** 有营养依据的克数 / 总克数 */
    val coverage: Double,
    val knownCount: Int,
    val unknownCount: Int,
    val totalGrams: Double,
    val lines: List<NutrientLine>
) {
    val isEstimate: Boolean get() = unknownCount > 0
    val hasAnyValue: Boolean get() = kcal != null || protein != null || fat != null || carbs != null
}

object NutritionCalculator {

    fun compute(items: List<NutrientItem>): NutrientResult {
        var kcalSum = 0.0
        var proteinSum = 0.0
        var fatSum = 0.0
        var carbsSum = 0.0
        var kcalAny = false
        var proteinAny = false
        var fatAny = false
        var carbsAny = false

        var knownGrams = 0.0
        var totalGrams = 0.0
        var knownCount = 0
        var unknownCount = 0

        val lines = items.map { item ->
            val food = item.food
            val grams = if (item.grams > 0) item.grams else 0.0
            totalGrams += grams

            val edible = food?.edibleRatio ?: 1.0
            val effective = grams * edible

            val k = food?.kcal?.let { it * effective / 100.0 }
            val p = food?.protein?.let { it * effective / 100.0 }
            val f = food?.fat?.let { it * effective / 100.0 }
            val c = food?.carbs?.let { it * effective / 100.0 }

            if (k != null) { kcalSum += k; kcalAny = true }
            if (p != null) { proteinSum += p; proteinAny = true }
            if (f != null) { fatSum += f; fatAny = true }
            if (c != null) { carbsSum += c; carbsAny = true }

            if (k != null) { knownGrams += grams; knownCount++ } else { unknownCount++ }

            NutrientLine(
                name = item.name.ifBlank { food?.displayName ?: "未命名食物" },
                grams = grams,
                kcal = k,
                protein = p,
                fat = f,
                carbs = c,
                per100Known = food?.kcal != null,
                pending = food?.pending == true,
                composite = food?.composite == true
            )
        }

        return NutrientResult(
            kcal = if (kcalAny) kcalSum else null,
            protein = if (proteinAny) proteinSum else null,
            fat = if (fatAny) fatSum else null,
            carbs = if (carbsAny) carbsSum else null,
            coverage = if (totalGrams > 0) knownGrams / totalGrams else 0.0,
            knownCount = knownCount,
            unknownCount = unknownCount,
            totalGrams = totalGrams,
            lines = lines
        )
    }

    /** 从饮食记录条目还原（记录里存的是每 100g 快照，避免源数据变更导致历史漂移） */
    fun fromRecordItems(items: List<RecordItem>): NutrientResult = compute(
        items.map { ri ->
            NutrientItem(
                name = ri.name,
                grams = ri.grams,
                food = if (ri.kcal100 == null) null else Food(
                    id = ri.foodId ?: ri.id,
                    name = ri.name,
                    nameZh = ri.name,
                    kcal = ri.kcal100,
                    protein = ri.protein100,
                    fat = ri.fat100,
                    carbs = ri.carbs100,
                    dataType = "record"
                )
            )
        }
    )
}

// --------------------------------------------------------------------------- 计划校验

data class PlanCheck(
    val knownTotalFen: Long,
    val unknownCount: Int,
    val deltaFen: Long,
    val withinBudget: Boolean?,
    val messages: List<String>
)

object PlanValidator {

    fun check(plan: Plan): PlanCheck {
        val knownTotal = plan.items.mapNotNull { it.costFen }.sum()
        val unknown = plan.items.count { it.costFen == null }
        val delta = plan.budgetFen - knownTotal
        val messages = mutableListOf<String>()

        if (plan.items.isEmpty()) messages += "计划为空"
        if (unknown > 0) messages += "有 $unknown 项价格未知，未计入合计"
        if (knownTotal > plan.budgetFen) {
            messages += "已知合计超出预算 ${com.foodmoment.app.core.Money.format(knownTotal - plan.budgetFen)}"
        }
        val mealTypes = plan.items.map { it.mealType }.distinct()
        if (mealTypes.size == 1 && plan.items.isNotEmpty()) messages += "只有一餐有安排"

        return PlanCheck(
            knownTotalFen = knownTotal,
            unknownCount = unknown,
            deltaFen = delta,
            withinBudget = if (unknown > 0) null else knownTotal <= plan.budgetFen,
            messages = messages
        )
    }
}

// --------------------------------------------------------------------------- 习惯

data class HabitStats(
    val fromDay: String,
    val toDay: String,
    val spanDays: Int,
    val recordedDays: Int,
    val mealCounts: Map<String, Int>,
    val knownCostFen: Long,
    val missingCostCount: Int,
    val avgCostFen: Long?,
    val dailyCost: List<Pair<String, Long>>
) {
    val coverage: Double get() = if (spanDays <= 0) 0.0 else recordedDays.toDouble() / spanDays
    val enoughData: Boolean get() = recordedDays >= 3
}

object HabitAnalyzer {

    fun analyze(
        records: List<com.foodmoment.app.data.DietRecord>,
        fromDay: String,
        toDay: String
    ): HabitStats {
        val span = daySpan(fromDay, toDay)
        val byDay = records.groupBy { com.foodmoment.app.core.Time.dayKey(it.eatenAt) }

        val counts = mutableMapOf<String, Int>()
        records.forEach { counts[it.mealType] = (counts[it.mealType] ?: 0) + 1 }

        val known = records.mapNotNull { it.costFen }
        val missing = records.count { it.costFen == null }

        val daily = byDay.entries
            .sortedBy { it.key }
            .map { (day, list) -> day to list.mapNotNull { it.costFen }.sum() }

        return HabitStats(
            fromDay = fromDay,
            toDay = toDay,
            spanDays = span,
            recordedDays = byDay.size,
            mealCounts = counts,
            knownCostFen = known.sum(),
            missingCostCount = missing,
            avgCostFen = known.takeIf { it.isNotEmpty() }?.sum()?.div(known.size.toLong()),
            dailyCost = daily
        )
    }

    private fun daySpan(fromDay: String, toDay: String): Int {
        val a = com.foodmoment.app.core.Time.dayStart(fromDay)
        val b = com.foodmoment.app.core.Time.dayStart(toDay)
        return ((b - a) / 86_400_000L).toInt() + 1
    }
}
