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
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
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
import com.foodmoment.app.core.Money
import com.foodmoment.app.data.MenuItem
import com.foodmoment.app.data.MenuParser
import com.foodmoment.app.ui.Navigator
import com.foodmoment.app.ui.components.DangerText
import com.foodmoment.app.ui.components.EmptyState
import com.foodmoment.app.ui.components.Field
import com.foodmoment.app.ui.components.LoadingBox
import com.foodmoment.app.ui.components.SectionCard
import com.foodmoment.app.ui.components.Tag
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * 菜单管理（拍照识别的依据）。
 * 默认菜单来自 assets/menu.md，用户可追加自己的菜品；内置条目不允许删除，避免误删后无法恢复。
 */
@Composable
fun MenuScreen(container: AppContainer, nav: Navigator) {
    val scope = rememberCoroutineScope()
    val rev by container.repo.revision.collectAsState()

    var all by remember { mutableStateOf<List<MenuItem>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var showAdd by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<MenuItem?>(null) }

    LaunchedEffect(rev) {
        all = container.repo.menuItems()
        loaded = true
    }

    val filtered = remember(all, query) {
        if (query.isBlank()) all
        else all.filter {
            it.name.contains(query.trim(), ignoreCase = true) ||
                it.category.contains(query.trim(), ignoreCase = true)
        }
    }
    val grouped = remember(filtered) { filtered.groupBy { it.category } }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 10.dp)
    ) {
        item {
            SectionCard(title = "菜单", subtitle = "共 ${all.size} 条，其中自定义 ${all.count { it.isCustom }} 条") {
                Field(
                    label = "搜索",
                    value = query,
                    onValueChange = { query = it },
                    placeholder = "菜名或分类"
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "识别时只会从这份菜单里挑，模型给出的菜单外菜名会被丢弃。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { showAdd = true }) { Text("+ 添加") }
                }
            }
        }

        if (!loaded) {
            item { LoadingBox() }
        } else if (filtered.isEmpty()) {
            item { EmptyState("没有匹配的条目", "换个关键词，或点「+ 添加」新建") }
        }

        grouped.forEach { (category, items) ->
            item(key = "header_$category") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        category,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        "${items.size} 条",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                HorizontalDivider(Modifier.padding(horizontal = 20.dp))
            }

            items(items, key = { it.id }) { item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(item.name, style = MaterialTheme.typography.bodyMedium)
                            if (item.isCustom) {
                                Spacer(Modifier.padding(horizontal = 3.dp))
                                Tag("自定义", MaterialTheme.colorScheme.primary)
                            }
                        }
                        Text(
                            listOfNotNull(
                                item.unit.takeIf { it.isNotBlank() },
                                item.displayPortion,
                                item.priceFen?.let { Money.format(it) }
                            ).joinToString(" · "),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (item.isCustom) {
                        TextButton(onClick = { pendingDelete = item }) { DangerText("删除") }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }

    if (showAdd) {
        AddMenuItemDialog(
            onDismiss = { showAdd = false },
            onConfirm = { item ->
                showAdd = false
                scope.launch { container.repo.addMenuItem(item) }
            }
        )
    }

    val target = pendingDelete
    if (target != null) {
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除菜单条目") },
            text = { Text("「${target.name}」将从菜单里移除，之后识别不会再返回它。", style = MaterialTheme.typography.bodyMedium) },
            confirmButton = {
                TextButton(onClick = {
                    pendingDelete = null
                    scope.launch { container.repo.deleteMenuItem(target.id) }
                }) { DangerText("删除") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun AddMenuItemDialog(
    onDismiss: () -> Unit,
    onConfirm: (MenuItem) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var unit by remember { mutableStateOf("份") }
    var gramsText by remember { mutableStateOf("") }
    var priceText by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("自建菜品") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加菜单条目") },
        text = {
            Column {
                Field("名称", name, { name = it }, placeholder = "例如 番茄鸡蛋盖饭")
                Field("分类", category, { category = it }, placeholder = "例如 自建菜品")
                Field("单位", unit, { unit = it }, placeholder = "份 / 个 / 碗")
                Field("克重", gramsText, { gramsText = it }, keyboardType = KeyboardType.Number)
                Field("单价（元）", priceText, { priceText = it }, keyboardType = KeyboardType.Decimal)
                if (error != null) DangerText(error ?: "")
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isBlank()) {
                    error = "名称不能为空"
                    return@TextButton
                }
                val grams = gramsText.trim().takeIf { it.isNotEmpty() }?.toDoubleOrNull()
                if (gramsText.isNotBlank() && grams == null) {
                    error = "克重填写不正确"
                    return@TextButton
                }
                val price = if (priceText.isBlank()) null else Money.parseYuan(priceText)
                if (priceText.isNotBlank() && price == null) {
                    error = "价格格式不正确（最多两位小数）"
                    return@TextButton
                }
                onConfirm(
                    MenuItem(
                        id = "custom_" + UUID.randomUUID().toString().take(8),
                        name = name.trim(),
                        unit = unit.trim(),
                        grams = grams,
                        gramsText = grams?.toInt()?.toString().orEmpty(),
                        priceFen = price,
                        category = category.trim().ifBlank { "自建菜品" },
                        isCustom = true
                    )
                )
            }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Suppress("unused")
private fun defaultId(name: String) = MenuParser.idFor(name)
