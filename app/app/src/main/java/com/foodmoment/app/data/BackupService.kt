package com.foodmoment.app.data

import android.content.Context
import android.net.Uri
import com.foodmoment.app.core.Time
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * 数据导出 / 导入（SET-07 / 10.5）。
 *
 * 导出为 zip：data.json + manifest.json（含 SHA-256）。
 * 含记录、计划、自定义食物、自定义菜单、会话；不含 API Key 与 keystore。
 * 不含 API Key、不含 keystore。导入策略为「仅新增，不覆盖」。
 */
class BackupService(
    private val context: Context,
    private val db: FoodDatabase,
    private val images: ImageStore
) {

    suspend fun export(uri: Uri): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val payload = buildJson().toString().toByteArray(Charsets.UTF_8)

            val out = context.contentResolver.openOutputStream(uri)
                ?: error("无法写入所选文件")

            ZipOutputStream(out.buffered()).use { zip ->
                zip.putNextEntry(ZipEntry("data.json"))
                zip.write(payload)
                zip.closeEntry()

                val manifest = JSONObject()
                    .put("schema_version", SCHEMA_VERSION)
                    .put("exported_at", Time.iso(Time.now()))
                    .put("data_sha256", sha256(payload))

                zip.putNextEntry(ZipEntry("manifest.json"))
                zip.write(manifest.toString().toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }

            "已导出：记录 ${db.allRecords().size} 条、计划 ${db.allPlans().size} 个、" +
                    "自定义菜单 ${db.menuItems().count { it.isCustom }} 条（社区帖子保存在服务端，不含在导出包内）"
        }
    }

    suspend fun import(uri: Uri): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val entries = mutableMapOf<String, ByteArray>()
            val input = context.contentResolver.openInputStream(uri) ?: error("无法读取所选文件")
            ZipInputStream(input.buffered()).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory) {
                        val buffer = ByteArrayOutputStream()
                        zip.copyTo(buffer)
                        entries[entry.name] = buffer.toByteArray()
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }

            val dataBytes = entries["data.json"] ?: error("压缩包缺少 data.json")
            val manifest = entries["manifest.json"]?.let {
                runCatching { JSONObject(String(it, Charsets.UTF_8)) }.getOrNull()
            }
            val expected = manifest?.optString("data_sha256").orEmpty()
            if (expected.isNotBlank() && expected != sha256(dataBytes)) {
                error("校验失败：data.json 与 manifest 不一致")
            }

            val root = JSONObject(String(dataBytes, Charsets.UTF_8))
            val schema = root.optInt("schema_version", 0)
            if (schema > SCHEMA_VERSION) error("备份来自更新的版本（schema=$schema），请升级 App")

            var addedRecords = 0
            var addedPlans = 0
            var addedMenu = 0

            // 自定义食物
            root.optJSONArray("custom_foods")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    db.upsertFood(o.toFood())
                }
            }

            // 自定义菜单（内置菜单由 assets 重建，不需要导入）
            root.optJSONArray("menu_items")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val id = o.optString("id")
                    if (id.isBlank() || db.menuItemExists(id)) continue
                    db.upsertMenuItem(o.toMenu())
                    addedMenu++
                }
            }

            // 计划
            root.optJSONArray("plans")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val planId = o.optString("id")
                    if (planId.isBlank() || db.planExists(planId)) continue
                    db.upsertPlan(o.toPlan())
                    addedPlans++
                }
            }

            // 记录
            root.optJSONArray("records")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val id = o.optString("id")
                    if (id.isBlank() || db.recordExists(id)) continue
                    db.upsertRecord(o.toRecord())
                    addedRecords++
                }
            }

            // 会话与消息
            root.optJSONArray("sessions")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val id = o.optString("id")
                    if (id.isBlank() || db.sessionExists(id)) continue
                    db.insertSession(o.toSession())
                }
            }
            root.optJSONArray("messages")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val sessionId = o.optString("session_id")
                    if (sessionId.isBlank() || !db.sessionExists(sessionId)) continue
                    db.insertMessage(o.toMessage())
                }
            }

            // 社区帖子保存在服务端，不进导出包；旧备份里的 feed / images 会被忽略

            "已导入：记录 $addedRecords 条、计划 $addedPlans 个、自定义菜单 $addedMenu 条（已存在的条目自动跳过）"
        }
    }

    // ------------------------------------------------------------------ 序列化

    private fun buildJson(): JSONObject {
        val records = JSONArray()
        db.allRecords().forEach { r ->
            records.put(JSONObject()
                .put("id", r.id)
                .put("eaten_at", r.eatenAt)
                .put("meal_type", r.mealType)
                .put("cost_fen", r.costFen ?: JSONObject.NULL)
                .put("note", r.note)
                .put("source_plan_id", r.sourcePlanId ?: JSONObject.NULL)
                .put("source", r.source)
                .put("items", JSONArray(r.items.map { item ->
                    JSONObject()
                        .put("id", item.id)
                        .put("food_id", item.foodId ?: JSONObject.NULL)
                        .put("name", item.name)
                        .put("grams", item.grams)
                        .put("kcal100", item.kcal100 ?: JSONObject.NULL)
                        .put("protein100", item.protein100 ?: JSONObject.NULL)
                        .put("fat100", item.fat100 ?: JSONObject.NULL)
                        .put("carbs100", item.carbs100 ?: JSONObject.NULL)
                })))
        }

        val plans = JSONArray()
        db.allPlans().forEach { p ->
            plans.put(JSONObject()
                .put("id", p.id)
                .put("date", p.date)
                .put("budget_fen", p.budgetFen)
                .put("status", p.status)
                .put("source", p.source)
                .put("version", p.version)
                .put("created_at", p.createdAt)
                .put("items", JSONArray(p.items.map { item ->
                    JSONObject()
                        .put("id", item.id)
                        .put("meal_type", item.mealType)
                        .put("food_id", item.foodId ?: JSONObject.NULL)
                        .put("name", item.name)
                        .put("grams", item.grams)
                        .put("cost_fen", item.costFen ?: JSONObject.NULL)
                        .put("kcal", item.kcal ?: JSONObject.NULL)
                        .put("protein", item.protein ?: JSONObject.NULL)
                        .put("fat", item.fat ?: JSONObject.NULL)
                        .put("carbs", item.carbs ?: JSONObject.NULL)
                })))
        }

        val customFoods = JSONArray()
        db.foods().filter { it.isCustom }.forEach { f ->
            customFoods.put(JSONObject()
                .put("id", f.id)
                .put("name", f.name)
                .put("name_zh", f.nameZh)
                .put("aliases", f.aliases)
                .put("kcal", f.kcal ?: JSONObject.NULL)
                .put("protein", f.protein ?: JSONObject.NULL)
                .put("fat", f.fat ?: JSONObject.NULL)
                .put("carbs", f.carbs ?: JSONObject.NULL)
                .put("edible_ratio", f.edibleRatio ?: JSONObject.NULL)
                .put("is_custom", true))
        }

        // 只导出用户自己加的菜单项；内置菜单由 assets/menu.md 生成，不需要进包
        val menuItems = JSONArray()
        db.menuItems().filter { it.isCustom }.forEach { m ->
            menuItems.put(JSONObject()
                .put("id", m.id)
                .put("name", m.name)
                .put("unit", m.unit)
                .put("grams", m.grams ?: JSONObject.NULL)
                .put("grams_text", m.gramsText)
                .put("price_fen", m.priceFen ?: JSONObject.NULL)
                .put("category", m.category))
        }

        val sessions = JSONArray()
        db.sessions().forEach { s ->
            sessions.put(JSONObject()
                .put("id", s.id)
                .put("title", s.title)
                .put("created_at", s.createdAt)
                .put("updated_at", s.updatedAt)
                .put("model_snapshot", s.modelSnapshot))
        }

        val messages = JSONArray()
        db.sessions().forEach { s ->
            db.messages(s.id).forEach { m ->
                messages.put(JSONObject()
                    .put("id", m.id)
                    .put("session_id", m.sessionId)
                    .put("role", m.role)
                    .put("content", m.content)
                    .put("cards_json", m.cardsJson ?: JSONObject.NULL)
                    .put("request_id", m.requestId)
                    .put("created_at", m.createdAt)
                    .put("status", m.status)
                    .put("detail", m.detail ?: JSONObject.NULL))
            }
        }

        return JSONObject()
            .put("schema_version", SCHEMA_VERSION)
            .put("exported_at", Time.iso(Time.now()))
            .put("records", records)
            .put("plans", plans)
            .put("custom_foods", customFoods)
            .put("menu_items", menuItems)
            .put("sessions", sessions)
            .put("messages", messages)
    }

    private fun JSONObject.toFood(): Food = Food(
        id = optString("id"),
        name = optString("name"),
        nameZh = optString("name_zh"),
        aliases = optString("aliases"),
        kcal = doubleOrNull("kcal"),
        protein = doubleOrNull("protein"),
        fat = doubleOrNull("fat"),
        carbs = doubleOrNull("carbs"),
        edibleRatio = doubleOrNull("edible_ratio"),
        isCustom = true
    )

    private fun JSONObject.toMenu(): MenuItem = MenuItem(
        id = optString("id"),
        name = optString("name"),
        unit = optString("unit"),
        grams = doubleOrNull("grams"),
        gramsText = optString("grams_text"),
        priceFen = longOrNull("price_fen"),
        category = optString("category", "未分类"),
        isCustom = true
    )

    private fun JSONObject.toPlan(): Plan {
        val id = optString("id")
        val items = mutableListOf<PlanItem>()
        optJSONArray("items")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                items += PlanItem(
                    id = o.optString("id"),
                    planId = id,
                    mealType = o.optString("meal_type"),
                    foodId = if (o.isNull("food_id")) null else o.optString("food_id"),
                    name = o.optString("name"),
                    grams = o.optDouble("grams", 0.0),
                    costFen = o.longOrNull("cost_fen"),
                    kcal = o.doubleOrNull("kcal"),
                    protein = o.doubleOrNull("protein"),
                    fat = o.doubleOrNull("fat"),
                    carbs = o.doubleOrNull("carbs")
                )
            }
        }
        return Plan(
            id = id,
            date = optString("date"),
            budgetFen = optLong("budget_fen"),
            status = optString("status", "saved"),
            source = optString("source", "ai"),
            version = optInt("version", 1),
            createdAt = optLong("created_at"),
            items = items
        )
    }

    private fun JSONObject.toRecord(): DietRecord {
        val id = optString("id")
        val items = mutableListOf<RecordItem>()
        optJSONArray("items")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                items += RecordItem(
                    id = o.optString("id"),
                    recordId = id,
                    foodId = if (o.isNull("food_id")) null else o.optString("food_id"),
                    name = o.optString("name"),
                    grams = o.optDouble("grams", 0.0),
                    kcal100 = o.doubleOrNull("kcal100"),
                    protein100 = o.doubleOrNull("protein100"),
                    fat100 = o.doubleOrNull("fat100"),
                    carbs100 = o.doubleOrNull("carbs100")
                )
            }
        }
        return DietRecord(
            id = id,
            eatenAt = optLong("eaten_at"),
            mealType = optString("meal_type"),
            costFen = longOrNull("cost_fen"),
            note = optString("note"),
            sourcePlanId = if (isNull("source_plan_id")) null else optString("source_plan_id"),
            deletedAt = null,
            source = optString("source", "manual").ifBlank { "manual" },
            items = items
        )
    }

    private fun JSONObject.toSession(): ChatSession = ChatSession(
        id = optString("id"),
        title = optString("title"),
        createdAt = optLong("created_at"),
        updatedAt = optLong("updated_at"),
        modelSnapshot = optString("model_snapshot")
    )

    private fun JSONObject.toMessage(): ChatMessage = ChatMessage(
        id = optString("id"),
        sessionId = optString("session_id"),
        role = optString("role"),
        content = optString("content"),
        cardsJson = if (isNull("cards_json")) null else optString("cards_json"),
        requestId = optString("request_id"),
        createdAt = optLong("created_at"),
        status = optString("status", "ok"),
        detail = if (isNull("detail")) null else optString("detail")
    )

    private fun JSONArray.toStringList(): List<String> {
        val list = mutableListOf<String>()
        for (i in 0 until length()) {
            val v = optString(i)
            if (v.isNotBlank()) list += v
        }
        return list
    }

    private fun JSONObject.doubleOrNull(key: String): Double? =
        if (has(key) && !isNull(key)) optDouble(key) else null

    private fun JSONObject.longOrNull(key: String): Long? =
        if (has(key) && !isNull(key)) optLong(key) else null

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) }

    companion object {
        const val SCHEMA_VERSION = 2
    }
}
