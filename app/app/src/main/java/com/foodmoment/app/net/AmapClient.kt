package com.foodmoment.app.net

import com.foodmoment.app.core.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlin.math.abs

/**
 * 高德 Web 服务直连（不经过后端）。
 *
 * 只用到两个能力：
 *  - 周边搜索 v3/place/around  → 附近的店
 *  - 坐标转换 v3/assistant/coordinate/convert → 手机 GPS(WGS-84) 转 GCJ-02
 *
 * 注意：高德 Web 服务在 status != "1" 时 HTTP 仍是 200，必须读 infocode 才能给出有意义的错误。
 */
class AmapClient(private val settings: Settings) {

    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    sealed interface Outcome<out T> {
        data class Ok<T>(val value: T) : Outcome<T>
        data class Fail(val message: String) : Outcome<Nothing>
    }

    /** 一个商家/地点。字段可为空表示高德没给，不猜。 */
    data class Poi(
        val id: String,
        val name: String,
        val address: String,
        val lng: Double,
        val lat: Double,
        val distance: Int?,
        val rating: Double?,
        val cost: Double?,
        val hours: String?,
        val phone: String?,
        val type: String?,
        val photoUrl: String?
    ) {
        val location: String get() = "$lng,$lat"
    }

    /**
     * 周边搜索。
     * @param location GCJ-02 的 "经度,纬度"
     * @param keyword 关键词，留空表示只按类型（餐饮）搜
     */
    suspend fun searchAround(
        location: String,
        keyword: String,
        radius: Int,
        page: Int = 1
    ): Outcome<List<Poi>> = withContext(Dispatchers.IO) {
        val key = settings.amapWebKey
        if (key.isNullOrBlank()) return@withContext Outcome.Fail("未填写高德 Web 服务 Key")
        if (!Coord.isValidLngLat(location)) return@withContext Outcome.Fail("中心点坐标格式不对，应为「经度,纬度」")

        val url = try {
            "https://restapi.amap.com/v3/place/around".toHttpUrlOrNull()?.newBuilder()
                ?.addQueryParameter("key", key)
                ?.addQueryParameter("location", location)
                ?.addQueryParameter("types", TYPE_RESTAURANT)
                ?.addQueryParameter("radius", radius.toString())
                ?.addQueryParameter("offset", PAGE_SIZE.toString())
                ?.addQueryParameter("page", page.toString())
                ?.addQueryParameter("extensions", "all")
                ?.addQueryParameter("sortrule", "distance")
                ?.apply { if (keyword.isNotBlank()) addQueryParameter("keywords", keyword.trim().take(60)) }
                ?.build() ?: return@withContext Outcome.Fail("搜索地址拼装失败")
        } catch (e: IllegalArgumentException) {
            return@withContext Outcome.Fail("中心点坐标格式不对，应为「经度,纬度」")
        }

        runCatching { get(url) }.fold(
            onSuccess = { text ->
                val json = JSONObject(text)
                if (json.optString("status") != "1") return@withContext Outcome.Fail(describe(json))
                val pois = json.optJSONArray("pois") ?: return@withContext Outcome.Ok(emptyList())
                val list = buildList {
                    for (i in 0 until pois.length()) {
                        pois.optJSONObject(i)?.toPoi()?.let { add(it) }
                    }
                }
                Outcome.Ok(list)
            },
            onFailure = { Outcome.Fail("搜索失败：${it.message ?: "网络异常"}") }
        )
    }

