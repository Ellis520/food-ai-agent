package com.foodmoment.app.ui.screens

import android.speech.tts.TextToSpeech
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.foodmoment.app.AppContainer
import com.foodmoment.app.core.MealType
import com.foodmoment.app.core.Time
import com.foodmoment.app.data.ChatMessage
import com.foodmoment.app.data.DietRecord
import com.foodmoment.app.data.RecordItem
import com.foodmoment.app.ui.components.ChatCardList
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.Locale
import java.util.UUID

/**
 * AI 聊天（4.1）。
 *
 * 语音输入不做（交给系统输入法）；朗读用系统 TTS（CHAT-09）；
 * 非流式，所以「停止」= 取消请求（CHAT-06）。
 */
@Composable
fun ChatScreen(
    container: AppContainer,
    sessionId: String?,
    onSessionChange: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val rev by container.repo.revision.collectAsState()

    var messages by remember { mutableStateOf<List<ChatMessage>>(emptyList()) }
    var input by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var sendJob by remember { mutableStateOf<Job?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }

    val listState = rememberLazyListState()

    var ttsReady by remember { mutableStateOf(false) }
    val ttsHolder = remember { mutableStateOf<TextToSpeech?>(null) }
    DisposableEffect(Unit) {
        var engine: TextToSpeech? = null
        engine = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = engine?.setLanguage(Locale.CHINA)
                ttsReady = result != TextToSpeech.LANG_MISSING_DATA &&
                    result != TextToSpeech.LANG_NOT_SUPPORTED
                engine?.setSpeechRate(container.settings.ttsRate)
            }
        }
        ttsHolder.value = engine
        onDispose {
            runCatching {
                engine?.stop()
                engine?.shutdown()
            }
        }
    }

    LaunchedEffect(sessionId) {
        if (sessionId == null) {
            val created = container.repo.createSession()
            onSessionChange(created.id)
        }
    }

    LaunchedEffect(rev, sessionId) {
        if (sessionId != null) {
            messages = container.repo.messages(sessionId)
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    fun send() {
        val text = input.trim()
        val sid = sessionId ?: return
        if (text.isEmpty() || sending) return
        input = ""
        sending = true
        sendJob = scope.launch {
            val result = container.chat.send(sid, text)
            sending = false
            if (!result.ok) notice = result.error
        }
    }

    fun confirmRecord(data: JSONObject) {
        val sid = sessionId ?: return
        scope.launch {
            val recordId = "rec_" + UUID.randomUUID().toString().take(8)
            val meal = data.optString("mealType").ifBlank { MealType.LUNCH }
            val cost = if (data.has("costFen") && !data.isNull("costFen")) data.optLong("costFen") else null
            val eatenAt = if (data.has("eatenAt") && !data.isNull("eatenAt")) {
                data.optLong("eatenAt")
            } else Time.now()

            val items = mutableListOf<RecordItem>()
            val arr = data.optJSONArray("items")
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    val entry = arr.optJSONObject(i) ?: continue
                    val name = entry.optString("name")
                    if (name.isBlank()) continue
                    val grams = entry.optDouble("grams", 100.0)
                    val food = container.repo.findFoodByName(name)
                    items += RecordItem(
                        id = "ri_" + UUID.randomUUID().toString().take(8),
                        recordId = recordId,
                        foodId = food?.id,
                        name = food?.displayName ?: name,
                        grams = grams,
                        kcal100 = food?.kcal,
                        protein100 = food?.protein,
                        fat100 = food?.fat,
                        carbs100 = food?.carbs
                    )
                }
            }

            if (items.isEmpty() && cost == null) {
                notice = "没有可记录的内容"
                return@launch
            }

            container.repo.saveRecord(
                DietRecord(
                    id = recordId,
                    eatenAt = eatenAt,
                    mealType = meal,
                    costFen = cost,
                    note = "来自对话",
                    sourcePlanId = null,
                    deletedAt = null,
                    items = items
                )
            )
            notice = "已记入${MealType.label(meal)}"
            container.repo.saveMessage(
                ChatMessage(
                    id = "m_" + UUID.randomUUID().toString().take(8),
                    sessionId = sid,
                    role = "assistant",
                    content = "已记入${MealType.label(meal)}。",
                    cardsJson = null,
                    requestId = "sys",
                    createdAt = Time.now(),
                    status = "ok"
                )
            )
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (messages.isEmpty()) {
                item {
                    Column(Modifier.padding(20.dp)) {
                        Text(
                            "说点什么，比如：",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(Modifier.height(8.dp))
                        listOf(
                            "今天 50 块，三餐，不辣",
                            "午饭吃了番茄鸡蛋盖饭 15 块",
                            "这碗 200 克米饭多少卡",
                            "这周吃得怎么样"
                        ).forEach {
                            TextButton(onClick = { input = it }) { Text("· $it") }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "语音输入可直接用输入法语音键，App 不采集麦克风。",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            items(messages, key = { it.id }) { message ->
                val isUser = message.role == "user"
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth(0.92f)
                            .background(
                                if (isUser) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                RoundedCornerShape(14.dp)
                            )
                            .padding(12.dp)
                    ) {
                        Text(
                            cleanMarkdown(message.content),
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isUser) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onSurface
                        )

                        if (!isUser) {
                            ChatCardList(
                                cardsJson = message.cardsJson,
                                onSavePlan = { planId ->
                                    scope.launch {
                                        container.repo.confirmPlan(planId)
                                        notice = "计划已保存"
                                    }
                                },
                                onConfirmRecord = { data -> confirmRecord(data) }
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    message.detail ?: Time.timeLabel(message.createdAt),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (ttsReady && container.settings.ttsEnabled) {
                                    IconButton(onClick = {
                                        val spoken = cleanMarkdown(message.content).take(500)
                                        ttsHolder.value?.speak(spoken, TextToSpeech.QUEUE_FLUSH, null, "fm")
                                    }) {
                                        Icon(Icons.Filled.VolumeUp, contentDescription = "朗读")
                                    }
                                }
                            }
                        } else {
                            Text(
                                Time.timeLabel(message.createdAt),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            if (sending) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.width(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("模型生成中…", style = MaterialTheme.typography.labelSmall)
                        TextButton(onClick = {
                            sendJob?.cancel()
                            sending = false
                        }) { Text("取消") }
                    }
                }
            }
        }

        if (notice != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(notice ?: "", style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
                TextButton(onClick = { notice = null }) { Text("知道了") }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .padding(8.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            TextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("说点什么…") },
                maxLines = 4
            )
            IconButton(
                onClick = { send() },
                enabled = input.isNotBlank() && !sending && sessionId != null
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Send,
                    contentDescription = "发送",
                    tint = if (input.isNotBlank()) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** 仅去掉 Markdown 标记，渲染纯文本（5.6 不执行原始 HTML） */
private fun cleanMarkdown(raw: String): String = raw
    .replace("```", "")
    .replace("**", "")
    .replace("###", "")
    .replace("##", "")
    .replace("# ", "")
    .replace("`", "")
    .trim()
