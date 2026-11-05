package com.foodmoment.app

import com.foodmoment.app.core.Money
import com.foodmoment.app.core.Time
import com.foodmoment.app.data.DietRecord
import com.foodmoment.app.data.Food
import com.foodmoment.app.data.RecordItem
import com.foodmoment.app.domain.BudgetCalculator
import com.foodmoment.app.domain.BudgetItem
import com.foodmoment.app.domain.HabitAnalyzer
import com.foodmoment.app.domain.NutrientItem
import com.foodmoment.app.domain.NutritionCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 领域层单元测试（14.1）。全部跑在 JVM 上，不依赖 Android 框架。
 */
class DomainTest {

    private val rice = Food(
        id = "rice", name = "Rice", nameZh = "白米饭",
        kcal = 130.0, protein = 2.7, fat = 0.3, carbs = 28.2
    )

    // ---------------------------------------------------------------- 金额

    @Test
    fun parseYuan_acceptsTwoDecimals() {
        assertEquals(1250L, Money.parseYuan("12.5"))
        assertEquals(1250L, Money.parseYuan("12.50"))
        assertEquals(1200L, Money.parseYuan("¥12"))
        assertEquals(0L, Money.parseYuan("0"))
    }

    @Test
    fun parseYuan_rejectsInvalid() {
        assertNull(Money.parseYuan("12.555"))
        assertNull(Money.parseYuan("-5"))
        assertNull(Money.parseYuan("abc"))
        assertNull(Money.parseYuan(""))
    }

    @Test
    fun format_keepsUnit() {
        assertEquals("¥12.50", Money.format(1250L))
        assertEquals("¥12", Money.format(1200L))
        assertEquals("未知", Money.format(null))
        assertEquals("-¥3", Money.format(-300L))
    }

    // ---------------------------------------------------------------- 预算

    @Test
    fun budget_unknownFeeIsNotZero() {
        val result = BudgetCalculator.calculate(
            items = listOf(BudgetItem("午饭", 1200L, 1)),
            fees = listOf(BudgetItem("配送费", null, 1)),
            budgetFen = 2000L
        )
        assertEquals(1200L, result.knownSubtotalFen)
        assertEquals(800L, result.deltaFen)
        assertNull("有未知项时不能判定是否在预算内", result.withinBudget)
        assertEquals(1, result.missingFeeItems.size)
    }

    @Test
    fun budget_withinWhenAllKnown() {
        val result = BudgetCalculator.calculate(
            items = listOf(BudgetItem("午饭", 1500L, 2)),
            budgetFen = 4000L
        )
        assertEquals(3000L, result.knownSubtotalFen)
        assertEquals(true, result.withinBudget)
    }

    // ---------------------------------------------------------------- 营养

    @Test
    fun nutrition_doublesWithGrams() {
        val at100 = NutritionCalculator.compute(listOf(NutrientItem("米饭", 100.0, rice)))
        val at200 = NutritionCalculator.compute(listOf(NutrientItem("米饭", 200.0, rice)))
        assertEquals(130.0, at100.kcal!!, 0.001)
        assertEquals(260.0, at200.kcal!!, 0.001)
        assertEquals(1.0, at100.coverage, 0.001)
    }

    @Test
    fun nutrition_unknownFoodCountedButNotZero() {
        val result = NutritionCalculator.compute(
            listOf(
                NutrientItem("米饭", 100.0, rice),
                NutrientItem("未知菜", 100.0, null)
            )
        )
        assertEquals(130.0, result.kcal!!, 0.001)
        assertEquals(1, result.unknownCount)
        assertEquals(0.5, result.coverage, 0.001)
        assertTrue(result.isEstimate)
    }

    @Test
    fun nutrition_noDataAtAllIsNull() {
        val result = NutritionCalculator.compute(listOf(NutrientItem("未知", 200.0, null)))
        assertNull(result.kcal)
        assertTrue(!result.hasAnyValue)
    }

    // ---------------------------------------------------------------- 习惯

    @Test
    fun habit_countsDaysAndMissingCost() {
        val day1 = Time.dayStart("2026-11-01")
        val day2 = Time.dayStart("2026-11-02")
        val records = listOf(
            record("r1", day1 + 8 * 3600_000L, "breakfast", 800L),
            record("r2", day1 + 12 * 3600_000L, "lunch", 1500L),
            record("r3", day2 + 12 * 3600_000L, "lunch", null)
        )
        val stats = HabitAnalyzer.analyze(records, "2026-11-01", "2026-11-02")

        assertEquals(2, stats.spanDays)
        assertEquals(2, stats.recordedDays)
        assertEquals(3, records.size)
        assertEquals(2300L, stats.knownCostFen)
        assertEquals(1, stats.missingCostCount)
        assertEquals(1, stats.mealCounts["breakfast"] ?: 0)
        assertEquals(2, stats.mealCounts["lunch"] ?: 0)
        assertTrue("少于 3 个记录日不给趋势结论", !stats.enoughData)
    }

    private fun record(id: String, at: Long, meal: String, cost: Long?) = DietRecord(
        id = id,
        eatenAt = at,
        mealType = meal,
        costFen = cost,
        note = "",
        sourcePlanId = null,
        deletedAt = null,
        items = listOf(
            RecordItem(
                id = "i_$id", recordId = id, foodId = "rice", name = "白米饭",
                grams = 100.0, kcal100 = 130.0, protein100 = 2.7, fat100 = 0.3, carbs100 = 28.2
            )
        )
    )
}
