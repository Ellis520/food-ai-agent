package com.foodmoment.app.net

import com.foodmoment.app.core.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * 社区客户端（对接 food-ai-agent 后端）。
 *
 * 该后端为网页版设计，不可更改，因此所有适配都在客户端：
 *  - 身份是匿名会话 cookie（fm_session），没有账号
 *  - 响应信封 {data, source}，错误是 {detail} + HTTP 状态码
 *  - 收藏、评分、标签等概念不存在
 */
class CommunityClient(private val settings: Settings) {

    data class Post(
        val id: Long,
        val author: String,
        val category: String,
        val food: String,
        val place: String,
        val meetup: String,
        val text: String,
        val createdAt: String,
        val mine: Boolean,
        val imageCount: Int,
        val replyCount: Int
    )

    data class Reply(
        val id: Long,
        val author: String,
        val text: String,
        val createdAt: String,
        val mine: Boolean
    )

    data class Page<T>(val items: List<T>, val next: Long?)

    sealed interface Outcome<out T> {
        data class Ok<T>(val value: T) : Outcome<T>
        data class Fail(val message: String, val status: Int = 0) : Outcome<Nothing>
    }

    private val http: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(90, TimeUnit.SECONDS)
            .build()
    }

    // ------------------------------------------------------------------ 会话

    /** 首次使用或会话失效后握手，拿到 fm_session。 */
    suspend fun handshake(): Outcome<Unit> = withContext(Dispatchers.IO) {
        val base = baseOrNull() ?: return@withContext Outcome.Fail("未配置社区服务端地址")
        try {
            val request = Request.Builder().url("$base/api/state").get().build()
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Outcome.Fail("服务端不可达：HTTP ${response.code}", response.code)
                }
                val token = sessionFromCookies(response.headers("Set-Cookie"))
                if (token.isNullOrBlank()) {
                    return@withContext Outcome.Fail("服务端没有下发会话 cookie")
                }
                settings.communitySession = token
                Outcome.Ok(Unit)
            }
        } catch (e: Exception) {
            Outcome.Fail("无法连接社区服务端：${e.message ?: e.javaClass.simpleName}")
        }
    }

    /** 确保有可用会话；没有就握手。 */
    suspend fun ensureSession(): Outcome<Unit> {
        if (!settings.communityReady) return Outcome.Fail("未启用社区或未填写地址")
        if (settings.communitySession != null) return Outcome.Ok(Unit)
        return handshake()
    }

    /** 清空本地会话（"重置社区身份"用）。 */
    fun resetSession() {
        settings.communitySession = null
        settings.communityNickname = ""
    }

    /** 连通性检查（不需要会话） */
    suspend fun health(): Outcome<Boolean> = withContext(Dispatchers.IO) {
        val base = baseOrNull() ?: return@withContext Outcome.Fail("未配置社区服务端地址")
        try {
            val request = Request.Builder().url("$base/api/health").get().build()
            http.newCall(request).execute().use { response ->
                if (response.isSuccessful) Outcome.Ok(true)
                else Outcome.Fail("HTTP ${response.code}", response.code)
            }
        } catch (e: Exception) {
            Outcome.Fail("无法连接：${e.message ?: e.javaClass.simpleName}")
        }
    }

    // ------------------------------------------------------------------ 帖子

    suspend fun listPosts(
        category: String? = null,
        query: String = "",
        before: Long? = null
    ): Outcome<Page<Post>> = call("帖子列表") { base ->
        val url = buildString {
            append(base).append("/api/community/posts")
            val params = buildList {
                category?.takeIf { it.isNotBlank() }?.let { add("category=$it") }
                query.takeIf { it.isNotBlank() }?.let { add("q=${java.net.URLEncoder.encode(it, "UTF-8")}") }
                before?.let { add("before=$it") }
            }
            if (params.isNotEmpty()) append("?").append(params.joinToString("&"))
        }
        val response = get(url)
        val root = JSONObject(response)
        val data = root.optJSONObject("data") ?: return@call Outcome.Fail("返回格式不正确")
        val array = data.optJSONArray("items") ?: JSONArray()
        val items = buildList {
            for (i in 0 until array.length()) {
                array.optJSONObject(i)?.let { add(it.toPost()) }
            }
        }
        Outcome.Ok(Page(items, if (data.isNull("next")) null else data.optLong("next")))
    }

    suspend fun createPost(
        requestId: String,
        category: String,
        text: String,
        food: String,
        place: String,
        meetup: String,
        imagesBase64: List<String>
    ): Outcome<Post> = call("发布") { base ->
        val payload = JSONObject().apply {
            put("requestId", requestId)
            put("category", category)
            put("text", text)
            put("food", food)
            put("place", place)
            put("meetup", meetup)
            put("images", JSONArray(imagesBase64))
        }
        val response = postJson("$base/api/community/posts", payload.toString())
        val data = JSONObject(response).optJSONObject("data")
            ?: return@call Outcome.Fail("服务端未返回帖子")
        Outcome.Ok(data.toPost())
    }

    suspend fun deletePost(id: Long): Outcome<Unit> = call("删除帖子") { base ->
        val response = delete("$base/api/community/posts/$id")
        if (response.isNullOrBlank()) return@call Outcome.Fail("服务端未返回内容")
        Outcome.Ok(Unit)
    }

    // ------------------------------------------------------------------ 回复

    suspend fun listReplies(postId: Long, before: Long? = null): Outcome<Page<Reply>> =
        call("回复列表") { base ->
            val url = buildString {
                append(base).append("/api/community/posts/").append(postId).append("/replies")
                if (before != null) append("?before=").append(before)
            }
            val root = JSONObject(get(url))
            val data = root.optJSONObject("data") ?: return@call Outcome.Fail("返回格式不正确")
            val array = data.optJSONArray("items") ?: JSONArray()
            val items = buildList {
                for (i in 0 until array.length()) {
                    array.optJSONObject(i)?.let { add(it.toReply()) }
                }
            }
            Outcome.Ok(Page(items, if (data.isNull("next")) null else data.optLong("next")))
        }

    suspend fun createReply(postId: Long, requestId: String, text: String): Outcome<Reply> =
        call("回复") { base ->
            val payload = JSONObject().put("requestId", requestId).put("text", text)
            val response = postJson("$base/api/community/posts/$postId/replies", payload.toString())
            val data = JSONObject(response).optJSONObject("data")
                ?: return@call Outcome.Fail("服务端未返回回复")
            Outcome.Ok(data.toReply())
        }

    suspend fun deleteReply(postId: Long, replyId: Long): Outcome<Unit> = call("删除回复") { base ->
        delete("$base/api/community/posts/$postId/replies/$replyId")
        Outcome.Ok(Unit)
    }

    // ------------------------------------------------------------------ 图片

    /**
     * 社区图片必须带会话才能取，且服务端返回 no-store，所以必须自己落盘缓存。
     */
    suspend fun imageBytes(postId: Long, position: Int): ByteArray? = withContext(Dispatchers.IO) {
        val base = baseOrNull() ?: return@withContext null
        val cookie = settings.communitySession ?: return@withContext null
        try {
            val request = Request.Builder()
                .url("$base/api/community/posts/$postId/images/$position")
                .header("Cookie", "fm_session=$cookie")
                .get()
                .build()
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) null else response.body?.bytes()
            }
        } catch (e: Exception) {
            null
        }
    }

    // ------------------------------------------------------------------ 昵称

    /**
     * 把 App 昵称写进服务端 state.profile.name。
     * 采用「读改写」：先取回整份 state，只改 name 再写回，避免覆盖服务端其他数据。
     */
    suspend fun syncNickname(name: String): Outcome<Unit> = withContext(Dispatchers.IO) {
        val trimmed = name.trim().take(20)
        if (trimmed.isEmpty()) return@withContext Outcome.Fail("昵称不能为空")
        if (!settings.communityReady) return@withContext Outcome.Fail("未启用社区")

        when (val ensured = ensureSession()) {
            is Outcome.Fail -> return@withContext ensured
            is Outcome.Ok -> Unit
        }

        val base = baseOrNull() ?: return@withContext Outcome.Fail("未配置社区服务端地址")
        try {
            val current = get("$base/api/state")
            val root = JSONObject(current)
            val data = root.optJSONObject("data") ?: return@withContext Outcome.Fail("state 格式不正确")
            val revision = data.optLong("revision")
            val state = data.optJSONObject("state") ?: JSONObject()
            val profile = state.optJSONObject("profile") ?: JSONObject()
            profile.put("name", trimmed)
            state.put("profile", profile)

            val payload = JSONObject().put("revision", revision).put("state", state)
            postJson("$base/api/state", payload.toString(), method = "PUT")
            settings.communityNickname = trimmed
            Outcome.Ok(Unit)
        } catch (e: CommunityException) {
            Outcome.Fail(e.message ?: "同步昵称失败", e.status)
        } catch (e: Exception) {
            Outcome.Fail("同步昵称失败：${e.message ?: e.javaClass.simpleName}")
        }
    }

    // ------------------------------------------------------------------ 内部

    private class CommunityException(message: String, val status: Int) : Exception(message)

    private fun baseOrNull(): String? {
        val base = settings.communityUrl
        if (base.isBlank()) return null
        if (base.startsWith("http://", true) && !settings.allowHttp) return null
        return base
    }

    private fun sessionFromCookies(headers: List<String>): String? {
        headers.forEach { raw ->
            val first = raw.substringBefore(';')
            val name = first.substringBefore('=')
            if (name.trim() == "fm_session") {
                return first.substringAfter('=', "").trim().ifBlank { null }
            }
        }
        return null
    }

    private fun newCall(url: String, method: String, body: okhttp3.RequestBody?): String {
        val builder = Request.Builder().url(url).header("Accept", "application/json")
        settings.communitySession?.let { builder.header("Cookie", "fm_session=$it") }
        when (method) {
            "POST" -> builder.post(body ?: "{}".toRequestBody(JSON_MEDIA))
            "PUT" -> builder.put(body ?: "{}".toRequestBody(JSON_MEDIA))
            "DELETE" -> builder.delete()
            else -> builder.get()
        }
        http.newCall(builder.build()).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val detail = runCatching { JSONObject(text).optString("detail") }.getOrNull()
                throw CommunityException(
                    detail?.takeIf { it.isNotBlank() } ?: "HTTP ${response.code}",
                    response.code
                )
            }
            return text
        }
    }

    private fun get(url: String): String = newCall(url, "GET", null)

    private fun delete(url: String): String = newCall(url, "DELETE", null)

    private fun postJson(url: String, json: String, method: String = "POST"): String =
        newCall(url, method, json.toRequestBody(JSON_MEDIA))

    /**
     * 统一处理：401 重新握手一次，其余错误转成可读消息。
     *
     * 注意：OkHttp 的 execute() 是阻塞调用，必须全程在 IO 线程上执行，
     * 否则会抛 NetworkOnMainThreadException。
     */
    private suspend fun <T> call(label: String, block: suspend (base: String) -> Outcome<T>): Outcome<T> =
        withContext(Dispatchers.IO) {
            if (!settings.communityReady) return@withContext Outcome.Fail("未启用社区或未填写地址")
            val base = baseOrNull() ?: return@withContext Outcome.Fail("已禁止明文 HTTP，请在设置中允许")

            var attempt = 0
            while (attempt <= 1) {
                try {
                    when (val ensured = ensureSession()) {
                        is Outcome.Fail -> return@withContext ensured
                        is Outcome.Ok -> Unit
                    }
                    return@withContext block(base)
                } catch (e: CommunityException) {
                    if (e.status == 401 && attempt == 0) {
                        settings.communitySession = null
                        attempt++
                        continue
                    }
                    return@withContext Outcome.Fail("${label}失败：${e.message}", e.status)
                } catch (e: java.net.SocketTimeoutException) {
                    return@withContext Outcome.Fail("${label}超时，可重试")
                } catch (e: Exception) {
                    return@withContext Outcome.Fail("${label}失败：${e.message ?: e.javaClass.simpleName}")
                }
            }
            Outcome.Fail("${label}失败：未知错误")
        }

    private fun JSONObject.toPost(): Post = Post(
        id = optLong("id"),
        author = optString("author"),
        category = optString("category"),
        food = optString("food"),
        place = optString("place"),
        meetup = optString("meetup"),
        text = optString("text"),
        createdAt = optString("createdAt"),
        mine = optBoolean("mine"),
        imageCount = optInt("imageCount"),
        replyCount = optInt("replyCount")
    )

    private fun JSONObject.toReply(): Reply = Reply(
        id = optLong("id"),
        author = optString("author"),
        text = optString("text"),
        createdAt = optString("createdAt"),
        mine = optBoolean("mine")
    )

    companion object {
        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

        fun newRequestId(): String = UUID.randomUUID().toString()
    }
}
