package com.foodmoment.app.core

import android.content.Context
import java.util.UUID

/**
 * 本地配置（SET-01 ~ SET-06）。
 *
 * 非敏感项直接存 SharedPreferences；API Key 经 [Crypto] 加密后再存。
 * 说明：方案文档写的是 DataStore，这里用 SharedPreferences 换取零协程依赖与更少的构建风险，
 * 语义等价，后续可平滑替换。
 */
class Settings(context: Context) {

    private val sp = context.applicationContext
        .getSharedPreferences("fm_settings", Context.MODE_PRIVATE)

    private fun putString(key: String, value: String?) {
        sp.edit().apply { if (value == null) remove(key) else putString(key, value) }.apply()
    }

    private fun putLong(key: String, value: Long) = sp.edit().putLong(key, value).apply()
    private fun putBool(key: String, value: Boolean) = sp.edit().putBoolean(key, value).apply()

    // ---------- 身份（SET-05） ----------

    val uid: String
        get() {
            val existing = sp.getString(K_UID, null)
            if (!existing.isNullOrBlank()) return existing
            val generated = UUID.randomUUID().toString()
            putString(K_UID, generated)
            return generated
        }

    /** 短 UID，用于界面展示与帖子归属标识 */
    val shortUid: String get() = "u-" + uid.replace("-", "").take(6)

    var displayName: String
        get() = sp.getString(K_NAME, null) ?: "我"
        set(value) = putString(K_NAME, value.ifBlank { "我" })

    /** 社区署名：昵称还是默认的「我」时，用设备短 UID 生成一个不重复的默认名 */
    val suggestedGuestName: String
        get() = "食客-" + shortUid.removePrefix("u-").take(4)

    /** 昵称是否为默认值（启用社区时应让用户确认一次） */
    val displayNameIsDefault: Boolean
        get() = displayName.trim().let { it.isEmpty() || it == "我" }

    // ---------- 模型配置（SET-01） ----------

    var modelBaseUrl: String
        get() = sp.getString(K_BASE_URL, "") ?: ""
        set(value) = putString(K_BASE_URL, value.trim().trimEnd('/'))

    var modelName: String
        get() = sp.getString(K_MODEL, "") ?: ""
        set(value) = putString(K_MODEL, value.trim())

    var apiKey: String?
        get() = Crypto.decrypt(sp.getString(K_API_KEY, null))
        set(value) = putString(K_API_KEY, value?.takeIf { it.isNotBlank() }?.let { Crypto.encrypt(it) })

    var temperature: Int
        get() = sp.getInt(K_TEMP, 30)
        set(value) = sp.edit().putInt(K_TEMP, value).apply()

    var maxTokens: Int
        get() = sp.getInt(K_MAX_TOKENS, 900)
        set(value) = sp.edit().putInt(K_MAX_TOKENS, value).apply()

    var timeoutSeconds: Int
        get() = sp.getInt(K_TIMEOUT, 60)
        set(value) = sp.edit().putInt(K_TIMEOUT, value).apply()

    var allowHttp: Boolean
        get() = sp.getBoolean(K_ALLOW_HTTP, false)
        set(value) = putBool(K_ALLOW_HTTP, value)

    val modelReady: Boolean
        get() = modelBaseUrl.isNotBlank() && modelName.isNotBlank()

    // ---------- 菜品识别（拍照） ----------

    /** 留空表示沿用上面的文本模型配置 */
    var visionBaseUrl: String
        get() = sp.getString(K_VISION_BASE, "") ?: ""
        set(value) = putString(K_VISION_BASE, value.trim().trimEnd('/'))

    var visionApiKey: String?
        get() = Crypto.decrypt(sp.getString(K_VISION_KEY, null))
        set(value) = putString(K_VISION_KEY, value?.takeIf { it.isNotBlank() }?.let { Crypto.encrypt(it) })

    var visionModel: String
        get() = sp.getString(K_VISION_MODEL, DEFAULT_VISION_MODEL)?.ifBlank { DEFAULT_VISION_MODEL }
            ?: DEFAULT_VISION_MODEL
        set(value) = putString(K_VISION_MODEL, value.trim())

    val effectiveVisionBase: String
        get() = visionBaseUrl.ifBlank { modelBaseUrl }

    val effectiveVisionKey: String?
        get() = visionApiKey?.takeIf { it.isNotBlank() } ?: apiKey

    val effectiveVisionModel: String
        get() = visionModel.ifBlank { DEFAULT_VISION_MODEL }

    val visionReady: Boolean
        get() = effectiveVisionBase.isNotBlank() && !effectiveVisionKey.isNullOrBlank()

    // ---------- 社区（新后端 food-ai-agent） ----------

    var communityEnabled: Boolean
        get() = sp.getBoolean(K_COMMUNITY_ON, false)
        set(value) = putBool(K_COMMUNITY_ON, value)

    var communityUrl: String
        get() = sp.getString(K_COMMUNITY_URL, "") ?: ""
        set(value) = putString(K_COMMUNITY_URL, value.trim().trimEnd('/'))

    /**
     * 服务端下发的会话 token（Set-Cookie: fm_session=...）。
     * 只存 token 本身，请求时手动带 Cookie 头；401 时清空并重新握手。
     */
    var communitySession: String?
        get() = sp.getString(K_COMMUNITY_SESSION, null)?.takeIf { it.isNotBlank() }
        set(value) = putString(K_COMMUNITY_SESSION, value)

