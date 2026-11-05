package com.foodmoment.app.ui.screens

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.foodmoment.app.AppContainer
import com.foodmoment.app.core.MealType
import com.foodmoment.app.core.Money
import com.foodmoment.app.core.Time
import com.foodmoment.app.data.DietRecord
import com.foodmoment.app.data.RecordItem
import com.foodmoment.app.net.VisionClient
import com.foodmoment.app.ui.Navigator
import com.foodmoment.app.ui.Screen
import com.foodmoment.app.ui.components.DangerText
import com.foodmoment.app.ui.components.SectionCard
import com.foodmoment.app.ui.components.Tag
import com.foodmoment.app.ui.theme.WarningAmber
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import java.util.UUID

private data class DraftLine(
    val dish: VisionClient.Dish,
    val count: Int,
    val grams: Double
) {
    val costFen: Long? get() = dish.priceFen?.let { it * count }
    val kcal: Double? get() = dish.kcal100?.let { it * grams * count / 100.0 }
}

/**
 * 拍照识别菜品（替代原来的底部「营养」位）。
 *
 * 识别不是凭空猜：只允许从菜单里挑，菜单里的菜名与价格来自 assets/menu.md 或用户自建。
 * 营养值由视觉模型按每 100g 估算，结果一律标「估算」。
 */
