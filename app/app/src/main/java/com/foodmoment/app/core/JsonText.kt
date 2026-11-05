package com.foodmoment.app.core

/**
 * 从模型返回的文本里抠出第一个平衡的 JSON 对象。
 * 容忍 ```json 围栏、前后说明文字、字符串里的花括号。
 */
fun extractJsonObject(raw: String): String? {
    var text = raw.trim()
    if (text.startsWith("```")) {
        text = text.removePrefix("```json").removePrefix("```")
        val end = text.lastIndexOf("```")
        if (end >= 0) text = text.substring(0, end)
        text = text.trim()
    }

    var start = -1
    var depth = 0
    var inString = false
    var escaped = false

    for (index in text.indices) {
        val ch = text[index]
        when {
            escaped -> escaped = false
            ch == '\\' && inString -> escaped = true
            ch == '"' -> inString = !inString
            inString -> Unit
            ch == '{' -> {
                if (depth == 0) start = index
                depth++
            }
            ch == '}' -> {
                depth--
                if (depth == 0 && start >= 0) return text.substring(start, index + 1)
            }
        }
    }
    return null
}