    /** 已经同步到服务端 state.profile.name 的值，用于判断是否需要再次 PUT */
    var communityNickname: String
        get() = sp.getString(K_COMMUNITY_NICK, "") ?: ""
        set(value) = putString(K_COMMUNITY_NICK, value)

    val communityReady: Boolean
        get() = communityEnabled && communityUrl.isNotBlank()

    // ---------- 高德地图（本地直连，不经过后端） ----------

    /** Web 端（JS API）Key：用于 WebView 里加载地图 */
    var amapJsKey: String?
        get() = Crypto.decrypt(sp.getString(K_AMAP_JS_KEY, null))
        set(value) = putString(K_AMAP_JS_KEY, value?.takeIf { it.isNotBlank() }?.let { Crypto.encrypt(it) })

    /**
     * JS API 安全密钥（securityJsCode）。
     * 2021-12-02 之后申请的 JS API Key 必须配它，否则地图加载会报 INVALID_USER_SCODE。
     */
    var amapJsSecurityCode: String?
        get() = Crypto.decrypt(sp.getString(K_AMAP_JS_CODE, null))
        set(value) = putString(K_AMAP_JS_CODE, value?.takeIf { it.isNotBlank() }?.let { Crypto.encrypt(it) })

    /** Web 服务 Key：用于 restapi.amap.com 的周边搜索与坐标转换 */
    var amapWebKey: String?
        get() = Crypto.decrypt(sp.getString(K_AMAP_WEB_KEY, null))
        set(value) = putString(K_AMAP_WEB_KEY, value?.takeIf { it.isNotBlank() }?.let { Crypto.encrypt(it) })

    /** 地图中心点（GCJ-02），定位失败时用。默认中大东校区附近 */
    var amapCenter: String
        get() = sp.getString(K_AMAP_CENTER, DEFAULT_CENTER)?.ifBlank { DEFAULT_CENTER } ?: DEFAULT_CENTER
        set(value) = putString(K_AMAP_CENTER, value.trim())

    /** 搜索半径（米） */
    var amapRadius: Int
        get() = sp.getInt(K_AMAP_RADIUS, 2000)
        set(value) = sp.edit().putInt(K_AMAP_RADIUS, value.coerceIn(200, 50000)).apply()

    /** 上次定位到的位置（GCJ-02），用于「我的」页展示 */
    var amapLastFix: String
        get() = sp.getString(K_AMAP_LAST_FIX, "") ?: ""
        set(value) = putString(K_AMAP_LAST_FIX, value)

    val mapReady: Boolean get() = !amapJsKey.isNullOrBlank()
    val searchReady: Boolean get() = !amapWebKey.isNullOrBlank()

    // ---------- 偏好与预算（SET-06） ----------

    var dailyBudgetFen: Long
        get() = sp.getLong(K_BUDGET, 5000L)
        set(value) = putLong(K_BUDGET, value)

    var preferences: List<String>
        get() = splitTags(sp.getString(K_PREFS, "") ?: "")
        set(value) = putString(K_PREFS, value.joinToString(","))

    var allergens: List<String>
        get() = splitTags(sp.getString(K_ALLERGENS, "") ?: "")
        set(value) = putString(K_ALLERGENS, value.joinToString(","))

    // ---------- 引导与隐私（ONB / 12.2） ----------

    var onboarded: Boolean
        get() = sp.getBoolean(K_ONBOARDED, false)
        set(value) = putBool(K_ONBOARDED, value)

    var ttsEnabled: Boolean
        get() = sp.getBoolean(K_TTS, true)
        set(value) = putBool(K_TTS, value)

    var ttsRate: Float
        get() = sp.getFloat(K_TTS_RATE, 1.0f)
        set(value) = sp.edit().putFloat(K_TTS_RATE, value).apply()

    fun clearAll() = sp.edit().clear().apply()

    private fun splitTags(raw: String): List<String> =
        raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }

    private companion object {
        const val DEFAULT_VISION_MODEL = "deepseek-flash"
        const val K_UID = "uid"
        const val K_NAME = "display_name"
        const val K_BASE_URL = "model_base_url"
        const val K_MODEL = "model_name"
        const val K_API_KEY = "model_api_key"
        const val K_TEMP = "model_temperature"
        const val K_MAX_TOKENS = "model_max_tokens"
        const val K_TIMEOUT = "model_timeout"
        const val K_ALLOW_HTTP = "allow_http"
        const val K_COMMUNITY_ON = "community_enabled"
        const val K_COMMUNITY_URL = "community_url"
        const val K_COMMUNITY_SESSION = "community_session"
        const val K_COMMUNITY_NICK = "community_nickname"
        const val K_BUDGET = "daily_budget_fen"
        const val K_PREFS = "preferences"
        const val K_ALLERGENS = "allergens"
        const val K_ONBOARDED = "onboarded"
        const val K_TTS = "tts_enabled"
        const val K_TTS_RATE = "tts_rate"
        const val K_VISION_BASE = "vision_base_url"
        const val K_VISION_KEY = "vision_api_key"
        const val K_VISION_MODEL = "vision_model"
        const val K_AMAP_JS_KEY = "amap_js_key"
        const val K_AMAP_JS_CODE = "amap_js_code"
        const val K_AMAP_WEB_KEY = "amap_web_key"
        const val K_AMAP_CENTER = "amap_center"
        const val K_AMAP_RADIUS = "amap_radius"
        const val K_AMAP_LAST_FIX = "amap_last_fix"
        /** 中山大学东校区附近（GCJ-02） */
        const val DEFAULT_CENTER = "113.398000,23.097000"
    }
}
