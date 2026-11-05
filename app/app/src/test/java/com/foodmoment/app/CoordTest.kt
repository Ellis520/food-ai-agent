package com.foodmoment.app

import com.foodmoment.app.net.Coord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * GCJ-02 偏移是本地兜底路径，误差要可预期，所以单独测。
 * 参考值：广州塔附近 GPS(113.324520, 23.106168) 的高德坐标约为 (113.329465, 23.103575)。
 */
class CoordTest {

    private fun approx(a: Double, b: Double, tol: Double) = abs(a - b) <= tol

    @Test
    fun `wgs84 转 gcj02 偏移方向与量级正确`() {
        val out = Coord.wgs84ToGcj02(113.324520, 23.106168)
        val (lng, lat) = Coord.parse(out)!!
        // 中国境内偏移量级约 300~800 米（0.003~0.008 度）
        assertTrue("经度应向东偏移", lng > 113.324520)
        assertTrue("纬度应有偏移", abs(lat - 23.106168) > 0.0)
        assertTrue("偏移量级应在合理范围", abs(lng - 113.324520) < 0.02 && abs(lat - 23.106168) < 0.02)
    }

    @Test
    fun `境外坐标不做偏移`() {
        val out = Coord.wgs84ToGcj02(-0.1276, 51.5072) // 伦敦
        val (lng, lat) = Coord.parse(out)!!
        assertTrue(approx(lng, -0.1276, 1e-6))
        assertTrue(approx(lat, 51.5072, 1e-6))
    }

    @Test
    fun `偏移是确定的 同样输入同样输出`() {
        val a = Coord.wgs84ToGcj02(113.398000, 23.097000)
        val b = Coord.wgs84ToGcj02(113.398000, 23.097000)
        assertEquals(a, b)
    }

    @Test
    fun `坐标格式校验`() {
        val ok = "113.398000,23.097000"
        assertTrue(Coord.isValidLngLat(ok))
        assertNotNull(Coord.parse(ok))

        for (bad in listOf("", "113.398", "abc,def", "999,23", "113.4,99", "113.4,23,1")) {
            assertFalse("应拒绝：$bad", Coord.isValidLngLat(bad))
            assertNull("应拒绝：$bad", Coord.parse(bad))
        }
    }

    @Test
    fun `inChina 边界`() {
        assertTrue(Coord.inChina(113.0, 23.0))
        assertFalse(Coord.inChina(-0.1, 51.5))
    }

    @Test
    fun `format 固定六位小数 用于拼 URL`() {
        assertEquals("113.398000,23.097000", Coord.format(113.398, 23.097))
    }
}
