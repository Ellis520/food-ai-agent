package com.foodmoment.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.foodmoment.app.AppContainer
import com.foodmoment.app.core.MealType
import com.foodmoment.app.core.Money
import com.foodmoment.app.core.Time
import com.foodmoment.app.data.DietRecord
import com.foodmoment.app.data.Food
import com.foodmoment.app.data.RecordItem
import com.foodmoment.app.ui.Navigator
import com.foodmoment.app.ui.Screen
import com.foodmoment.app.ui.components.DangerText
import com.foodmoment.app.ui.components.EmptyState
import com.foodmoment.app.ui.components.Field
import com.foodmoment.app.ui.components.KeyValueRow
import com.foodmoment.app.ui.components.SectionCard
import com.foodmoment.app.ui.components.Tag
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * 饮食记录（4.3 / REC-01~06）。
 * 金额缺失存 null，不按 0 计入（REC-05）。删除进回收站，7 天可恢复（REC-03）。
 */
@Composable
fun RecordsScreen(container: AppContainer, nav: Navigator) {
    val scope = rememberCoroutineScope()
    val rev by container.repo.revision.collectAsState()
    var day by remember { mutableStateOf(Time.todayKey()) }
    var records by remember { mutableStateOf<List<DietRecord>>(emptyList()) }
    var showAdd by remember { mutableStateOf(false) }
    var showTrash by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(rev, day) {
        records = container.repo.recordsOfDay(day)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 10.dp)
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = { day = Time.addDays(day, -1) }) { Text("‹ 前一天") }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        Time.dayLabel(day),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "${records.size} 餐 · ${Money.format(records.mapNotNull { it.costFen }.sum())}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(onClick = { day = Time.addDays(day, 1) }) { Text("后一天 ›") }
            }
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(onClick = { showAdd = true }, modifier = Modifier.weight(1f)) { Text("记一笔") }
                TextButton(onClick = { nav.go(Screen.Nutrition) }) { Text("营养") }
                TextButton(onClick = { showTrash = true }) { Text("回收站") }
            }
        }

        if (notice != null) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(notice ?: "", style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
                    TextButton(onClick = { notice = null }) { Text("知道了") }
                }
            }
        }

        if (records.isEmpty()) {
            item { EmptyState("这一天还没有记录", "点「记一笔」或直接在聊天里说「午饭吃了 15 块」") }
        }

        items(records, key = { it.id }) { record ->
            SectionCard(title = MealType.label(record.mealType), subtitle = Time.timeLabel(record.eatenAt)) {
                if (record.fromVision) {
                    Tag("拍照估算", com.foodmoment.app.ui.theme.WarningAmber)
                    Spacer(Modifier.height(4.dp))
                }
                if (record.items.isEmpty()) {
                    Text("未记录具体食物", style = MaterialTheme.typography.bodySmall)
                }
                record.items.forEach { item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(item.name, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        Text("${item.grams.toInt()}g", style = MaterialTheme.typography.bodySmall)
                    }
                }
                Spacer(Modifier.height(4.dp))
                KeyValueRow(
                    "金额",
                    if (record.costFen == null) "未填写" else Money.format(record.costFen),
                    if (record.costFen == null) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.onSurface
                )
                if (record.note.isNotBlank()) {
                    Text(record.note, style = MaterialTheme.typography.labelSmall)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = {
                        scope.launch {
                            container.repo.deleteRecord(record.id)
                            notice = "已移入回收站，7 天内可恢复"
                        }
                    }) { DangerText("删除") }
                }
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }

    if (showAdd) {
        AddRecordDialog(
            container = container,
            day = day,
            onDismiss = { showAdd = false },
            onSaved = {
                showAdd = false
                notice = "已记录"
            }
        )
    }

    if (showTrash) {
        TrashDialog(container = container, onDismiss = { showTrash = false })
    }
}

private val UNITS = listOf("克", "份", "碗", "个", "条", "杯", "勺")

