package com.foodmoment.app.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * 本地数据库（SQLiteOpenHelper 手写实现）。
 *
 * 说明：方案文档写的是 Room，但 Room 依赖 KSP 注解处理，而本工程使用 AGP 9 的内置 Kotlin，
 * 注解处理器存在兼容风险。为了一次编译通过，这里手写等价的表结构与 DAO，语义与 10.1 一致。
 */
class FoodDatabase(context: Context) :
    SQLiteOpenHelper(context.applicationContext, NAME, null, VERSION) {

    private val appContext: Context = context.applicationContext

    override fun onConfigure(db: SQLiteDatabase) {
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE foods(
                id TEXT PRIMARY KEY, name TEXT NOT NULL, name_zh TEXT,
                aliases TEXT, kcal REAL, protein REAL, fat REAL, carbs REAL,
                edible_ratio REAL, state TEXT, fdc_id INTEGER, data_type TEXT,
                source_id TEXT, is_custom INTEGER NOT NULL DEFAULT 0)"""
        )
        db.execSQL(
            """CREATE TABLE sources(
                id TEXT PRIMARY KEY, title TEXT, publisher TEXT, url TEXT,
                version TEXT, retrieved_at TEXT, license_note TEXT)"""
        )
        db.execSQL(
            """CREATE TABLE plans(
                id TEXT PRIMARY KEY, date TEXT NOT NULL, budget_fen INTEGER NOT NULL,
                status TEXT NOT NULL, source TEXT NOT NULL, version INTEGER NOT NULL,
                created_at INTEGER NOT NULL)"""
        )
        db.execSQL(
            """CREATE TABLE plan_items(
                id TEXT PRIMARY KEY, plan_id TEXT NOT NULL, meal_type TEXT NOT NULL,
                food_id TEXT, name TEXT NOT NULL, grams REAL NOT NULL, cost_fen INTEGER,
                kcal REAL, protein REAL, fat REAL, carbs REAL)"""
        )
        db.execSQL(
            """CREATE TABLE records(
                id TEXT PRIMARY KEY, eaten_at INTEGER NOT NULL, meal_type TEXT NOT NULL,
                cost_fen INTEGER, note TEXT, source_plan_id TEXT, deleted_at INTEGER,
                source TEXT NOT NULL DEFAULT 'manual')"""
        )
        db.execSQL(
            """CREATE TABLE record_items(
                id TEXT PRIMARY KEY, record_id TEXT NOT NULL, food_id TEXT,
                name TEXT NOT NULL, grams REAL NOT NULL, kcal100 REAL,
                protein100 REAL, fat100 REAL, carbs100 REAL)"""
        )
        db.execSQL(
            """CREATE TABLE sessions(
                id TEXT PRIMARY KEY, title TEXT NOT NULL, created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL, model_snapshot TEXT)"""
        )
        db.execSQL(
            """CREATE TABLE messages(
                id TEXT PRIMARY KEY, session_id TEXT NOT NULL, role TEXT NOT NULL,
                content TEXT NOT NULL, cards_json TEXT, request_id TEXT,
                created_at INTEGER NOT NULL, status TEXT NOT NULL, detail TEXT)"""
        )
        db.execSQL(
            """CREATE TABLE community_posts(
                id INTEGER PRIMARY KEY, author TEXT NOT NULL, category TEXT NOT NULL,
                food TEXT NOT NULL DEFAULT '', place TEXT NOT NULL DEFAULT '',
                meetup TEXT NOT NULL DEFAULT '', text TEXT NOT NULL,
                created_at TEXT NOT NULL, mine INTEGER NOT NULL DEFAULT 0,
                image_count INTEGER NOT NULL DEFAULT 0,
                reply_count INTEGER NOT NULL DEFAULT 0,
                cached_at INTEGER NOT NULL DEFAULT 0)"""
        )
        db.execSQL(
            """CREATE TABLE community_replies(
                id INTEGER PRIMARY KEY, post_id INTEGER NOT NULL,
                author TEXT NOT NULL, text TEXT NOT NULL, created_at TEXT NOT NULL,
                mine INTEGER NOT NULL DEFAULT 0, cached_at INTEGER NOT NULL DEFAULT 0)"""
        )
        db.execSQL(
            """CREATE TABLE community_drafts(
                id TEXT PRIMARY KEY, request_id TEXT NOT NULL, category TEXT NOT NULL,
                food TEXT NOT NULL DEFAULT '', place TEXT NOT NULL DEFAULT '',
                meetup TEXT NOT NULL DEFAULT '', text TEXT NOT NULL,
                image_paths TEXT NOT NULL DEFAULT '', created_at INTEGER NOT NULL,
                last_error TEXT)"""
        )

        db.execSQL(
            """CREATE TABLE menu_items(
                id TEXT PRIMARY KEY, name TEXT NOT NULL, unit TEXT,
                grams REAL, grams_text TEXT, price_fen INTEGER,
                category TEXT, is_custom INTEGER NOT NULL DEFAULT 0)"""
        )

        db.execSQL("CREATE INDEX idx_records_eaten ON records(eaten_at)")
        db.execSQL("CREATE INDEX idx_records_meal ON records(meal_type, eaten_at)")
        db.execSQL("CREATE INDEX idx_messages_session ON messages(session_id, created_at)")
        db.execSQL("CREATE INDEX idx_plan_items_plan ON plan_items(plan_id)")
        db.execSQL("CREATE INDEX idx_record_items_record ON record_items(record_id)")
        db.execSQL("CREATE INDEX idx_replies_post ON community_replies(post_id, id)")
        db.execSQL("CREATE INDEX idx_posts_id ON community_posts(id)")
        db.execSQL("CREATE INDEX idx_menu_name ON menu_items(name)")

        installSeed(db)
        installMenu(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE records ADD COLUMN source TEXT NOT NULL DEFAULT 'manual'")
            db.execSQL(
                """CREATE TABLE IF NOT EXISTS menu_items(
                    id TEXT PRIMARY KEY, name TEXT NOT NULL, unit TEXT,
                    grams REAL, grams_text TEXT, price_fen INTEGER,
                    category TEXT, is_custom INTEGER NOT NULL DEFAULT 0)"""
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_menu_name ON menu_items(name)")
            installMenu(db)
        }
        if (oldVersion < 3) {
            // 旧美食圈后端已停用：帖子无法再同步，留着只会变成删不掉的僵尸数据
            db.execSQL("DROP TABLE IF EXISTS feed")
            db.execSQL(
                """CREATE TABLE IF NOT EXISTS community_posts(
                    id INTEGER PRIMARY KEY, author TEXT NOT NULL, category TEXT NOT NULL,
                    food TEXT NOT NULL DEFAULT '', place TEXT NOT NULL DEFAULT '',
                    meetup TEXT NOT NULL DEFAULT '', text TEXT NOT NULL,
                    created_at TEXT NOT NULL, mine INTEGER NOT NULL DEFAULT 0,
                    image_count INTEGER NOT NULL DEFAULT 0,
                    reply_count INTEGER NOT NULL DEFAULT 0,
                    cached_at INTEGER NOT NULL DEFAULT 0)"""
            )
            db.execSQL(
                """CREATE TABLE IF NOT EXISTS community_replies(
                    id INTEGER PRIMARY KEY, post_id INTEGER NOT NULL,
                    author TEXT NOT NULL, text TEXT NOT NULL, created_at TEXT NOT NULL,
                    mine INTEGER NOT NULL DEFAULT 0, cached_at INTEGER NOT NULL DEFAULT 0)"""
            )
            db.execSQL(
                """CREATE TABLE IF NOT EXISTS community_drafts(
                    id TEXT PRIMARY KEY, request_id TEXT NOT NULL, category TEXT NOT NULL,
                    food TEXT NOT NULL DEFAULT '', place TEXT NOT NULL DEFAULT '',
                    meetup TEXT NOT NULL DEFAULT '', text TEXT NOT NULL,
                    image_paths TEXT NOT NULL DEFAULT '', created_at INTEGER NOT NULL,
                    last_error TEXT)"""
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_replies_post ON community_replies(post_id, id)")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_posts_id ON community_posts(id)")
        }
    }

    private fun installSeed(db: SQLiteDatabase) {
        SeedFoods.sources.forEach { row ->
            db.insertWithOnConflict("sources", null, ContentValues().apply {
                put("id", row[0]); put("title", row[1]); put("publisher", row[2])
                put("url", row[3]); put("version", row[4]); put("retrieved_at", row[5])
                put("license_note", row[6])
            }, SQLiteDatabase.CONFLICT_REPLACE)
        }
        SeedFoods.items.forEach { f ->
            db.insertWithOnConflict("foods", null, f.toValues(), SQLiteDatabase.CONFLICT_REPLACE)
        }
    }

    /** 首次建库或迁移时导入内置菜单（assets/menu.md） */
    private fun installMenu(db: SQLiteDatabase) {
        val items = runCatching {
            appContext.assets.open("menu.md").bufferedReader().use { MenuParser.parse(it.readText()) }
        }.getOrDefault(emptyList())
        items.forEach { item ->
            db.insertWithOnConflict(
                "menu_items", null, item.toMenuValues(), SQLiteDatabase.CONFLICT_IGNORE
            )
        }
    }

    // ------------------------------------------------------------------ 事务

    private fun <T> transaction(block: (SQLiteDatabase) -> T): T {
        val db = writableDatabase
        db.beginTransaction()
        return try {
            val result = block(db)
            db.setTransactionSuccessful()
            result
        } finally {
            db.endTransaction()
        }
    }

    // ------------------------------------------------------------------ 食物

    fun foods(): List<Food> = readableDatabase.rawQuery("SELECT * FROM foods", null).use { c ->
        buildList { while (c.moveToNext()) add(c.toFood()) }
    }

    fun upsertFood(food: Food) {
        writableDatabase.insertWithOnConflict("foods", null, food.toValues(), SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun deleteCustomFood(id: String) {
        writableDatabase.delete("foods", "id=? AND is_custom=1", arrayOf(id))
    }

    // ------------------------------------------------------------------ 菜单

    fun menuItems(): List<MenuItem> =
        readableDatabase.rawQuery(
            "SELECT * FROM menu_items ORDER BY category, name", null
        ).use { c -> buildList { while (c.moveToNext()) add(c.toMenuItem()) } }

    fun menuItemExists(id: String): Boolean =
        readableDatabase.rawQuery("SELECT 1 FROM menu_items WHERE id=?", arrayOf(id)).use { it.moveToFirst() }

    fun upsertMenuItem(item: MenuItem) {
        writableDatabase.insertWithOnConflict(
            "menu_items", null, item.toMenuValues(), SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    fun deleteMenuItem(id: String) {
        writableDatabase.delete("menu_items", "id=? AND is_custom=1", arrayOf(id))
    }

    fun menuCount(): Int = readableDatabase
        .rawQuery("SELECT COUNT(*) FROM menu_items", null)
        .use { c -> if (c.moveToFirst()) c.getInt(0) else 0 }

    // ------------------------------------------------------------------ 计划

    fun plansForDate(date: String): List<Plan> =
        readableDatabase.rawQuery(
            "SELECT * FROM plans WHERE date=? ORDER BY created_at DESC",
            arrayOf(date)
        ).use { c -> buildList { while (c.moveToNext()) add(c.toPlan()) } }
            .map { it.copy(items = planItems(it.id)) }

    fun planById(id: String): Plan? =
        readableDatabase.rawQuery("SELECT * FROM plans WHERE id=?", arrayOf(id)).use { c ->
            if (c.moveToFirst()) c.toPlan().copy(items = planItems(id)) else null
        }

    fun allPlans(): List<Plan> =
        readableDatabase.rawQuery("SELECT * FROM plans ORDER BY date DESC", null).use { c ->
            buildList { while (c.moveToNext()) add(c.toPlan()) }
        }.map { it.copy(items = planItems(it.id)) }

    fun planExists(id: String): Boolean =
        readableDatabase.rawQuery("SELECT 1 FROM plans WHERE id=?", arrayOf(id)).use { it.moveToFirst() }

    fun recordExists(id: String): Boolean =
        readableDatabase.rawQuery("SELECT 1 FROM records WHERE id=?", arrayOf(id)).use { it.moveToFirst() }

    fun sessionExists(id: String): Boolean =
        readableDatabase.rawQuery("SELECT 1 FROM sessions WHERE id=?", arrayOf(id)).use { it.moveToFirst() }

    private fun planItems(planId: String): List<PlanItem> =
        readableDatabase.rawQuery(
            "SELECT * FROM plan_items WHERE plan_id=?",
            arrayOf(planId)
        ).use { c -> buildList { while (c.moveToNext()) add(c.toPlanItem()) } }

    fun upsertPlan(plan: Plan) = transaction { db ->
        db.insertWithOnConflict("plans", null, ContentValues().apply {
            put("id", plan.id); put("date", plan.date); put("budget_fen", plan.budgetFen)
            put("status", plan.status); put("source", plan.source)
            put("version", plan.version); put("created_at", plan.createdAt)
        }, SQLiteDatabase.CONFLICT_REPLACE)
        db.delete("plan_items", "plan_id=?", arrayOf(plan.id))
        plan.items.forEach { item ->
            db.insert("plan_items", null, ContentValues().apply {
                put("id", item.id); put("plan_id", item.planId); put("meal_type", item.mealType)
                put("food_id", item.foodId); put("name", item.name); put("grams", item.grams)
                put("cost_fen", item.costFen); put("kcal", item.kcal); put("protein", item.protein)
                put("fat", item.fat); put("carbs", item.carbs)
            })
        }
    }

    fun updatePlanStatus(id: String, status: String, version: Int) {
        writableDatabase.execSQL(
            "UPDATE plans SET status=?, version=? WHERE id=?",
            arrayOf<Any>(status, version, id)
        )
    }

    fun deletePlan(id: String) {
        writableDatabase.delete("plans", "id=?", arrayOf(id))
        writableDatabase.delete("plan_items", "plan_id=?", arrayOf(id))
    }

    // ------------------------------------------------------------------ 记录

    fun recordsBetween(fromMs: Long, toMs: Long, includeDeleted: Boolean = false): List<DietRecord> {
        val sql = StringBuilder("SELECT * FROM records WHERE eaten_at BETWEEN ? AND ?")
        if (!includeDeleted) sql.append(" AND deleted_at IS NULL")
        sql.append(" ORDER BY eaten_at DESC")
        return readableDatabase.rawQuery(sql.toString(), arrayOf(fromMs.toString(), toMs.toString())).use { c ->
            buildList { while (c.moveToNext()) add(c.toRecord()) }
        }.map { it.copy(items = recordItems(it.id)) }
    }

    fun trashedRecords(): List<DietRecord> =
        readableDatabase.rawQuery(
            "SELECT * FROM records WHERE deleted_at IS NOT NULL ORDER BY deleted_at DESC",
            null
        ).use { c -> buildList { while (c.moveToNext()) add(c.toRecord()) } }
            .map { it.copy(items = recordItems(it.id)) }

    fun allRecords(): List<DietRecord> =
        readableDatabase.rawQuery("SELECT * FROM records WHERE deleted_at IS NULL", null).use { c ->
            buildList { while (c.moveToNext()) add(c.toRecord()) }
        }.map { it.copy(items = recordItems(it.id)) }

    private fun recordItems(recordId: String): List<RecordItem> =
        readableDatabase.rawQuery(
            "SELECT * FROM record_items WHERE record_id=?",
            arrayOf(recordId)
        ).use { c -> buildList { while (c.moveToNext()) add(c.toRecordItem()) } }

    fun upsertRecord(record: DietRecord) = transaction { db ->
        db.insertWithOnConflict("records", null, ContentValues().apply {
            put("id", record.id); put("eaten_at", record.eatenAt)
            put("meal_type", record.mealType); put("cost_fen", record.costFen)
            put("note", record.note); put("source_plan_id", record.sourcePlanId)
            put("deleted_at", record.deletedAt); put("source", record.source)
        }, SQLiteDatabase.CONFLICT_REPLACE)
        db.delete("record_items", "record_id=?", arrayOf(record.id))
        record.items.forEach { item ->
            db.insert("record_items", null, ContentValues().apply {
                put("id", item.id); put("record_id", item.recordId); put("food_id", item.foodId)
                put("name", item.name); put("grams", item.grams); put("kcal100", item.kcal100)
                put("protein100", item.protein100); put("fat100", item.fat100)
                put("carbs100", item.carbs100)
            })
        }
    }

    fun setRecordDeleted(id: String, deletedAt: Long?) {
        writableDatabase.execSQL(
            "UPDATE records SET deleted_at=? WHERE id=?",
            arrayOf<Any?>(deletedAt, id)
        )
    }

    fun purgeExpiredTrash(beforeMs: Long) {
        writableDatabase.rawQuery(
            "SELECT id FROM records WHERE deleted_at IS NOT NULL AND deleted_at < ?",
            arrayOf(beforeMs.toString())
        ).use { c ->
            val ids = buildList { while (c.moveToNext()) add(c.getString(0)) }
            ids.forEach { id ->
                writableDatabase.delete("record_items", "record_id=?", arrayOf(id))
                writableDatabase.delete("records", "id=?", arrayOf(id))
            }
        }
    }

    fun hardDeleteRecord(id: String) {
        writableDatabase.delete("record_items", "record_id=?", arrayOf(id))
        writableDatabase.delete("records", "id=?", arrayOf(id))
    }

    // ------------------------------------------------------------------ 会话

    fun sessions(): List<ChatSession> =
        readableDatabase.rawQuery("SELECT * FROM sessions ORDER BY updated_at DESC", null).use { c ->
            buildList { while (c.moveToNext()) add(c.toSession()) }
        }

    fun insertSession(session: ChatSession) {
        writableDatabase.insertWithOnConflict("sessions", null, ContentValues().apply {
            put("id", session.id); put("title", session.title)
            put("created_at", session.createdAt); put("updated_at", session.updatedAt)
            put("model_snapshot", session.modelSnapshot)
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun touchSession(id: String, at: Long) {
        writableDatabase.execSQL("UPDATE sessions SET updated_at=? WHERE id=?", arrayOf<Any>(at, id))
    }

    fun renameSession(id: String, title: String) {
        writableDatabase.execSQL("UPDATE sessions SET title=? WHERE id=?", arrayOf<Any>(title, id))
    }

    fun deleteSession(id: String) {
        writableDatabase.delete("messages", "session_id=?", arrayOf(id))
        writableDatabase.delete("sessions", "id=?", arrayOf(id))
    }

    fun messages(sessionId: String): List<ChatMessage> =
        readableDatabase.rawQuery(
            "SELECT * FROM messages WHERE session_id=? ORDER BY created_at ASC",
            arrayOf(sessionId)
        ).use { c -> buildList { while (c.moveToNext()) add(c.toMessage()) } }

    fun insertMessage(message: ChatMessage) {
        writableDatabase.insertWithOnConflict("messages", null, ContentValues().apply {
            put("id", message.id); put("session_id", message.sessionId); put("role", message.role)
            put("content", message.content); put("cards_json", message.cardsJson)
            put("request_id", message.requestId); put("created_at", message.createdAt)
            put("status", message.status); put("detail", message.detail)
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun deleteMessage(id: String) = writableDatabase.delete("messages", "id=?", arrayOf(id))

    // ------------------------------------------------------------------ 社区

    fun cachedPosts(category: String?, query: String, limit: Int = 200): List<CommunityPost> {
        val sql = StringBuilder("SELECT * FROM community_posts WHERE 1=1")
        val args = mutableListOf<String>()
        if (!category.isNullOrBlank()) {
            sql.append(" AND category=?")
            args += category
        }
        if (query.isNotBlank()) {
            sql.append(" AND (text LIKE ? OR food LIKE ? OR place LIKE ?)")
            val like = "%${query.trim()}%"
            args += like; args += like; args += like
        }
        sql.append(" ORDER BY id DESC LIMIT ?")
        args += limit.toString()
        return readableDatabase.rawQuery(sql.toString(), args.toTypedArray()).use { c ->
            buildList { while (c.moveToNext()) add(c.toCommunityPost()) }
        }
    }

    fun communityPost(id: Long): CommunityPost? =
        readableDatabase.rawQuery("SELECT * FROM community_posts WHERE id=?", arrayOf(id.toString())).use { c ->
            if (c.moveToFirst()) c.toCommunityPost() else null
        }

    fun upsertPosts(posts: List<CommunityPost>) = transaction { db ->
        posts.forEach { post ->
            db.insertWithOnConflict("community_posts", null, ContentValues().apply {
                put("id", post.id); put("author", post.author); put("category", post.category)
                put("food", post.food); put("place", post.place); put("meetup", post.meetup)
                put("text", post.text); put("created_at", post.createdAt)
                put("mine", if (post.mine) 1 else 0)
                put("image_count", post.imageCount); put("reply_count", post.replyCount)
                put("cached_at", post.cachedAt)
            }, SQLiteDatabase.CONFLICT_REPLACE)
        }
    }

    fun deletePostCache(id: Long) {
        writableDatabase.delete("community_posts", "id=?", arrayOf(id.toString()))
        writableDatabase.delete("community_replies", "post_id=?", arrayOf(id.toString()))
    }

    /** 只保留最近 N 条缓存 */
    fun prunePosts(keep: Int) {
        writableDatabase.execSQL(
            "DELETE FROM community_posts WHERE id NOT IN (SELECT id FROM community_posts ORDER BY id DESC LIMIT ?)",
            arrayOf<Any>(keep)
        )
    }

    fun cachedReplies(postId: Long, limit: Int = 200): List<CommunityReply> =
        readableDatabase.rawQuery(
            "SELECT * FROM community_replies WHERE post_id=? ORDER BY id ASC LIMIT ?",
            arrayOf(postId.toString(), limit.toString())
        ).use { c -> buildList { while (c.moveToNext()) add(c.toCommunityReply()) } }

    fun upsertReplies(replies: List<CommunityReply>) = transaction { db ->
        replies.forEach { reply ->
            db.insertWithOnConflict("community_replies", null, ContentValues().apply {
                put("id", reply.id); put("post_id", reply.postId); put("author", reply.author)
                put("text", reply.text); put("created_at", reply.createdAt)
                put("mine", if (reply.mine) 1 else 0); put("cached_at", reply.cachedAt)
            }, SQLiteDatabase.CONFLICT_REPLACE)
        }
    }

    fun deleteReplyCache(postId: Long, replyId: Long) {
        writableDatabase.delete(
            "community_replies", "post_id=? AND id=?",
            arrayOf(postId.toString(), replyId.toString())
        )
    }

    fun drafts(): List<CommunityDraft> =
        readableDatabase.rawQuery("SELECT * FROM community_drafts ORDER BY created_at ASC", null).use { c ->
            buildList { while (c.moveToNext()) add(c.toCommunityDraft()) }
        }

    fun upsertDraft(draft: CommunityDraft) {
        writableDatabase.insertWithOnConflict("community_drafts", null, ContentValues().apply {
            put("id", draft.id); put("request_id", draft.requestId); put("category", draft.category)
            put("food", draft.food); put("place", draft.place); put("meetup", draft.meetup)
            put("text", draft.text); put("image_paths", draft.imagePaths.joinToString(","))
            put("created_at", draft.createdAt); put("last_error", draft.lastError)
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun deleteDraft(id: String) = writableDatabase.delete("community_drafts", "id=?", arrayOf(id))

    // ------------------------------------------------------------------ 维护

    /** 清除全部用户数据，保留内置食物表（SET-08） */
    fun clearUserData() = transaction { db ->
        listOf(
            "messages", "sessions", "plan_items", "plans", "record_items", "records",
            "community_posts", "community_replies", "community_drafts"
        ).forEach { db.delete(it, null, null) }
        db.delete("foods", "is_custom=1", null)
    }

    // ------------------------------------------------------------------ 映射

    private fun Food.toValues() = ContentValues().apply {
        put("id", id); put("name", name); put("name_zh", nameZh); put("aliases", aliases)
        put("kcal", kcal); put("protein", protein); put("fat", fat); put("carbs", carbs)
        put("edible_ratio", edibleRatio); put("state", state); put("fdc_id", fdcId)
        put("data_type", dataType); put("source_id", sourceId)
        put("is_custom", if (isCustom) 1 else 0)
    }

    private fun Cursor.optLong(name: String): Long? {
        val i = getColumnIndex(name)
        return if (i < 0 || isNull(i)) null else getLong(i)
    }

    private fun Cursor.optDouble(name: String): Double? {
        val i = getColumnIndex(name)
        return if (i < 0 || isNull(i)) null else getDouble(i)
    }

    private fun Cursor.str(name: String): String = getColumnIndex(name).let { if (it < 0 || isNull(it)) "" else getString(it) }

    private fun Cursor.toFood() = Food(
        id = str("id"),
        name = str("name"),
        nameZh = str("name_zh"),
        aliases = str("aliases"),
        kcal = optDouble("kcal"),
        protein = optDouble("protein"),
        fat = optDouble("fat"),
        carbs = optDouble("carbs"),
        edibleRatio = optDouble("edible_ratio"),
        state = str("state"),
        fdcId = optLong("fdc_id"),
        dataType = str("data_type"),
        sourceId = str("source_id"),
        isCustom = getColumnIndex("is_custom").let { it >= 0 && getInt(it) == 1 }
    )

    private fun Cursor.toPlanItem() = PlanItem(
        id = str("id"),
        planId = str("plan_id"),
        mealType = str("meal_type"),
        foodId = getColumnIndex("food_id").let { if (it >= 0 && !isNull(it)) getString(it) else null },
        name = str("name"),
        grams = optDouble("grams") ?: 0.0,
        costFen = optLong("cost_fen"),
        kcal = optDouble("kcal"),
        protein = optDouble("protein"),
        fat = optDouble("fat"),
        carbs = optDouble("carbs")
    )

    private fun Cursor.toPlan() = Plan(
        id = str("id"),
        date = str("date"),
        budgetFen = optLong("budget_fen") ?: 0L,
        status = str("status"),
        source = str("source"),
        version = optLong("version")?.toInt() ?: 1,
        createdAt = optLong("created_at") ?: 0L
    )

    private fun Cursor.toRecord() = DietRecord(
        id = str("id"),
        eatenAt = optLong("eaten_at") ?: 0L,
        mealType = str("meal_type"),
        costFen = optLong("cost_fen"),
        note = str("note"),
        sourcePlanId = getColumnIndex("source_plan_id").let { if (it >= 0 && !isNull(it)) getString(it) else null },
        deletedAt = optLong("deleted_at"),
        source = run {
            val index = getColumnIndex("source")
            if (index < 0 || isNull(index)) "manual" else getString(index).ifBlank { "manual" }
        }
    )

    private fun MenuItem.toMenuValues() = ContentValues().apply {
        put("id", id); put("name", name); put("unit", unit); put("grams", grams)
        put("grams_text", gramsText); put("price_fen", priceFen); put("category", category)
        put("is_custom", if (isCustom) 1 else 0)
    }

    private fun Cursor.toMenuItem() = MenuItem(
        id = str("id"),
        name = str("name"),
        unit = str("unit"),
        grams = optDouble("grams"),
        gramsText = str("grams_text"),
        priceFen = optLong("price_fen"),
        category = str("category").ifBlank { "未分类" },
        isCustom = getColumnIndex("is_custom").let { it >= 0 && getInt(it) == 1 }
    )

    private fun Cursor.toRecordItem() = RecordItem(
        id = str("id"),
        recordId = str("record_id"),
        foodId = getColumnIndex("food_id").let { if (it >= 0 && !isNull(it)) getString(it) else null },
        name = str("name"),
        grams = optDouble("grams") ?: 0.0,
        kcal100 = optDouble("kcal100"),
        protein100 = optDouble("protein100"),
        fat100 = optDouble("fat100"),
        carbs100 = optDouble("carbs100")
    )

    private fun Cursor.toSession() = ChatSession(
        id = str("id"),
        title = str("title"),
        createdAt = optLong("created_at") ?: 0L,
        updatedAt = optLong("updated_at") ?: 0L,
        modelSnapshot = str("model_snapshot")
    )

    private fun Cursor.toMessage() = ChatMessage(
        id = str("id"),
        sessionId = str("session_id"),
        role = str("role"),
        content = str("content"),
        cardsJson = getColumnIndex("cards_json").let { if (it >= 0 && !isNull(it)) getString(it) else null },
        requestId = str("request_id"),
        createdAt = optLong("created_at") ?: 0L,
        status = str("status"),
        detail = getColumnIndex("detail").let { if (it >= 0 && !isNull(it)) getString(it) else null }
    )

    private fun Cursor.toCommunityPost() = CommunityPost(
        id = optLong("id") ?: 0L,
        author = str("author"),
        category = str("category"),
        food = str("food"),
        place = str("place"),
        meetup = str("meetup"),
        text = str("text"),
        createdAt = str("created_at"),
        mine = getColumnIndex("mine").let { it >= 0 && getInt(it) == 1 },
        imageCount = optLong("image_count")?.toInt() ?: 0,
        replyCount = optLong("reply_count")?.toInt() ?: 0,
        cachedAt = optLong("cached_at") ?: 0L
    )

    private fun Cursor.toCommunityReply() = CommunityReply(
        id = optLong("id") ?: 0L,
        postId = optLong("post_id") ?: 0L,
        author = str("author"),
        text = str("text"),
        createdAt = str("created_at"),
        mine = getColumnIndex("mine").let { it >= 0 && getInt(it) == 1 },
        cachedAt = optLong("cached_at") ?: 0L
    )

    private fun Cursor.toCommunityDraft() = CommunityDraft(
        id = str("id"),
        requestId = str("request_id"),
        category = str("category"),
        food = str("food"),
        place = str("place"),
        meetup = str("meetup"),
        text = str("text"),
        imagePaths = str("image_paths").split(",").filter { it.isNotBlank() },
        createdAt = optLong("created_at") ?: 0L,
        lastError = getColumnIndex("last_error").let { if (it >= 0 && !isNull(it)) getString(it) else null }
    )

    companion object {
        const val NAME = "foodmoment.db"
        const val VERSION = 3
    }
}
