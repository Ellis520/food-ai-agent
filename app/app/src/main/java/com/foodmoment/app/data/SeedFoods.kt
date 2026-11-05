package com.foodmoment.app.data

/**
 * 内置食物表种子数据（NUT-06 / 4.4）。
 *
 * ⚠ 数据状态说明（重要）
 * 本文件中的数值为常见参考值，**尚未逐条与 USDA FoodData Central 核对**，
 * 因此 `fdcId = null`、`dataType = "pending"`，界面上会显示「待核验」标记。
 * 取证步骤：在 https://fdc.nal.usda.gov 按 name 检索，取 Foundation/SR Legacy/FNDDS 条目，
 * 填入 fdcId 与 dataType，并把四项数值改为该条目的每 100g 值。
 * 核验完成后把 dataType 改为对应数据集，标记自动消失。
 *
 * 复合菜（FNDDS）条目必须显示「典型配方估算」，不代表店家实际做法。
 */
object SeedFoods {

    private const val SOURCE_PENDING = "usda_fdc_pending"
    private const val SOURCE_FNDDS = "usda_fndds_pending"

    private fun base(
        id: String,
        name: String,
        nameZh: String,
        kcal: Double,
        protein: Double,
        fat: Double,
        carbs: Double,
        state: String = "cooked",
        edible: Double = 1.0,
        aliases: String = ""
    ) = Food(
        id = id,
        name = name,
        nameZh = nameZh,
        aliases = aliases,
        kcal = kcal,
        protein = protein,
        fat = fat,
        carbs = carbs,
        edibleRatio = edible,
        state = state,
        fdcId = null,
        dataType = "pending",
        sourceId = SOURCE_PENDING
    )

    val items: List<Food> = listOf(
        // ---- 主食 ----
        base("f_rice_white", "Rice, white, cooked", "白米饭（熟）", 130.0, 2.7, 0.3, 28.2, aliases = "米饭,白饭"),
        base("f_rice_brown", "Rice, brown, cooked", "糙米饭（熟）", 123.0, 2.7, 1.0, 25.6, aliases = "糙米"),
        base("f_noodle", "Noodles, cooked", "面条（煮）", 138.0, 4.5, 2.1, 25.0, aliases = "面,挂面"),
        base("f_bun", "Steamed bun", "馒头", 223.0, 7.0, 1.1, 47.0, aliases = "馍"),
        base("f_bread_ww", "Bread, whole wheat", "全麦面包", 247.0, 13.0, 3.4, 41.0, aliases = "面包"),
        base("f_potato", "Potatoes, cooked", "土豆（熟）", 87.0, 1.9, 0.1, 20.1, edible = 0.95, aliases = "马铃薯"),
        base("f_sweetpotato", "Sweet potato, cooked", "红薯（蒸）", 90.0, 2.0, 0.2, 20.7, edible = 0.90, aliases = "地瓜"),

        // ---- 蛋白 ----
        base("f_egg", "Egg, whole, boiled", "鸡蛋（煮）", 155.0, 12.6, 10.6, 1.1, edible = 0.88, aliases = "水煮蛋"),
        base("f_chicken_breast", "Chicken breast, cooked", "鸡胸肉（熟）", 165.0, 31.0, 3.6, 0.0, aliases = "鸡胸"),
        base("f_pork_loin", "Pork loin, cooked", "猪里脊（熟）", 187.0, 27.0, 8.0, 0.0, aliases = "瘦猪肉"),
        base("f_beef_lean", "Beef, lean, cooked", "牛肉（瘦，熟）", 250.0, 26.0, 15.0, 0.0),
        base("f_salmon", "Salmon, cooked", "三文鱼（熟）", 206.0, 22.0, 12.0, 0.0),
        base("f_tofu", "Tofu, firm", "豆腐（北豆腐）", 116.0, 12.2, 6.5, 2.9),

        // ---- 乳与饮品 ----
        base("f_milk", "Milk, whole", "牛奶（全脂）", 61.0, 3.2, 3.3, 4.8),
        base("f_yogurt", "Yogurt, plain", "酸奶（原味）", 61.0, 3.5, 3.3, 4.7),
        base("f_soymilk", "Soymilk, unsweetened", "豆浆（无糖）", 33.0, 3.3, 1.8, 1.2),
        base("f_cola", "Carbonated beverage, cola", "可乐", 42.0, 0.0, 0.0, 10.6, aliases = "汽水"),

        // ---- 蔬果 ----
        base("f_broccoli", "Broccoli, cooked", "西兰花（熟）", 35.0, 2.4, 0.4, 7.2, edible = 0.90),
        base("f_spinach", "Spinach, cooked", "菠菜（熟）", 23.0, 3.0, 0.4, 3.8, edible = 0.90),
        base("f_tomato", "Tomato, raw", "番茄", 18.0, 0.9, 0.2, 3.9, state = "raw", aliases = "西红柿"),
        base("f_apple", "Apple, raw", "苹果", 52.0, 0.3, 0.2, 13.8, state = "raw", edible = 0.90),
        base("f_banana", "Banana, raw", "香蕉", 89.0, 1.1, 0.3, 22.8, state = "raw", edible = 0.67),

        // ---- 油脂与坚果 ----
        base("f_oil", "Soybean oil", "大豆油", 884.0, 0.0, 100.0, 0.0),
        base("f_peanut", "Peanuts, roasted", "花生（炒）", 567.0, 25.8, 49.2, 16.1, edible = 1.0),

        // ---- 复合菜（FNDDS 口径，典型配方估算）----
        base("f_fried_rice", "Fried rice, with egg", "蛋炒饭", 180.0, 4.6, 6.0, 27.0)
            .copy(dataType = "fndds", sourceId = SOURCE_FNDDS),
        base("f_dumpling", "Dumpling, pork, boiled", "猪肉饺子（煮）", 220.0, 9.0, 8.0, 28.0)
            .copy(dataType = "fndds", sourceId = SOURCE_FNDDS),
        base("f_baozi", "Steamed bun, meat filling", "肉包子", 227.0, 8.0, 8.0, 31.0)
            .copy(dataType = "fndds", sourceId = SOURCE_FNDDS),
        base("f_tomato_rice", "Rice with tomato and egg", "番茄鸡蛋盖饭", 150.0, 5.0, 5.0, 21.0)
            .copy(dataType = "fndds", sourceId = SOURCE_FNDDS)
    )