/** 家庭计量单位到克的大致换算，界面会显示「≈」，用户可切到「克」精确填写 */
private fun unitFactor(unit: String): Double = when (unit) {
    "份" -> 200.0
    "碗" -> 250.0
    "个" -> 80.0
    "条" -> 100.0
    "杯" -> 250.0
    "勺" -> 15.0
    else -> 1.0
}

private data class DraftRow(
    val key: String,
    val name: String,
    val food: Food?,
    val unit: String,
    val amountText: String,
    val gramsText: String
) {
    val grams: Double? get() = gramsText.toDoubleOrNull()?.takeIf { it > 0 }

    fun summary(): String {
        val g = grams ?: return if (unit == "克") "克重未填" else "数量未填"
        val kcal = food?.kcal?.let { it * g / 100.0 }
        return buildString {
            if (unit != "克") {
                append(amountText.ifBlank { "1" })
                append(" $unit · ≈")
            }
            append("${g.toInt()}g")
            append(" · ")
            append(kcal?.let { "估算 ${it.toInt()} kcal" } ?: "营养未知")
        }
    }

    fun withUnit(next: String): DraftRow {
        if (next == unit) return this
        if (next == "克") {
            return copy(unit = next, gramsText = gramsText.ifBlank { "200" })
        }
        val amount = amountText.toDoubleOrNull() ?: 1.0
        return copy(
            unit = next,
            amountText = amountText.ifBlank { "1" },
            gramsText = (amount * unitFactor(next)).toInt().toString()
        )
    }

    fun withAmount(text: String): DraftRow {
        val parsed = text.toDoubleOrNull() ?: 0.0
        return copy(
            amountText = text,
            gramsText = (parsed * unitFactor(unit)).toInt().toString()
        )
    }
}

@Composable
private fun UnitPicker(unit: String, onPick: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { expanded = true }) { Text(unit) }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            UNITS.forEach { candidate ->
                DropdownMenuItem(
                    text = { Text(candidate) },
                    onClick = {
                        expanded = false
                        onPick(candidate)
                    }
                )
            }
        }
    }
}

