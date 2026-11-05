package com.foodmoment.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.foodmoment.app.AppContainer
import android.content.Context
import com.foodmoment.app.core.Money
import com.foodmoment.app.net.LlmClient
import com.foodmoment.app.ui.Navigator
import com.foodmoment.app.ui.Screen
import com.foodmoment.app.ui.components.DangerText
import com.foodmoment.app.ui.components.Field
import com.foodmoment.app.net.CommunityClient
import com.foodmoment.app.ui.components.AuthorAvatar
import com.foodmoment.app.ui.components.KeyValueRow
import com.foodmoment.app.ui.components.SectionCard
import kotlinx.coroutines.launch

/**
 * 设置中心（4.7 / SET-01~09）。所有配置都在本地。
 */
@Composable
fun SettingsScreen(container: AppContainer, nav: Navigator) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val settings = container.settings

    var baseUrl by remember { mutableStateOf(settings.modelBaseUrl) }
    var modelName by remember { mutableStateOf(settings.modelName) }
    // 密钥不回填明文：输入框始终从空开始，留空表示保持原值
    var apiKeyInput by remember { mutableStateOf("") }
    var clearApiKey by remember { mutableStateOf(false) }
    var maxTokens by remember { mutableStateOf(settings.maxTokens.toString()) }
    var timeout by remember { mutableStateOf(settings.timeoutSeconds.toString()) }
    var allowHttp by remember { mutableStateOf(settings.allowHttp) }

    var visionBase by remember { mutableStateOf(settings.visionBaseUrl) }
    var visionKeyInput by remember { mutableStateOf("") }
    var clearVisionKey by remember { mutableStateOf(false) }
    var visionModelText by remember { mutableStateOf(settings.visionModel) }

    var communityEnabled by remember { mutableStateOf(settings.communityEnabled) }
    var communityUrl by remember { mutableStateOf(settings.communityUrl) }

    // 高德：三个 Key 都不回填明文，输入框从空开始，留空表示保持原值
    var amapJsKeyInput by remember { mutableStateOf("") }
    var amapJsCodeInput by remember { mutableStateOf("") }
    var amapWebKeyInput by remember { mutableStateOf("") }
    var clearAmapJsKey by remember { mutableStateOf(false) }
    var clearAmapJsCode by remember { mutableStateOf(false) }
    var clearAmapWebKey by remember { mutableStateOf(false) }
    var amapCenter by remember { mutableStateOf(settings.amapCenter) }
    var amapRadiusText by remember { mutableStateOf(settings.amapRadius.toString()) }

    var displayName by remember { mutableStateOf(settings.displayName) }
    var budgetText by remember { mutableStateOf(Money.toYuanText(settings.dailyBudgetFen)) }
    var prefsText by remember { mutableStateOf(settings.preferences.joinToString("、")) }
    var allergensText by remember { mutableStateOf(settings.allergens.joinToString("、")) }

    var testResult by remember { mutableStateOf<String?>(null) }
    var testing by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }
    var showClear by remember { mutableStateOf(false) }
    var showGuide by remember { mutableStateOf(false) }
    var showNicknamePrompt by remember { mutableStateOf(false) }

    val version = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: ""
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                notice = container.backup.export(uri).fold(
                    onSuccess = { it },
                    onFailure = { "导出失败：${it.message}" }
                )
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                notice = container.backup.import(uri).fold(
                    onSuccess = { it },
                    onFailure = { "导入失败：${it.message}" }
                )
            }
        }
    }

    fun persistModel() {
        settings.modelBaseUrl = baseUrl
        settings.modelName = modelName
        when {
            clearApiKey -> settings.apiKey = null
            apiKeyInput.isNotBlank() -> settings.apiKey = apiKeyInput
        }
        apiKeyInput = ""
        clearApiKey = false
        settings.maxTokens = maxTokens.toIntOrNull()?.coerceIn(128, 4096) ?: 900
        settings.timeoutSeconds = timeout.toIntOrNull()?.coerceIn(10, 300) ?: 60
        settings.allowHttp = allowHttp
    }

    fun persistCommunity() {
        settings.communityEnabled = communityEnabled
        settings.communityUrl = communityUrl
    }

    fun persistVision() {        settings.visionBaseUrl = visionBase
        when {
            clearVisionKey -> settings.visionApiKey = null
            visionKeyInput.isNotBlank() -> settings.visionApiKey = visionKeyInput
        }
        visionKeyInput = ""
        clearVisionKey = false
        settings.visionModel = visionModelText
    }

    fun persistAmap() {
        when {
            clearAmapJsKey -> settings.amapJsKey = null
            amapJsKeyInput.isNotBlank() -> settings.amapJsKey = amapJsKeyInput
        }
        when {
            clearAmapJsCode -> settings.amapJsSecurityCode = null
            amapJsCodeInput.isNotBlank() -> settings.amapJsSecurityCode = amapJsCodeInput
        }
        when {
            clearAmapWebKey -> settings.amapWebKey = null
            amapWebKeyInput.isNotBlank() -> settings.amapWebKey = amapWebKeyInput
        }
        amapJsKeyInput = ""
        amapJsCodeInput = ""
        amapWebKeyInput = ""
        clearAmapJsKey = false
        clearAmapJsCode = false
        clearAmapWebKey = false
        settings.amapCenter = amapCenter
        settings.amapRadius = amapRadiusText.toIntOrNull() ?: settings.amapRadius
    }

    fun persistProfile() {        settings.displayName = displayName
        settings.dailyBudgetFen = Money.parseYuan(budgetText) ?: settings.dailyBudgetFen
        settings.preferences = prefsText.split("、", ",", "，").map { it.trim() }.filter { it.isNotEmpty() }
        settings.allergens = allergensText.split("、", ",", "，").map { it.trim() }.filter { it.isNotEmpty() }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 10.dp)
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AuthorAvatar(settings.displayName, 52.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        settings.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        if (settings.communityNickname.isBlank()) "社区署名未同步"
                        else "社区署名：${settings.communityNickname}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (notice != null) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(notice ?: "", style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
                    TextButton(onClick = { notice = null }) { Text("知道了") }
                }
            }
        }

        item {
            SectionCard(title = "模型", subtitle = "只保存在本机，经 Keystore 加密") {
                Field("Base URL", baseUrl, { baseUrl = it }, placeholder = "https://api.deepseek.com/v1")
                Field("模型名", modelName, { modelName = it }, placeholder = "deepseek-chat")
                Field(
                    "API Key",
                    apiKeyInput,
                    { apiKeyInput = it },
                    placeholder = maskSecret(settings.apiKey).ifBlank { "sk-..." },
                    supporting = when {
                        clearApiKey -> "保存后将清除已存密钥"
                        settings.apiKey != null -> "已保存 ${maskSecret(settings.apiKey)}，留空则保持不变"
                        else -> "尚未保存"
                    },
                    visualTransformation = PasswordVisualTransformation()
                )
                if (settings.apiKey != null || clearApiKey) {
                    TextButton(onClick = { clearApiKey = !clearApiKey; apiKeyInput = "" }) {
                        Text(if (clearApiKey) "撤销清除" else "清除已保存的密钥")
                    }
                }
                Field("最大输出 token", maxTokens, { maxTokens = it }, keyboardType = KeyboardType.Number)
                Field("超时（秒）", timeout, { timeout = it }, keyboardType = KeyboardType.Number)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("允许明文 HTTP", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "仅用于连接局域网内的本地模型；开启后请只填你信任的地址。",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = allowHttp, onCheckedChange = { allowHttp = it })
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
                ) {
                    TextButton(enabled = !testing, onClick = {
                        persistModel()
                        testing = true
                        scope.launch {
                            val outcome = container.llm.test()
                            testResult = when (outcome) {
                                is LlmClient.Outcome.Ok ->
                                    "连接成功 · ${outcome.reply.model} · ${outcome.reply.latencyMs}ms"
                                is LlmClient.Outcome.Fail -> "失败：${outcome.message}"
                            }
                            testing = false
                        }
                    }) { Text(if (testing) "测试中…" else "测试连接") }
                    Button(onClick = {
                        persistModel()
                        testResult = "已保存"
                    }) { Text("保存") }
                }
                if (testResult != null) {
                    Text(testResult ?: "", style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        item {
            SectionCard(
                title = "菜品识别（拍照）",
                subtitle = "视觉模型只从菜单里挑菜；留空则沿用上面的文本模型配置"
            ) {
                Field(
                    "Base URL",
                    visionBase,
                    { visionBase = it },
                    placeholder = "留空 = 用上面的 Base URL"
                )
                Field(
                    "API Key",
                    visionKeyInput,
                    { visionKeyInput = it },
                    placeholder = if (settings.visionApiKey != null) maskSecret(settings.visionApiKey)
                    else "留空 = 用上面的 API Key",
                    supporting = when {
                        clearVisionKey -> "保存后清除单独设置的密钥"
                        settings.visionApiKey != null -> "已单独保存 ${maskSecret(settings.visionApiKey)}"
                        else -> "未单独设置"
                    },
                    visualTransformation = PasswordVisualTransformation()
                )
                if (settings.visionApiKey != null || clearVisionKey) {
                    TextButton(onClick = { clearVisionKey = !clearVisionKey; visionKeyInput = "" }) {
                        Text(if (clearVisionKey) "撤销清除" else "清除单独设置的密钥")
                    }
                }
                Field(
                    "模型名",
                    visionModelText,
                    { visionModelText = it },
                    placeholder = "deepseek-flash",
                    supporting = "默认 deepseek-flash，需要该模型支持图片输入"
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        if (settings.visionReady) "识别可用" else "未配置，拍照页无法识别",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (settings.visionReady) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.error
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                        TextButton(onClick = { nav.go(Screen.Menu) }) { Text("菜单管理") }
                        Button(onClick = {
                            persistVision()
                            notice = "识别配置已保存"
                        }) { Text("保存") }
                    }
                }
                Text(
                    "营养数据来自模型估算，不是检测结果；保存的记录会标记为「拍照估算」。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item {
            SectionCard(
                title = "高德地图",
                subtitle = "直连高德，不经过后端；两个 Key 分工不同，各填各的"
            ) {
                // ---- 地图展示用的 JS API Key ----
                Text(
                    "① 地图展示 · Web端(JS API) Key",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Field(
                    "JS API Key",
                    amapJsKeyInput,
                    { amapJsKeyInput = it },
                    placeholder = maskSecret(settings.amapJsKey).ifBlank { "在高德控制台建「Web端(JS API)」类型的 Key" },
                    supporting = when {
                        clearAmapJsKey -> "保存后将清除已存的地图 Key"
                        settings.amapJsKey != null -> "已保存 ${maskSecret(settings.amapJsKey)}，留空则保持不变"
                        else -> "未填写：地图页会提示去配置"
                    },
                    visualTransformation = PasswordVisualTransformation()
                )
                if (settings.amapJsKey != null || clearAmapJsKey) {
                    TextButton(onClick = { clearAmapJsKey = !clearAmapJsKey; amapJsKeyInput = "" }) {
                        Text(if (clearAmapJsKey) "撤销清除" else "清除地图 Key")
                    }
                }
                Field(
                    "安全密钥 securityJsCode",
                    amapJsCodeInput,
                    { amapJsCodeInput = it },
                    placeholder = maskSecret(settings.amapJsSecurityCode).ifBlank { "2021-12-02 之后申请的 Key 必填" },
                    supporting = when {
                        clearAmapJsCode -> "保存后清除安全密钥"
                        settings.amapJsSecurityCode != null -> "已保存 ${maskSecret(settings.amapJsSecurityCode)}"
                        else -> "不填的话地图会报 INVALID_USER_SCODE（白屏或只有底图）"
                    },
                    visualTransformation = PasswordVisualTransformation()
                )
                if (settings.amapJsSecurityCode != null || clearAmapJsCode) {
                    TextButton(onClick = { clearAmapJsCode = !clearAmapJsCode; amapJsCodeInput = "" }) {
                        Text(if (clearAmapJsCode) "撤销清除" else "清除安全密钥")
                    }
                }

                Spacer(Modifier.height(10.dp))
                HorizontalDivider()
                Spacer(Modifier.height(10.dp))

                // ---- 搜索用的 Web 服务 Key ----
                Text(
                    "② 附近搜索 · Web服务 Key",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Field(
                    "Web 服务 Key",
                    amapWebKeyInput,
                    { amapWebKeyInput = it },
                    placeholder = maskSecret(settings.amapWebKey).ifBlank { "建「Web服务」类型的 Key，用于周边搜索" },
                    supporting = when {
                        clearAmapWebKey -> "保存后将清除已存的搜索 Key"
                        settings.amapWebKey != null -> "已保存 ${maskSecret(settings.amapWebKey)}，留空则保持不变"
                        else -> "未填写：附近页不可用"
                    },
                    visualTransformation = PasswordVisualTransformation()
                )
                if (settings.amapWebKey != null || clearAmapWebKey) {
                    TextButton(onClick = { clearAmapWebKey = !clearAmapWebKey; amapWebKeyInput = "" }) {
                        Text(if (clearAmapWebKey) "撤销清除" else "清除搜索 Key")
                    }
                }
                Text(
                    "⚠️ 两种 Key 不能混用：把 JS API Key 填到搜索里会报「平台类型不匹配」。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(10.dp))
                HorizontalDivider()
                Spacer(Modifier.height(10.dp))

                // ---- 定位与搜索范围 ----
                Text(
                    "③ 搜索范围",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Field(
                    "默认中心点（经度,纬度）",
                    amapCenter,
                    { amapCenter = it },
                    placeholder = "113.398000,23.097000",
                    supporting = "定位拿不到时用这里；坐标系是 GCJ-02（高德坐标），不是手机 GPS 原始坐标"
                )
                Field(
                    "搜索半径（米）",
                    amapRadiusText,
                    { amapRadiusText = it },
                    keyboardType = KeyboardType.Number,
                    supporting = "200 ~ 50000，默认 2000"
                )
                if (settings.amapLastFix.isNotBlank()) {
                    KeyValueRow("上次定位", settings.amapLastFix)
                }
                Text(
                    when {
                        settings.mapReady && settings.searchReady -> "地图与搜索均可用"
                        settings.mapReady -> "仅地图可用：还没填 Web 服务 Key"
                        settings.searchReady -> "仅搜索可用：还没填 JS API Key"
                        else -> "未配置"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (settings.mapReady || settings.searchReady) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.error
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
                ) {
                    TextButton(onClick = { nav.go(Screen.Nearby) }) { Text("搜附近") }
                    Button(onClick = {
                        persistAmap()
                        notice = "高德配置已保存"
                    }) { Text("保存") }
                }
            }
        }

        item {
            SectionCard(title = "社区", subtitle = "默认关闭；关闭时不发起任何网络请求") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("启用社区", style = MaterialTheme.typography.bodyMedium)
                    Switch(
                        checked = communityEnabled,
                        onCheckedChange = { checked ->
                            communityEnabled = checked
                            // 首次启用且昵称还是默认值时，让用户确认一次署名
                            if (checked && settings.displayNameIsDefault) showNicknamePrompt = true
                        }
                    )
                }
                Field(
                    "服务端地址",
                    communityUrl,
                    { communityUrl = it },
                    placeholder = "http://192.168.1.20:8017"
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
                ) {
                    TextButton(onClick = {
                        persistCommunity()
                        scope.launch {
                            val outcome = container.community.health()
                            notice = when (outcome) {
                                is CommunityClient.Outcome.Ok<*> -> "服务端可用"
                                is CommunityClient.Outcome.Fail -> "服务端不可用：${outcome.message}"
                            }
                        }
                    }) { Text("检查连通") }
                    Button(onClick = {
                        persistCommunity()
                        notice = "已保存"
                    }) { Text("保存") }
                }

                Spacer(Modifier.height(4.dp))
                Text(
                    "身份：服务端用匿名会话 cookie 识别你，没有账号。换设备或清除数据后，旧帖不再属于你，也无法删除。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "当前昵称：${settings.displayName}；服务端署名：${
                        settings.communityNickname.ifBlank { "未同步" }
                    }",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(enabled = communityEnabled, onClick = {
                        scope.launch {
                            val result = container.repo.ensureNicknameSynced(force = true)
                            notice = if (result.ok) "署名已同步为「${settings.displayName}」" else result.message
                        }
                    }) { Text("同步署名") }

                    TextButton(enabled = communityEnabled, onClick = {
                        container.repo.resetCommunityIdentity()
                        notice = "已重置社区身份，下次访问会重新建立会话"
                    }) { Text("重置身份") }
                }

                val session = settings.communitySession
                if (!session.isNullOrBlank()) {
                    TextButton(onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE)
                            as android.content.ClipboardManager
                        clipboard.setPrimaryClip(
                            android.content.ClipData.newPlainText("fm_session", session)
                        )
                        notice = "会话凭据已复制。⚠️ 它是你的身份凭据，不要发给别人"
                    }) { Text("复制会话凭据") }
                }
            }
        }

        item {
            SectionCard(title = "身份") {
                KeyValueRow("本机 UID", settings.shortUid)
                Text(
                    "UID 在首次启动时生成，卸载应用后重置。昵称不保证唯一。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Field("昵称", displayName, { displayName = it })
            }
        }

        item {
            SectionCard(title = "偏好与预算") {
                Field("默认日预算（元）", budgetText, { budgetText = it }, keyboardType = KeyboardType.Decimal)
                Field(
                    "口味偏好",
                    prefsText,
                    { prefsText = it },
                    supporting = "用顿号或逗号分隔，例如：不辣、清淡"
                )
                Field(
                    "忌口与过敏",
                    allergensText,
                    { allergensText = it },
                    supporting = "与口味偏好分开记录"
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Button(onClick = {
                        persistProfile()
                        notice = "已保存"
                    }) { Text("保存") }
                }
            }
        }

        item {
            SectionCard(title = "数据", subtitle = "导出为 zip，含图片；不含密钥") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = {
                        exportLauncher.launch("foodmoment-export.zip")
                    }) { Text("导出") }
                    TextButton(onClick = {
                        importLauncher.launch(arrayOf("application/zip", "application/octet-stream", "*/*"))
                    }) { Text("导入") }
                }
                TextButton(onClick = { showClear = true }) { DangerText("清除全部数据") }
            }
        }

        item {
            SectionCard(title = "使用说明", subtitle = "第一次用建议从第 1 条看起") {
                if (!showGuide) {
                    Text(
                        "配置模型、语音输入、记账、做计划、备份与社区。展开后可查看步骤和常见问题。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    USAGE_STEPS.forEachIndexed { index, (title, body) ->
                        Text(
                            "${index + 1}. $title",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            body,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                    HorizontalDivider(Modifier.padding(vertical = 4.dp))
                    Spacer(Modifier.height(6.dp))
                    Text("常见问题", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    FAQ.forEach { (question, answer) ->
                        Text("· $question", style = MaterialTheme.typography.bodySmall)
                        Text(
                            "   $answer",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(6.dp))
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(onClick = { showGuide = !showGuide }) {
                        Text(if (showGuide) "收起" else "展开全部")
                    }
                    TextButton(onClick = {
                        settings.onboarded = false
                        notice = "下次启动会重新显示引导"
                    }) { Text("重看引导") }
                }
            }
        }

        item {
            SectionCard(title = "关于") {
                KeyValueRow("版本", version)
                KeyValueRow("包名", context.packageName)
                Text(
                    "营养数据来源：U.S. Department of Agriculture, Agricultural Research Service, " +
                        "FoodData Central。内置条目目前标记为「待核验」，详见 docs/data-sources.md。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "本应用以 MIT 许可证开源。不提供模型服务，AI 能力依赖你自行配置的端点，费用由该服务方结算。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item { Spacer(Modifier.height(30.dp)) }
    }

    if (showClear) {
        AlertDialog(
            onDismissRequest = { showClear = false },
            title = { Text("清除全部数据") },
            text = {
                Text(
                    "将删除全部记录、计划、会话与帖子（含图片），且不可恢复。内置食物表与你的模型配置会保留。\n\n" +
                        "建议先导出备份。",
                    style = MaterialTheme.typography.bodySmall
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        container.repo.clearAllData()
                        showClear = false
                        notice = "已清除"
                    }
                }) { DangerText("确认清除") }
            },
            dismissButton = { TextButton(onClick = { showClear = false }) { Text("取消") } }
        )
    }

    if (showNicknamePrompt) {
        var nameInput by remember { mutableStateOf(settings.suggestedGuestName) }
        AlertDialog(
            onDismissRequest = {
                settings.displayName = settings.suggestedGuestName
                showNicknamePrompt = false
            },
            title = { Text("社区署名") },
            text = {
                Column {
                    Text(
                        "发帖和回复会用这个名字显示。当前昵称还是默认的「我」，建议改一个。",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Field("昵称", nameInput, { nameInput = it }, placeholder = settings.suggestedGuestName)
                    Text(
                        "不想改也可以用默认的「${settings.suggestedGuestName}」。改名只影响之后发布的帖子。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    settings.displayName = nameInput.trim().ifBlank { settings.suggestedGuestName }
                    showNicknamePrompt = false
                    notice = "署名已设为「${settings.displayName}」"
                }) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = {
                    settings.displayName = settings.suggestedGuestName
                    showNicknamePrompt = false
                }) { Text("用默认") }
            }
        )
    }
}

/** 已保存的密钥只显示掩码，永远不回填明文 */
private fun maskSecret(value: String?): String {
    if (value.isNullOrBlank()) return ""
    return if (value.length <= 8) "••••••" else value.take(3) + "••••" + value.takeLast(4)
}

private val USAGE_STEPS: List<Pair<String, String>> = listOf(
    "配置模型" to
        "设置 → 模型：填 Base URL、模型名、API Key，点「测试连接」。不配置也能用记录、营养换算和离线模板计划，只是没有 AI 对话与生成。",
    "语音输入" to
        "点输入框后用输入法的语音键说话。App 不采集麦克风，也不需要录音权限。",
    "记一笔" to
        "首页「记一笔」，或直接在聊天里说「午饭吃了 15 块」。先说金额不填也可以，金额未知不会被当成 0。",
    "做计划" to
        "首页「今天吃什么」。没有模型时用离线模板，模板不含价格，需要自己逐项填写；超出预算会直接标出来。",
    "查营养" to
        "营养页搜索食物后按克重换算。内置数据标「待核验」，自定义食物的数值完全由你填写，未填的项不会按 0 计算。",
    "备份数据" to
        "设置 → 数据 → 导出。数据只存在本机，卸载或换机前请务必导出一次。",
    "社区" to
        "默认关闭，关闭时不发起任何网络请求。需要先自己部署服务端，再在设置里填地址；身份是匿名会话，没有账号，换设备后旧帖不再属于你。"
)

private val FAQ: List<Pair<String, String>> = listOf(
    "提示 401 / 密钥无效" to "Key 填错或已失效，重新填写后点「测试连接」确认。",
    "请求超时" to "检查网络，并确认 Base URL 是否包含 /v1 与正确路径。",
    "连不上 127.0.0.1" to "手机上的 127.0.0.1 指向手机自己，本地模型要填电脑的局域网 IP。",
    "提示模型不存在" to "换成该端点实际支持的模型名，大小写要一致。",
    "金额为什么是「未知」" to "没有来源的价格一律留空，不猜也不按 0 计算，这样预算判断才可信。"
)