    /** GPS(WGS-84) → 高德(GCJ-02)。失败时返回原坐标并附提示由调用方决定。 */
    suspend fun wgs84ToGcj02(lng: Double, lat: Double): String? = withContext(Dispatchers.IO) {
        val key = settings.amapWebKey
        if (key.isNullOrBlank()) return@withContext null
        // 先本地近似：中国境外不需要偏移
        if (!Coord.inChina(lng, lat)) return@withContext "$lng,$lat"
        val url = try {
            "https://restapi.amap.com/v3/assistant/coordinate/convert".toHttpUrlOrNull()?.newBuilder()
                ?.addQueryParameter("key", key)
                ?.addQueryParameter("locations", String.format("%.6f,%.6f", lng, lat))
                ?.addQueryParameter("coordsys", "gps")
                ?.build() ?: return@withContext null
        } catch (e: IllegalArgumentException) {
            return@withContext null
        }
        runCatching { get(url) }.getOrNull()?.let { text ->
            val json = runCatching { JSONObject(text) }.getOrNull()
            if (json?.optString("status") == "1") {
                val locations = json.optString("locations")
                if (locations.isNotBlank()) return@withContext locations.split(";").first().trim()
            }
        }
        // 接口失败时用本地算法兜底，误差通常在几十米内，比直接用 GPS 坐标好
        Coord.wgs84ToGcj02(lng, lat)
    }

    private fun get(url: HttpUrl): String {
        http.newCall(Request.Builder().url(url).get().build()).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) error("HTTP ${response.code}")
            return text
        }
    }

    /** 高德错误码 → 人能看懂的说明 */
    private fun describe(json: JSONObject): String {
        val info = json.optString("info").ifBlank { "未知错误" }
        return when (json.optString("infocode")) {
            "10001" -> "Key 无效（$info），请检查 Web 服务 Key 是否填对、是否属于「Web服务」类型"
            "10002" -> "Key 没有该接口权限（$info），请在高德控制台为该 Key 勾选「周边搜索」"
            "10003" -> "今日调用量已超配额（$info）"
            "10004" -> "调用过于频繁（$info），请稍后再试"
            "10009" -> "Key 与平台类型不匹配（$info）：搜索要用「Web服务」Key，不是 JS API Key"
            "10010" -> "IP 白名单限制（$info），手机网络出口 IP 变来变去，建议关闭白名单"
            "10044" -> "当日调用量已达上限（$info）"
            "20000" -> "请求参数错误（$info）"
            "20001" -> "缺少必要参数（$info）"
            "20003" -> "超出服务范围（$info）"
            else -> "高德返回错误：$info（infocode=${json.optString("infocode")}）"
        }
    }

    private fun JSONObject.toPoi(): Poi? {
        val id = optString("id")
        val name = optString("name")
        val parts = optString("location").split(",")
        if (id.isBlank() || name.isBlank() || parts.size != 2) return null
        val lng = parts[0].trim().toDoubleOrNull() ?: return null
        val lat = parts[1].trim().toDoubleOrNull() ?: return null
        val biz = optJSONObject("biz_ext")
        return Poi(
            id = id,
            name = name,
            address = cleanAmapString(optString("address")).ifBlank { "地址未提供" },
            lng = lng,
            lat = lat,
            distance = cleanAmapString(optString("distance")).toIntOrNull(),
            rating = cleanAmapString(biz?.optString("rating").orEmpty()).toDoubleOrNull(),
            cost = cleanAmapString(biz?.optString("cost").orEmpty()).toDoubleOrNull(),
            hours = cleanAmapString(biz?.optString("open_time").orEmpty()).takeIf { it.isNotBlank() },
            phone = cleanAmapString(optString("tel")).takeIf { it.isNotBlank() },
            type = cleanAmapString(optString("type")).takeIf { it.isNotBlank() },
            photoUrl = firstHttpsPhoto()
        )
    }

    /** 高德在字段缺失时返回空数组 []，要先剔除这种值 */
    private fun cleanAmapString(raw: String): String {
        val trimmed = raw.trim()
        return if (trimmed == "[]" || trimmed == "null") "" else trimmed
    }

    private fun JSONObject.firstHttpsPhoto(): String? {
        val photos = optJSONArray("photos") ?: return null
        for (i in 0 until photos.length()) {
            val raw = photos.optJSONObject(i)?.optString("url").orEmpty()
            if (raw.startsWith("https://")) return raw
            if (raw.startsWith("http://")) {
                val https = "https://" + raw.removePrefix("http://")
                // 只有高德自家图床才敢改协议，第三方域名保持原样
                if (https.contains(".autonavi.com/") || https.contains(".amap.com/")) return https
            }
        }
        return null
    }

    companion object {
        /** 050000 = 餐饮服务 */
        const val TYPE_RESTAURANT = "050000"
        const val PAGE_SIZE = 20
    }
}

