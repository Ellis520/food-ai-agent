package com.foodmoment.app.core

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * 一次性取当前位置（不引入 Google Play Services 的 FusedLocationProvider，
 * 避免多一个依赖和版本对齐面）。
 *
 * 返回的是 WGS-84 原始坐标，调用方负责转成 GCJ-02 再交给高德。
 */
object Locator {

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * 取最后已知位置，再尝试一次单次定位。
     * 任何失败（无权限、定位服务关闭、超时）都返回 null，由调用方回退到手动中心点。
     *
     * 注意：这里故意不做超时阻塞等待，只取系统已有的位置或单次更新，
     * 免得用户在没开定位时白等十几秒。
     */
    @SuppressLint("MissingPermission")
    suspend fun current(context: Context): Location? {
        if (!hasPermission(context)) return null
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return null

        val lastKnown = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER
        ).mapNotNull { provider ->
            runCatching {
                if (manager.isProviderEnabled(provider)) manager.getLastKnownLocation(provider) else null
            }.getOrNull()
        }.maxByOrNull { it.time }

        // 缓存位置在 2 分钟内就直接用，不再等新定位
        if (lastKnown != null && System.currentTimeMillis() - lastKnown.time < 2 * 60_000L) {
            return lastKnown
        }

        val fresh = requestSingleUpdate(manager)
        return fresh ?: lastKnown
    }

    @SuppressLint("MissingPermission")
    private suspend fun requestSingleUpdate(manager: LocationManager): Location? =
        suspendCoroutine { cont ->
            val providers = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
                .filter { runCatching { manager.isProviderEnabled(it) }.getOrDefault(false) }
            if (providers.isEmpty()) {
                cont.resume(null)
                return@suspendCoroutine
            }
            var resumed = false
            val listener = object : android.location.LocationListener {
                override fun onLocationChanged(location: Location) {
                    if (resumed) return
                    resumed = true
                    runCatching { manager.removeUpdates(this) }
                    cont.resume(location)
                }

                // Android 30+ 必须实现；不关心状态变化
                override fun onProviderEnabled(provider: String) = Unit
                override fun onProviderDisabled(provider: String) = Unit
                @Deprecated("Deprecated in Java")
                override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) = Unit
            }
            runCatching {
                // 网络定位通常几秒内就有结果，GPS 可能很久，所以先要网络、再要 GPS
                manager.requestLocationUpdates(providers.first(), 0L, 0f, listener, android.os.Looper.getMainLooper())
            }.onFailure {
                if (!resumed) {
                    resumed = true
                    cont.resume(null)
                }
            }
        }
}
