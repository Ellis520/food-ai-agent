package com.foodmoment.app.data

import android.app.Application
import android.net.Uri
import android.util.Base64
import com.foodmoment.app.core.MealType
import com.foodmoment.app.core.Settings
import com.foodmoment.app.core.Time
import com.foodmoment.app.net.CommunityClient
import com.foodmoment.app.net.LlmClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * 唯一的数据出入口。UI 只与本类交互，不直接碰 SQLite。
 *
 * 跨页面刷新用 [revision]：任何写操作后自增，页面观察到变化就重新加载。
 */
class Repository(
    private val app: Application,
    private val db: FoodDatabase,
    val settings: Settings,
    val llm: LlmClient,
    val community: CommunityClient
) {

    val images = ImageStore(app)

    private val _revision = MutableStateFlow(0)
    val revision: StateFlow<Int> = _revision.asStateFlow()

    @Volatile
    private var foodCache: List<Food>? = null

    private fun bump() {
        _revision.value = _revision.value + 1
    }

    private suspend fun <T> io(block: suspend () -> T): T = withContext(Dispatchers.IO) { block() }

    // ------------------------------------------------------------------ 食物库

    suspend fun foods(): List<Food> = io {
        foodCache ?: db.foods().also { foodCache = it }
    }

    suspend fun searchFoods(query: String, limit: Int = 30): List<Food> = io {
        val all = foodCache ?: db.foods().also { foodCache = it }
        if (query.isBlank()) all.take(limit) else all.filter { it.matches(query) }.take(limit)
    }

    suspend fun findFoodByName(name: String): Food? = io {
        val all = foodCache ?: db.foods().also { foodCache = it }
        val n = name.trim()
        if (n.isEmpty()) return@io null
        all.firstOrNull { it.nameZh == n || it.name.equals(n, ignoreCase = true) }
            ?: all.firstOrNull { food ->
                food.aliases.split(",", "，").any { it.isNotBlank() && (n.contains(it) || it.contains(n)) }
            }
            ?: all.firstOrNull { it.nameZh.length >= 2 && n.contains(it.nameZh) }
    }

    suspend fun addCustomFood(food: Food) {
        io { db.upsertFood(food) }
        foodCache = null
        bump()
    }

    suspend fun deleteCustomFood(id: String) {
        io { db.deleteCustomFood(id) }
        foodCache = null
        bump()
    }

    // ------------------------------------------------------------------ 菜单

    @Volatile
    private var menuCache: List<MenuItem>? = null

    suspend fun menuItems(): List<MenuItem> = io {
        menuCache ?: db.menuItems().also { menuCache = it }
    }

    suspend fun searchMenu(query: String, limit: Int = 40): List<MenuItem> = io {
        val all = menuCache ?: db.menuItems().also { menuCache = it }
        if (query.isBlank()) {
            all.take(limit)
        } else {
            val keyword = query.trim()
            all.filter {
                it.name.contains(keyword, ignoreCase = true) ||
                    it.category.contains(keyword, ignoreCase = true)
            }.take(limit)
        }
    }

    suspend fun addMenuItem(item: MenuItem) {
        io { db.upsertMenuItem(item) }
        menuCache = null
        bump()
    }

    suspend fun deleteMenuItem(id: String) {
        io { db.deleteMenuItem(id) }
        menuCache = null
        bump()
    }

    suspend fun menuCount(): Int = io { db.menuCount() }

    // ------------------------------------------------------------------ 计划

    suspend fun plansOfDay(day: String): List<Plan> = io { db.plansForDate(day) }

    suspend fun planById(id: String): Plan? = io { db.planById(id) }

    suspend fun savePlan(plan: Plan) {
        io { db.upsertPlan(plan) }
        bump()
    }

    suspend fun confirmPlan(id: String) {
        io {
            val current = db.planById(id) ?: return@io
            db.updatePlanStatus(id, "saved", current.version + 1)
        }
        bump()
    }

    suspend fun deletePlan(id: String) {
        io { db.deletePlan(id) }
        bump()
    }

    /** 用离线模板生成草稿（PLAN-08）。金额留空，由用户补填，不臆造价格。 */
    suspend fun buildTemplatePlan(template: TemplatePlan, day: String): Plan {
        val all = foods().associateBy { it.id }
        val planId = "plan_" + UUID.randomUUID().toString().take(8)
        val items = template.meals.flatMap { (meal, list) ->
            list.map { entry ->
                val food = all[entry.foodId]
                val factor = entry.grams * (food?.edibleRatio ?: 1.0) / 100.0
                PlanItem(
                    id = "pi_" + UUID.randomUUID().toString().take(8),
                    planId = planId,
                    mealType = meal,
                    foodId = entry.foodId,
                    name = food?.displayName ?: entry.name,
                    grams = entry.grams,
                    costFen = null,
                    kcal = food?.kcal?.let { it * factor },
                    protein = food?.protein?.let { it * factor },
                    fat = food?.fat?.let { it * factor },
                    carbs = food?.carbs?.let { it * factor }
                )
            }
        }
        val plan = Plan(
            id = planId,
            date = day,
            budgetFen = template.budgetFen,
            status = "draft",
            source = "template",
            version = 1,
            createdAt = Time.now(),
            items = items
        )
        io { db.upsertPlan(plan) }
        bump()
        return plan
    }

    // ------------------------------------------------------------------ 记录

    suspend fun recordsOfDay(day: String): List<DietRecord> =
        io { db.recordsBetween(Time.dayStart(day), Time.dayEnd(day)) }

    suspend fun recordsBetween(fromDay: String, toDay: String): List<DietRecord> =
        io { db.recordsBetween(Time.dayStart(fromDay), Time.dayEnd(toDay)) }

    suspend fun allRecords(): List<DietRecord> = io { db.allRecords() }

    suspend fun saveRecord(record: DietRecord) {
        io { db.upsertRecord(record) }
        bump()
    }

    suspend fun deleteRecord(id: String) {
        io { db.setRecordDeleted(id, Time.now()) }
        bump()
    }

    suspend fun restoreRecord(id: String) {
        io { db.setRecordDeleted(id, null) }
        bump()
    }

    suspend fun purgeTrash() {
        io { db.trashedRecords().forEach { db.hardDeleteRecord(it.id) } }
        bump()
    }

    /** 回收站保留 7 天（12.4） */
    suspend fun purgeExpiredTrash() {
        io { db.purgeExpiredTrash(Time.now() - 7L * 24 * 3600 * 1000) }
    }

    suspend fun trashed(): List<DietRecord> = io { db.trashedRecords() }

    /** 计划转记录（PLAN-06）：同一计划的同一餐只生成一次 */
    suspend fun recordFromPlan(plan: Plan, mealType: String): DietRecord? {
        val existing = recordsOfDay(plan.date).firstOrNull {
            it.sourcePlanId == plan.id && it.mealType == mealType
        }
        if (existing != null) return null

        val recordId = "rec_" + UUID.randomUUID().toString().take(8)
        val items = plan.itemsOf(mealType).map { pi ->
            val per100 = if (pi.grams > 0 && pi.kcal != null) pi.kcal * 100.0 / pi.grams else null
            val p100 = if (pi.grams > 0 && pi.protein != null) pi.protein * 100.0 / pi.grams else null
            val f100 = if (pi.grams > 0 && pi.fat != null) pi.fat * 100.0 / pi.grams else null
            val c100 = if (pi.grams > 0 && pi.carbs != null) pi.carbs * 100.0 / pi.grams else null
            RecordItem(
                id = "ri_" + UUID.randomUUID().toString().take(8),
                recordId = recordId,
                foodId = pi.foodId,
                name = pi.name,
                grams = pi.grams,
                kcal100 = per100,
                protein100 = p100,
                fat100 = f100,
                carbs100 = c100
            )
        }
        val record = DietRecord(
            id = recordId,
            eatenAt = Time.dayStart(plan.date) + mealHour(mealType) * 3600_000L,
            mealType = mealType,
            costFen = plan.itemsOf(mealType).mapNotNull { it.costFen }.takeIf { it.isNotEmpty() }?.sum(),
            note = "来自计划",
            sourcePlanId = plan.id,
            deletedAt = null,
            source = "plan",
            items = items
        )
        io { db.upsertRecord(record) }
        bump()
        return record
    }

    private fun mealHour(mealType: String): Int = when (mealType) {
        MealType.BREAKFAST -> 8
        MealType.LUNCH -> 12
        MealType.DINNER -> 18
        else -> 15
    }

    // ------------------------------------------------------------------ 会话

    suspend fun sessions(): List<ChatSession> = io { db.sessions() }

    suspend fun createSession(title: String = "新会话"): ChatSession = io {
        val now = Time.now()
        val session = ChatSession(
            id = "s_" + UUID.randomUUID().toString().take(8),
            title = title,
            createdAt = now,
            updatedAt = now,
            modelSnapshot = settings.modelName
        )
        db.insertSession(session)
        session
    }

    suspend fun renameSession(id: String, title: String) {
        io { db.renameSession(id, title) }
        bump()
    }

    suspend fun deleteSession(id: String) {
        io { db.deleteSession(id) }
        bump()
    }

    suspend fun messages(sessionId: String): List<ChatMessage> = io { db.messages(sessionId) }

    suspend fun saveMessage(message: ChatMessage) {
        io {
            db.insertMessage(message)
            db.touchSession(message.sessionId, message.createdAt)
        }
        bump()
    }

    suspend fun deleteMessage(id: String) {
        io { db.deleteMessage(id) }
        bump()
    }

    // ------------------------------------------------------------------ 社区

    data class CommunityResult(val ok: Boolean, val message: String = "", val added: Int = 0)

    private val communityOk = CommunityResult(true)

    suspend fun cachedCommunityPosts(category: String?, query: String): List<CommunityPost> =
        io { db.cachedPosts(category, query) }

    suspend fun cachedCommunityPost(id: Long): CommunityPost? = io { db.communityPost(id) }

    suspend fun cachedReplies(postId: Long): List<CommunityReply> = io { db.cachedReplies(postId) }

    suspend fun drafts(): List<CommunityDraft> = io { db.drafts() }

    /** 拉一页（before=null 表示刷新首页），写入本地缓存 */
    suspend fun fetchCommunity(category: String?, query: String, before: Long? = null): CommunityResult {
        if (!settings.communityReady) return CommunityResult(false, "未启用社区或未填写地址")
        return when (val outcome = community.listPosts(category, query, before)) {
            is CommunityClient.Outcome.Fail -> CommunityResult(false, outcome.message)
            is CommunityClient.Outcome.Ok -> {
                val page = outcome.value
                io {
                    db.upsertPosts(page.items.map { it.toLocal() })
                    db.prunePosts(200)
                }
                bump()
                CommunityResult(true, added = page.items.size)
            }
        }
    }

    /** 发帖：图片先压缩落盘，再上传；失败保留草稿，联网后可补发 */
    suspend fun publishCommunity(
        category: String,
        text: String,
        food: String,
        place: String,
        meetup: String,
        imageUris: List<Uri>
    ): CommunityResult {
        if (!settings.communityReady) return CommunityResult(false, "未启用社区或未填写地址")
        if (text.isBlank()) return CommunityResult(false, "正文不能为空")

        val draftId = "d_" + UUID.randomUUID().toString().take(8)
        // 压缩与落盘是阻塞 IO，必须离开主线程
        val saved = io {
            imageUris.take(3).mapIndexedNotNull { index, uri ->
                images.saveCommunityImage(uri, draftId, index)?.name
            }
        }

        val draft = CommunityDraft(
            id = draftId,
            requestId = CommunityClient.newRequestId(),
            category = category,
            food = food.trim().take(60),
            place = place.trim().take(80),
            meetup = meetup.trim().take(60),
            text = text.trim().take(600),
            imagePaths = saved,
            createdAt = Time.now()
        )
        io { db.upsertDraft(draft) }
        bump()
        return sendDraft(draft)
    }

    private suspend fun sendDraft(draft: CommunityDraft): CommunityResult {
        // 读图转 Base64 是阻塞 IO，放到 IO 线程
        val encoded = io {
            draft.imagePaths.mapNotNull { name ->
                val file = images.outboxFile(name)
                if (file.exists()) Base64.encodeToString(file.readBytes(), Base64.NO_WRAP) else null
            }
        }

        return when (val outcome = community.createPost(
            requestId = draft.requestId,
            category = draft.category,
            text = draft.text,
            food = draft.food,
            place = draft.place,
            meetup = draft.meetup,
            imagesBase64 = encoded
        )) {
            is CommunityClient.Outcome.Fail -> {
                io { db.upsertDraft(draft.copy(lastError = outcome.message)) }
                bump()
                CommunityResult(false, outcome.message)
            }

            is CommunityClient.Outcome.Ok -> {
                io {
                    db.deleteDraft(draft.id)
                    db.upsertPosts(listOf(outcome.value.toLocal()))
                }
                images.deleteOutboxPrefix("${draft.id}_")
                bump()
                communityOk
            }
        }
    }

    /** 补发所有离线草稿 */
    suspend fun flushDrafts(): CommunityResult {
        val pending = drafts()
        if (pending.isEmpty()) return communityOk
        var failed = 0
        var lastError = ""
        pending.forEach { draft ->
            val result = sendDraft(draft)
            if (!result.ok) {
                failed++
                lastError = result.message
            }
        }
        return if (failed == 0) communityOk else CommunityResult(false, "$failed 条未发出：$lastError")
    }

    suspend fun deleteCommunityPost(post: CommunityPost): CommunityResult =
        when (val outcome = community.deletePost(post.id)) {
            is CommunityClient.Outcome.Fail -> CommunityResult(false, outcome.message)
            is CommunityClient.Outcome.Ok -> {
                io { db.deletePostCache(post.id) }
                for (index in 0 until post.imageCount) {
                    runCatching { images.communityCacheFile(post.id, index).delete() }
                }
                bump()
                communityOk
            }
        }

    suspend fun fetchReplies(postId: Long, before: Long? = null): CommunityResult =
        when (val outcome = community.listReplies(postId, before)) {
            is CommunityClient.Outcome.Fail -> CommunityResult(false, outcome.message)
            is CommunityClient.Outcome.Ok -> {
                io { db.upsertReplies(outcome.value.items.map { it.toLocal(postId) }) }
                bump()
                communityOk
            }
        }

    suspend fun addReply(postId: Long, text: String): CommunityResult {
        if (text.isBlank()) return CommunityResult(false, "回复不能为空")
        val requestId = CommunityClient.newRequestId()
        return when (val outcome = community.createReply(postId, requestId, text.trim().take(300))) {
            is CommunityClient.Outcome.Fail -> CommunityResult(false, outcome.message)
            is CommunityClient.Outcome.Ok -> {
                io { db.upsertReplies(listOf(outcome.value.toLocal(postId))) }
                bump()
                communityOk
            }
        }
    }

    suspend fun deleteReply(postId: Long, replyId: Long): CommunityResult =
        when (val outcome = community.deleteReply(postId, replyId)) {
            is CommunityClient.Outcome.Fail -> CommunityResult(false, outcome.message)
            is CommunityClient.Outcome.Ok -> {
                io { db.deleteReplyCache(postId, replyId) }
                bump()
                communityOk
            }
        }

    /** 把 App 昵称同步到服务端 state.profile.name（读改写，不覆盖其他数据） */
    suspend fun ensureNicknameSynced(force: Boolean = false): CommunityResult {
        if (!settings.communityReady) return CommunityResult(false, "未启用社区")
        // 昵称还是默认的「我」时，用设备短 UID 生成一个不重复的署名
        if (settings.displayNameIsDefault) {
            settings.displayName = settings.suggestedGuestName
        }
        val name = settings.displayName.trim()
        if (name.isBlank()) return CommunityResult(false, "昵称不能为空")
        if (!force && name == settings.communityNickname) return communityOk
        return when (val outcome = community.syncNickname(name)) {
            is CommunityClient.Outcome.Fail -> CommunityResult(false, outcome.message)
            is CommunityClient.Outcome.Ok -> communityOk
        }
    }

    /** 重置社区身份：清掉会话与已同步昵称，下次访问重新握手 */
    fun resetCommunityIdentity() {
        community.resetSession()
        bump()
    }

    private fun CommunityClient.Post.toLocal() = CommunityPost(
        id = id,
        author = author,
        category = category,
        food = food,
        place = place,
        meetup = meetup,
        text = text,
        createdAt = createdAt,
        mine = mine,
        imageCount = imageCount,
        replyCount = replyCount,
        cachedAt = Time.now()
    )

    private fun CommunityClient.Reply.toLocal(postId: Long) = CommunityReply(
        id = id,
        postId = postId,
        author = author,
        text = text,
        createdAt = createdAt,
        mine = mine,
        cachedAt = Time.now()
    )

    // ------------------------------------------------------------------ 维护

    suspend fun clearAllData() {
        io {
            db.clearUserData()
            images.clearCommunityCache()
        }
        foodCache = null
        bump()
    }
}

private fun Food.matches(query: String): Boolean {
    val q = query.trim()
    if (q.isEmpty()) return true
    return nameZh.contains(q, ignoreCase = true) ||
        name.contains(q, ignoreCase = true) ||
        aliases.split(",", "，").any { it.isNotBlank() && it.contains(q, ignoreCase = true) }
}