@Composable
private fun AddRecordDialog(
    container: AppContainer,
    day: String,
    onDismiss: () -> Unit,
    onSaved: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var meal by remember { mutableStateOf(MealType.LUNCH) }
    var query by remember { mutableStateOf("") }
    var suggestions by remember { mutableStateOf<List<Food>>(emptyList()) }
    var rows by remember { mutableStateOf<List<DraftRow>>(emptyList()) }
    var costText by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(query) {
        suggestions = if (query.isBlank()) emptyList() else container.repo.searchFoods(query, 5)
    }

    fun addRow(name: String, food: Food?) {
        rows = rows + DraftRow(
            key = UUID.randomUUID().toString(),
            name = food?.displayName ?: name,
            food = food,
            unit = "克",
            amountText = "",
            gramsText = "200"
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("记一笔") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    MealType.all.forEach { type ->
                        TextButton(onClick = { meal = type }, modifier = Modifier.padding(0.dp)) {
                            Text(
                                MealType.label(type),
                                fontWeight = if (meal == type) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }

                Field(
                    label = "添加食物",
                    value = query,
                    onValueChange = { query = it },
                    placeholder = "搜索内置食物，或输入自定义名称"
                )

                suggestions.forEach { food ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                addRow(food.displayName, food)
                                query = ""
                            }
                            .padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(food.displayName, style = MaterialTheme.typography.bodySmall)
                        Text(
                            food.kcal?.let { "${it.toInt()} kcal/100g" } ?: "无数据",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }

                if (query.isNotBlank() && suggestions.none { it.displayName == query.trim() }) {
                    TextButton(onClick = {
                        addRow(query.trim(), null)
                        query = ""
                    }) { Text("+ 添加「${query.trim()}」（无营养数据）") }
                }

                if (rows.isEmpty()) {
                    Text(
                        "还没有添加食物。一次可以记录多种，比如早餐的馒头 + 豆浆 + 牛奶。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                rows.forEachIndexed { index, row ->
                    HorizontalDivider(Modifier.padding(vertical = 4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(row.name, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                row.summary(),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = {
                            rows = rows.filterIndexed { i, _ -> i != index }
                        }) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = "移除 ${row.name}",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        UnitPicker(row.unit) { next ->
                            rows = rows.toMutableList().also { it[index] = row.withUnit(next) }
                        }
                        if (row.unit == "克") {
                            OutlinedTextField(
                                value = row.gramsText,
                                onValueChange = { text ->
                                    val cleaned = text.filter { it.isDigit() || it == '.' }
                                    rows = rows.toMutableList().also {
                                        it[index] = row.copy(gramsText = cleaned)
                                    }
                                },
                                label = { Text("克重") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.width(120.dp)
                            )
                        } else {
                            OutlinedTextField(
                                value = row.amountText,
                                onValueChange = { text ->
                                    val cleaned = text.filter { it.isDigit() || it == '.' }
                                    rows = rows.toMutableList().also {
                                        it[index] = row.withAmount(cleaned)
                                    }
                                },
                                label = { Text("数量") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.width(110.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "≈${row.gramsText.ifBlank { "0" }}g",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
                Field(
                    label = "本餐金额（元，可不填）",
                    value = costText,
                    onValueChange = { costText = it },
                    keyboardType = KeyboardType.Decimal,
                    supporting = "整餐合计；留空表示未知，不会按 0 计算"
                )
                if (error != null) DangerText(error ?: "")
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val valid = rows.mapNotNull { row -> row.grams?.let { g -> row to g } }
                if (valid.isEmpty()) {
                    error = "请至少添加一种食物并填写克重"
                    return@TextButton
                }
                if (valid.size < rows.size) {
                    error = "有 ${rows.size - valid.size} 项克重为空，请补全或删除该行"
                    return@TextButton
                }
                val cost = if (costText.isBlank()) null else Money.parseYuan(costText)
                if (costText.isNotBlank() && cost == null) {
                    error = "金额格式不正确（最多两位小数）"
                    return@TextButton
                }
                scope.launch {
                    val recordId = "rec_" + UUID.randomUUID().toString().take(8)
                    val items = valid.map { (row, grams) ->
                        RecordItem(
                            id = "ri_" + UUID.randomUUID().toString().take(8),
                            recordId = recordId,
                            foodId = row.food?.id,
                            name = row.name,
                            grams = grams,
                            kcal100 = row.food?.kcal,
                            protein100 = row.food?.protein,
                            fat100 = row.food?.fat,
                            carbs100 = row.food?.carbs
                        )
                    }
                    container.repo.saveRecord(
                        DietRecord(
                            id = recordId,
                            eatenAt = Time.dayStart(day) + mealHour(meal) * 3600_000L,
                            mealType = meal,
                            costFen = cost,
                            note = "",
                            sourcePlanId = null,
                            deletedAt = null,
                            items = items
                        )
                    )
                    onSaved()
                }
            }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

private fun mealHour(meal: String): Int = when (meal) {
    MealType.BREAKFAST -> 8
    MealType.LUNCH -> 12
    MealType.DINNER -> 18
    else -> 15
}

@Composable
private fun TrashDialog(container: AppContainer, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    val rev by container.repo.revision.collectAsState()
    var trashed by remember { mutableStateOf<List<DietRecord>>(emptyList()) }

    LaunchedEffect(rev) { trashed = container.repo.trashed() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("回收站（保留 7 天）") },
        text = {
            Column {
                if (trashed.isEmpty()) {
                    Text("回收站是空的", style = MaterialTheme.typography.bodySmall)
                }
                trashed.take(20).forEach { record ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "${Time.dateTimeLabel(record.eatenAt)} · ${MealType.label(record.mealType)}",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                record.items.joinToString("、") { it.name }.ifBlank { "无食物明细" },
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                        TextButton(onClick = {
                            scope.launch { container.repo.restoreRecord(record.id) }
                        }) { Text("恢复") }
                    }
                }
                if (trashed.isNotEmpty()) {
                    TextButton(onClick = {
                        scope.launch { container.repo.purgeTrash() }
                    }) { DangerText("清空回收站") }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } }
    )
}
