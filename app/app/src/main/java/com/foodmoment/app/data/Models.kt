package com.foodmoment.app.data

/** 食物条目。数值均为每 100g（可食部）口径。 */
data class Food(
    val id: String,
    /** FDC 英文原名 */
    val name: String,
    /** 本项目本地化中文名（译注，非 FDC 原文） */
    val nameZh: String,
    val aliases: String = "",
    val kcal: Double? = null,
    val protein: Double? = null,
    val fat: Double? = null,
    val carbs: Double? = null,
    /** 可食部比例 0..1，null 表示未知 */
    val edibleRatio: Double? = null,
    /** raw / cooked */
    val state: String = "cooked",
    val fdcId: Long? = null,
    /** foundation / sr_legacy / fndds / custom / pending */
    val dataType: String = "pending",
    val sourceId: String = "",
    val isCustom: Boolean = false
) {
    val displayName: String get() = nameZh.ifBlank { name }

    /** 是否带「待核验」标记 */
    val pending: Boolean get() = dataType == "pending"

    /** FNDDS 复合菜，需提示「典型配方估算」 */
    val composite: Boolean get() = dataType == "fndds"
}

data class PlanItem(
    val id: String,
    val planId: String,
    val mealType: String,
    val foodId: String?,
    val name: String,
    val grams: Double,
    val costFen: Long?,
    val kcal: Double?,
    val protein: Double?,
    val fat: Double?,
    val carbs: Double?
)

data class Plan(
    val id: String,
    val date: String,
    val budgetFen: Long,
    /** draft / saved */
    val status: String,
    /** ai / template */
    val source: String,
    val version: Int,
    val createdAt: Long,
    val items: List<PlanItem> = emptyList()
) {
    val isDraft: Boolean get() = status == "draft"
    val isTemplate: Boolean get() = source == "template"

    val knownTotalFen: Long get() = items.mapNotNull { it.costFen }.sum()
    val unknownCount: Int get() = items.count { it.costFen == null }
    val deltaFen: Long get() = budgetFen - knownTotalFen

    fun itemsOf(meal: String): List<PlanItem> = items.filter { it.mealType == meal }
}

data class RecordItem(
    val id: String,
    val recordId: String,
    val foodId: String?,
    val name: String,
    val grams: Double,
    val kcal100: Double?,
    val protein100: Double?,
    val fat100: Double?,
    val carbs100: Double?
)

data class DietRecord(
    val id: String,
    val eatenAt: Long,
    val mealType: String,
    val costFen: Long?,
    val note: String,
    val sourcePlanId: String?,
    val deletedAt: Long?,
    /** manual / plan / vision —— 拍照识别的记录要能被认出来 */
    val source: String = "manual",
    val items: List<RecordItem> = emptyList()
) {
    val inTrash: Boolean get() = deletedAt != null
    val fromVision: Boolean get() = source == "vision"
}

/** 菜单条目。价格与克重来自菜单；营养值由识别时估算，不落在这里。 */
data class MenuItem(
    val id: String,
    val name: String,
    val unit: String = "",
    val grams: Double? = null,
    val gramsText: String = "",
    val priceFen: Long? = null,
    val category: String = "未分类",
    val isCustom: Boolean = false
) {
    val displayPortion: String
        get() = when {
            gramsText.isNotBlank() -> "$gramsText g"
            grams != null -> "${grams.toInt()} g"
            else -> "份量未知"
        }
}

data class ChatSession(
    val id: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val modelSnapshot: String
)

data class ChatMessage(
    val id: String,
    val sessionId: String,
    /** user / assistant */
    val role: String,
    val content: String,
    val cardsJson: String?,
    val requestId: String,
    val createdAt: Long,
    /** ok / error / cancelled */
    val status: String,
    val detail: String? = null
)

/** 社区帖子（服务端 community_posts）。分数用 Double 会失真，这里全部按服务端原样保存。 */
data class CommunityPost(
    val id: Long,
    val author: String,
    val category: String,
    val food: String,
    val place: String,
    val meetup: String,
    val text: String,
    /** 服务端 ISO 8601（UTC），展示时转北京时间 */
    val createdAt: String,
    val mine: Boolean,
    val imageCount: Int,
    val replyCount: Int,
    val cachedAt: Long = 0L
)

data class CommunityReply(
    val id: Long,
    val postId: Long,
    val author: String,
    val text: String,
    val createdAt: String,
    val mine: Boolean,
    val cachedAt: Long = 0L
)

/** 离线草稿：先落本地，联网后按 requestId 幂等补发 */
data class CommunityDraft(
    val id: String,
    val requestId: String,
    val category: String,
    val food: String = "",
    val place: String = "",
    val meetup: String = "",
    val text: String,
    val imagePaths: List<String> = emptyList(),
    val createdAt: Long,
    val lastError: String? = null
)

object CommunityCategory {
    const val SHARE = "share"
    const val RECOMMEND = "recommend"
    const val AVOID = "avoid"
    const val BUDDY = "buddy"

    val all = listOf(SHARE, RECOMMEND, AVOID, BUDDY)

    fun label(code: String): String = when (code) {
        RECOMMEND -> "推荐"
        AVOID -> "避雷"
        BUDDY -> "约饭"
        else -> "分享"
    }
}
