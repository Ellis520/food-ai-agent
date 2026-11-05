package com.foodmoment.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.foodmoment.app.AppContainer
import com.foodmoment.app.core.MealType
import com.foodmoment.app.core.Time
import com.foodmoment.app.data.CommunityCategory
import com.foodmoment.app.data.CommunityDraft
import com.foodmoment.app.data.CommunityPost
import com.foodmoment.app.ui.Navigator
import com.foodmoment.app.ui.Screen
import com.foodmoment.app.ui.components.AuthorAvatar
import com.foodmoment.app.ui.components.CommunityImage
import com.foodmoment.app.ui.components.EmptyState
import com.foodmoment.app.ui.components.Field
import com.foodmoment.app.ui.components.SectionCard
import com.foodmoment.app.ui.components.Tag
import com.foodmoment.app.ui.theme.WarningAmber
import kotlinx.coroutines.launch

/**
 * 社区（对接 food-ai-agent）。
 *
 * 身份是匿名会话，没有账号；离线时只能看缓存，发帖进草稿队列。
 */
@Composable
fun CommunityScreen(container: AppContainer, nav: Navigator) {
    val scope = rememberCoroutineScope()
    val rev by container.repo.revision.collectAsState()

    var category by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    var posts by remember { mutableStateOf<List<CommunityPost>>(emptyList()) }
    var drafts by remember { mutableStateOf<List<CommunityDraft>>(emptyList()) }
    var notice by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var showCompose by remember { mutableStateOf(false) }
    var moreAvailable by remember { mutableStateOf(false) }

    LaunchedEffect(rev, category, query) {
        posts = container.repo.cachedCommunityPosts(category, query)
        drafts = container.repo.drafts()
    }

    if (!container.settings.communityEnabled) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp)
        ) {
            Text("社区未启用", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(10.dp))
            Text(
                "这是可选模块。默认关闭，关闭时不发起任何网络请求。\n\n" +
                    "启用后帖子保存在你自己部署的服务端；身份是匿名会话，没有账号，换设备后旧帖不再属于你。",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(16.dp))
            Text(
                "注意：服务端没有内容审核，请只在自己的实例上使用。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))
            Button(onClick = { nav.go(Screen.Settings) }) { Text("去设置里启用") }
        }
        return
    }

    fun refresh() {
        scope.launch {
            busy = true
            notice = null
            val nickname = container.repo.ensureNicknameSynced()
            if (!nickname.ok) notice = "昵称同步失败：${nickname.message}"
            val result = container.repo.fetchCommunity(category, query)
            if (result.ok) {
                moreAvailable = result.added >= 20
            } else {
                notice = result.message
            }
            busy = false
        }
    }

    fun loadMore() {
        val before = posts.lastOrNull()?.id ?: return
        scope.launch {
            busy = true
            val result = container.repo.fetchCommunity(category, query, before)
            if (result.ok) {
                moreAvailable = result.added >= 20
            } else {
                notice = result.message
            }
            busy = false
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 10.dp)
    ) {
        item {
            SectionCard(
                title = "社区",
                subtitle = if (container.settings.communitySession == null) "尚未连接服务端" else "已连接"
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    FilterChip("全部", category == null) { category = null }
                    CommunityCategory.all.forEach { code ->
                        FilterChip(CommunityCategory.label(code), category == code) {
                            category = code
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Field(
                    label = "搜索",
                    value = query,
                    onValueChange = { query = it },
                    placeholder = "菜名、店名或正文"
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(onClick = { showCompose = true }) { Text("发布") }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(enabled = !busy, onClick = { refresh() }) {
                            Text(if (busy) "同步中…" else "刷新")
                        }
                    }
                }
            }
        }

        if (drafts.isNotEmpty()) {
            item {
                SectionCard(title = "待发送 ${drafts.size} 条", subtitle = "联网后会自动补发，也可手动重试") {
                    drafts.forEach { draft ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "${CommunityCategory.label(draft.category)} · ${draft.text.take(24)}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                if (!draft.lastError.isNullOrBlank()) {
                                    Text(
                                        draft.lastError ?: "",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(enabled = !busy, onClick = {
                            scope.launch {
                                busy = true
                                val result = container.repo.flushDrafts()
                                notice = if (result.ok) "草稿已全部发出" else result.message
                                busy = false
                            }
                        }) { Text("重试发送") }
                    }
                }
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

        if (posts.isEmpty()) {
            item {
                EmptyState(
                    "还没有帖子",
                    if (busy) "正在拉取…" else "点「刷新」从服务端拉取，或点「发布」写一条"
                )
            }
        }

        items(posts, key = { it.id }) { post ->
            CommunityCard(post, container) { nav.go(Screen.PostDetail(post.id.toString())) }
        }

        if (moreAvailable && posts.isNotEmpty()) {
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    TextButton(enabled = !busy, onClick = { loadMore() }) {
                        Text(if (busy) "加载中…" else "加载更多")
                    }
                }
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }

    if (showCompose) {
        ComposeDialog(
            container = container,
            onDismiss = { showCompose = false },
            onDone = { message ->
                showCompose = false
                notice = message
            }
        )
    }
}

@Composable
private fun FilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    )
}

@Composable
private fun CommunityCard(post: CommunityPost, container: AppContainer, onClick: () -> Unit) {
    SectionCard(modifier = Modifier.clickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AuthorAvatar(post.author, 36.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    post.author,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "${formatTime(post.createdAt)} · ${CommunityCategory.label(post.category)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        if (post.food.isNotBlank() || post.place.isNotBlank()) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (post.food.isNotBlank()) {
                    Tag(post.food, MaterialTheme.colorScheme.primary, Icons.Filled.Restaurant)
                }
                if (post.place.isNotBlank()) {
                    Tag(post.place, MaterialTheme.colorScheme.secondary, Icons.Filled.Place)
                }
            }
            Spacer(Modifier.height(4.dp))
        }
        if (post.category == CommunityCategory.BUDDY && post.meetup.isNotBlank()) {
            Text("约饭时间：${post.meetup}", style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.height(4.dp))
        }
        Text(post.text, style = MaterialTheme.typography.bodyMedium, maxLines = 4)

        if (post.imageCount > 0) {
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (position in 0 until post.imageCount.coerceAtMost(3)) {
                    CommunityImage(
                        container = container,
                        postId = post.id,
                        position = position,
                        modifier = Modifier.size(72.dp)
                    )
                }
            }
        }

        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                "回复 ${post.replyCount}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "查看详情 ›",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

