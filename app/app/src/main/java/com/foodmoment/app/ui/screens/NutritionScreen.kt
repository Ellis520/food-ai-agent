package com.foodmoment.app.ui.screens

import androidx.compose.foundation.clickable
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.foodmoment.app.AppContainer
import com.foodmoment.app.data.Food
import com.foodmoment.app.domain.NutrientItem
import com.foodmoment.app.domain.NutritionCalculator
import com.foodmoment.app.ui.Navigator
import com.foodmoment.app.ui.components.CompositeTag
import com.foodmoment.app.ui.components.EmptyState
import com.foodmoment.app.ui.components.Field
import com.foodmoment.app.ui.components.KeyValueRow
import com.foodmoment.app.ui.components.PendingTag
import com.foodmoment.app.ui.components.SectionCard
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.UUID

/**
 * 营养分析（4.4 / NUT-01~07）。
 * 单次换算 + 食物库；统计在「分析」页。数值全部本地计算，标明来源与覆盖率。
 */
@Composable
fun NutritionScreen(container: AppContainer, nav: Navigator) {
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var suggestions by remember { mutableStateOf<List<Food>>(emptyList()) }
    var showCustom by remember { mutableStateOf(false) }

    val entries = remember { mutableStateListOf<NutrientItem>() }

    LaunchedEffect(query) {
        suggestions = if (query.isBlank()) emptyList() else container.repo.searchFoods(query, 6)
    }

    val result = NutritionCalculator.compute(entries.toList())

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 10.dp)
    ) {
        item {
            SectionCard(title = "添加食物", subtitle = "内置数据标「待核验」，自定义食物请自行确认数值") {
                Field(
                    label = "搜索食物",
                    value = query,
                    onValueChange = { query = it },
                    placeholder = "例如 米饭 / 鸡蛋 / 豆腐"
                )
                suggestions.forEach { food ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                entries.add(NutrientItem(food.displayName, 100.0, food))
                                query = ""
                            }
                            .padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(food.displayName, style = MaterialTheme.typography.bodyMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (food.pending) PendingTag()
                            if (food.composite) CompositeTag()
                            Text(
                                food.kcal?.let { "${it.toInt()} kcal" } ?: "无数据",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
                TextButton(onClick = { showCustom = true }) { Text("+ 新建自定义食物") }
            }
        }

        if (entries.isNotEmpty()) {
            item {
                SectionCard(title = "本次换算") {
                    entries.forEachIndexed { index, item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(item.name, style = MaterialTheme.typography.bodySmall)
                                Text(
                                    "${item.grams.toInt()}g" +
                                        (item.food?.edibleRatio?.let { " · 可食部 ${(it * 100).toInt()}%" } ?: ""),
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                            TextButton(onClick = {
                                entries[index] = item.copy(grams = (item.grams + 50).coerceAtMost(2000.0))
                            }) { Text("+50g") }
                            TextButton(onClick = { entries.removeAt(index) }) { Text("移除") }
                        }
                    }
                }
            }

            item {
                SectionCard(title = "结果") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        MetricBlock("能量", result.kcal?.let { "${it.toInt()}" } ?: "未知", "kcal")
                        MetricBlock("蛋白质", result.protein?.let { one(it) } ?: "未知", "g")
                        MetricBlock("脂肪", result.fat?.let { one(it) } ?: "未知", "g")
                        MetricBlock("碳水", result.carbs?.let { one(it) } ?: "未知", "g")
                    }
                    Spacer(Modifier.height(8.dp))
                    KeyValueRow("覆盖率", "${(result.coverage * 100).toInt()}%（有营养依据的克数占比）")
                    if (result.unknownCount > 0) {
                        KeyValueRow("无数据条目", "${result.unknownCount} 项")
                    }
                    HorizontalDivider(Modifier.padding(vertical = 6.dp))
                    result.lines.forEach { line ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(line.name, style = MaterialTheme.typography.bodySmall)
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    if (line.pending) PendingTag()
                                    if (line.composite) CompositeTag()
                                }
                            }
                            Text(
                                line.kcal?.let { "${it.toInt()} kcal" } ?: "未知",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                    Text(
                        "按每 100g 成分 × 实际可食克重 ÷ 100 计算；数值仅供参考，不构成医疗建议。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        } else {
            item {
                EmptyState("还没有添加食物", "搜索并点选上面的项目，或新建自定义食物")
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }

    if (showCustom) {
        CustomFoodDialog(container, onDismiss = { showCustom = false }) { food ->
            showCustom = false
            entries.add(NutrientItem(food.displayName, 100.0, food))
        }
    }
}

@Composable
private fun MetricBlock(label: String, value: String, unit: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(label, style = MaterialTheme.typography.labelSmall)
        Text(unit, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun CustomFoodDialog(
    container: AppContainer,
    onDismiss: () -> Unit,
    onCreated: (Food) -> Unit
) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var kcal by remember { mutableStateOf("") }
    var protein by remember { mutableStateOf("") }
    var fat by remember { mutableStateOf("") }
    var carbs by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新建自定义食物") },
        text = {
            Column {
                Text(
                    "数值按每 100g 填写，可先只填能量。留空的项不会按 0 计算。",
                    style = MaterialTheme.typography.labelSmall
                )
                Field("名称", name, { name = it }, placeholder = "例如 燕麦粥")
                Field("能量 (kcal/100g)", kcal, { kcal = it }, keyboardType = KeyboardType.Decimal)
                Field("蛋白质 (g/100g)", protein, { protein = it }, keyboardType = KeyboardType.Decimal)
                Field("脂肪 (g/100g)", fat, { fat = it }, keyboardType = KeyboardType.Decimal)
                Field("碳水 (g/100g)", carbs, { carbs = it }, keyboardType = KeyboardType.Decimal)
                if (error != null) Text(error ?: "", color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isBlank()) {
                    error = "请填写名称"
                    return@TextButton
                }
                val food = Food(
                    id = "custom_" + UUID.randomUUID().toString().take(8),
                    name = name.trim(),
                    nameZh = name.trim(),
                    kcal = kcal.toDoubleOrNull(),
                    protein = protein.toDoubleOrNull(),
                    fat = fat.toDoubleOrNull(),
                    carbs = carbs.toDoubleOrNull(),
                    dataType = "custom",
                    isCustom = true
                )
                scope.launch {
                    container.repo.addCustomFood(food)
                    onCreated(food)
                }
            }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

private fun one(value: Double): String = String.format(Locale.CHINA, "%.1f", value)
