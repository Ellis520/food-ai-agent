package com.foodmoment.app.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.foodmoment.app.AppContainer
import com.foodmoment.app.core.Locator
import com.foodmoment.app.net.AmapClient
import com.foodmoment.app.net.Coord
import com.foodmoment.app.ui.Navigator
import com.foodmoment.app.ui.Screen
import com.foodmoment.app.ui.components.EmptyState
import com.foodmoment.app.ui.components.RemoteThumb
import com.foodmoment.app.ui.components.Tag
import kotlinx.coroutines.launch

/**
 * 附近商家（高德 Web 服务直连）。
 *
 * 中心点优先级：手机定位（转 GCJ-02）→ 设置里的手动中心点。
 */
@Composable
fun NearbyScreen(container: AppContainer, nav: Navigator) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val settings = container.settings
    val amap = remember { AmapClient(settings) }

    var keyword by remember { mutableStateOf("") }
    var center by remember { mutableStateOf(settings.amapLastFix.ifBlank { settings.amapCenter }) }
    var centerFrom by remember { mutableStateOf(if (settings.amapLastFix.isNotBlank()) "上次定位" else "设置的中心点") }
    var locating by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var pois by remember { mutableStateOf<List<AmapClient.Poi>>(emptyList()) }
    var searched by remember { mutableStateOf(false) }

    suspend fun locate() {
        locating = true
        val location = Locator.current(context)
        if (location != null) {
            val gcj = amap.wgs84ToGcj02(location.longitude, location.latitude)
            if (gcj != null) {
                center = gcj
                centerFrom = "手机定位"
                settings.amapLastFix = gcj
                message = null
            } else {
                message = "定位成功但转换成高德坐标失败，已回退到设置的中心点"
            }
        } else {
            message = "取不到定位（可能没授权或定位服务关闭），已回退到设置的中心点"
        }
        locating = false
    }

    suspend fun search() {
        loading = true
        message = null
        when (val outcome = amap.searchAround(center, keyword, settings.amapRadius)) {
            is AmapClient.Outcome.Ok -> {
                pois = outcome.value
                searched = true
                if (outcome.value.isEmpty()) message = "附近没有搜到结果，换个关键词或放大半径试试"
            }

            is AmapClient.Outcome.Fail -> {
                pois = emptyList()
                searched = true
                message = outcome.message
            }
        }
        loading = false
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        scope.launch {
            if (granted.values.any { it }) {
                locate()
            } else {
                message = "没有定位权限，将使用设置里的中心点"
            }
            search()
        }
    }

    // 进来先尝试定位并搜一次，用户不用点两次
    LaunchedEffect(Unit) {
        if (settings.searchReady) {
            if (Locator.hasPermission(context)) {
                locate()
            } else {
                val fine = androidx.core.content.ContextCompat.checkSelfPermission(
                    context, Manifest.permission.ACCESS_FINE_LOCATION
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                if (!fine) message = "未授权定位，先用设置里的中心点；可点「重新定位」申请权限"
            }
            search()
        }
    }

    if (!settings.searchReady) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                EmptyState(
                    "还没配置高德 Web 服务 Key",
                    "去「我的 → 高德地图」填写「Web服务」类型的 Key，就能搜附近的店。"
                )
                Button(onClick = { nav.go(Screen.Settings) }) { Text("去设置") }
            }
        }
        return
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = keyword,
                onValueChange = { keyword = it },
                modifier = Modifier.weight(1f),
                singleLine = true,
                label = { Text("搜什么") },
                placeholder = { Text("留空 = 附近的餐饮") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) }
            )
            Spacer(Modifier.width(8.dp))
            Button(enabled = !loading, onClick = { scope.launch { search() } }) {
                Text(if (loading) "搜索中" else "搜索")
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "中心 $center（$centerFrom）· 半径 ${settings.amapRadius} 米",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            TextButton(enabled = !locating, onClick = {
                if (Locator.hasPermission(context)) {
                    scope.launch { locate(); search() }
                } else {
                    permissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        )
                    )
                }
            }) {
                Icon(Icons.Filled.MyLocation, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(if (locating) "定位中" else "重新定位")
            }
        }

        if (message != null) {
            Text(
                message ?: "",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }

        when {
            loading && pois.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
            }

            pois.isEmpty() && searched -> EmptyState("没有结果", "换个关键词，或把搜索半径调大。")

            pois.isEmpty() -> EmptyState("点「搜索」看看附近有什么", "按距离排序，最多 20 条。")

            else -> LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                items(pois, key = { it.id }) { poi ->
                    PoiCard(poi) {
                        nav.go(Screen.MapDetail(poi.lng, poi.lat, poi.name))
                    }
                }
            }
        }
    }
}

@Composable
private fun PoiCard(poi: AmapClient.Poi, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 5.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    poi.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    poi.address,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (poi.photoUrl != null) {
                Spacer(Modifier.width(8.dp))
                RemoteThumb(url = poi.photoUrl, size = 56.dp)
            }
        }

        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            if (poi.distance != null) {
                Tag(formatDistance(poi.distance), MaterialTheme.colorScheme.primary, Icons.Filled.Place)
            }
            if (poi.rating != null) Tag("${poi.rating} 分", MaterialTheme.colorScheme.secondary)
            if (poi.cost != null) Tag("人均 ¥${trimMoney(poi.cost)}", MaterialTheme.colorScheme.tertiary)
        }
        if (poi.hours != null) {
            Spacer(Modifier.height(4.dp))
            Text(
                poi.hours,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun formatDistance(meters: Int): String =
    if (meters < 1000) "${meters} m" else String.format("%.1f km", meters / 1000.0)

private fun trimMoney(value: Double): String =
    if (value % 1.0 == 0.0) value.toInt().toString() else String.format("%.1f", value)
