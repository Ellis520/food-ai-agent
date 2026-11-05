package com.foodmoment.app

import com.foodmoment.app.data.MenuParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 菜单解析（assets/menu.md）。
 * 单元测试的工作目录是模块根目录，因此可以直接读源文件，避免把 3.8KB 的菜单抄进测试。
 */
class MenuParserTest {

    private fun readDefaultMenu(): String {
        val candidates = listOf(
            File("src/main/assets/menu.md"),
            File("app/src/main/assets/menu.md")
        )
        val file = candidates.firstOrNull { it.exists() }
            ?: error("找不到 menu.md，工作目录=" + File(".").absolutePath)
        return file.readText()
    }

    @Test
    fun parsesDefaultMenuFile() {
        val items = MenuParser.parse(readDefaultMenu())

        assertTrue("应解析出 80 条以上，实际 ${items.size}", items.size > 80)
        assertTrue(items.any { it.name == "番茄煮鸡蛋" })
        assertTrue(items.any { it.category == "早餐" })
        assertTrue(items.any { it.category == "午餐/晚餐" })

        val rice = items.first { it.name == "普通大米饭 (100g)" }
        assertEquals(20L, rice.priceFen)
        assertEquals(100.0, rice.grams!!, 0.001)

        // 重量是区间时取第一个数字作为默认克重
        val friedRice = items.first { it.name.startsWith("台式炒饭") }
        assertEquals(150.0, friedRice.grams!!, 0.001)
    }

    @Test
    fun ignoresHeaderAndSeparatorRows() {
        val text = """
            | 名称 | 单位 | 重量 (克) | 单价 (元) |
            | :--- | :--- | :--- | :--- |
            | **【测试】** | | | |
            | 测试菜 | 份 | 100 | 2.5 |
        """.trimIndent()

        val items = MenuParser.parse(text)

        assertEquals(1, items.size)
        assertEquals("测试菜", items[0].name)
        assertEquals("测试", items[0].category)
        assertEquals(250L, items[0].priceFen)
        assertEquals(100.0, items[0].grams!!, 0.001)
    }

    @Test
    fun handlesMissingWeightAndPrice() {
        val text = "| 只有名字 | - | - | - |"
        val items = MenuParser.parse(text)
        assertEquals(1, items.size)
        assertEquals(null, items[0].grams)
        assertEquals(null, items[0].priceFen)
    }

    @Test
    fun idsAreStable() {
        val first = MenuParser.parse(readDefaultMenu())
        val second = MenuParser.parse(readDefaultMenu())
        assertEquals(first.map { it.id }, second.map { it.id })
        assertEquals(first.size, first.map { it.id }.distinct().size)
    }
}
