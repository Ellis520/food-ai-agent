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
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.foodmoment.app.data.Plan
import com.foodmoment.app.data.PlanItem
import com.foodmoment.app.data.SeedFoods
import com.foodmoment.app.domain.PlanValidator
import com.foodmoment.app.ui.Navigator
import com.foodmoment.app.ui.components.EmptyState
import com.foodmoment.app.ui.components.Field
import com.foodmoment.app.ui.components.KeyValueRow
import com.foodmoment.app.ui.components.RowActions
import com.foodmoment.app.ui.components.SectionCard
import com.foodmoment.app.ui.components.Tag
import com.foodmoment.app.ui.theme.DangerRed
import com.foodmoment.app.ui.theme.WarningAmber
import kotlinx.coroutines.launch

/**
 * 饮食计划（4.2 / PLAN-01~08）。
 * 模板计划明确标注「离线模板，非 AI 生成」，金额留空由用户填写，不臆造价格。
 */
@Composable
fun PlansScreen(container: AppContainer, nav: Navigator) {
    val scope = rememberCoroutineScope()
    val rev by container.repo.revision.collectAsState()
    var day by remember { mutableStateOf(Time.todayKey()) }
    var plans by remember { mutableStateOf<List<Plan>>(emptyList()) }
    var editing by remember { mutableStateOf<Pair<Plan, PlanItem>?>(null) }
    var costInput by remember { mutableStateOf("") }
    var notice by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(rev, day) {
        plans = container.repo.plansOfDay(day)
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
                        Time.weekdayLabel(day),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(onClick = { day = Time.addDays(day, 1) }) { Text("后一天 ›") }
            }
        }

        item {
            SectionCard(title = "离线模板", subtitle = "不需要模型，随时可用") {
                SeedFoods.templates.forEach { template ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                scope.launch {
                                    container.repo.buildTemplatePlan(template, day)
                                    notice = "已生成「${template.title}」，请填写金额"
                                }
                            }
                            .padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(template.title, style = MaterialTheme.typography.bodyMedium)
                        Text(Money.format(template.budgetFen), style = MaterialTheme.typography.bodySmall)
                    }
                }
                Text(
                    "模板不含价格，生成后请逐项填写实际金额；金额未知不会按 0 计算。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (notice != null) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(notice ?: "", style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
                    TextButton(onClick = { notice = null }) { Text("知道了") }
                }
            }
        }

        if (plans.isEmpty()) {
            item { EmptyState("这一天还没有计划", "可以用上面的模板，或去聊天里让 AI 安排") }
        }

        items(plans, key = { it.id }) { plan ->
            val check = PlanValidator.check(plan)
            SectionCard(
                title = "${Time.dayLabel(plan.date)} · ${if (plan.status == "draft") "草稿" else "已保存"}",
                subtitle = if (plan.isTemplate) "离线模板，非 AI 生成" else "AI 生成"
            ) {
                if (check.unknownCount > 0) {
                    Tag("${check.unknownCount} 项待核价", WarningAmber)
                    Spacer(Modifier.height(6.dp))
                }
                MealType.all.forEach { meal ->
                    val items = plan.itemsOf(meal)
                    if (items.isEmpty()) return@forEach
                    Text(
                        MealType.label(meal),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Medium
                    )
                    items.forEach { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    editing = plan to item
                                    costInput = Money.toYuanText(item.costFen)
                                }
                                .padding(vertical = 3.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "${item.name} ${item.grams.toInt()}g",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                if (item.costFen == null) "填写金额" else Money.format(item.costFen),
                                style = MaterialTheme.typography.bodySmall,
                                color = if (item.costFen == null) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                }

                HorizontalDivider(Modifier.padding(vertical = 6.dp))
                KeyValueRow("预算", Money.format(plan.budgetFen))
                KeyValueRow("已知合计", Money.format(check.knownTotalFen))
                KeyValueRow(
                    if (check.deltaFen >= 0) "余量" else "超支",
                    Money.format(if (check.deltaFen >= 0) check.deltaFen else -check.deltaFen),
                    if (check.deltaFen >= 0) MaterialTheme.colorScheme.onSurface else DangerRed
                )

                RowActions(
                    "保存计划" to {
                        scope.launch {
                            container.repo.confirmPlan(plan.id)
                            notice = "已保存"
                        }
                    },
                    "记为已吃" to {
                        scope.launch {
                            var count = 0
                            MealType.all.forEach { meal ->
                                if (plan.itemsOf(meal).isNotEmpty()) {
                                    if (container.repo.recordFromPlan(plan, meal) != null) count++
                                }
                            }
                            notice = if (count > 0) "已转为 $count 条记录" else "这些餐已经记录过了"
                        }
                    },
                    "删除" to {
                        scope.launch {
                            container.repo.deletePlan(plan.id)
                            notice = "已删除"
                        }
                    }
                )
            }
        }

        item { Spacer(Modifier.height(20.dp)) }
    }

    val target = editing
    if (target != null) {
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text("填写金额") },
            text = {
                Column {
                    Text(target.second.name, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                    Field(
                        label = "金额（元）",
                        value = costInput,
                        onValueChange = { costInput = it },
                        placeholder = "例如 12.5",
                        keyboardType = KeyboardType.Decimal,
                        supporting = "最多两位小数；不确定就留空"
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val (plan, item) = target
                    val fen = Money.parseYuan(costInput)
                    if (costInput.isNotBlank() && fen == null) {
                        notice = "金额格式不正确"
                        return@TextButton
                    }
                    scope.launch {
                        val updated = plan.copy(
                            items = plan.items.map { if (it.id == item.id) it.copy(costFen = fen) else it }
                        )
                        container.repo.savePlan(updated)
                        editing = null
                    }
                }) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { editing = null }) { Text("取消") }
            }
        )
    }
}
