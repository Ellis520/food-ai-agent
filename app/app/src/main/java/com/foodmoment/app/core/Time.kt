package com.foodmoment.app.core

import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * 时间工具。
 *
 * 口径（10.2）：全库存 UTC 毫秒时间戳；归属日与统计边界固定按 Asia/Shanghai 计算，
 * 不随设备时区变化。
 */
object Time {

    val ZONE: TimeZone = TimeZone.getTimeZone("Asia/Shanghai")

    private fun fmt(pattern: String): SimpleDateFormat =
        SimpleDateFormat(pattern, Locale.CHINA).apply { timeZone = ZONE }

    fun now(): Long = System.currentTimeMillis()

    /** 归属日键：yyyy-MM-dd（北京时间） */
    fun dayKey(millis: Long): String = fmt("yyyy-MM-dd").format(Date(millis))

    fun todayKey(): String = dayKey(now())

    /** 某日 00:00:00（北京时间）的时间戳 */
    fun dayStart(dayKey: String): Long =
        runCatching { fmt("yyyy-MM-dd").parse(dayKey)?.time }.getOrNull() ?: now()

    fun dayEnd(dayKey: String): Long = dayStart(dayKey) + 86_400_000L - 1

    fun addDays(dayKey: String, days: Int): String {
        val cal = Calendar.getInstance(ZONE).apply { time = Date(dayStart(dayKey)) }
        cal.add(Calendar.DAY_OF_MONTH, days)
        return dayKey(cal.timeInMillis)
    }

    fun timeLabel(millis: Long): String = fmt("HH:mm").format(Date(millis))

    fun dateTimeLabel(millis: Long): String = fmt("MM-dd HH:mm").format(Date(millis))

    fun fullLabel(millis: Long): String = fmt("yyyy-MM-dd HH:mm").format(Date(millis))

    fun dayLabel(dayKey: String): String = fmt("M月d日").format(Date(dayStart(dayKey)))

    fun weekdayLabel(dayKey: String): String {
        val cal = Calendar.getInstance(ZONE).apply { time = Date(dayStart(dayKey)) }
        val names = arrayOf("周日", "周一", "周二", "周三", "周四", "周五", "周六")
        return names[cal.get(Calendar.DAY_OF_WEEK) - 1]
    }

    /** 发给后端的 ISO 8601（带 +08:00） */
    fun iso(millis: Long): String = fmt("yyyy-MM-dd'T'HH:mm:ssXXX").format(Date(millis))

    /**
     * 解析服务端返回的 ISO 8601，返回毫秒。
     *
     * 实测后端格式：2026-11-04T07:32:11.043697+00:00（微秒 + 偏移）。
     * 小数秒位数不固定，必须先剥掉再解析，否则 SimpleDateFormat 会匹配失败并
     * 退化成「按本地时区」解释，整整差 8 小时。
     */
    fun parseIsoMillis(raw: String?): Long? {
        if (raw.isNullOrBlank()) return null
        val text = raw.trim().replace(Regex("\\.\\d+"), "")
        if (text.isEmpty()) return null

        val hasZone = text.endsWith("Z") || Regex("[+-]\\d{2}:?\\d{2}$").containsMatchIn(text)
        val patterns = if (hasZone) {
            listOf("yyyy-MM-dd'T'HH:mm:ssXXX", "yyyy-MM-dd'T'HH:mm:ss'Z'")
        } else {
            listOf("yyyy-MM-dd'T'HH:mm:ss", "yyyy-MM-dd HH:mm:ss")
        }

        for (pattern in patterns) {
            val format = SimpleDateFormat(pattern, Locale.US)
            format.isLenient = false
            if (!hasZone) format.timeZone = TimeZone.getTimeZone("UTC")
            val position = ParsePosition(0)
            val parsed = format.parse(text, position)
            if (parsed != null && position.index == text.length) return parsed.time
        }
        return null
    }

    fun hourOf(millis: Long): Int {
        val cal = Calendar.getInstance(ZONE).apply { time = Date(millis) }
        return cal.get(Calendar.HOUR_OF_DAY)
    }

    /** 餐次建议：按时间推断默认值，用户可改 */
    fun mealTypeByHour(millis: Long): String = when (hourOf(millis)) {
        in 5..9 -> "breakfast"
        in 10..14 -> "lunch"
        in 15..20 -> "dinner"
        else -> "snack"
    }
}

object MealType {
    const val BREAKFAST = "breakfast"
    const val LUNCH = "lunch"
    const val DINNER = "dinner"
    const val SNACK = "snack"

    val all = listOf(BREAKFAST, LUNCH, DINNER, SNACK)

    fun label(code: String): String = when (code) {
        BREAKFAST -> "早餐"
        LUNCH -> "午餐"
        DINNER -> "晚餐"
        else -> "加餐"
    }
}
