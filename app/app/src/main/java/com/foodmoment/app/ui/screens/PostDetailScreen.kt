package com.foodmoment.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.foodmoment.app.AppContainer
import com.foodmoment.app.core.Time
import com.foodmoment.app.data.CommunityCategory
import com.foodmoment.app.data.CommunityPost
import com.foodmoment.app.data.CommunityReply
import com.foodmoment.app.ui.Navigator
import com.foodmoment.app.ui.components.AuthorAvatar
import com.foodmoment.app.ui.components.CommunityImage
import com.foodmoment.app.ui.components.DangerText
import com.foodmoment.app.ui.components.EmptyState
import com.foodmoment.app.ui.components.FullscreenCommunityImage
import com.foodmoment.app.ui.components.LoadingBox
import com.foodmoment.app.ui.components.Tag
import kotlinx.coroutines.launch

/**
 * 帖子详情 + 回复。
 * 删除与回复都依赖服务端会话：换设备后旧帖 mine=false，删不掉也发不了"原身份"的回复。
 */
@Composable
fun PostDetailScreen(container: AppContainer, nav: Navigator, postId: String) {
    val id = postId.toLongOrNull()
    if (id == null) {
        EmptyState("帖子不存在")
        return
    }

    val scope = rememberCoroutineScope()
    val rev by container.repo.revision.collectAsState()

    var post by remember { mutableStateOf<CommunityPost?>(null) }
    var replies by remember { mutableStateOf<List<CommunityReply>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }
    var fullscreen by remember { mutableStateOf<Int?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var replyText by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(rev, id) {
        post = container.repo.cachedCommunityPost(id)
        replies = container.repo.cachedReplies(id)
        loaded = true
    }

    LaunchedEffect(id) {
        container.repo.fetchReplies(id)
    }

    val current = post
    if (current == null) {
        if (loaded) EmptyState("帖子不存在或已被删除", "返回列表刷新看看") else LoadingBox()
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AuthorAvatar(current.author, 40.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    current.author,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    formatIso(current.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Tag(CommunityCategory.label(current.category), MaterialTheme.colorScheme.primary)
            if (current.food.isNotBlank()) {
                Tag(current.food, MaterialTheme.colorScheme.secondary, Icons.Filled.Restaurant)
            }
            if (current.place.isNotBlank()) {
                Tag(current.place, MaterialTheme.colorScheme.tertiary, Icons.Filled.Place)
            }
        }

        if (current.meetup.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text("约饭时间：${current.meetup}", style = MaterialTheme.typography.bodySmall)
        }

        Spacer(Modifier.height(12.dp))
        Text(current.text, style = MaterialTheme.typography.bodyLarge)

        if (current.imageCount > 0) {
            Spacer(Modifier.height(14.dp))
            Text(
                "图片 ${current.imageCount} 张 · 点击全屏",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            for (position in 0 until current.imageCount.coerceAtMost(3)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                ) {
                    CommunityImage(
                        container = container,
                        postId = current.id,
                        position = position,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp),
                        contentScale = ContentScale.Crop,
                        onClick = { fullscreen = position }
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(Modifier.height(10.dp))

        Text(
            "回复 ${replies.size}",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )

        if (replies.isEmpty()) {
            Text(
                "还没有回复",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        replies.forEach { reply ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.Top
            ) {
                AuthorAvatar(reply.author, 24.dp)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "${reply.author} · ${formatIso(reply.createdAt)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(reply.text, style = MaterialTheme.typography.bodyMedium)
                }
                if (reply.mine) {
                    TextButton(onClick = {
                        scope.launch {
                            val result = container.repo.deleteReply(current.id, reply.id)
                            notice = if (result.ok) "已删除回复" else result.message
                        }
                    }) { DangerText("删除") }
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = replyText,
                onValueChange = { if (it.length <= 300) replyText = it },
                label = { Text("回复（≤300 字）") },
                modifier = Modifier.weight(1f),
                maxLines = 3
            )
            TextButton(enabled = !busy && replyText.isNotBlank(), onClick = {
                val text = replyText
                scope.launch {
                    busy = true
                    val result = container.repo.addReply(current.id, text)
                    busy = false
                    if (result.ok) {
                        replyText = ""
                        notice = "已回复"
                    } else {
                        notice = result.message
                    }
                }
            }) { Text("发送") }
        }

        if (notice != null) {
            Text(
                notice ?: "",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.height(16.dp))
        if (current.mine) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { confirmDelete = true }) { DangerText("删除帖子") }
            }
        } else {
            Text(
                "这条帖子不属于当前会话身份，无法删除。换设备后旧帖也会变成这样。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.height(24.dp))
    }

    fullscreen?.let { position ->
        FullscreenCommunityImage(container, current.id, position) { fullscreen = null }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("删除帖子") },
            text = { Text("会从服务端删除，且无法恢复。", style = MaterialTheme.typography.bodyMedium) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    scope.launch {
                        val result = container.repo.deleteCommunityPost(current)
                        if (result.ok) nav.back() else notice = result.message
                    }
                }) { DangerText("删除") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("取消") } }
        )
    }
}

private fun formatIso(raw: String): String {
    val millis = Time.parseIsoMillis(raw)
    return if (millis == null) raw else Time.dateTimeLabel(millis)
}
