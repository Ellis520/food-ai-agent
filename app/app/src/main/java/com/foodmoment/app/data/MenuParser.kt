package com.foodmoment.app.data

/**
 * 菜单解析：把 assets/menu.md 那种 4 列表格读成菜单条目。
 *
 * 兼容：
 *   - 行与行之间有空行
 *   - `| **【早餐】** | | | |` 这种分类行（也可能带反斜杠转义 `\*\*`）
 *   - 重量列可能是 `150-160`、`220`、`-`
 *   - 单价是元，解析后存「分」
 */
object MenuParser {

    private val sectionRegex = Regex("""【(.+?)】""")
    private val numberRegex = Regex("""\d+(?:\.\d+)?""")

    fun parse(text: String): List<MenuItem> {
        val items = mutableListOf<MenuItem>()
        var category = "未分类"

        text.lineSequence().forEach { rawLine ->
            val line = rawLine.trim()
            if (!line.startsWith("|")) return@forEach

            val cells = line
                .removePrefix("|")
                .removeSuffix("|")
                .split("|")
                .map { it.trim() }
            if (cells.size < 4) return@forEach

            val name = cells[0].replace("\\*", "").trim()
            if (name.isEmpty()) return@forEach
            if (name == "名称" || name.startsWith(":---")) return@forEach

            val section = sectionRegex.find(name)
            if (section != null) {
                category = section.groupValues[1].trim()
                return@forEach
            }

            val unit = cells[1].takeIf { it.isNotEmpty() && it != "-" }.orEmpty()
            val gramsText = cells[2].takeIf { it != "-" }.orEmpty()
            val grams = numberRegex.find(gramsText)?.value?.toDoubleOrNull()
            val priceYuan = numberRegex.find(cells[3])?.value?.toDoubleOrNull()
            val priceFen = priceYuan?.let { Math.round(it * 100).toLong() }

            items += MenuItem(
                id = idFor(name),
                name = name,
                unit = unit,
                grams = grams,
                gramsText = gramsText,
                priceFen = priceFen,
                category = category,
                isCustom = false
            )
        }
        return items
    }

    /** 同名条目 id 稳定，便于恢复默认菜单时不重复插入 */
    fun idFor(name: String): String = "menu_" + name.hashCode().toUInt().toString(16)
}