private fun formatTime(raw: String): String {
    val millis = Time.parseIsoMillis(raw)
    return if (millis == null) raw else Time.dateTimeLabel(millis)
}

@Composable
private fun ComposeDialog(
    container: AppContainer,
    onDismiss: () -> Unit,
    onDone: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    var category by remember { mutableStateOf(CommunityCategory.SHARE) }
    var text by remember { mutableStateOf("") }
    var food by remember { mutableStateOf("") }
    var place by remember { mutableStateOf("") }
    var meetup by remember { mutableStateOf("") }
    val picked = remember { mutableStateListOf<android.net.Uri>() }
    var error by remember { mutableStateOf<String?>(null) }
    var working by remember { mutableStateOf(false) }
    var showRecordPick by remember { mutableStateOf(false) }
    var todayFoods by remember { mutableStateOf<List<String>>(emptyList()) }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(3)
    ) { uris ->
        picked.clear()
        picked.addAll(uris.take(3))
    }

    AlertDialog(
        onDismissRequest = { if (!working) onDismiss() },
        title = { Text("发布到社区") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    CommunityCategory.all.forEach { code ->
                        TextButton(onClick = { category = code }, modifier = Modifier.padding(0.dp)) {
                            Text(
                                CommunityCategory.label(code),
                                fontWeight = if (category == code) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = text,
                    onValueChange = { if (it.length <= 600) text = it },
                    label = { Text("正文（≤600 字）") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3
                )

                Field("菜名（推荐/避雷至少填一个）", food, { food = it })
                Field("店名或地点", place, { place = it })
                if (category == CommunityCategory.BUDDY) {
                    Field("约饭时间（必填）", meetup, { meetup = it }, placeholder = "例如 周五 12:10")
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(onClick = {
                        scope.launch {
                            val records = container.repo.recordsOfDay(Time.todayKey())
                            todayFoods = records.flatMap { record ->
                                record.items.map { "${MealType.label(record.mealType)} · ${it.name}" }
                            }.distinct()
                            showRecordPick = todayFoods.isNotEmpty()
                            if (todayFoods.isEmpty()) error = "今天还没有记录"
                        }
                    }) { Text("从今天的记录带菜名") }

                    TextButton(onClick = {
                        picker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    }) { Text("图片 ${picked.size}/3") }
                }

                if (error != null) {
                    Text(error ?: "", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                }
                Text(
                    "图片会压缩到长边 1280、约 450KB 再上传；正文与图片会发送到你配置的服务端。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(enabled = !working, onClick = {
                if (text.isBlank()) {
                    error = "正文不能为空"
                    return@TextButton
                }
                if (category == CommunityCategory.BUDDY && (meetup.isBlank() || place.isBlank())) {
                    error = "约饭需要填写时间和地点"
                    return@TextButton
                }
                if ((category == CommunityCategory.RECOMMEND || category == CommunityCategory.AVOID) &&
                    food.isBlank() && place.isBlank()
                ) {
                    error = "推荐或避雷至少要填写菜名或店名"
                    return@TextButton
                }
                working = true
                scope.launch {
                    val result = container.repo.publishCommunity(
                        category = category,
                        text = text,
                        food = food,
                        place = place,
                        meetup = meetup,
                        imageUris = picked.toList()
                    )
                    working = false
                    onDone(if (result.ok) "已发布" else "已存为草稿：${result.message}")
                }
            }) { Text(if (working) "发布中…" else "发布") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )

    if (showRecordPick) {
        AlertDialog(
            onDismissRequest = { showRecordPick = false },
            title = { Text("从今天的记录选菜名") },
            text = {
                Column(Modifier.heightIn(max = 300.dp).verticalScroll(rememberScrollState())) {
                    todayFoods.forEach { name ->
                        Text(
                            name,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    food = name.substringAfter("· ").take(60)
                                    showRecordPick = false
                                }
                                .padding(vertical = 8.dp),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showRecordPick = false }) { Text("关闭") } }
        )
    }
}
