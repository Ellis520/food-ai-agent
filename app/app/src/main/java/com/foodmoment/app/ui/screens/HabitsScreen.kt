package com.foodmoment.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.foodmoment.app.AppContainer
import com.foodmoment.app.core.MealType
import com.foodmoment.app.core.Money
import com.foodmoment.app.core.Time
import com.foodmoment.app.data.DietRecord
import com.foodmoment.app.domain.HabitAnalyzer
import com.foodmoment.app.domain.HabitStats
import com.foodmoment.app.domain.NutritionCalculator
import com.foodmoment.app.ui.Navigator
import com.foodmoment.app.ui.Screen
import com.foodmoment.app.ui.components.BarChart
import com.foodmoment.app.ui.components.EmptyState
import com.foodmoment.app.ui.components.KeyValueRow
import com.foodmoment.app.ui.components.SectionCard
import com.foodmoment.app.ui.components.Tag
import com.foodmoment.app.ui.theme.WarningAmber

/**
 * 饮食习惯分析（4.5 / HAB-01~06）。
 * 统计全部本地完成；少于 3 个记录日只给摘要（不把漏记当没吃）。
 */
@Composable
fun HabitsScreen(container: AppContainer, nav: Navigator) {
    val rev by container.repo.revision.collectAsState()
    var days by remember { mutableIntStateOf(7) }
    var stats by remember { mutableStateOf<HabitStats?>(null) }
    var records by remember { mutableStateOf<List<DietRecord>>(emptyList()) }
    var kcalTotal by remember { mutableStateOf<Double?>(null) }

    LaunchedEffect(rev, days) {
        val to = Time.todayKey()
        val from = Time.addDays(to, -(days - 1))
        records = container.repo.recordsBetween(from, to)
        stats = HabitAnalyzer.analyze(records, from, to)
        val nutrition = NutritionCalculator.fromRecordItems(records.flatMap { it.items })
        kcalTotal = nutrition.kcal
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 10.dp)
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(7 to "近 7 天", 30 to "近 30 天").forEach { (value, label) ->
                    TextButton(onClick = { days = value }) {
                        Text(
                            label,
                            fontWeight = if (days == value) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        }

        val current = stats
        if (current == null) {
            item { EmptyState("暂无数据") }
        } else {
            item {
                SectionCard(
                    title = "${current.fromDay} ~ ${current.toDay}",
                    subtitle = "共 ${current.spanDays} 天，已记录 ${current.recordedDays} 天"
                ) {
                    if (!current.enoughData) {
                        Tag("样本不足，仅作记录摘要", WarningAmber)
                        Spacer(Modifier.height(6.dp))
                    }
                    KeyValueRow(
                        "记录覆盖率",
                        "${(current.coverage * 100).toInt()}%（漏记不等于没吃）"
                    )
                    KeyValueRow("记录餐次", "${records.size} 次")
                    MealType.all.forEach { type ->
                        val count = current.mealCounts[type] ?: 0
                        if (count > 0) KeyValueRow("· ${MealType.label(type)}", "$count 次")
                    }
                }
            }

            item {
                SectionCard(title = "消费与预算") {
                    KeyValueRow("有金额合计", Money.format(current.knownCostFen))
                    KeyValueRow("单日均值", Money.format(current.knownCostFen / maxOf(1, current.recordedDays)))
                    KeyValueRow("预算", Money.format(container.settings.dailyBudgetFen))
                    if (current.missingCostCount > 0) {
                        KeyValueRow("金额缺失", "${current.missingCostCount} 条未计入")
                    }
                    Spacer(Modifier.height(10.dp))
                    BarChart(
                        points = current.dailyCost.map { (day, cost) -> Time.dayLabel(day).take(2) to cost.toDouble() },
                        unit = "元"
                    )
                }
            }

            item {
                SectionCard(title = "营养趋势") {
                    KeyValueRow("区间能量", kcalTotal?.let { "${it.toInt()} kcal" } ?: "无可用数据")
                    Text(
                        "只有记录了食物且有营养依据的条目才会计入；缺失项不按 0 计。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = { nav.go(Screen.Records) }) { Text("查看原始记录 ›") }
                }
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}