    /** 数据来源表（NUT-06 / 12.5） */
    val sources: List<Array<String>> = listOf(
        arrayOf(
            SOURCE_PENDING,
            "USDA FoodData Central（待核验）",
            "U.S. Department of Agriculture, Agricultural Research Service",
            "https://fdc.nal.usda.gov/",
            "pending",
            "2026-11-03",
            "美国政府作品，可自由使用；条目数值尚未逐条核验。界面显示「待核验」。"
        ),
        arrayOf(
            SOURCE_FNDDS,
            "USDA FoodData Central — FNDDS（待核验）",
            "U.S. Department of Agriculture, Agricultural Research Service",
            "https://fdc.nal.usda.gov/",
            "pending",
            "2026-11-03",
            "复合菜为典型配方估算，不代表实际店家配方；界面显示「典型配方估算」。"
        )
    )

    /** 离线模板计划（PLAN-08）：按 15 / 25 / 40 元三档组织的三餐模板 */
    val templates: List<TemplatePlan> = listOf(
        TemplatePlan(
            key = "t15",
            title = "15 元 · 精简三餐",
            budgetFen = 1500,
            notes = "以食堂基础餐为主，蛋白质偏低时可加一个鸡蛋",
            meals = mapOf(
                "breakfast" to listOf(Triple("f_bun", "馒头", 100.0), Triple("f_soymilk", "豆浆（无糖）", 250.0)),
                "lunch" to listOf(Triple("f_rice_white", "白米饭（熟）", 200.0), Triple("f_tomato_rice", "番茄鸡蛋盖饭", 200.0)),
                "dinner" to listOf(Triple("f_noodle", "面条（煮）", 250.0), Triple("f_broccoli", "西兰花（熟）", 100.0))
            )
        ),
        TemplatePlan(
            key = "t25",
            title = "25 元 · 均衡三餐",
            budgetFen = 2500,
            notes = "三餐都有蛋白质来源，适合日常",
            meals = mapOf(
                "breakfast" to listOf(Triple("f_egg", "鸡蛋（煮）", 100.0), Triple("f_milk", "牛奶（全脂）", 250.0)),
                "lunch" to listOf(Triple("f_rice_white", "白米饭（熟）", 200.0), Triple("f_chicken_breast", "鸡胸肉（熟）", 120.0), Triple("f_broccoli", "西兰花（熟）", 150.0)),
                "dinner" to listOf(Triple("f_noodle", "面条（煮）", 250.0), Triple("f_tofu", "豆腐（北豆腐）", 150.0))
            )
        ),
        TemplatePlan(
            key = "t40",
            title = "40 元 · 高蛋白三餐",
            budgetFen = 4000,
            notes = "蛋白质充足，预算较宽裕时使用",
            meals = mapOf(
                "breakfast" to listOf(Triple("f_egg", "鸡蛋（煮）", 150.0), Triple("f_bread_ww", "全麦面包", 80.0), Triple("f_milk", "牛奶（全脂）", 250.0)),
                "lunch" to listOf(Triple("f_rice_white", "白米饭（熟）", 250.0), Triple("f_beef_lean", "牛肉（瘦，熟）", 120.0), Triple("f_spinach", "菠菜（熟）", 150.0)),
                "dinner" to listOf(Triple("f_noodle", "面条（煮）", 200.0), Triple("f_salmon", "三文鱼（熟）", 120.0), Triple("f_tomato", "番茄", 100.0))
            )
        ),
        TemplatePlan(
            key = "t_simple",
            title = "简易两餐",
            budgetFen = 1800,
            notes = "跳过早/晚任选其一时的简化组合",
            meals = mapOf(
                "breakfast" to listOf(Triple("f_bread_ww", "全麦面包", 100.0), Triple("f_milk", "牛奶（全脂）", 250.0)),
                "lunch" to listOf(Triple("f_fried_rice", "蛋炒饭", 300.0), Triple("f_tomato", "番茄", 100.0))
            )
        ),
        TemplatePlan(
            key = "t_light",
            title = "轻食日",
            budgetFen = 2200,
            notes = "少油、蔬菜比重高，注意蛋白质可能不足",
            meals = mapOf(
                "breakfast" to listOf(Triple("f_yogurt", "酸奶（原味）", 200.0), Triple("f_apple", "苹果", 150.0)),
                "lunch" to listOf(Triple("f_sweetpotato", "红薯（蒸）", 200.0), Triple("f_chicken_breast", "鸡胸肉（熟）", 120.0)),
                "dinner" to listOf(Triple("f_broccoli", "西兰花（熟）", 200.0), Triple("f_tofu", "豆腐（北豆腐）", 150.0))
            )
        )
    )
}

/** 模板里的一项：食物 id、显示名、克重 */
data class Triple(val foodId: String, val name: String, val grams: Double)

data class TemplatePlan(
    val key: String,
    val title: String,
    val budgetFen: Long,
    val notes: String,
    val meals: Map<String, List<Triple>>
)
