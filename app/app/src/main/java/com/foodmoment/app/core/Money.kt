package com.foodmoment.app.core

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * 金额工具。全链路以「分」为单位的 Long 表示，禁止 Double/Float 参与运算（6.3）。
 */
object Money {

    /** 1250 -> "¥12.50"，1200 -> "¥12" */
    fun format(fen: Long?): String {
        if (fen == null) return "未知"
        val negative = fen < 0
        val abs = kotlin.math.abs(fen)
        val yuan = abs / 100
        val cents = (abs % 100).toInt()
        val body = if (cents == 0) "$yuan" else "$yuan.${cents.toString().padStart(2, '0')}"
        return if (negative) "-¥$body" else "¥$body"
    }

    /** 不带符号的数值部分，用于输入框回填 */
    fun toYuanText(fen: Long?): String {
        if (fen == null) return ""
        val abs = kotlin.math.abs(fen)
        val yuan = abs / 100
        val cents = (abs % 100).toInt()
        return if (cents == 0) "$yuan" else "$yuan.${cents.toString().padStart(2, '0')}"
    }

    /**
     * 解析用户输入的元，返回分。
     * 规则（6.3）：最多两位小数，超出直接返回 null 由调用方提示，不做静默四舍五入。
     */
    fun parseYuan(input: String): Long? {
        val cleaned = input.trim()
            .removePrefix("¥")
            .removeSuffix("元")
            .trim()
        if (cleaned.isEmpty()) return null
        if (!cleaned.matches(Regex("^\\d{1,9}(\\.\\d{1,2})?$"))) return null
        return runCatching {
            BigDecimal(cleaned).multiply(BigDecimal(100))
                .setScale(0, RoundingMode.UNNECESSARY)
                .longValueExact()
        }.getOrNull()
    }
}
