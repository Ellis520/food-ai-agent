package com.foodmoment.app.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** 页面标识（7.1）。底部导航 5 项，计划/分析/社区走二级。 */
sealed class Screen(val route: String, val label: String) {
    data object Onboarding : Screen("onboarding", "")
    data object Home : Screen("home", "首页")
    data object Chat : Screen("chat", "聊天")
    data object Plans : Screen("plans", "计划")
    data object Records : Screen("records", "记录")
    data object Nutrition : Screen("nutrition", "营养")
    /** 拍照识别菜品（底部一级入口，取代原来的营养位） */
    data object Photo : Screen("photo", "拍照")
    data object Menu : Screen("menu", "菜单管理")
    data object Habits : Screen("habits", "分析")
    data object Community : Screen("community", "社区")
    data object Settings : Screen("settings", "我的")

    /** 帖子详情。带参数，因此是 data class 而非 data object。 */
    data class PostDetail(val postId: String) : Screen("post_detail", "帖子详情")

    /** 附近商家（高德搜索） */
    data object Nearby : Screen("nearby", "附近")

    /** 地图详情：定位到某个商家 */
    data class MapDetail(val lng: Double, val lat: Double, val title: String) : Screen("map_detail", "地图") {
        override fun equals(other: Any?): Boolean =
            other is MapDetail && other.lng == lng && other.lat == lat && other.title == title

        override fun hashCode(): Int = ((lng.hashCode() * 31 + lat.hashCode()) * 31) + title.hashCode()
    }
}

val BottomTabs: List<Screen> = listOf(
    Screen.Home, Screen.Chat, Screen.Records, Screen.Photo, Screen.Settings
)

/**
 * 极简回退栈。不引入 navigation-compose，减少一个版本对齐面。
 */
class Navigator(start: Screen = Screen.Home) {

    var current: Screen by mutableStateOf(start)
        private set

    private val stack = mutableStateListOf<Screen>()

    val canGoBack: Boolean get() = stack.isNotEmpty()

    fun go(screen: Screen) {
        if (screen == current) return
        stack.add(current)
        current = screen
    }

    fun back(): Boolean {
        if (stack.isEmpty()) return false
        current = stack.removeAt(stack.lastIndex)
        return true
    }

    fun reset(screen: Screen) {
        stack.clear()
        current = screen
    }
}