/**
 * 坐标工具。GCJ-02 加密算法是公开的，本地实现用于几条场景：
 *  - 没有 Web 服务 Key / 接口失败时的兜底偏移
 *  - 判断坐标是否在中国境内（境外不偏移）
 */
object Coord {

    private const val A = 6378245.0
    private const val EE = 0.00669342162296594323

    fun inChina(lng: Double, lat: Double): Boolean =
        lng in 73.66..135.05 && lat in 3.86..53.55

    fun isValidLngLat(raw: String): Boolean {
        val parts = raw.split(",")
        if (parts.size != 2) return false
        val lng = parts[0].trim().toDoubleOrNull() ?: return false
        val lat = parts[1].trim().toDoubleOrNull() ?: return false
        return abs(lng) <= 180 && abs(lat) <= 90
    }

    /** 拆出 "经度,纬度"，失败返回 null */
    fun parse(raw: String): Pair<Double, Double>? {
        val parts = raw.split(",")
        if (parts.size != 2) return null
        val lng = parts[0].trim().toDoubleOrNull() ?: return null
        val lat = parts[1].trim().toDoubleOrNull() ?: return null
        if (!isValidLngLat(raw)) return null
        return lng to lat
    }

    fun format(lng: Double, lat: Double): String = String.format("%.6f,%.6f", lng, lat)

    /** WGS-84 → GCJ-02 */
    fun wgs84ToGcj02(lng: Double, lat: Double): String {
        if (!inChina(lng, lat)) return format(lng, lat)
        val dLat = transformLat(lng - 105.0, lat - 35.0)
        val dLng = transformLng(lng - 105.0, lat - 35.0)
        val radLat = lat / 180.0 * Math.PI
        var magic = Math.sin(radLat)
        magic = 1 - EE * magic * magic
        val sqrtMagic = Math.sqrt(magic)
        val outLat = lat + (dLat * 180.0) / ((A * (1 - EE)) / (magic * sqrtMagic) * Math.PI)
        val outLng = lng + (dLng * 180.0) / (A / sqrtMagic * Math.cos(radLat) * Math.PI)
        return format(outLng, outLat)
    }

    private fun transformLat(x: Double, y: Double): Double {
        var ret = -100.0 + 2.0 * x + 3.0 * y + 0.2 * y * y + 0.1 * x * y + 0.2 * Math.sqrt(abs(x))
        ret += (20.0 * Math.sin(6.0 * x * Math.PI) + 20.0 * Math.sin(2.0 * x * Math.PI)) * 2.0 / 3.0
        ret += (20.0 * Math.sin(y * Math.PI) + 40.0 * Math.sin(y / 3.0 * Math.PI)) * 2.0 / 3.0
        ret += (160.0 * Math.sin(y / 12.0 * Math.PI) + 320.0 * Math.sin(y * Math.PI / 30.0)) * 2.0 / 3.0
        return ret
    }

    private fun transformLng(x: Double, y: Double): Double {
        var ret = 300.0 + x + 2.0 * y + 0.1 * x * x + 0.1 * x * y + 0.1 * Math.sqrt(abs(x))
        ret += (20.0 * Math.sin(6.0 * x * Math.PI) + 20.0 * Math.sin(2.0 * x * Math.PI)) * 2.0 / 3.0
        ret += (20.0 * Math.sin(x * Math.PI) + 40.0 * Math.sin(x / 3.0 * Math.PI)) * 2.0 / 3.0
        ret += (150.0 * Math.sin(x / 12.0 * Math.PI) + 300.0 * Math.sin(x / 30.0 * Math.PI)) * 2.0 / 3.0
        return ret
    }
}