@Composable
fun PhotoScreen(container: AppContainer, nav: Navigator) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val rev by container.repo.revision.collectAsState()

    var photoUri by remember { mutableStateOf<Uri?>(null) }
    var captureUri by remember { mutableStateOf<Uri?>(null) }
    var analyzing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var result by remember { mutableStateOf<VisionClient.Result?>(null) }
    var drafts by remember { mutableStateOf<List<DraftLine>>(emptyList()) }
    var mealType by remember { mutableStateOf(Time.mealTypeByHour(Time.now())) }
    var savedMessage by remember { mutableStateOf<String?>(null) }
    var menuCount by remember { mutableIntStateOf(0) }

    LaunchedEffect(rev) { menuCount = container.repo.menuCount() }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            photoUri = captureUri
            result = null
            drafts = emptyList()
            error = null
            savedMessage = null
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            photoUri = uri
            result = null
            drafts = emptyList()
            error = null
            savedMessage = null
        }
    }

    fun startCamera() {
        val dir = File(context.cacheDir, "photos").apply { mkdirs() }
        val file = File(dir, "capture_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        captureUri = uri
        cameraLauncher.launch(uri)
    }

    fun analyze() {
        val uri = photoUri ?: return
        scope.launch {
            analyzing = true
            error = null
            savedMessage = null
            val base64 = withContext(Dispatchers.IO) { container.repo.images.toBase64(uri) }
            if (base64 == null) {
                error = "读取图片失败，换一张试试"
                analyzing = false
                return@launch
            }
            val menu = container.repo.menuItems()
            when (val outcome = container.vision.analyze(base64, menu)) {
                is VisionClient.Outcome.Ok -> {
                    val parsed = outcome.result
                    result = parsed
                    drafts = parsed.dishes.map { DraftLine(it, it.count, it.grams) }
                    if (parsed.dishes.isNotEmpty()) mealType = parsed.mealType
                    if (parsed.dishes.isEmpty()) {
                        error = parsed.warnings.firstOrNull() ?: "没有识别出菜单里的菜品"
                    }
                }

                is VisionClient.Outcome.Fail -> {
                    result = null
                    drafts = emptyList()
                    error = outcome.message
                }
            }
            analyzing = false
        }
    }

    fun save() {
        if (drafts.isEmpty()) return
        scope.launch {
            val recordId = "rec_" + UUID.randomUUID().toString().take(8)
            val items = drafts.map { line ->
                RecordItem(
                    id = "ri_" + UUID.randomUUID().toString().take(8),
                    recordId = recordId,
                    foodId = null,
                    name = if (line.count > 1) "${line.dish.name} ×${line.count}" else line.dish.name,
                    grams = line.grams * line.count,
                    kcal100 = line.dish.kcal100,
                    protein100 = line.dish.protein100,
                    fat100 = line.dish.fat100,
                    carbs100 = line.dish.carbs100
                )
            }
            val cost = drafts.mapNotNull { it.costFen }.takeIf { it.isNotEmpty() }?.sum()
            container.repo.saveRecord(
                DietRecord(
                    id = recordId,
                    eatenAt = Time.now(),
                    mealType = mealType,
                    costFen = cost,
                    note = "拍照识别 · 营养为估算",
                    sourcePlanId = null,
                    deletedAt = null,
                    source = "vision",
                    items = items
                )
            )
            savedMessage = "已加入今日${MealType.label(mealType)}记录"
        }
    }

    val totalKcal = drafts.sumOf { it.kcal ?: 0.0 }
    val totalCost = drafts.mapNotNull { it.costFen }.sum()
    val hasUnknownNutrition = drafts.any { it.dish.kcal100 == null }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 10.dp)
    ) {
        item {
            SectionCard(
                title = "拍照识别菜品",
                subtitle = "只从菜单里选，不凭空生成菜品；营养值由模型估算"
            ) {
                Text(
                    "菜品来自内置菜单（$menuCount 条）或你自己添加的条目。识别结果会先给你确认，保存后才进入记录。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(onClick = { nav.go(Screen.Menu) }) { Text("菜单管理") }
                    TextButton(onClick = { nav.go(Screen.Settings) }) { Text("识别模型设置") }
                }
            }
        }

        if (!container.settings.visionReady) {
            item {
                SectionCard(title = "还没配置识别模型") {
                    Text(
                        "到「我的 → 菜品识别」填写 Base URL 与 API Key。留空则沿用文本模型的配置，默认模型为 deepseek-flash。",
                        style = MaterialTheme.typography.bodySmall
                    )
                    TextButton(onClick = { nav.go(Screen.Settings) }) { Text("去设置") }
                }
            }
        }

        item {
            SectionCard(title = "这一餐的照片") {
                if (photoUri == null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                            .clickable { startCamera() },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "点这里拍照，或从相册选择",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    val preview by produceState<Bitmap?>(null, photoUri) {
                        value = withContext(Dispatchers.IO) {
                            photoUri?.let { container.repo.images.decodeUri(it, 900) }
                        }
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                        contentAlignment = Alignment.Center
                    ) {
                        val bitmap = preview
                        if (bitmap != null) {
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextButton(onClick = { startCamera() }) { Text("拍照") }
                    TextButton(onClick = {
                        galleryLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    }) { Text("相册") }
                    Spacer(Modifier.weight(1f))
                    Button(
                        enabled = photoUri != null && !analyzing && container.settings.visionReady,
                        onClick = { analyze() }
                    ) { Text(if (analyzing) "识别中…" else "识别") }
                }

                if (analyzing) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("正在对照菜单识别…", style = MaterialTheme.typography.labelSmall)
                    }
                }

                error?.let {
                    Spacer(Modifier.height(6.dp))
                    DangerText(it)
                }
            }
        }

        if (drafts.isNotEmpty()) {
            item {
                SectionCard(title = "识别结果", subtitle = "确认并调整后再保存") {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        MealType.all.forEach { type ->
                            TextButton(onClick = { mealType = type }) {
                                Text(
                                    MealType.label(type),
                                    fontWeight = if (mealType == type) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }

                    drafts.forEachIndexed { index, line ->
                        Column(Modifier.padding(vertical = 8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    line.dish.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    line.costFen?.let { Money.format(it) } ?: "价格未知",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "克重",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.width(6.dp))
                                OutlinedTextField(
                                    value = line.grams.toInt().toString(),
                                    onValueChange = { text ->
                                        val grams = text.filter { it.isDigit() }.toDoubleOrNull()
                                        if (grams != null && grams > 0) {
                                            drafts = drafts.toMutableList().also {
                                                it[index] = line.copy(grams = grams)
                                            }
                                        }
                                    },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.width(96.dp)
                                )
                                Spacer(Modifier.width(10.dp))
                                Counter(
                                    value = line.count,
                                    onChange = { next ->
                                        drafts = drafts.toMutableList().also {
                                            it[index] = line.copy(count = next)
                                        }
                                    }
                                )
                            }
                            Text(
                                buildString {
                                    append("估算 ")
                                    append(line.kcal?.let { "${it.toInt()} kcal" } ?: "能量未知")
                                    if (line.dish.confidence > 0) {
                                        append(" · 置信度 ${(line.dish.confidence * 100).toInt()}%")
                                    }
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("合计", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            if (hasUnknownNutrition) "≈ ${totalKcal.toInt()} kcal · ${Money.format(totalCost)}"
                            else "${totalKcal.toInt()} kcal · ${Money.format(totalCost)}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Tag("营养为模型估算", WarningAmber)
                        if (hasUnknownNutrition) Tag("部分项无营养值", WarningAmber)
                    }

                    result?.warnings?.takeIf { it.isNotEmpty() }?.let { warnings ->
                        Spacer(Modifier.height(6.dp))
                        warnings.forEach {
                            Text(
                                "· $it",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Button(onClick = { save() }) { Text("保存到记录") }
                    }

                    savedMessage?.let {
                        Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        TextButton(onClick = { nav.go(Screen.Records) }) { Text("去看记录") }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun Counter(value: Int, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = { if (value > 1) onChange(value - 1) }) { Text("−") }
        Text(
            "×$value",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.width(34.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        TextButton(onClick = { if (value < 20) onChange(value + 1) }) { Text("+") }
    }
}

@Suppress("unused")
private fun fmt(value: Double): String = String.format(Locale.CHINA, "%.1f", value)
