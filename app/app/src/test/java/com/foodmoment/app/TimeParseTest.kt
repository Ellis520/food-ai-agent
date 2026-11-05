package com.foodmoment.app

import com.foodmoment.app.core.Time
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 服务端时间解析。样例取自 food-ai-agent 的真实返回，
 * 小数秒位数不固定，且必须按偏移量解释（否则会差 8 小时）。
 */
class TimeParseTest {

    @Test
    fun parsesMicrosecondsWithOffset() {
        val millis = Time.parseIsoMillis("2026-11-04T07:32:11.043697+00:00")
        assertEquals("15:32", Time.timeLabel(millis!!))
    }

    @Test
    fun parsesNoFraction() {
        val millis = Time.parseIsoMillis("2026-11-04T07:32:11+00:00")
        assertEquals("15:32", Time.timeLabel(millis!!))
    }

    @Test
    fun parsesZulu() {
        val millis = Time.parseIsoMillis("2026-11-04T07:32:11.043Z")
        assertEquals("15:32", Time.timeLabel(millis!!))
    }

    @Test
    fun treatsOffsetlessAsUtc() {
        val millis = Time.parseIsoMillis("2026-11-04T07:32:11")
        assertEquals("15:32", Time.timeLabel(millis!!))
    }

    @Test
    fun parsesLocalOffset() {
        val millis = Time.parseIsoMillis("2026-11-04T15:32:11+08:00")
        assertEquals("15:32", Time.timeLabel(millis!!))
    }

    @Test
    fun rejectsGarbage() {
        assertNull(Time.parseIsoMillis(""))
        assertNull(Time.parseIsoMillis("yesterday"))
        assertNull(Time.parseIsoMillis(null))
    }
}
