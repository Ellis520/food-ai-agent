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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.foodmoment.app.AppContainer
import com.foodmoment.app.core.MealType
import com.foodmoment.app.core.Money
import com.foodmoment.app.core.Time
import com.foodmoment.app.data.ChatSession
import com.foodmoment.app.data.DietRecord
import com.foodmoment.app.ui.Navigator
import com.foodmoment.app.ui.Screen
import com.foodmoment.app.ui.components.SectionCard
import com.foodmoment.app.ui.theme.DangerRed
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    container: AppContainer,
    nav: Navigator,
    onOpenChat: (String?) -> Unit,
    onSessionDeleted: (String) -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    val rev by container.repo.revision.collectAsState()
    var records by remember { mutableStateOf<List<DietRecord>>(emptyList()) }
    var sessions by remember { mutableStateOf<List<ChatSession>>(emptyList()) }
    var ready by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<ChatSession?>(null) }

    LaunchedEffect(rev) {
        records = container.repo.recordsOfDay(Time.todayKey())
        sessions = container.repo.sessions()
        ready = true
    }

    val today = Time.todayKey()
    val budget = container.settings.dailyBudgetFen
    val spent = records.mapNotNull { it.costFen }.sum()
    val remaining = budget - spent
    val mealCount = records.size

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 12.dp)
    ) {
        item {
            Column(Modifier.padding(horizontal = 20.dp)) {
                Text(
                    "食刻AI",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "${Time.dayLabel(today)} · ${Time.weekdayLabel(today)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(12.dp))
        }

        if (!container.settings.modelReady) {
            item {
                SectionCard(title = "未配置模型") {
                    Text(
                        "AI 对话与生成计划不可用，其余功能正常。",
                        style = MaterialTheme.typography.bodySmall
                    )
                    TextButton(onClick = { nav.go(Screen.Settings) }) { Text("去设置") }
                }
            }
        }

        item {
            SectionCard(title = "今日预算") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Text(
                        Money.format(if (remaining >= 0) remaining else -remaining),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (remaining >= 0) MaterialTheme.colorScheme.onSurface else DangerRed
                    )
                    Text(
                        if (remaining >= 0) "可用余额" else "已超支",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { if (budget <= 0) 0f else (spent.toFloat() / budget).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "预算 ${Money.format(budget)} · 已花 ${Money.format(spent)} · 已记 $mealCount 餐",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (records.any { it.costFen == null }) {
                    Text(
                        "有 ${records.count { it.costFen == null }} 条记录未填金额，未计入已花",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                Button(
                    onClick = { nav.go(Screen.Plans) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                ) {
                    Icon(Icons.Filled.Restaurant, contentDescription = null)
                    Spacer(Modifier.padding(4.dp))
                    Text("今天吃什么", fontWeight = FontWeight.SemiBold)
                }
            }
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                QuickEntry("记一笔", Icons.Filled.Add, Modifier.weight(1f)) { nav.go(Screen.Records) }
                QuickEntry("查营养", Icons.Filled.Analytics, Modifier.weight(1f)) { nav.go(Screen.Nutrition) }
                QuickEntry("看分析", Icons.Filled.Analytics, Modifier.weight(1f)) { nav.go(Screen.Habits) }
                QuickEntry("社区", Icons.Filled.Public, Modifier.weight(1f)) { nav.go(Screen.Community) }
                QuickEntry("附近", Icons.Filled.Place, Modifier.weight(1f)) { nav.go(Screen.Nearby) }
            }
        }

        item {
            Text(
                "最近会话",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 20.dp, top = 12.dp, bottom = 4.dp)
            )
        }

        if (sessions.isEmpty() && ready) {
            item {
                Text(
                    "还没有对话。点底部「聊天」开始。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
            }
        }

        items(sessions.take(5), key = { it.id }) { session ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onOpenChat(session.id) }
                            .padding(start = 14.dp, top = 14.dp, bottom = 14.dp, end = 8.dp)
                    ) {
                        Text(session.title, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            Time.dateTimeLabel(session.updatedAt),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { pendingDelete = session }) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = "删除会话「${session.title}」",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }

    val target = pendingDelete
    if (target != null) {
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除会话") },
            text = {
                Text(
                    "「${target.title}」及其中的全部消息会被删除，且无法恢复。记录与计划不受影响。",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingDelete = null
                    scope.launch {
                        container.repo.deleteSession(target.id)
                        onSessionDeleted(target.id)
                    }
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun QuickEntry(
    label: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(modifier = modifier.clickable(onClick = onClick)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(4.dp))
            Text(label, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Suppress("unused")
private fun mealLabel(code: String) = MealType.label(code)
